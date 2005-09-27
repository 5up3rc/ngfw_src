/*
 * Copyright (c) 2003 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
#include <stdlib.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <netinet/udp.h>
#include <arpa/inet.h>
#include <string.h>
#include <netinet/ip_icmp.h>
#include <netinet/ip.h>
#include <mvutil/errlog.h>
#include <mvutil/debug.h>
#include <mvutil/list.h>
#include <mvutil/unet.h>

#include "libnetcap.h"
#include "netcap_hook.h"
#include "netcap_queue.h"
#include "netcap_globals.h"
#include "netcap_sesstable.h"
#include "netcap_session.h"
#include "netcap_icmp.h"
#include "netcap_icmp_msg.h"
#include "netcap_shield.h"

/* Cleanup at most 2 UDP packets per iteration */
#define _ICMP_CACHE_CLEANUP_MAX 2

static struct {
    int fd;
} _icmp = {
    .fd -1
};

typedef enum
{
    /* an error occured while finding the session */
    _FIND_ERROR = -1,

    /* Session exists, and packet was placed into the correct mailbox. */
    _FIND_EXIST = 0,

    /* new session was created and the packet was placed into its client mailbox */
    _FIND_NEW   = 1,

    /* packet cannot be associated with a session, and should be dealt with individually */
    _FIND_NONE  = 2,

    /* packet cannot be associated with a session and should be dropped */
    _FIND_DROP  = 3
} _find_t;

/* Duplicated functionality from netcap_udp.c */
static int _cache_packet( char* full_pkt, int full_pkt_len, mailbox_t* icmp_mb );

static int _restore_cached_msg( mailbox_t* mb, netcap_icmp_msg_t* msg );

static int _shield_check_reputation( netcap_pkt_t* pkt, struct in_addr* ip, netcap_intf_t intf );

/** 
 * Retrieve an ICMP session using the packet as the key 
 */
static netcap_session_t* _icmp_get_tuple( netcap_pkt_t* pkt )
{    
    /* pkt->data has already been tested for size and validity */
    int id = ntohs( ((struct icmphdr*)pkt->data)->un.echo.id );
    
    /* The ID is treated as the sequence number, this way each ICMP message is distinguishable */
    return netcap_nc_sesstable_get_tuple( !NC_SESSTABLE_LOCK, IPPROTO_UDP,
                                          pkt->src.host.s_addr, pkt->dst.host.s_addr,
                                          0, 0, id );
}

/**
 * Retrieve a UDP or TCP session using the information from an error message as the key
 * This also updates mb with the correct value.
 */
static netcap_session_t* _icmp_get_error_session( netcap_pkt_t* pkt, mailbox_t** mb )
{
    struct ip*     ip_header;
    struct tcphdr* tcp_header;
    struct udphdr* udp_header;
    struct icmp*   icmp_header;
    netcap_session_t* netcap_sess;
    int ping_id = 0;
    int protocol = -1;

    in_addr_t src_host;
    in_addr_t dst_host;
    u_short src_port;
    u_short dst_port;
    
    /* Default to NULL */
    *mb = NULL;

    /* XXX MOVE ALL OF THIS LOGIC TO A COMMON PLACE */
    if ( pkt->data_len < ICMP_ADVLENMIN ) {
        return errlog_null ( ERR_WARNING, "Invalid ICMP error packet, %d < %d\n", 
                             pkt->data_len, ICMP_ADVLENMIN );
    }

    ip_header  = &((struct icmp*)pkt->data)->icmp_ip;
    
    if ( ip_header->ip_hl > 15  || ip_header->ip_hl < ( sizeof(struct ip) >> 2)) {
        errlog( ERR_WARNING,"Illogical IP header length (%d), Assuming 5.\n", ip_header->ip_hl );
        tcp_header = (struct tcphdr*) ( (char*)ip_header + sizeof(struct iphdr));
    }
    else if ( pkt->data_len >= ( ICMP_ADVLENMIN - sizeof(struct ip) + ( ip_header->ip_hl << 2 ))) {
        tcp_header = (struct tcphdr*) ( (char*)ip_header + ( 4 * ip_header->ip_hl ));
    } else {
        return errlog_null( ERR_WARNING, "Invalid ICMP error packet, %d < %d\n",
                            pkt->data_len, 
                            ( ICMP_ADVLENMIN - sizeof( struct ip ) + ( ip_header->ip_hl << 2 )));
    }
    
    udp_header  = (struct udphdr*)tcp_header;
    icmp_header = (struct icmp*)tcp_header;
    
    /* Host and dest are swapped since this is the packet that was sent out */
    src_host = ip_header->ip_dst.s_addr;
    dst_host = ip_header->ip_src.s_addr;
    
    switch ( ip_header->ip_p ) {
    case IPPROTO_TCP:
        protocol = IPPROTO_TCP;
        src_port = ntohs ( tcp_header->dest );
        dst_port = ntohs ( tcp_header->source );
        ping_id  = 0;
        break;

    case IPPROTO_UDP:
        protocol = IPPROTO_UDP;
        src_port = ntohs ( udp_header->dest );
        dst_port = ntohs ( udp_header->source );
        ping_id  = 0;
        break;

    case IPPROTO_ICMP:
        protocol = IPPROTO_UDP;
        
        if ( netcap_icmp_verify_type_and_code( icmp_header->icmp_type, icmp_header->icmp_code ) < 0 ) {
            return errlog_null( ERR_WARNING, "netcap_icmp_verify_type_and_code\n" );
        }
        
        if ( icmp_header->icmp_type == ICMP_ECHO || icmp_header->icmp_type == ICMP_ECHOREPLY ) {
            src_port = 0;
            dst_port = 0;
            ping_id  = ntohs( icmp_header->icmp_id );
        } else {
            debug( 5, "ICMP: Unable to lookup ICMP Error session for icmp type %d, code %d\n", 
                   icmp_header->icmp_type, icmp_header->icmp_code );
            return NULL;
        }
        break;

    default:
        return errlog_null( ERR_WARNING, "ICMP: Unable to lookup session for protocol %d\n", ip_header->ip_p );
                            
    }
    
    debug( 10, "ICMP: Looking up packet %s:%d -> %s:%d (%d)\n", 
           unet_next_inet_ntoa( src_host ), src_port, unet_next_inet_ntoa( dst_host ), dst_port, ping_id );
    
    netcap_sess = netcap_nc_sesstable_get_tuple( !NC_SESSTABLE_LOCK, protocol,
                                                 src_host, dst_host, src_port, dst_port, ping_id );

    if ( netcap_sess != NULL ) {
        netcap_intf_t intf = -1;
        
        // Figure out the correct mailbox (TCP only has a server mailbox, no client mailbox)
        if ( src_host == netcap_sess->srv.srv.host.s_addr ) {
            /* Error packet from the server wrg packet from the client */
            debug( 10, "ICMP: Server mailbox\n" );
            *mb  =  &netcap_sess->srv_mb;
            intf = netcap_sess->srv.intf;
        } else if ( src_host == netcap_sess->cli.cli.host.s_addr ) {
            /* Error packet from the client wrg to a packet from the server */
            debug( 10, "ICMP: Client mailbox\n" );
            if ( ip_header->ip_p == IPPROTO_TCP ) {
                debug( 4, "ICMP: Received ICMP message from client for TCP session\n" );
                netcap_sess = NULL;
                *mb = NULL;
            } else {
                *mb  = &netcap_sess->cli_mb;
                intf = netcap_sess->cli.intf;
            }
        } else {
            *mb = NULL;
            return errlog_null( ERR_CRITICAL, "Cannot determine correct mailbox: msg %s, cli %s, srv %s\n",
                                unet_next_inet_ntoa( src_host ), 
                                unet_next_inet_ntoa( netcap_sess->cli.cli.host.s_addr ),
                                unet_next_inet_ntoa( netcap_sess->srv.srv.host.s_addr ));
        }

        if (( *mb != NULL ) && ( pkt->src_intf != intf )) {
            *mb = NULL;
            debug( 5, "ICMP: Packet from the incorrect interface expected %d actual %d\n", 
                    intf, pkt->src_intf );
            return NULL;
        }
    } else {
        debug( 5, "ICMP: No session for packet with protocol %d from %s\n", protocol,
               unet_next_inet_ntoa( src_host ));
    }
    
    return netcap_sess;
}

static netcap_session_t* _icmp_create_session( netcap_pkt_t* pkt )
{
    netcap_session_t* session;
    u_short icmp_client_id = 0;
    
    icmp_client_id = ntohs(((struct icmphdr*)pkt->data)->un.echo.id );

    debug( 10, "ICMP: Creating a new session for %s -> %s\n",
           unet_next_inet_ntoa( pkt->src.host.s_addr ), unet_next_inet_ntoa( pkt->dst.host.s_addr ));
    
    /* XXXXXX DO SHIELD STUFF */
    
    /* XXX is it safe to just use udp sessions */
    session = netcap_udp_session_create( pkt );

    /* pkt->data has already been tested for size and validity */    
    if ( netcap_nc_sesstable_add_tuple( !NC_SESSTABLE_LOCK, session, IPPROTO_UDP,
                                        pkt->src.host.s_addr, pkt->dst.host.s_addr,
                                        0, 0, icmp_client_id ) < 0 ) {
        netcap_udp_session_raze( !NC_SESSTABLE_LOCK, session );
        return errlog_null( ERR_CRITICAL, "netcap_nc_sesstable_add_tuple\n" );
    }
    
    /* Insert the session ID */
    if ( netcap_nc_sesstable_add ( !NC_SESSTABLE_LOCK, session )) {
        netcap_udp_session_raze( !NC_SESSTABLE_LOCK, session );
        return errlog_null( ERR_CRITICAL, "netcap_nc_sesstable_add\n" );
    }

    /* Update the ICMP client session identifier */
    session->icmp.client_id = icmp_client_id;
    session->icmp.server_id = icmp_client_id;

    return session;
}

/**
 * Determine which mailbox a packet should go into */
static int _icmp_get_mailbox( netcap_pkt_t* pkt, netcap_session_t* session,
                                         mailbox_t** mb, mailbox_t** icmp_mb )
{
    *mb      = NULL;
    *icmp_mb = NULL;
    netcap_intf_t intf = -1;

    // Figure out the correct mailbox
    if ( pkt->src.host.s_addr == session->cli.cli.host.s_addr ) {
        debug( 10, "ICMP: Client mailbox\n" );
        *mb      = &session->cli_mb;
        *icmp_mb = &session->icmp_cli_mb;
        intf = session->cli.intf;
    } else if ( pkt->src.host.s_addr == session->srv.srv.host.s_addr ) {
        debug( 10, "ICMP: Server mailbox\n" );
        *mb = &session->srv_mb;
        *icmp_mb = &session->icmp_srv_mb;
        intf = session->srv.intf;
    } else {
        return errlog( ERR_CRITICAL, "Cannot determine correct mailbox: pkt %s, cli %s, srv %s\n",
                       unet_next_inet_ntoa( pkt->src.host.s_addr ), 
                       unet_next_inet_ntoa( session->cli.cli.host.s_addr ),
                       unet_next_inet_ntoa( session->srv.srv.host.s_addr ));
    }
    
    if ( pkt->src_intf != intf ) {
        debug( 5, "ICMP: Packet from the incorrect interface expected %d actual %d\n",
               intf, pkt->src_intf );
        return _FIND_DROP;
    }
    
    return 0;
}

/**
 * Put a packet into the mailbox for a session */
static int _icmp_put_mailbox( mailbox_t* mb, netcap_pkt_t* pkt )
{
    if ( mailbox_size( mb ) > MAX_MB_SIZE ) {
        return errlog( ERR_WARNING, "ICMP: Mailbox Full - Dropping Packet (from %s)\n", 
                       inet_ntoa( pkt->src.host ));
    } else if ( mailbox_put( mb, (void*)pkt ) < 0 ) {
        return perrlog("mailbox_put");
    }
    
    return 0;
}

static int  _netcap_icmp_send( char *data, int data_len, netcap_pkt_t* pkt, int flags );

/* Move the data pointer so that it points to the correct location inside of the packet, 
 * rather than starting at the header of the packet
 */
static int  _icmp_fix_packet( netcap_pkt_t* pkt, char** full_pkt, int* full_pkt_len );

/**
 * Determine how a session should be handled.
 */
static _find_t _icmp_find_session( netcap_pkt_t* pkt, netcap_session_t** netcap_sess, 
                                   char* full_pkt, int full_pkt_len );

static struct cmsghdr * my__cmsg_nxthdr(struct msghdr *msg, struct cmsghdr *cmsg, int size);

int  netcap_icmp_init()
{
    if (( _icmp.fd = socket( AF_INET, SOCK_RAW, IPPROTO_ICMP )) < 0 )
        return perrlog("socket");

    return 0;
}

int  netcap_icmp_cleanup()
{
    int fd;
    fd = _icmp.fd;
    _icmp.fd = -1;

    if (( fd > 0 ) && close( fd ) < 0 ) {
        perrlog( "close" );
    }

    return 0;
}

void netcap_icmp_null_hook( netcap_session_t* netcap_sess, netcap_pkt_t* pkt, void* arg)
{
    errlog( ERR_CRITICAL, "ICMP: NULL HOOK, freeing packet(%#10x) and session(%#10x)\n", pkt, netcap_sess );

    if ( pkt != NULL ) {
        netcap_pkt_raze( pkt );
    }

    if ( netcap_sess != NULL ) {
        netcap_session_raze( netcap_sess );
    }
}

int  netcap_icmp_call_hook( netcap_pkt_t* pkt )
{
    int ret = -1;
    netcap_session_t* netcap_sess = NULL;

    /* Drop the packet, but hold onto the data. */
    debug( 10, "ICMP: Dropping packet (%#10x) and passing data\n", pkt->packet_id );
    
    do {
        char* full_pkt;
        int full_pkt_len;

        if ( netcap_set_verdict( pkt->packet_id, NF_DROP, NULL, 0 ) < 0 ) {
            ret = errlog( ERR_CRITICAL, "netcap_set_verdict\n" );
            break;
        }
        
        /* Clear out the packet id */
        pkt->packet_id = 0;
        
        if ( _icmp_fix_packet( pkt, &full_pkt, &full_pkt_len ) < 0 ) {
            ret = errlog( ERR_CRITICAL, "_icmp_fix_packet\n" );
            break;
        }
        
        switch( _icmp_find_session( pkt, &netcap_sess, full_pkt, full_pkt_len )) {
        case _FIND_EXIST:
            /* Packets in mailbox, nothing left to do */
            ret = 0;
            break;
            
            /* Call the hooks */
        case _FIND_NEW:
            /* Packet has already been put into the session mailbox */
            pkt = NULL;
            /* fallthrough */
        case _FIND_NONE:
            debug( 10, "ICMP: Calling global icmp hook\n" );
            
            global_icmp_hook( netcap_sess, pkt, NULL ); /* XXX NULL arg */
            ret = 0;
            break;

        case _FIND_DROP:
            debug( 10, "ICMP: Dropping packet\n" );
            netcap_pkt_raze( pkt );
            pkt = NULL;
            ret = 0;
            
            break;

        case _FIND_ERROR:
        default:
            ret = errlog( ERR_CRITICAL, "_icmp_find_session\n" );
        }
    } while ( 0 );

    if ( ret < 0 ) {
        if ( pkt != NULL ) {
            netcap_pkt_raze( pkt );
        }
    }

    return ret;
}

int  netcap_icmp_send( char *data, int data_len, netcap_pkt_t* pkt )
{
    return _netcap_icmp_send( data, data_len, pkt, 0 );
}

int  netcap_icmp_update_pkt( char* data, int data_len, int data_lim,
                              int icmp_type, int icmp_code, int id, mailbox_t* icmp_mb )
{
    /* Length of the data that is copied in */
    int len;

    /* New packet length */
    int new_len = data_len;

    struct icmp*   icmp_header;
    netcap_icmp_msg_t* msg;
    char* reply_data;

    if ( data == NULL || icmp_mb == NULL )
        return errlogargs();

    if ( data_len < data_lim ) {
        return errlog( ERR_CRITICAL, "Data is larger than the buffer\n" );
    }

    if ( netcap_icmp_verify_type_and_code( icmp_type, icmp_code ) < 0 ) {
        return errlog( ERR_WARNING, "netcap_icmp_verify_type_and_code\n" );
    }


    /* By default do not modify the length of the packet */
    new_len = data_len;
    
    switch ( icmp_type ) {
    case ICMP_ECHO:
        /* fallthrough */
    case ICMP_ECHOREPLY:
        if ( data_lim < ICMP_MINLEN ) {
            return errlog( ERR_WARNING, "Not enough room, %d < %d\n", data_lim, ICMP_MINLEN );
        }
        
        icmp_header = (struct icmp*)data;

        if ( id > 0 ) {
            /* id is a 16 bit field */
            id = id & 0xFFFF;
            /* Convert to a network short */
            id = htons( id );
            if ( icmp_header->icmp_id != id ) {
                debug( 9, "ICMP: change id, %d to %d\n", ntohs( icmp_header->icmp_id ), ntohs( id ));
                       
                icmp_header->icmp_id = id;

                /* Update the checksum */
                icmp_header->icmp_cksum = 0;
                icmp_header->icmp_cksum = unet_in_cksum( (u_int16_t*)data, new_len );
            }
        }
        break;

    case ICMP_PARAMETERPROB:
        errlog( ERR_WARNING, "ICMP: parameter problem packet\n" );
        /* fallthrough */
        /* XXX Doesn't change the code for packets that do not fit one of the error conditions */

    case ICMP_DEST_UNREACH:
        /* fallthrough */
    case ICMP_SOURCE_QUENCH:
        /* fallthrough */
    case ICMP_REDIRECT:
        /* fallthrough */
    case ICMP_TIME_EXCEEDED:
        /* fallthrough */
        /* Fix the packet */

        /* XXX May need the ID of the last packet received */
        if ( data_lim < ICMP_ADVLENMIN ) {
            return errlog( ERR_WARNING, "Not enough room, %d < %d\n", data_lim, ICMP_ADVLENMIN );
        }
        
        if (( msg = mailbox_timed_get( icmp_mb, 1 )) == NULL ) {
            return errlog( ERR_CRITICAL, "mailbox_timed_get\n" );
        }
        
        reply_data = &msg->data;
        
        icmp_header = (struct icmp*)data;

        if ( icmp_header->icmp_type != icmp_type ) {
            debug( 4, "ICMP: Modifying type on packet (%d->%d)\n", icmp_header->icmp_type, icmp_type );
            icmp_header->icmp_type = icmp_type;
        }

        if ( icmp_header->icmp_code != icmp_code ) {
            debug( 4, "ICMP: Modifying code on packet (%d->%d)\n", icmp_header->icmp_code, icmp_code );
            icmp_header->icmp_code = icmp_code;
        }

        len = msg->data_len;
        if ( data_lim < ( sizeof( struct icmphdr ) + len )) {
            debug( 9, "ICMP: Not enough space to put entire cached packet, truncated to %d bytes\n",
                   data_lim - sizeof( struct icmphdr ));
            len = data_lim - sizeof( struct icmphdr );
        }
        
        /* Copy in the data packet */
        debug( 10, "ICMP: Updating packet: copying in %d bytes\n", len );

        memcpy( &icmp_header->icmp_ip, reply_data, len );

        /* Update the length of the packet */
        new_len = sizeof( struct icmphdr ) + len;

        if ( _restore_cached_msg( icmp_mb, msg ) < 0 ) {
            errlog( ERR_CRITICAL, "restore_cached_msg\n" );
        }
               
        /* Update the checksum */
        icmp_header->icmp_cksum = 0;
        icmp_header->icmp_cksum = unet_in_cksum( (u_int16_t*)data, new_len );

    default:
        break;
    }        
    
    return new_len;
}

int  netcap_icmp_get_source( char* data, int data_len, netcap_pkt_t* pkt, struct in_addr* source )
{
    struct icmp* icmp_pkt;

    if ( data == NULL || pkt == NULL || source == NULL ) return errlogargs();
    
    if ( data_len < ICMP_MINLEN ) {
        errlog( ERR_WARNING, "ICMP Packet is too short\n" );
        return 0;
    }

    icmp_pkt = (struct icmp*)data;

    if ( netcap_icmp_verify_type_and_code( icmp_pkt->icmp_type, icmp_pkt->icmp_code ) < 0 ) {
        errlog( ERR_WARNING, "netcap_icmp_verify_type_and_code\n" );
    }
    
    if ( ICMP_INFOTYPE( icmp_pkt->icmp_type )) {
        return 0;
    }
    
    if ( data_len <  ICMP_ADVLENMIN ) {
        errlog( ERR_WARNING, "ICMP Packet is too short" );
        return 0;
    }
        
    if ( icmp_pkt->icmp_ip.ip_dst.s_addr != pkt->src.host.s_addr ) {
        memcpy( source, &pkt->src.host, sizeof( struct in_addr ));
        return 1;
    }
    
    return 0;
}

int  netcap_icmp_verify_type_and_code( u_int type, u_int code )
{
    if ( type > NR_ICMP_TYPES ) 
        return -1;

    switch ( type ) {
    case ICMP_DEST_UNREACH:
        if ( code > NR_ICMP_UNREACH ) return -1;
        break;

    case ICMP_REDIRECT:
        if ( code > ICMP_REDIRECT_TOSHOST ) return -1;
        break;
    case ICMP_TIME_EXCEEDED:
        if ( code > ICMP_TIMXCEED_REASS ) return -1;
        break;
    case ICMP_PARAMETERPROB:
        if ( code > ICMP_PARAMPROB_OPTABSENT ) return -1;
        break;
    case ICMP_SOURCE_QUENCH:
        /* fallthrough */
    case ICMP_ECHO:
        /* fallthrough */
    case ICMP_ECHOREPLY:
        /* fallthrough */
    case ICMP_TIMESTAMP:
        /* fallthrough */
    case ICMP_TIMESTAMPREPLY:
        /* fallthrough */
    case ICMP_INFO_REQUEST:
        /* fallthrough */
    case ICMP_INFO_REPLY:
        /* fallthrough */
    case ICMP_ADDRESS:
        /* fallthrough */
    case ICMP_ADDRESSREPLY:
        /* fallthrough */
        if ( code != 0 ) return -1;
    }
    
    return 0;
}



static int  _netcap_icmp_send( char *data, int data_len, netcap_pkt_t* pkt, int flags )
{
    struct msghdr      msg;
    struct cmsghdr*    cmsg;
    struct iovec       iov[1];
    struct sockaddr_in dst;
    char               control[4096];
    int                ret;
    u_int              nfmark = ( MARK_ANTISUB | MARK_NOTRACK | (pkt->is_marked ? pkt->nfmark : 0 )); 
    /* mark is  antisub + notrack + whatever packet marks are specified */

    if ( pkt->dst_intf != NC_INTF_UNK ) {
        errlog(ERR_CRITICAL,"NC_INTF_UNK Unsupported (IP_DEVICE)\n");
    }

    /* Setup the destination */
    memcpy( &dst.sin_addr, &pkt->dst.host, sizeof(struct in_addr));
    dst.sin_port = 0; /* ICMP does not use ports */
    dst.sin_family = AF_INET;

    msg.msg_name       = &dst;
    msg.msg_namelen    = sizeof( dst );
    msg.msg_iov        = iov;
    iov[0].iov_base    = data;
    iov[0].iov_len     = data_len;
    msg.msg_iovlen     = 1;
    msg.msg_flags      = 0;
    msg.msg_control    = control;
    msg.msg_controllen = 4096;

    /* tos ancillary */
    cmsg = CMSG_FIRSTHDR( &msg );
    if( !cmsg ) {
        errlog(ERR_CRITICAL,"No more CMSG Room\n");
        goto err_out;
    }
    cmsg->cmsg_len = CMSG_LEN(sizeof(pkt->tos));
    cmsg->cmsg_level = SOL_IP;
    cmsg->cmsg_type  = IP_TOS;
    memcpy( CMSG_DATA(cmsg), &pkt->tos, sizeof(pkt->tos) );

    /* ttl ancillary */
    cmsg = my__cmsg_nxthdr( &msg, cmsg, sizeof(pkt->ttl) );
    if( !cmsg ) {
        errlog(ERR_CRITICAL,"No more CMSG Room\n");
        goto err_out;
    }
    cmsg->cmsg_len   = CMSG_LEN(sizeof(pkt->ttl));
    cmsg->cmsg_level = SOL_IP;
    cmsg->cmsg_type  = IP_TTL;
    memcpy( CMSG_DATA(cmsg), &pkt->ttl, sizeof(pkt->ttl) );
    
    /* Source IP ancillary data */
    cmsg = my__cmsg_nxthdr( &msg, cmsg, sizeof(pkt->ttl) );
    if( !cmsg ) {
        errlog( ERR_CRITICAL, "No more CMSG Room\n" );
        goto err_out;
    }
    cmsg->cmsg_len   = CMSG_LEN(sizeof( struct in_addr ));
    cmsg->cmsg_level = SOL_IP;
    cmsg->cmsg_type  = IP_SADDR;
    memcpy( CMSG_DATA(cmsg), &pkt->src.host, sizeof( struct in_addr ));

    /* nfmark */
    cmsg = my__cmsg_nxthdr( &msg, cmsg, sizeof(pkt->ttl) );
    if( !cmsg ) {
        errlog( ERR_CRITICAL, "No more CMSG Room\n" );
        goto err_out;
    }
    cmsg->cmsg_len = CMSG_LEN(sizeof(nfmark));
    cmsg->cmsg_level = SOL_IP;
    cmsg->cmsg_type  = IP_SENDNFMARK;
    memcpy( CMSG_DATA( cmsg ), &nfmark, sizeof(nfmark));

    /* sanity check */
    cmsg =  my__cmsg_nxthdr(&msg, cmsg, 0);
    if ( ((char*)cmsg) > control + MAX_CONTROL_MSG)
        errlog(ERR_CRITICAL,"CMSG overrun");

    msg.msg_controllen =
        CMSG_SPACE(sizeof(pkt->src.host)) +
        CMSG_SPACE(sizeof(pkt->tos)) +
        CMSG_SPACE(sizeof(pkt->ttl)) + 
        CMSG_SPACE(sizeof(nfmark));

    /* Send Packet */
    debug( 10, "sending ICMP %s -> %s  data_len:%i ttl:%i tos:%i nfmark:%#10x\n",
           unet_next_inet_ntoa(pkt->src.host.s_addr), 
           unet_next_inet_ntoa(pkt->dst.host.s_addr),
           data_len, pkt->ttl, pkt->tos, nfmark);

    
    if (( ret = sendmsg( _icmp.fd, &msg, flags )) < 0 ) {
        if ( errno == EPERM ) {
            errlog( ERR_CRITICAL, "ICMP: EPERM sending an ICMP packet\n" );
        } else {
            errlog(ERR_CRITICAL,"sendmsg: %s | ",errstr);
            errlog_noprefix(ERR_CRITICAL, "(%s -> ", inet_ntoa(pkt->src.host));
            errlog_noprefix(ERR_CRITICAL, "%s) data_len:%i ttl:%i tos:%i nfmark:%#10x\n",
                            inet_ntoa(pkt->dst.host),data_len, pkt->ttl, pkt->tos, nfmark);
        }
    }
    
    goto out;

 err_out:
    errlog( ERR_WARNING, "ICMP: Unable to send packet\n" );
    ret = -1;
 out:
    return ret;
}

static int  _icmp_fix_packet( netcap_pkt_t* pkt, char** full_pkt, int* full_pkt_len )
{
    int offset;

    struct iphdr* iph = (struct iphdr*)pkt->data;

    /* Have to move the data pointer, since right now it points to the whole packet */
    
    /* Get the length of the ip header */
    offset = iph->ihl;
    
    /* Validate the offset is valid XXX Magic numbers */
    if ( offset > 20 ) {
        return errlog( ERR_CRITICAL, "ICMP: Invalid data offset - %d\n", offset );
    }
    
    /* Words to bytes */
    offset = offset << 2;
    *full_pkt_len = pkt->data_len;
    pkt->data_len = pkt->data_len - offset;
    
    if (( pkt->data_len < 0 ) || ( pkt->data_len > QUEUE_MAX_MESG_SIZE )) {
        return errlog( ERR_CRITICAL, "ICMP: Invalid data size - %d\n", pkt->data_len );
    }
    
    /* Remove the header from the data buffer, and just move in the data */
    if ( pkt->buffer == NULL ) {
        return errlog( ERR_CRITICAL, "pkt->buffer is null\n" );
    }
    
    *full_pkt = pkt->data;
    pkt->data = &pkt->data[offset];

    return 0;
}

static _find_t _icmp_find_session( netcap_pkt_t* pkt, netcap_session_t** netcap_sess, 
                                   char* full_pkt, int full_pkt_len )
{
    /* Lookup the session information */
    struct icmp *packet = (struct icmp*)pkt->data;
    int ret = -1;

    netcap_session_t* session;
    mailbox_t* mb      = NULL;
    mailbox_t* icmp_mb = NULL;
    
    /* Grab the session table lock */
    SESSTABLE_WRLOCK();
    do {
        switch( packet->icmp_type ) {
            /* These both are treated as ICMP sessions with port 0 */
        case ICMP_ECHOREPLY:
            /* fallthrough */
        case ICMP_ECHO:
            if (( session = _icmp_get_tuple( pkt )) == NULL ) {
                /* Check if this sessions should be allowed */
                if ( _shield_check_reputation( pkt, &pkt->src.host, pkt->src_intf ) < 0 ) {
                    ret = _FIND_DROP;
                    break;
                }

                /* Let the shield know about the request */
                if ( netcap_shield_rep_add_request( &pkt->src.host ) < 0 ) {
                    errlog ( ERR_CRITICAL, "netcap_shield_rep_add_request\n" );
                }

                if ( netcap_shield_rep_add_chunk( &pkt->src.host, IPPROTO_ICMP, pkt->data_len ) < 0 ) {
                    errlog( ERR_CRITICAL, "netcap_shield_rep_add_chunk" );
                }

                if (( session = _icmp_create_session( pkt )) == NULL ) {
                    ret = errlog( ERR_CRITICAL, "_icmp_create_session\n" );
                    break;
                }
                
                /* Let the shield know about the new session */
                if ( netcap_shield_rep_add_session( &pkt->src.host ) < 0 ) {
                    errlog ( ERR_CRITICAL, "netcap_shield_rep_add_session\n" );
                }                
                
                /* Drop the packet into the client mailbox */
                mb = &session->cli_mb;

                /* Drop the packet into the client ICMP mailbox */
                icmp_mb = &session->icmp_cli_mb;
                
                /* Indicate that a new session was created */
                *netcap_sess = session;
            } else {
                /* Add this chunk against the client reputation */
                if ( netcap_shield_rep_add_chunk( &session->cli.cli.host, IPPROTO_ICMP, pkt->data_len ) < 0 ) {
                    errlog( ERR_CRITICAL, "netcap_shield_rep_add_chunk\n" );
                }

                /* Check if this sessions should be allowed */
                if ( _shield_check_reputation( pkt, &session->cli.cli.host, session->cli.intf ) < 0 ) {
                    ret = _FIND_DROP;
                    break;
                }

                if (( ret = _icmp_get_mailbox( pkt, session, &mb, &icmp_mb )) < 0 ) {
                    ret = errlog( ERR_CRITICAL, "_icmp_get_mailbox\n" );
                    break;
                } else if ( ret == _FIND_DROP ) {
                    /* Unable to find the mailbox, but drop the packet silently( it is not an error) */
                    break;
                }
                /* Indicate that a new session was not created */
                *netcap_sess = NULL;
            }

            /* Put the packet into the mailbox */
            if ( _icmp_put_mailbox( mb, pkt ) < 0 ) {
                ret = errlog( ERR_CRITICAL, "_icmp_put_mailbox\n" );
                break;
            }

            if ( _cache_packet( full_pkt, full_pkt_len, icmp_mb ) < 0 ) {
                ret = errlog( ERR_CRITICAL, "_cache_packet\n" );
            }
            
            /* Set the return code */
            ret = ( *netcap_sess == NULL ) ? _FIND_EXIST : _FIND_NEW;
            break;
            
        case ICMP_REDIRECT:
        case ICMP_SOURCE_QUENCH:
        case ICMP_TIME_EXCEEDED:
        case ICMP_DEST_UNREACH:
            /* Lookup the session, if it doesn't exist, then drop it */
            /* Here is the reasoning: ICMP DEST unreachable can come from two sources
             * 1. Packet is for the local host, if this is true, then the session should be
             *    in the conntrack table, and all sessions in the conntrack table are antisubscribed.
             * 2. Packet is not for the local host, if this is true, then the sessino should
             *    be in the session table, if this is true, then we put it in that sessions mb
             * Otherwise, this packet has no business being here, hence it is dropped
             */
            if (( session = _icmp_get_error_session( pkt, &mb )) == NULL ) {
                ret = _FIND_DROP;
                break;
            }
            
            if ( netcap_shield_rep_add_chunk( &session->cli.cli.host, IPPROTO_ICMP, pkt->data_len ) < 0 ) {
                                              
                errlog( ERR_CRITICAL, "netcap_shield_rep_add_chunk" );
            }

            /* Check if this sessions should be allowed */
            if ( _shield_check_reputation( pkt, &session->cli.cli.host, session->cli.intf ) < 0 ) {
                ret = _FIND_DROP;
                break;
            }

            if ( mb == NULL ) {
                ret = errlog( ERR_CRITICAL, "_icmp_get_error_session\n" );
                break;
            }
            
            /* Put the packet into the mailbox */
            if ( _icmp_put_mailbox( mb, pkt ) < 0 ) {
                ret = errlog( ERR_CRITICAL, "_icmp_put_mailbox\n" );
                break;
            }
            
            ret = _FIND_EXIST;
            break;

        case ICMP_PARAMETERPROB:
        case ICMP_TIMESTAMP:
        case ICMP_TIMESTAMPREPLY:
        case ICMP_INFO_REQUEST:
        case ICMP_INFO_REPLY:
        case ICMP_ADDRESS:
        case ICMP_ADDRESSREPLY:
            /* We don't really care about these, these packets should be dropped */
            *netcap_sess = NULL;
            ret = _FIND_DROP;
            break;
        }
    } while ( 0 );
    /* Make sure this is unlocked */
    SESSTABLE_UNLOCK();

    if ( ret < 0 ) ret = _FIND_ERROR;

    return ret;
}

static int _restore_cached_msg( mailbox_t* mb, netcap_icmp_msg_t* msg )
{
    int mb_size;
    
    if (( mb_size = mailbox_size( mb )) < 0 ) {
        netcap_icmp_msg_raze( msg );
        return errlog( ERR_CRITICAL, "mailbox_size\n" );
    }

    /* Only restore the packet if the size is zero */
    if ( mb_size == 0 ) {
        debug( 10, "ICMP: Restoring cached msg\n" );
        if ( mailbox_put( mb, (void*)msg ) < 0 ) {
            netcap_icmp_msg_raze( msg );
            return errlog( ERR_CRITICAL, "mailbox_put\n" );
        }
    } else {
        debug( 10, "ICMP: Dropping cached msg\n" );
        netcap_icmp_msg_raze( msg );
    }
    
    return 0;
}

static int _cache_packet( char* full_pkt, int full_pkt_len, mailbox_t* icmp_mb )
{
    netcap_icmp_msg_t* msg;
    netcap_icmp_msg_t* old_msg;
    int c;

    if ( full_pkt == NULL )
        return 0;
    
    if (( msg = netcap_icmp_msg_create( full_pkt, full_pkt_len )) == NULL ) {
        return errlog( ERR_CRITICAL, "netcap_icmp_msg_create\n" );
    }

    /* Try to fetch some packages out */
    for ( c = 0 ; c < _ICMP_CACHE_CLEANUP_MAX ; c++ ) {
        if (( old_msg = mailbox_try_get( icmp_mb )) != NULL ) {
            debug( 10, "ICMP: Removing cached ICMP message\n" );
            if ( netcap_icmp_msg_raze( old_msg ) < 0 ) errlog( ERR_CRITICAL, "netcap_icmp_msg_raze\n" );
        }
    }
    
    if ( mailbox_put( icmp_mb, (void*)msg ) < 0 ) {
        netcap_icmp_msg_raze( msg );
        return perrlog( "mailbox_put\n" );
    }
    
    return 0;
}

static int _shield_check_reputation( netcap_pkt_t* pkt, struct in_addr* ip, netcap_intf_t intf )
{
    netcap_shield_response_t response;
    
    if ( netcap_shield_rep_check( &response, ip, IPPROTO_ICMP, intf ) < 0 ) {
        errlog ( ERR_CRITICAL, "netcap_shield_rep_check\n" );
    } else {
        switch ( response.ans ) {
        case NC_SHIELD_DROP:
        case NC_SHIELD_RESET:
            if ( response.if_print ) {
                debug( 4, "ICMP: Shield dropped packet: %s:%d -> %s:%d\n",
                       unet_next_inet_ntoa ( pkt->src.host.s_addr ), pkt->src.port, 
                       unet_next_inet_ntoa ( pkt->dst.host.s_addr ), pkt->dst.port );
            }
            return -1;
        case NC_SHIELD_YES:
        case NC_SHIELD_LIMITED:
            break;
        default:
            errlog ( ERR_CRITICAL, "netcap_shield_rep_check\n" );
        }
    }

    return 0;
}

/* this gets rid of the mess in libc (in bits/socket.h) */
static struct cmsghdr * my__cmsg_nxthdr(struct msghdr *msg, struct cmsghdr *cmsg, int size)
{
	struct cmsghdr * ptr;

	ptr = (struct cmsghdr*)(((unsigned char *) cmsg) +  CMSG_ALIGN(cmsg->cmsg_len));

    if ((((char*)ptr) + CMSG_LEN(size)) > ((char*)msg->msg_control + msg->msg_controllen)) {
		return (struct cmsghdr *)0;
    }

	return ptr;
}

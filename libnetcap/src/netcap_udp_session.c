/**
 * $Id$
 */
#include <stdlib.h>
#include <netinet/ip.h>
#include <netinet/udp.h>
#include <mvutil/debug.h>
#include <mvutil/errlog.h>
#include <libnetcap.h>
#include "netcap_globals.h"
#include "netcap_queue.h"
#include "netcap_nfconntrack.h"
#include "netcap_session.h"
#include "netcap_sesstable.h"

/* callback for a UDP session */
static int _callback    ( netcap_session_t* netcap_sess, netcap_callback_action_t action );


int netcap_udp_session_init( netcap_session_t* netcap_sess, netcap_pkt_t* pkt ) 
{
    netcap_endpoints_t endpoints;
    int src_intf;
    int dst_intf;
    
    if ( pkt == NULL ) return errlogargs();

    if ( pkt->proto != IPPROTO_UDP ) {
        return errlog( ERR_CRITICAL, "non-udp packet for udp session: %d.\n", pkt->proto );
    }

    netcap_endpoints_bzero( &endpoints );
        
    endpoints.cli.host.s_addr = pkt->nat_info.original.src_address;
    endpoints.cli.port        = ntohs( pkt->nat_info.original.src_protocol_id );
    endpoints.srv.host.s_addr = pkt->nat_info.reply.src_address;
    endpoints.srv.port        = ntohs( pkt->nat_info.reply.src_protocol_id );
    
    //memcpy( &endpoints.cli, &pkt->src, sizeof( endpoints.cli ));
    //memcpy( &endpoints.srv, &pkt->dst, sizeof( endpoints.srv ));

    if ( pkt->src.host.s_addr == pkt->nat_info.reply.src_address ) {
        src_intf = pkt->dst_intf;
        dst_intf = pkt->src_intf;
    } else {
        src_intf = pkt->src_intf;
        dst_intf = pkt->dst_intf;
    }

    endpoints.intf = src_intf;
    if ( netcap_session_init( netcap_sess, &endpoints, dst_intf, NC_SESSION_IF_MB ) < 0 ) {
        return errlog( ERR_CRITICAL, "netcap_session_init\n" );
    }

    /* Set alive to true */
    netcap_sess->alive = 1;

    /* Set the protocol */
    netcap_sess->protocol = pkt->proto;

    /* Set the TTL and TOS value */
    netcap_sess->ttl      = pkt->ttl;
    netcap_sess->tos      = pkt->tos;

    /* Copy NAT info */
    netcap_sess->nat_info = pkt->nat_info;

    /* Set the callback, for most actions this doesn't do anything */
    netcap_sess->callback = _callback;
    
    return 0;
}

// Create a new session
netcap_session_t* netcap_udp_session_create(netcap_pkt_t* pkt)
{
    netcap_session_t* netcap_sess;

    if ((netcap_sess = netcap_udp_session_malloc()) == NULL) {
        return errlog_null(ERR_CRITICAL,"netcap_udp_session_malloc");
    }

    if ( netcap_udp_session_init(netcap_sess,pkt) < 0) {
        if ( netcap_udp_session_free(netcap_sess)) {
            errlog( ERR_CRITICAL, "netcap_udp_session_free\n" );
        }

        return errlog_null( ERR_CRITICAL, "netcap_udp_session_init\n" );
    }

    return netcap_sess;
}

int netcap_udp_session_destroy(int if_lock, netcap_session_t* netcap_sess)
{
    int err = 0;

    if ( netcap_sess == NULL ) {
        return errlog(ERR_CRITICAL,"Invalid arguments\n");
    }
    
    /* Remove the session from the endpoints first */
    netcap_sesstable_remove_session(if_lock, netcap_sess);

    // Free the session and its mailboxes
    if ( netcap_nc_session__destroy(netcap_sess,NC_SESSION_IF_MB) ) {
        err -= errlog(ERR_CRITICAL,"netcap_session_raze");
    }

    return err;
}

int netcap_udp_session_raze(int if_lock, netcap_session_t* netcap_sess)
{
    int err = 0;

    if ( netcap_sess == NULL ) {
        return errlog(ERR_CRITICAL,"Invalid arguments\n");
    }

    if ( netcap_udp_session_destroy(if_lock, netcap_sess) < 0 ) {
        err -= 1;
        errlog(ERR_CRITICAL,"netcap_udp_session_destroy");
    }

    if ( netcap_udp_session_free(netcap_sess) < 0 ) {
        err -= 2;
        errlog(ERR_CRITICAL,"netcap_udp_session_free");
    }

    return err;
}

static int _callback ( netcap_session_t* netcap_sess, netcap_callback_action_t action )
{
    if ( netcap_sess == NULL ) return errlogargs();
        
    switch ( action ) {
    case SRV_COMPLETE: 
    case CLI_COMPLETE: 
    case CLI_DROP:
        /* fallthrough */
        return 0;
    case CLI_ICMP:
    case CLI_RESET:
    case CLI_FORWARD_REJECT:
        errlog( ERR_WARNING, "_udp_rejection type %d is not implemented, ignoring\n", action );
        return 0;

    default:
        return errlog( ERR_CRITICAL, "Unknown action: %i\n", action );
    }

    return errlogcons();
}


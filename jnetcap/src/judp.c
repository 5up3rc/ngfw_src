/*
 * Copyright (c) 2003 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id$
 */

#include <jni.h>

#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/ip_icmp.h>
#include <arpa/inet.h>

#include <libnetcap.h>
#include <mvutil/libmvutil.h>
#include <mvutil/errlog.h>
#include <mvutil/debug.h>
#include <mvutil/utime.h>
#include <jmvutil.h>

#include "jnetcap.h"

#include JH_IPTraffic
#include JH_ICMPTraffic
#include JH_UDPSession

#define JLONG_TO_PACKET( packet, packet_ptr )   do { \
    if (( packet_ptr ) == 0 ) return errlogargs(); \
    (packet) = (netcap_pkt_t*)JLONG_TO_UINT(( packet_ptr )); \
  } while (0)

#define JLONG_TO_PACKET_NULL( packet, packet_ptr )   do { \
    if (( packet_ptr ) == 0 ) return errlogargs_null(); \
    (packet) = (netcap_pkt_t*)JLONG_TO_UINT(( packet_ptr )); \
  } while (0)

#define JLONG_TO_PACKET_VOID( packet, packet_ptr )   do { \
    if (( packet_ptr ) == 0 ) return (void)errlogargs(); \
    (packet) = (netcap_pkt_t*)JLONG_TO_UINT(( packet_ptr )); \
  } while (0)

static netcap_endpoint_t* _get_endpoint( netcap_pkt_t* pkt, int req_id )
{
    if (( req_id & JN_IPTraffic( FLAG_SRC_MASK )) == JN_IPTraffic( FLAG_SRC )) return &pkt->src;
    
    return &pkt->dst;
}


#define ICMP_GET_INFO_TYPE 1
#define ICMP_GET_INFO_CODE 0

/**
 * Retrieve the type or code from an ICMP packet 
 */
static int _icmp_get_info( netcap_pkt_t* pkt, int is_type );

/*
 * Class:     com_metavize_jnetcap_IPTraffic
 * Method:    createIPTraffic
 * Signature: (JJJJ)I
 */
JNIEXPORT jlong JNICALL JF_IPTraffic( createIPTraffic )
  (JNIEnv* env, jclass _class, jlong src, jint src_port, jlong dst, jint dst_port )
{
    netcap_pkt_t* pkt;

    /* XXX This should go to a netcap_pkt_create, like function */
    if (( pkt = calloc( 1, sizeof( netcap_pkt_t ))) == NULL ) return errlogmalloc();
    
    pkt->proto           = IPPROTO_UDP;
    pkt->src.host.s_addr = JLONG_TO_UINT( src );
    pkt->src.port        = (u_short)src_port;
    pkt->src.intf        = NC_INTF_UNK;

    pkt->dst.host.s_addr = JLONG_TO_UINT( dst );
    pkt->dst.port        = (u_short)dst_port;
    pkt->dst.intf        = NC_INTF_UNK;

    pkt->ttl             = 255;
    pkt->tos             = 0;
    pkt->opts            = NULL;
    pkt->opts_len        = 0;
    pkt->data            = NULL;
    pkt->data_len        = 0;

    return UINT_TO_JLONG((uint)pkt);
}


/*
 * Class:     com_metavize_jnetcap_IPTraffic
 * Method:    send
 * Signature: (J[B)I
 */
JNIEXPORT jint JNICALL JF_IPTraffic( send )
  (JNIEnv* env, jclass _class, jlong _pkt, jbyteArray _data )
{
    jbyte* data;
    int ret = 0;
    int data_len;
    netcap_pkt_t* pkt = (netcap_pkt_t*)JLONG_TO_UINT(_pkt);
    if ( pkt == NULL ) return errlogargs();

    /* Convert the byte array */
    if (( data = (*env)->GetByteArrayElements( env, _data, NULL )) == NULL ) {
        return errlogmalloc();
    }
    
    data_len = (*env)->GetArrayLength( env, _data );
    
    do { 
        if ( netcap_udp_send( (char*)data, data_len, pkt ) < 0 ) {
            ret = errlog( ERR_CRITICAL, "netcap_udp_send\n" );
        }
    } while ( 0 );

    (*env)->ReleaseByteArrayElements( env, _data, data, 0 );
    
    return ret;
}

/*
 * Class:     com_metavize_jnetcap_IPTraffic
 * Method:    getLongValue
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL JF_IPTraffic( getLongValue )
  (JNIEnv* env, jclass _class, jlong pkt_ptr, jint req )
{
    netcap_pkt_t* pkt;
    netcap_endpoint_t* endpoint;

    /* XXX What happens on the long return */
    JLONG_TO_PACKET( pkt, pkt_ptr );

    endpoint = _get_endpoint( pkt, req );
    
    switch( req & JN_IPTraffic( FLAG_MASK )) {
    case JN_IPTraffic( FLAG_HOST ): return UINT_TO_JLONG((uint)endpoint->host.s_addr );
    }

    return (jlong)errlogargs();    
}

/*
 * Class:     com_metavize_jnetcap_IPTraffic
 * Method:    getIntValue
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL JF_IPTraffic( getIntValue )
  ( JNIEnv* env, jclass _class, jlong pkt_ptr, jint req )
{
    netcap_pkt_t* pkt;

    netcap_endpoint_t* endpoint;

    /* XXX What happens on the long return */
    JLONG_TO_PACKET( pkt, pkt_ptr );

    endpoint = _get_endpoint( pkt, req );

    switch( req & JN_IPTraffic( FLAG_MASK )) {
    case JN_IPTraffic( FLAG_PORT ): return endpoint->port;
    case JN_IPTraffic( FLAG_TTL ): return pkt->ttl;
    case JN_IPTraffic( FLAG_TOS ): return pkt->tos;
    case JN_IPTraffic( FLAG_MARK_EN ): return pkt->is_marked;
    case JN_IPTraffic( FLAG_MARK ): return pkt->nfmark;
    case JN_IPTraffic( FLAG_INTERFACE ): return endpoint->intf;
    case JN_IPTraffic( FLAG_PROTOCOL ): return pkt->proto;
    case JN_ICMPTraffic( FLAG_TYPE ): return _icmp_get_info( pkt, ICMP_GET_INFO_TYPE );
    case JN_ICMPTraffic( FLAG_CODE ): return _icmp_get_info( pkt, ICMP_GET_INFO_CODE );
    }

    return errlogargs();
}

/*
 * Class:     com_metavize_jnetcap_IPTraffic
 * Method:    getStringValue
 * Signature: (JI)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL JF_IPTraffic( getStringValue )
  (JNIEnv* env, jclass _class, jlong pkt_ptr, jint req )
{
    netcap_pkt_t* pkt;
    char buf[NETCAP_MAX_IF_NAME_LEN]; /* XXX Update to the longest possible string returned */
    netcap_intf_t intf;
    netcap_endpoint_t* endpoint;

    JLONG_TO_PACKET_NULL( pkt, pkt_ptr );
    
    endpoint = _get_endpoint( pkt, req );

    switch( req & JN_IPTraffic( FLAG_MASK )) {
    case JN_IPTraffic( FLAG_INTERFACE ): intf = endpoint->intf; break;
    default: return errlogargs_null();
    }
    
    if ( intf == NC_INTF_UNK ) return (*env)->NewStringUTF( env, "" );
        
    if ( netcap_interface_intf_to_string( intf, buf, sizeof( buf )) < 0 ) {
        return errlog_null( ERR_CRITICAL, "netcap_intf_to_string\n" );
    }

    return (*env)->NewStringUTF( env, buf );
}

/*
 * Class:     com_metavize_jnetcap_IPTraffic
 * Method:    setLongValue
 * Signature: (JIJ)I
 */
JNIEXPORT jint JNICALL JF_IPTraffic( setLongValue )
  (JNIEnv* env, jclass _class, jlong pkt_ptr, jint req, jlong value )
{
    netcap_pkt_t* pkt;
    netcap_endpoint_t* endpoint;

    /* XXX What happens on the long return */
    JLONG_TO_PACKET( pkt, pkt_ptr );
    endpoint = _get_endpoint( pkt, req );

    switch( req & JN_IPTraffic( FLAG_MASK )) {
    case JN_IPTraffic( FLAG_HOST ): endpoint->host.s_addr = UINT_TO_JLONG(value); break;
    default:
        return errlogargs();
    }
    
    return 0;
}

/*
 * Class:     com_metavize_jnetcap_IPTraffic
 * Method:    setIntValue
 * Signature: (JII)I
 */
JNIEXPORT jint JNICALL JF_IPTraffic( setIntValue )
  (JNIEnv* env, jclass _class, jlong pkt_ptr, jint req, jint value )
{
    netcap_pkt_t* pkt;
    netcap_endpoint_t* endpoint;

    JLONG_TO_PACKET( pkt, pkt_ptr );
    endpoint = _get_endpoint( pkt, req );

    switch( req & JN_IPTraffic( FLAG_MASK ) ) {
    case JN_IPTraffic( FLAG_PORT ): endpoint->port = value; break;
    case JN_IPTraffic( FLAG_TTL ):  pkt->ttl = value; break;
    case JN_IPTraffic( FLAG_TOS ):  pkt->tos = value; break;
    case JN_IPTraffic( FLAG_MARK_EN ): pkt->is_marked = value; break;
    case JN_IPTraffic( FLAG_MARK ): pkt->nfmark = value; break;
    case JN_IPTraffic( FLAG_INTERFACE ): 
        /* Verify that this is a valid interface */
        if ( netcap_interface_intf_verify( value ) < 0 ) {
            return errlog( ERR_CRITICAL, "netcap_interface_intf_verify\n" );
        }
        endpoint->intf = value; 
        break;
    default:
        return errlogargs();        
    }

    return 0;
}

/*
 * Class:     com_metavize_jnetcap_IPTraffic
 * Method:    setStringValue
 * Signature: (JILjava/lang/String;)I
 */
JNIEXPORT jint JNICALL JF_IPTraffic( setStringValue )
  (JNIEnv* env, jclass _class, jlong pkt_ptr, jint req, jstring value )
{
    netcap_pkt_t* pkt;
    const char* str;
    netcap_endpoint_t* endpoint;
    netcap_intf_t *intf;
    int ret;

    JLONG_TO_PACKET( pkt, pkt_ptr );
    endpoint = _get_endpoint( pkt, req );
    
    switch( req & JN_IPTraffic( FLAG_MASK ) ) {
    case JN_IPTraffic( FLAG_INTERFACE ): intf = &endpoint->intf; break;
    default: 
        return errlogargs();
    }
    
    if (( str = (*env)->GetStringUTFChars( env, value, NULL )) == NULL ) {
        return errlogmalloc();
    };
    
    do {
        if ( netcap_interface_string_to_intf( (char*)str, intf ) < 0 ) {
            ret = errlog( ERR_CRITICAL, "netcap_string_to_intf\n" );
        }
    } while ( 0 );
    
    (*env)->ReleaseStringUTFChars( env, value, str );
    
    return ret;
}

/*
 * Class:     com_metavize_jnetcap_IPTraffic
 * Method:    raze
 * Signature: (J)V
 */
JNIEXPORT void JNICALL JF_IPTraffic( raze )
  (JNIEnv* env, jclass _class, jlong _pkt )
{
    netcap_pkt_t* pkt = (netcap_pkt_t*)JLONG_TO_UINT(_pkt);
    if ( pkt == NULL ) return (void)errlogargs();

    /* Remove the packet */
    netcap_pkt_raze( pkt );

    return;
}

/*
 * Class:     com_metavize_jnetcap_UDPSession
 * Method:    read
 * Signature: (JZI)J
 */
JNIEXPORT jlong JNICALL JF_UDPSession( read )
  (JNIEnv* env, jclass _class, jlong session_ptr, jboolean if_client, jint timeout )
{
    struct timeval tv;

    mailbox_t*    mb = NULL;
    netcap_session_t* netcap_sess = (netcap_session_t*)JLONG_TO_UINT( session_ptr );
    if ( netcap_sess == NULL ) return UINT_TO_JLONG((uint)errlogargs_null());

    if ( if_client == JNI_TRUE ) mb = &netcap_sess->cli_mb; 
    else                         mb = &netcap_sess->srv_mb; 
    
    if ( utime_msec_add_now( &tv, timeout ) < 0 ) {
        return UINT_TO_JLONG((u_int)errlog_null( ERR_CRITICAL, "utime_msec_add_now\n" ));
    }
                                     
    return UINT_TO_JLONG((uint)mailbox_utimed_get( mb, &tv ));
}

/*
 * Class:     com_metavize_jnetcap_UDPSession
 * Method:    data
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL JF_UDPSession( data )
  (JNIEnv* env, jclass _class, jlong packet_ptr )
{
    netcap_pkt_t* pkt = (netcap_pkt_t*)JLONG_TO_UINT( packet_ptr );
    jbyteArray bytes = NULL;
    
    if ( pkt == NULL ) return errlogargs_null();

    bytes = (*env)->NewByteArray( env, pkt->data_len );

    if ( bytes == NULL ) return errlogmalloc_null();

    if ( pkt->data_len > 0 ) {
        (*env)->SetByteArrayRegion( env, bytes, 0, pkt->data_len, (jbyte*)pkt->data );
    }
    
    return bytes;
}

/*
 * Class:     com_metavize_jnetcap_UDPSession
 * Method:    getData
 * Signature: (J)[B
 */
JNIEXPORT int JNICALL JF_UDPSession( getData )
  (JNIEnv* env, jclass _class, jlong packet_ptr, jbyteArray buffer )
{
    netcap_pkt_t* pkt = (netcap_pkt_t*)JLONG_TO_UINT( packet_ptr );
    int buffer_len;

    if ( pkt == NULL || buffer == NULL ) return errlogargs();

    buffer_len = (*env)->GetArrayLength( env, buffer );
    if ( pkt->data_len > buffer_len )
        return jmvutil_error( JMVUTIL_ERROR_ARGS, ERR_CRITICAL,
                              "UDP: Buffer is to small for packet: %d < %d", buffer_len, pkt->data_len );


    if ( pkt->data_len > 0 ) {
        (*env)->SetByteArrayRegion( env, buffer, 0, pkt->data_len, (jbyte*)pkt->data );
    }
    
    return pkt->data_len;
}


/*
 * Class:     com_metavize_jnetcap_UDPSession
 * Method:    send
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL JF_UDPSession( send )
  (JNIEnv* env, jclass _class, jlong packet_ptr )
{
    netcap_pkt_t* pkt = (netcap_pkt_t*)JLONG_TO_UINT( packet_ptr );
    if ( pkt == NULL ) return errlogargs();
    
    if ( pkt->data == NULL && pkt->data_len != 0 ) {
        return errlog( ERR_CRITICAL, "NULL packet, non-zero length\n" );
    }
    
    if ( pkt->data == NULL || pkt->data_len == 0 ) errlog( ERR_WARNING, "Sending a zero length packet\n" );

    if ( netcap_udp_send( (char*)pkt->data, pkt->data_len, pkt ) < 0 ) {
        return errlog( ERR_CRITICAL, "netcap_udp_send\n" );
    }

    return 0;
}

/*
 * Class:     com_metavize_jnetcap_NetcapUDPSession
 * Method:    merge
 * Signature: (JJJII)I
 */
JNIEXPORT jint JNICALL JF_UDPSession( merge )
  ( JNIEnv *env, jclass _class, jlong pointer, jlong src_addr, jint src_port, 
    jlong dst_addr, jint dst_port, jbyte intf )
{
    int ret;
    in_addr_t src = (in_addr_t)JLONG_TO_UINT( src_addr );
    in_addr_t dst = (in_addr_t)JLONG_TO_UINT( dst_addr );

    netcap_session_t* session = (netcap_session_t*)JLONG_TO_UINT( pointer );

    if ( session == NULL ) return errlogargs();

    ret = netcap_sesstable_merge_udp_tuple( session, src, dst, src_port, dst_port, intf );

    if ( ret < 0 ) {
        return errlog( ERR_CRITICAL, "netcap_sesstable_merge_udp\n" );
    } else if ( ret > 0 ) {
        return JN_UDPSession( MERGED_DEAD );
    }
    
    return 0;
}

/*
 * Class:     com_metavize_jnetcap_NetcapUDPSession
 * Method:    icmpMerge
 * Signature: (JJJII)I
 */
JNIEXPORT jint JNICALL JF_UDPSession( icmpMerge )
( JNIEnv *env, jclass _class, jlong pointer, jint icmp_pid, jlong src_addr, jlong dst_addr, jbyte intf )
{
    int ret;
    in_addr_t src = (in_addr_t)JLONG_TO_UINT( src_addr );
    in_addr_t dst = (in_addr_t)JLONG_TO_UINT( dst_addr );

    netcap_session_t* session = (netcap_session_t*)JLONG_TO_UINT( pointer );

    if ( session == NULL ) return errlogargs();
    
    ret = netcap_sesstable_merge_icmp_tuple( session, src, dst, intf, icmp_pid );

    if ( ret < 0 ) {
        return errlog( ERR_CRITICAL, "netcap_sesstable_merge_icmp_tuple\n" );
    } else if ( ret > 0 ) {
        return JN_UDPSession( MERGED_DEAD );
    }
    
    return 0;
}

/*
 * Class:     com_metavize_jnetcap_NetcapUDPSession
 * Method:    mailboxPointer
 * Signature: (JZ)I
 */
JNIEXPORT jint JNICALL JF_UDPSession( mailboxPointer )
  ( JNIEnv *env, jclass _class, jlong pointer, jboolean if_client )
{
    netcap_session_t* session = (netcap_session_t*)JLONG_TO_UINT( pointer );
    if ( session == NULL ) return errlogargs();

    return (jint)(( if_client == JNI_TRUE ) ? &session->cli_mb : &session->srv_mb);
}

/*
 * Class:     com_metavize_jnetcap_ICMPTraffic
 * Method:    icmpSource
 * Signature: (J[BI)J
 */
JNIEXPORT jlong JNICALL JF_ICMPTraffic( icmpSource )
   ( JNIEnv *env, jobject _this, jlong pointer, jbyteArray _data, jint limit )
{
    jbyte* data;
    int data_len;
    struct in_addr source;
    int ret;
    
    netcap_pkt_t* pkt = (netcap_pkt_t*)JLONG_TO_UINT( pointer );
    if ( pkt == NULL ) {
        return (jlong)jmvutil_error( JMVUTIL_ERROR_ARGS, ERR_CRITICAL, "NULL pkt\n" );
    }

    if (( data_len = (*env)->GetArrayLength( env, _data )) < 0 ) {
        errlog( ERR_WARNING, "ICMP: Negative data array length\n" );
        return JN_ICMPTraffic( SAME_SOURCE );
    }
    
    if ( data_len < limit ) {
        errlog( ERR_WARNING, "Limit is larger than array, using array size %d < %d\n", data_len, limit );
        limit = data_len;
    }

    if (( data = (*env)->GetByteArrayElements( env, _data, NULL )) == NULL ) {
        return (jlong)jmvutil_error( JMVUTIL_ERROR_ARGS, ERR_CRITICAL, "GetByteArrayElements\n" );
    }
    
    do {
        if (( ret = netcap_icmp_get_source( data, limit, pkt, &source )) < 0 ) {
            jmvutil_error( JMVUTIL_ERROR_ARGS, ERR_CRITICAL, "netcap_icmp_get_source\n" );
            break;
        }
    } while ( 0 );

    (*env)->ReleaseByteArrayElements( env, _data, data, 0 );

    if ( ret < 0 ) {
        return (jlong)ret;
    } else if ( ret ) {
        return UINT_TO_JLONG( source.s_addr );
    }
    
    return JN_ICMPTraffic( SAME_SOURCE );
}

/**
 * Retrieve the type from an ICMP packet 
 */
static int _icmp_get_info( netcap_pkt_t* pkt, int is_type )
{
    struct icmp* icmp;

    if ( pkt->proto != IPPROTO_ICMP ) {
        return errlog( ERR_CRITICAL, "Attempt to retrieve ICMP type on non-icmp (%d) packet\n", pkt->proto );
    }
    
    /* Type cast the data as an ICMP packet */
    if (( icmp = (struct icmp*)pkt->data ) == NULL ) {
        return errlog( ERR_CRITICAL, "Unable to get type, NULL data\n" );
    }

    return ( is_type == ICMP_GET_INFO_TYPE ) ? icmp->icmp_type : icmp->icmp_code;
}

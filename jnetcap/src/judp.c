/**
 * $Id$
 */
#include <jni.h>

#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <arpa/inet.h>

#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/ip_icmp.h>

#include <linux/netfilter.h>

#include <libnetcap.h>
#include <libmvutil.h>
#include <mvutil/errlog.h>
#include <mvutil/debug.h>
#include <mvutil/utime.h>
#include <jmvutil.h>

#include "jnetcap.h"
#include "jsession.h"

#include JH_IPTraffic
#include JH_UDPSession

#define VERIFY_PKT_SESSION(session) if ((session)->protocol != IPPROTO_UDP) \
   return jmvutil_error( JMVUTIL_ERROR_ARGS, ERR_CRITICAL, \
                         "JPKT: Expecting a UDP session: %d\n", (session)->protocol )

#define VERIFY_PKT_SESSION_VOID(session) if ((session)->protocol != IPPROTO_UDP ) \
   return jmvutil_error_void( JMVUTIL_ERROR_ARGS, ERR_CRITICAL, \
                              "JPKT: Expecting a UDP Session: %d\n", (session)->protocol )

#define JLONG_TO_PACKET( packet, packet_ptr )   do { \
    if (( packet_ptr ) == 0 ) return errlogargs(); \
    (packet) = (netcap_pkt_t*)JLONG_TO_ULONG(( packet_ptr )); \
  } while (0)

#define JLONG_TO_PACKET_NULL( packet, packet_ptr )   do { \
    if (( packet_ptr ) == 0 ) return errlogargs_null(); \
    (packet) = (netcap_pkt_t*)JLONG_TO_ULONG(( packet_ptr )); \
  } while (0)

#define JLONG_TO_PACKET_VOID( packet, packet_ptr )   do { \
    if (( packet_ptr ) == 0 ) return (void)errlogargs(); \
    (packet) = (netcap_pkt_t*)JLONG_TO_ULONG(( packet_ptr )); \
  } while (0)


static void _udp_callback( jlong session_ptr, netcap_callback_action_t action );

static netcap_endpoint_t* _get_pkt_endpoint( netcap_pkt_t* pkt, int req_id )
{
    if (( req_id & JN_IPTraffic( FLAG_SRC_MASK )) == JN_IPTraffic( FLAG_SRC )) return &pkt->src;
    
    return &pkt->dst;
}


/*
 * Class:     com_untangle_jnetcap_IPTraffic
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
    pkt->src_intf        = NF_INTF_UNKNOWN;

    pkt->dst.host.s_addr = JLONG_TO_UINT( dst );
    pkt->dst.port        = (u_short)dst_port;
    pkt->dst_intf        = NF_INTF_UNKNOWN;

    pkt->ttl             = 255;
    pkt->tos             = 0;
    pkt->opts            = NULL;
    pkt->opts_len        = 0;
    pkt->data            = NULL;
    pkt->data_len        = 0;

    return UINT_TO_JLONG(pkt);
}


/*
 * Class:     com_untangle_jnetcap_IPTraffic
 * Method:    send
 * Signature: (J[B)I
 */
JNIEXPORT jint JNICALL JF_IPTraffic( send )
  (JNIEnv* env, jclass _class, jlong _pkt, jbyteArray _data )
{
    jbyte* data;
    int ret = 0;
    int data_len;
    netcap_pkt_t* pkt = (netcap_pkt_t*)JLONG_TO_ULONG(_pkt);
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
 * Class:     com_untangle_jnetcap_IPTraffic
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

    endpoint = _get_pkt_endpoint( pkt, req );
    
    switch( req & JN_IPTraffic( FLAG_MASK )) {
    case JN_IPTraffic( FLAG_HOST ): return UINT_TO_JLONG((uint)endpoint->host.s_addr );
    }

    return (jlong)errlogargs();    
}

/*
 * Class:     com_untangle_jnetcap_IPTraffic
 * Method:    getIntValue
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL JF_IPTraffic( getIntValue )
  ( JNIEnv* env, jclass _class, jlong pkt_ptr, jint req )
{
    netcap_pkt_t* pkt;

    netcap_endpoint_t* endpoint   = NULL;

    /* XXX What happens on the long return */
    JLONG_TO_PACKET( pkt, pkt_ptr );

    

    if (( endpoint  = _get_pkt_endpoint( pkt, req )) == NULL ) return errlogargs();

    switch( req & JN_IPTraffic( FLAG_MASK )) {
    case JN_IPTraffic( FLAG_PORT ): return endpoint->port;
    case JN_IPTraffic( FLAG_TTL ): return pkt->ttl;
    case JN_IPTraffic( FLAG_TOS ): return pkt->tos;
    case JN_IPTraffic( FLAG_MARK_EN ): return pkt->is_marked;
    case JN_IPTraffic( FLAG_MARK ): return pkt->nfmark;
    case JN_IPTraffic( FLAG_INTERFACE ):
        if (( req & JN_IPTraffic( FLAG_SRC_MASK )) == JN_IPTraffic( FLAG_SRC )) return pkt->src_intf;
        return pkt->dst_intf;

    case JN_IPTraffic( FLAG_PROTOCOL ): return pkt->proto;
    }

    return errlogargs();
}

/*
 * Class:     com_untangle_jnetcap_IPTraffic
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
    endpoint = _get_pkt_endpoint( pkt, req );

    switch( req & JN_IPTraffic( FLAG_MASK )) {
    case JN_IPTraffic( FLAG_HOST ): endpoint->host.s_addr = UINT_TO_JLONG(value); break;
    default:
        return errlogargs();
    }
    
    return 0;
}

/*
 * Class:     com_untangle_jnetcap_IPTraffic
 * Method:    setIntValue
 * Signature: (JII)I
 */
JNIEXPORT jint JNICALL JF_IPTraffic( setIntValue )
  (JNIEnv* env, jclass _class, jlong pkt_ptr, jint req, jint value )
{
    netcap_pkt_t* pkt;
    netcap_endpoint_t* endpoint;

    JLONG_TO_PACKET( pkt, pkt_ptr );
    endpoint = _get_pkt_endpoint( pkt, req );

    switch( req & JN_IPTraffic( FLAG_MASK ) ) {
    case JN_IPTraffic( FLAG_PORT ): endpoint->port = value; break;
    case JN_IPTraffic( FLAG_TTL ):  pkt->ttl = value; break;
    case JN_IPTraffic( FLAG_TOS ):  pkt->tos = value; break;
    case JN_IPTraffic( FLAG_MARK_EN ): pkt->is_marked = value; break;
    case JN_IPTraffic( FLAG_MARK ): pkt->nfmark = value; break;
    case JN_IPTraffic( FLAG_INTERFACE ): 
        if (( req & JN_IPTraffic( FLAG_SRC_MASK )) == JN_IPTraffic( FLAG_SRC )) {
            pkt->src_intf = value;
        } else {
            pkt->dst_intf = value; 
        }
        break;
    default:
        return errlogargs();        
    }

    return 0;
}

/*
 * Class:     com_untangle_jnetcap_IPTraffic
 * Method:    raze
 * Signature: (J)V
 */
JNIEXPORT void JNICALL JF_IPTraffic( raze )
  (JNIEnv* env, jclass _class, jlong _pkt )
{
    netcap_pkt_t* pkt = (netcap_pkt_t*)JLONG_TO_ULONG(_pkt);
    if ( pkt == NULL ) return (void)errlogargs();

    /* Remove the packet */
    debug(10, "FLAG: IPTraffic.raze packet %d\n", pkt->packet_id);
        
    /* This will occasionally happen if a session is dropped after being accepted.  In this
     * case we must drop the packet */
    if ( pkt->packet_id != 0 ) debug( 3, "IPTraffic object with nonzero packet id.\n" );

    netcap_pkt_action_raze( pkt, NF_DROP );

    return;
}

/*
 * Class:     com_untangle_jnetcap_UDPSession
 * Method:    read
 * Signature: (JZI)J
 */
JNIEXPORT jlong JNICALL JF_UDPSession( read )
  (JNIEnv* env, jclass _class, jlong session_ptr, jboolean if_client, jint timeout )
{
    struct timeval tv;
    netcap_pkt_t *pkt = NULL;
    mailbox_t*    mb = NULL;
    netcap_session_t* netcap_sess = (netcap_session_t*)JLONG_TO_ULONG( session_ptr );
    if ( netcap_sess == NULL ) return UINT_TO_JLONG(errlogargs_null());

    if ( if_client == JNI_TRUE ) mb = &netcap_sess->cli_mb; 
    else                         mb = &netcap_sess->srv_mb; 
    
    if ( utime_msec_add_now( &tv, timeout ) < 0 ) {
        return UINT_TO_JLONG(errlog_null( ERR_CRITICAL, "utime_msec_add_now\n" ));
    }
    pkt = (netcap_pkt_t *) mailbox_utimed_get( mb, &tv );
    /*
    if (( pkt != NULL ) && ( pkt->packet_id != 0 )) {
        debug(10, "pulled intact UDP packet %d from mailbox, droping\n",pkt->packet_id);
        netcap_set_verdict( pkt->packet_id, NF_DROP, NULL, 0 );
        pkt->packet_id = 0;
    }
    */
    return  UINT_TO_JLONG( pkt );
}

/*
 * Class:     com_untangle_jnetcap_UDPSession
 * Method:    data
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL JF_UDPSession( data )
  (JNIEnv* env, jclass _class, jlong packet_ptr )
{
    netcap_pkt_t* pkt = (netcap_pkt_t*)JLONG_TO_ULONG( packet_ptr );
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
 * Class:     com_untangle_jnetcap_UDPSession
 * Method:    getData
 * Signature: (J)[B
 */
JNIEXPORT int JNICALL JF_UDPSession( getData )
  (JNIEnv* env, jclass _class, jlong packet_ptr, jbyteArray buffer )
{
    netcap_pkt_t* pkt = (netcap_pkt_t*)JLONG_TO_ULONG( packet_ptr );
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
 * Class:     com_untangle_jnetcap_UDPSession
 * Method:    send
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL JF_UDPSession( send )
  (JNIEnv* env, jclass _class, jlong packet_ptr )
{
    netcap_pkt_t* pkt = (netcap_pkt_t*)JLONG_TO_ULONG( packet_ptr );
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
 * Class:     com_untangle_jnetcap_NetcapUDPSession
 * Method:    mailboxPointer
 * Signature: (JZ)I
 */
JNIEXPORT jlong JNICALL JF_UDPSession( mailboxPointer )
  ( JNIEnv *env, jclass _class, jlong pointer, jboolean if_client )
{
    netcap_session_t* session = (netcap_session_t*)JLONG_TO_ULONG( pointer );
    if ( session == NULL ) return errlogargs();

    return (jlong)(long)(( if_client == JNI_TRUE ) ? &session->cli_mb : &session->srv_mb);
}

/*
 * Class:     com_untangle_jnetcap_NetcapUDPSession
 * Method:    setSessionMark
 * Signature: (JJI)I
 */
JNIEXPORT void JNICALL JF_UDPSession( setSessionMark )
    ( JNIEnv *env, jclass _class, jlong session_ptr, jint mark )
{
    netcap_session_t* session = (netcap_session_t*)JLONG_TO_ULONG( session_ptr );
    
    if (session != NULL) 
        netcap_nfconntrack_update_mark( session, mark );
}

/*
 * Class:     com_untangle_jnetcap_NetcapUDPSession
 * Method:    serverComplete
 * Signature: (JI)I
 */
JNIEXPORT void JNICALL JF_UDPSession( serverComplete )
    ( JNIEnv *env, jclass _class, jlong session_ptr  )
{
    _udp_callback( session_ptr, SRV_COMPLETE );
}

static void _udp_callback( jlong session_ptr, netcap_callback_action_t action )
{
    netcap_session_t* netcap_sess;

    JLONG_TO_SESSION_VOID( netcap_sess, session_ptr );
    VERIFY_PKT_SESSION_VOID( netcap_sess );    
        
    if ( netcap_sess->callback == NULL ) {
        return jmvutil_error_void( JMVUTIL_ERROR_STT, ERR_CRITICAL, "JPKT: null callback %d\n", action );
    }

    if ( netcap_sess->callback( netcap_sess, action ) < 0 ) {
        debug( 2, "JPKT: callback failed=%d\n", action );

        /* Throw an error, but don't print an error message */
        jmvutil_error_throw( JMVUTIL_ERROR_STT, "JPKT: callback failed action=%d", action );
    }
}

/*
 * Copyright (c) 2003,2004 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id: TCPSink.c,v 1.7 2005/01/20 22:20:54 rbscott Exp $
 */

#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <sys/socket.h>
#include <unistd.h>
#include <libmvutil.h>
#include <libvector.h>
#include <mvutil/debug.h>
#include <mvutil/mvpoll.h>
#include <mvutil/errlog.h>
#include <mvutil/debug.h>
#include <mvutil/unet.h>

#include <vector/event.h>
#include <vector/source.h>
#include <vector/fd_sink.h>

#include "jni_header.h"
#include "jerror.h"
#include "jvector.h"


#include JH_TCPSink

static int _sink_get_fd( jint pointer );

/*
 * Class:     TCPSink
 * Method:    create
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL JF_TCPSink( create )
( JNIEnv *env, jobject _this, jint _fd ) 
{
    jvector_sink_t* snk;
    mvpoll_key_t* key;
    int fd;
    
    if (( fd = dup( _fd )) < 0 ) {
        return (jint)jvector_error_null( JVECTOR_ERROR_STT, ERR_CRITICAL, "dup: %s\n", errstr );
    }

    /* Set to NON-blocking */
    if ( unet_blocking_disable( fd ) < 0 ) {
        return (jint)jvector_error_null( JVECTOR_ERROR_STT, ERR_CRITICAL, "unet_blocking_disable\n" );
    }

    if (( key = mvpoll_key_fd_create( fd )) == NULL ) {
        return (jint)jvector_error_null( JVECTOR_ERROR_STT, ERR_CRITICAL, "mvpoll_key_fd_create\n" );
    }
    
    if (( snk = jvector_sink_create( _this, key )) == NULL ) {
        return (jint)jvector_error_null( JVECTOR_ERROR_STT, ERR_CRITICAL, "jvector_sink_create\n" );
    }
    
    return (jint)snk;    
}

JNIEXPORT jint JNICALL JF_TCPSink( write )
( JNIEnv *env, jobject _this, jint pointer, jbyteArray _data, jint offset, jint size )
{
    jbyte* data;
    int number_bytes = -1;
    int data_len;
    int fd;

    if (( fd = _sink_get_fd( pointer )) < 0 ) return errlog( ERR_CRITICAL, "_sink_get_fd\n" );
    
    /* Convert the byte array */
    if (( data = (*env)->GetByteArrayElements( env, _data, NULL )) == NULL ) return errlogmalloc();
    
    data_len = (*env)->GetArrayLength( env, _data );
    
    do {
        if (( offset + size ) > data_len ) {
            jvector_error( JVECTOR_ERROR_ARGS, ERR_CRITICAL, 
                           "Requested %d write with a buffer of size %d\n", size, data_len );
            break;
        } else if ( offset > data_len ) {
            jvector_error( JVECTOR_ERROR_ARGS, ERR_CRITICAL, 
                           "Requested %d offset with a buffer of size %d\n", offset, data_len );
            break;
        }
        
        number_bytes = write( fd, (char*)&data[offset], size );
        if ( number_bytes < 0 ) {
            switch ( errno ) {
            case ECONNRESET:
                /* Received a reset, let the caller know */
                debug( 5, "TCPSink: fd %d reset\n", fd );
                number_bytes = -1;
                break;

            case EAGAIN:
                /* Unable to write at this time, would have blocked */
                debug( 5, "TCPSink: fd %d polled when unable to write data\n", fd );
                number_bytes = 0;
                break;

            default:
                jvector_error( JVECTOR_ERROR_STT, ERR_CRITICAL, "TCPSink: write: %s\n", errstr );
                /* Doesn't matter, it will through an error */
                number_bytes = -2;
            }
        }
    } while ( 0 );

    (*env)->ReleaseByteArrayElements( env, _data, data, 0 );

    return number_bytes;
}

/*
 * Class:     TCPSink
 * Method:    close
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL JF_TCPSink( close )
  (JNIEnv *env, jclass _this, jint pointer)
{
    int fd;
    
    if (( fd = _sink_get_fd( pointer )) < 0 ) return errlog( ERR_CRITICAL, "_sink_get_fd\n" );
    if ( close( fd ) < 0 ) return perrlog( "close" );

    return 0;
}

/*
 * Class:     com_metavize_jvector_TCPSink
 * Method:    reset
 * Signature: (I)I
 */
JNIEXPORT void JNICALL JF_TCPSink( reset )
  (JNIEnv *env, jclass _class, jint pointer )
{
    int fd;
    if (( fd = _sink_get_fd( pointer )) < 0 ) {
        return jvector_error_void( JVECTOR_ERROR_ARGS, ERR_CRITICAL, "_sink_get_fd" );
    }
    
    /**
     * XXX BUG: Sometimes this may not reset.  See the program tcp_reset.c
     * it appears this will only reset if data comes in on filedescriptor.
     */
    if ( unet_reset( fd ) < 0 ) {
        return jvector_error_void( JVECTOR_ERROR_STT, ERR_CRITICAL, "unet_reset" );
    }
}

/*
 * Class:     TCPSink
 * Method:    shutdown
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL JF_TCPSink( shutdown )
  (JNIEnv *env, jclass _this, jint pointer)
{
    jvector_sink_t* jv_snk = (jvector_sink_t*)pointer;
    int fd;

    if ( jv_snk == NULL || jv_snk->key == NULL ) return errlogargs();
    
    fd = (int)jv_snk->key->data;
    /* XXX Must NULL this out at some point
      jv_snk->key->data = (void*)-1;
      do not NULL out here, because it is used later
    */

    if ( fd < 0) {
        errlog( ERR_WARNING, "JVECTOR: TCPSink- Multiple shutdown attempt\n" );
        return 0;
    }

    debug( 10, "JVECTOR: TCPSink shutdown.\n" );

    if ( shutdown( fd, SHUT_WR ) < 0 ) {
         /* If it is not connected (ENOTCONN), Ignore it */
        if (errno != ENOTCONN)
            return perrlog("JVECTOR: shutdown");
    }
   
    return 0;
}

static int _sink_get_fd( jint pointer )
{
    /* XXX This should be throwing errors left and right */
    if ( pointer == (jint)NULL || ((jvector_sink_t*)pointer)->key == NULL ) {
        return jvector_error( JVECTOR_ERROR_ARGS, ERR_CRITICAL, "Invalid pointer\n" );
    }
    
    return (int)((jvector_sink_t*)pointer)->key->data;
}

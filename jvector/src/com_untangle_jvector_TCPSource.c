/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/socket.h>

#include <libmvutil.h>
#include <mvutil/debug.h>
#include <mvutil/mvpoll.h>
#include <mvutil/errlog.h>
#include <mvutil/debug.h>
#include <mvutil/unet.h>
#include <jmvutil.h>

#include <libvector.h>
#include <vector/event.h>
#include <vector/source.h>
#include <vector/fd_source.h>

#include "jvector.h"

#include "com_untangle_jvector_TCPSource.h"

static int _source_get_fd( jint pointer );

/*
 * Class:     TCPSource
 * Method:    create
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_untangle_jvector_TCPSource_create
  (JNIEnv *env, jobject _this, jint fd )
{
    jvector_source_t* src;
    mvpoll_key_t* key;

    /* Set to non-blocking */
    if ( unet_blocking_disable( fd ) < 0 ) {
        return (jint)jmvutil_error_null( JMVUTIL_ERROR_STT, ERR_CRITICAL, "unet_blocking_disable\n" );
    }

    if (( key = mvpoll_key_fd_create( fd )) == NULL ) {
        return (jint)jmvutil_error_null( JMVUTIL_ERROR_STT, ERR_CRITICAL, "mvpoll_key_fd_create\n" );
    }

    if (( src = jvector_source_create( _this, key )) == NULL ) {
        return (jint)jmvutil_error_null( JMVUTIL_ERROR_STT, ERR_CRITICAL, "jvector_source_create\n" );
    }
    
    return (jint)src;    
}

/*
 * Class:     TCPSource
 * Method:    read
 * Signature: (I[B)I
 */
JNIEXPORT jint JNICALL Java_com_untangle_jvector_TCPSource_read
(JNIEnv *env, jclass _this, jint pointer, jbyteArray _data )
{
    jbyte* data;
    int ret = 0;
    int data_len;
    int fd;
    jvector_source_t* src = (jvector_source_t*)pointer;
    eventmask_t events;

    /* source_get_fd checks for NULL */
    if (( fd = _source_get_fd( pointer )) < 0 ) {
        return jmvutil_error( JMVUTIL_ERROR_ARGS, ERR_CRITICAL, "_source_get_fd\n" );
    }

    /* Poll the key, if there is an error, get out of town */
    events = src->key->events;
    debug( 10, "JVECTOR(%08x): TCPSource poll - %08x\n", src->key, events );


/* Ignore the event flags from poll, if there isn't an error or shutdown after reading 
 * from the fd, then something has gone wrong */
#if 0
    if ( events & MVPOLLERR ) {
        debug( 9, "JVECTOR(%08x): ERR from vectoring\n", src->key );
        return com_untangle_jvector_TCPSource_READ_RESET;
    } else if ( events & MVPOLLHUP ) {
        debug( 9, "JVECTOR(%08x): HUP from vectoring\n", src->key );
        return 0;
    }
#endif // 0

    /* Convert the byte array */
    if (( data = (*env)->GetByteArrayElements( env, _data, NULL )) == NULL ) {
        return jmvutil_error( JMVUTIL_ERROR_STT, ERR_FATAL, "(*env)->GetByteArrayElements\n" );
    }
    
    data_len = (*env)->GetArrayLength( env, _data );

    do {
        /* XXX Do we need to check for EINTR */
        ret = read( fd, (char*)data, data_len );            
        
        if ( ret < 0 ) {
            switch ( errno ) {
            case ECONNRESET:
                /* Received a reset, let the caller know */
                debug( 5, "JVECTOR: TCPSource: fd %d reset\n", fd );
                ret = com_untangle_jvector_TCPSource_READ_RESET;
                break;

            case EAGAIN:
                /* Unable to write at this time, would have blocked, let the caller know */
                debug( 5, "JVECTOR: TCPSource: fd %d polled for read without data\n", fd );
                ret = 0;
                break;

            case ETIMEDOUT:
                debug( 5, "JVECTOR: TCPSource: fd %d connection time out (keep alive unaswered)\n", fd );
                ret = com_untangle_jvector_TCPSource_READ_RESET;
                break;

            case EHOSTUNREACH:
                debug( 5, "JVECTOR: TCPSource: fd %d host unreachable\n", fd );
                ret = com_untangle_jvector_TCPSource_READ_RESET;
                break;

            case ENETUNREACH:
                debug( 5, "JVECTOR: TCPSource: fd %d net unreachable\n", fd );
                ret = com_untangle_jvector_TCPSource_READ_RESET;
                break;

            default:
                jmvutil_error( JMVUTIL_ERROR_STT, ERR_CRITICAL, "TCPSource: read: %s\n", errstr );
                ret = -2;
            }
        }
    } while ( 0 );

    (*env)->ReleaseByteArrayElements( env, _data, data, 0 );
    
    return ret;
}

/* XX May need to throw an error */
JNIEXPORT jint JNICALL Java_com_untangle_jvector_TCPSource_shutdown
(JNIEnv *env, jclass _this, jint pointer)
{
    jvector_source_t* jv_src = (jvector_source_t*)pointer;
    int fd;
    
    if ( jv_src == NULL || jv_src->key == NULL ) return errlogargs();
    
    fd = (int)jv_src->key->data;
    // jv_src->key->data = (void*)-1;
    /* Do not NULL here, it is used later */
    
    if ( fd < 0) {
        errlog( ERR_WARNING, "Multiple shutdown attempt\n" );
        return 0;
    }
    
    if ( shutdown( fd, SHUT_RD ) < 0 ) {
        /* If it is not connected (ENOTCONN), Ignore it */
        if (errno != ENOTCONN)
            return perrlog("shutdown");
    }
    
    return 0;
}

static int _source_get_fd( jint pointer )
{
    if ( pointer == (jint)NULL ) return errlog( ERR_CRITICAL, "Invalid pointer\n" );
    
    return (int)((jvector_source_t*)pointer)->key->data;
}




/*
 * $HeadURL:$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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
#include <jmvutil.h>

#include <vector/event.h>
#include <vector/source.h>
#include <vector/fd_sink.h>

#include "jvector.h"


#include "com_untangle_jvector_TCPSink.h"

static int _sink_get_fd( jint pointer );

/*
 * Class:     TCPSink
 * Method:    create
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_untangle_jvector_TCPSink_create
( JNIEnv *env, jobject _this, jint _fd ) 
{
    jvector_sink_t* snk;
    mvpoll_key_t* key;
    int fd;
    
    if (( fd = dup( _fd )) < 0 ) {
        return (jint)jmvutil_error_null( JMVUTIL_ERROR_STT, ERR_CRITICAL, "dup: %s\n", errstr );
    }

    /* Set to NON-blocking */
    if ( unet_blocking_disable( fd ) < 0 ) {
        return (jint)jmvutil_error_null( JMVUTIL_ERROR_STT, ERR_CRITICAL, "unet_blocking_disable\n" );
    }

    if (( key = mvpoll_key_fd_create( fd )) == NULL ) {
        return (jint)jmvutil_error_null( JMVUTIL_ERROR_STT, ERR_CRITICAL, "mvpoll_key_fd_create\n" );
    }
    
    if (( snk = jvector_sink_create( _this, key )) == NULL ) {
        return (jint)jmvutil_error_null( JMVUTIL_ERROR_STT, ERR_CRITICAL, "jvector_sink_create\n" );
    }
    
    return (jint)snk;    
}

JNIEXPORT jint JNICALL Java_com_untangle_jvector_TCPSink_write
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
            jmvutil_error( JMVUTIL_ERROR_ARGS, ERR_CRITICAL, 
                           "Requested %d write with a buffer of size %d\n", size, data_len );
            break;
        } else if ( offset > data_len ) {
            jmvutil_error( JMVUTIL_ERROR_ARGS, ERR_CRITICAL, 
                           "Requested %d offset with a buffer of size %d\n", offset, data_len );
            break;
        }
        
        number_bytes = write( fd, (char*)&data[offset], size );
        if ( number_bytes < 0 ) {
            switch ( errno ) {
            case ECONNRESET:
                /* Received a reset, let the caller know */
                debug( 5, "TCPSink: fd %d reset\n", fd );
                number_bytes = com_untangle_jvector_TCPSink_WRITE_RETURN_IGNORE;
                break;
                
            case EPIPE:
                /**
                 * This occurs if the corresponding source reads the reset, and then the
                 * sink is called.  In this situation, the reset has already been read, but 
                 * the fd is being used anyway).  This starting cropping up with the new
                 * vectoring that services POLLHUP | POLLIN as a POLLIN.
                 */
                debug( 5, "TCPSink: Broken pipe fd %d, resetting\n", fd );
                number_bytes = com_untangle_jvector_TCPSink_WRITE_RETURN_IGNORE;
                break;

            case EAGAIN:
                /* Unable to write at this time, would have blocked 
                 * XXXX Why isn't this an error */
                debug( 5, "TCPSink: fd %d polled when unable to write data\n", fd );
                number_bytes = 0;
                break;                

            default:
                jmvutil_error( JMVUTIL_ERROR_STT, ERR_CRITICAL, "TCPSink: write: %s\n", errstr );
                /* Doesn't matter, it will throw an error */
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
JNIEXPORT jint JNICALL Java_com_untangle_jvector_TCPSink_close
  (JNIEnv *env, jclass _this, jint pointer)
{
    int fd;
    
    if (( fd = _sink_get_fd( pointer )) < 0 ) return errlog( ERR_CRITICAL, "_sink_get_fd\n" );
    if ( close( fd ) < 0 ) return perrlog( "close" );

    return 0;
}

/*
 * Class:     com_untangle_jvector_TCPSink
 * Method:    reset
 * Signature: (I)I
 */
JNIEXPORT void JNICALL Java_com_untangle_jvector_TCPSink_reset
  (JNIEnv *env, jclass _class, jint pointer )
{
    int fd;
    if (( fd = _sink_get_fd( pointer )) < 0 ) {
        return jmvutil_error_void( JMVUTIL_ERROR_ARGS, ERR_CRITICAL, "_sink_get_fd" );
    }
    
    /**
     * XXX BUG: Sometimes this may not reset.  See the program tcp_reset.c
     * it appears this will only reset if data comes in on filedescriptor.
     */
    if ( unet_reset( fd ) < 0 ) {
        return jmvutil_error_void( JMVUTIL_ERROR_STT, ERR_CRITICAL, "unet_reset" );
    }
}

/*
 * Class:     TCPSink
 * Method:    shutdown
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_untangle_jvector_TCPSink_shutdown
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
        return jmvutil_error( JMVUTIL_ERROR_ARGS, ERR_CRITICAL, "Invalid pointer\n" );
    }
    
    return (int)((jvector_sink_t*)pointer)->key->data;
}

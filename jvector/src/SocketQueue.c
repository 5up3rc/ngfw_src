/*
 * Copyright (c) 2003,2004 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <libmvutil.h>
#include <libvector.h>
#include <mvutil/debug.h>
#include <mvutil/mvpoll.h>
#include <mvutil/errlog.h>
#include <mvutil/debug.h>
#include <vector/event.h>
#include <vector/source.h>
#include <vector/sink.h>

#include "jni_header.h"
#include "jvector.h"
#include "jerror.h"

#include JH_SocketQueue

typedef struct sq_mvpoll_key {
    mvpoll_key_t key;

    /* Just in case there are more cache method ids */
    struct {
        jmethodID poll;
    } mid;
} sq_mvpoll_key_t;

static eventmask_t   _poll_wrapper (mvpoll_key_t* key);

static int  _sq_key_destroy ( mvpoll_key_t* key );

static struct {
    jmethodID poll;
} _socket_queue = 
{
    .poll NULL
};

int socket_queue_init( JNIEnv* env )
{       
    if ( env == NULL ) return errlogargs();
    
    jclass cls    = (*env)->FindClass( env, "com/metavize/jvector/SocketQueue" );

    _socket_queue.poll = (*env)->GetMethodID( env, cls, "poll", "()I" );

    if ( _socket_queue.poll == NULL ) 
        errlog( ERR_CRITICAL, "poll method ID not found\n" );
    
    return 0;
}

/*
 * Class:     SocketQueueImpl
 * Method:    mvpoll_key_notify_observers
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL JF_SocketQueue( mvpoll_1key_1notify_1observers )
  ( JNIEnv* env, jobject _this, jint ptr, jint eventmask )
{
    mvpoll_key_notify_observers((mvpoll_key_t*)ptr,(eventmask_t)eventmask);

    return 0;
}

static eventmask_t   _poll_wrapper (mvpoll_key_t* key)
{
    sq_mvpoll_key_t* sq_key = (sq_mvpoll_key_t*)key;
    JNIEnv* env   = jvector_get_java_env();
    eventmask_t mask;

    if ( sq_key == NULL || sq_key->key.data == NULL ) return errlogargs();

    if ( env == NULL ) return errlog( ERR_CRITICAL, "jvector_get_java_env" );

    /* XXX Probably should go back to the main cached method idz methods */
    // mask = (eventmask_t)(*env)->CallIntMethod( env, key->data, _socket_queue.poll );
    mask = (eventmask_t)(*env)->CallIntMethod( env, sq_key->key.data, sq_key->mid.poll );
    
    if ( jvector_exception_clear() < 0 ) return errlog( ERR_CRITICAL, "Exception calling poll\n" );
    
    return mask;
}

mvpoll_key_t*     socket_queue_key_malloc     ( void )
{
    mvpoll_key_t* key;
    if (( key = malloc( sizeof( sq_mvpoll_key_t ))) == NULL ) return errlogmalloc_null();
    return key;
}

int               socket_queue_key_init       ( mvpoll_key_t* key, jobject this )
{
    sq_mvpoll_key_t* sq_key = (sq_mvpoll_key_t*)key;
    jclass class;

    if ( key == NULL ) return errlogargs();
    JNIEnv* env   = jvector_get_java_env();
    
    if ( env == NULL ) return errlog( ERR_CRITICAL, "jvector_get_java_env" );

    if (( class = (*env)->GetObjectClass( env, this )) == NULL ) {
        return errlog( ERR_CRITICAL, "GetObjectClass" );
    }

    /* XXX Switch back to the cached one */
    if (( sq_key->mid.poll = (*env)->GetMethodID( env, class, "poll", "()I" )) == NULL ) {
        return errlog( ERR_CRITICAL, "Unable to locate method id\n" );
    }

    if ( mvpoll_key_base_init( (mvpoll_key_t*)key ) < 0 ) {
        return errlog( ERR_CRITICAL, "mvpoll_key_base_init" );
    }

    key->type            = JV_KEY_TYPE;
    key->poll            = _poll_wrapper;
    key->special_destroy = _sq_key_destroy;

    if (( key->data = (void*)(*env)->NewGlobalRef( env, this )) == NULL ) {
        return errlog( ERR_CRITICAL, "NewGlobalRef\n" );
    }
    
    return 0;
}

mvpoll_key_t*     socket_queue_key_create     ( jobject this )
{
    mvpoll_key_t* key;

    if (( key = socket_queue_key_malloc()) == NULL ) {
        return errlog_null( ERR_CRITICAL, "socket_queue_key_malloc\n" );
    }
        
    if ( socket_queue_key_init( key, this ) < 0 ) {
        free( key );
        return errlog_null( ERR_CRITICAL, "socket_queue_key_init\n" );
    }
    
    return key;
}

static int _sq_key_destroy ( mvpoll_key_t* key )
{
    JNIEnv* env;

    if ( key == NULL ) return errlogargs();
    
    if ( key->type != JV_KEY_TYPE ) return errlog( ERR_CRITICAL, "Invalid key type: %d\n", key->type );

    if (( env = jvector_get_java_env()) == NULL ) return errlog( ERR_CRITICAL, "jvector_get_java_env\n" );

    if ( key->data != NULL ) (*env)->DeleteGlobalRef( env, key->data );
    
    return 0;
}


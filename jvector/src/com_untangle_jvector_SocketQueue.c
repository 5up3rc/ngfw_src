/*
 * $HeadURL$
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
#include <libmvutil.h>
#include <libvector.h>
#include <mvutil/debug.h>
#include <mvutil/mvpoll.h>
#include <mvutil/errlog.h>
#include <mvutil/debug.h>
#include <jmvutil.h>

#include <vector/event.h>
#include <vector/source.h>
#include <vector/sink.h>

#include "jvector.h"

typedef struct sq_mvpoll_key {
    mvpoll_key_t key;

    /* Just in case there are more cache method ids */
    struct {
        jmethodID poll;
    } mid;
} sq_mvpoll_key_t;

static eventmask_t   _poll_wrapper (mvpoll_key_t* key);

static int  _sq_key_destroy ( mvpoll_key_t* key );

static eventmask_t   _poll_wrapper (mvpoll_key_t* key)
{
    sq_mvpoll_key_t* sq_key = (sq_mvpoll_key_t*)key;
    JNIEnv* env   = jmvutil_get_java_env();
    eventmask_t mask;

    if ( sq_key == NULL || sq_key->key.data.ptr == NULL ) return errlogargs();

    if ( env == NULL ) return errlog( ERR_CRITICAL, "jmvutil_get_java_env" );

    mask = (eventmask_t)(*env)->CallIntMethod( env, sq_key->key.data.ptr, sq_key->mid.poll );
    
    if ( jmvutil_error_exception_clear() < 0 ) return errlog( ERR_CRITICAL, "Exception calling poll\n" );
    
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
    JNIEnv* env   = jmvutil_get_java_env();
    
    if ( env == NULL ) return errlog( ERR_CRITICAL, "jmvutil_get_java_env" );

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

    if (( key->data.ptr = (void*)(*env)->NewGlobalRef( env, this )) == NULL ) {
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

    if (( env = jmvutil_get_java_env()) == NULL ) return errlog( ERR_CRITICAL, "jmvutil_get_java_env\n" );

    if ( key->data.ptr != NULL ) (*env)->DeleteGlobalRef( env, key->data.ptr );
    
    return 0;
}


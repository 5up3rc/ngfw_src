/*
 * Copyright (c) 2003-2006 Untangle, Inc.
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
#include <libmvutil.h>
#include <libvector.h>
#include <mvutil/debug.h>
#include <mvutil/mvpoll.h>
#include <mvutil/errlog.h>
#include <mvutil/debug.h>
#include <jmvutil.h>

#include <vector/event.h>
#include <vector/source.h>

#include "jni_header.h"
#include "jvector.h"

#include JH_Source

JNIEXPORT void JNICALL JF_Source( raze )
    ( JNIEnv *env, jobject _this, jint pointer )
{
    jvector_source_t* jv_src = (jvector_source_t*)pointer;
    
    if ( jv_src == NULL ) {
        errlogargs();
        return;
    }

    if ( jv_src->key != NULL ) 
        mvpoll_key_raze( jv_src->key );

    jv_src->key = NULL;

    if ( jv_src->this != NULL )
        (*env)->DeleteGlobalRef( env, jv_src->this );

    jv_src->this = NULL;

    free( jv_src );
}


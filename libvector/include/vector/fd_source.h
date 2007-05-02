/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * @author: Dirk Morris <dmorris@untangle.com>
 * $Id$
 */
#ifndef __FD_SOURCE_H_
#define __FD_SOURCE_H_

#include "source.h"

typedef struct fd_source {

    source_t base;

    int fd;

    mvpoll_key_t* key;
    
} fd_source_t;

source_t* fd_source_create ( int fd );
event_t*  fd_source_get_event ( source_t* src );
mvpoll_key_t* fd_source_get_event_key ( source_t* src );
int       fd_source_shutdown ( source_t* src );
void      fd_source_raze ( source_t* src );

#endif

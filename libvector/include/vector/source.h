/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
#ifndef __SOURCE_H
#define __SOURCE_H

#include <mvutil/mvpoll.h>
#include "event.h"

typedef struct source {
    event_t*      (*get_event) (struct source* src);
    mvpoll_key_t* (*get_event_key) (struct source* src);
    int           (*shutdown) (struct source* src);
    void          (*raze) (struct source* src);
} source_t;

#endif

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
#include <mvutil/debug.h>
#include <mvutil/errlog.h>
#include "netcap_server.h"

/*! \brief donates a thread to the pool
 *  you must donate atleast one thread for netcap to start \n
 *  the format is meant to be used with pthread_create    \n
 *  or you can just call it from a thread or process      \n 
 *  arg is meaningless and is ignored                     \n
 * 
 *  \param arg is ignored 
 *  \return int thread_id can be used to stop or reclaim thread
 */
void* netcap_thread_donate (void* arg)
{
    debug(4,"NETCAP: Donating Thread\n");
    netcap_server();
    return NULL;
}

/*! \brief removes a thread from the pool
 *  \warning not implemented
 *  \return 0 upon success, -1 otherwise
 */
int netcap_thread_undonate (int thread_id)
{
    return errlog(ERR_CRITICAL,"Unimplemented\n");
}

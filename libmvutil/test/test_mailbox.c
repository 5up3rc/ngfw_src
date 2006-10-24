/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include "libmvutil.h"
#include "mailbox.h"
#include "errlog.h"
#include "debug.h"


#define MB_EMPTY_TEST(mb)    if (mailbox_try_get((mb))) \
                                return errlog(ERR_CRITICAL,"mailbox should be empty\n");
#define MB_PUT(mb,val)       if (mailbox_put((mb),(val))<0) \
                                return errlog(ERR_CRITICAL,"mailbox_put failed\n");
#define MB_GET_EQU(mb,test)   if ((mail = mailbox_get((mb))) != (void*)test) \
                                return errlog(ERR_CRITICAL,"mailbox_get failed %08x != %08x\n",mail,test);
#define MB_TRY_GET_EQU(mb,test)   if ((mail = mailbox_try_get((mb))) != (void*)test) \
                                return errlog(ERR_CRITICAL,"mailbox_get failed %08x != %08x\n",mail,test);
#define MB_TIMED_GET_EQU(mb,test)   if ((mail = mailbox_timed_get((mb),1)) != (void*)test) \
                                return errlog(ERR_CRITICAL,"mailbox_get failed %08x != %08x\n",mail,test);

int main()
{
    mailbox_t mb;
    void*     mail;
    int i;

    libmvutil_init();

    if (mailbox_init(&mb)<0)
        return errlog(ERR_CRITICAL,"mailbox_init failed\n");
    
    MB_EMPTY_TEST(&mb);

    MB_PUT(&mb,(void*)1);
    MB_GET_EQU(&mb,(void*)1);
    MB_EMPTY_TEST(&mb);

    MB_PUT(&mb,(void*)1);
    MB_PUT(&mb,(void*)2);
    MB_PUT(&mb,(void*)3);
    MB_PUT(&mb,(void*)4);
    MB_GET_EQU(&mb,(void*)1);
    MB_TRY_GET_EQU(&mb,(void*)2);
    MB_TIMED_GET_EQU(&mb,(void*)3);
    MB_GET_EQU(&mb,(void*)4);
    MB_EMPTY_TEST(&mb);
    
    for(i=1;i<100;i++)
        MB_PUT(&mb,(void*)i);
    for(i=1;i<100;i++)
        MB_GET_EQU(&mb,(void*)i);
    MB_EMPTY_TEST(&mb);

    if (mailbox_destroy(&mb)<0)
        return errlog(ERR_CRITICAL,"mailbox_destroy failed\n");

    return 0;
}
    

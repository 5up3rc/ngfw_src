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
#ifndef __NETCAP_SESSTABLE_H_
#define __NETCAP_SESSTABLE_H_

#include "netcap_session.h"

#define NC_SESSTABLE_LOCK   1

/**
 * Sesstable stores a table of all sessions accessable in several forms
 * first, you can get a sess by its session id.
 * you can also get a session its client-side or server-side 5-tuple
 */

int        netcap_sesstable_init (void);
int        netcap_sesstable_cleanup (void);


netcap_session_t* netcap_nc_sesstable_get (int if_lock, u_int id);

netcap_session_t* netcap_nc_sesstable_get_tuple (int if_lock, int proto, 
                                                 in_addr_t src, in_addr_t dst, 
                                                 u_short sport, u_short dport,
                                                 u_int seq);

list_t*    netcap_nc_sesstable_get_all_sessions ( int if_lock );
int        netcap_nc_sesstable_numsessions (int if_lock);

int        netcap_nc_sesstable_add (int if_lock, netcap_session_t* sess);
int        netcap_nc_sesstable_add_tuple (int if_lock, netcap_session_t* sess, 
                                          int protocol, 
                                          in_addr_t src, in_addr_t dst, 
                                          u_short sport, u_short dport, u_int seq);


int        netcap_sesstable_remove (int if_lock, netcap_session_t* netcap_sess);
int        netcap_sesstable_remove_tuple (int if_lock, int proto, 
                                          in_addr_t shost, in_addr_t dhost, 
                                          u_short sport, u_short dport, u_int seq);

int netcap_sesstable_remove_session (int if_lock, netcap_session_t* netcap_sess);

int netcap_sesstable_nextid(void);

#define SESSTABLE_UNLOCK()  if (lock_unlock(&netcap_sesstable_lock)) \
                                errlog(ERR_CRITICAL,"lock_unlock failed\n"); 
#define SESSTABLE_WRLOCK()  if (lock_wrlock(&netcap_sesstable_lock)<0) \
                                errlog(ERR_CRITICAL,"lock_wrlock failed\n"); 
#define SESSTABLE_RDLOCK()  if (lock_rdlock(&netcap_sesstable_lock)<0) \
                                errlog(ERR_CRITICAL,"lock_wrlock failed\n"); 

extern lock_t netcap_sesstable_lock;

#endif

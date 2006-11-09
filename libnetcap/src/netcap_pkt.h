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
#ifndef __NETCAP_PKT_
#define __NETCAP_PKT_

#include <netinet/in.h>

#include "libnetcap.h"

netcap_pkt_t*  netcap_pkt_malloc (void);
int            netcap_pkt_init   (netcap_pkt_t* pkt);
netcap_pkt_t*  netcap_pkt_create (void);

int            netcap_pkt_action_raze ( netcap_pkt_t* pkt, int action );

struct iphdr*  netcap_pkt_get_ip_hdr  ( netcap_pkt_t* pkt );
struct tcphdr* netcap_pkt_get_tcp_hdr ( netcap_pkt_t* pkt );


#endif

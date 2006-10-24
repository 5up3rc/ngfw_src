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

#ifndef _NETCAP_ARP_H_
#define _NETCAP_ARP_H_

#include <netinet/in.h>
#include <net/ethernet.h>

#include "libnetcap.h"
#include "netcap_intf_db.h"

/* Maximum number of arps to try before giving, This guarantees that
 * the delay array eventually ends  */
#define NETCAP_ARP_MAX     15

#define NETCAP_ARP_SUCCESS  1
#define NETCAP_ARP_NOERROR  0

struct netcap_bridge_info;

int netcap_arp_init            ( void );

int netcap_arp_cleanup         ( void );

/**
 * Retrieve the necessary information to determine the interface a
 * packet will go out on over a bridge.
 */
int netcap_arp_configure_bridge( netcap_intf_db_t* db, netcap_intf_info_t* intf_info );

/**
 * Request the outgoing interface for an IP address using the default delay array.
 * @param intf     - The interface index that the packet is going out on.
 * @param src_intf - The interface that the session came in on. (unused, but may be important in the future)
 * @param src_ip   - The source IP address. (unused, but may be important in the future).
 * @param dst_ip   - The ip address to lookup the outgoing interface for.
 * @return       NETCAP_ARP_SUCCESS: found interface
 *               NETCAP_ARP_NOERROR: no errors, but unable to find the interface.
 *               < 0               : an error occured.
 */
int netcap_arp_dst_intf       ( netcap_intf_t* intf, netcap_intf_t src_intf, struct in_addr* src_ip, 
                                struct in_addr* dst_ip );


/**
 * Request the outgoing interface for an IP address passing in the desired delay array.
 * @param intf     - The interface index that the packet is going out on.
 * @param src_intf - The interface that the session came in on.
 * @param src_ip   - The source IP address.
 * @param dst_ip   - The ip address to lookup the outgoing interface for.
 * @param delay    - An array of delays to wait for a response.  The last value should be zero.
 * @return         NETCAP_ARP_SUCCESS: found interface
 *                 NETCAP_ARP_NOERROR: no errors, but unable to find the interface.
 *                 < 0               : an error occured.
 */
int netcap_arp_dst_intf_delay ( netcap_intf_t* intf, netcap_intf_t src_intf, struct in_addr* src_ip, 
                                struct in_addr* dst_ip, unsigned long* delays );

/**
 * Request the MAC address of IP, and place the result into mac.
 * @param ip         - The ip address to lookup.
 * @param mac        - The MAC address, on success.
 * @param intf_index - The os index of the bridge device.
 * @param delays     - An array of delays to wait for a response.  The last value should be zero, 
 * @return           NETCAP_ARP_SUCCESS: found address
 *                   NETCAP_ARP_NOERROR: no errors, but unable to find the address.
 *                   < 0               : an error occured.
 */
int netcap_arp_address        ( struct in_addr* dst_ip, struct ether_addr* mac, int bridge_intf_index, 
                                 unsigned long* delays );

/**
 * Request the interface that a MAC address is going out on.
 * @param out_intf    - Updating to contain the name of the interface
 * @param mac_address - The mac address to lookup.
 * @param intf_index  - The os index of the bridge device.
 * @return            NETCAP_ARP_SUCCESS: found interface
 *                    NETCAP_ARP_NOERROR: no errors, but unable to find the interface.
 *                    < 0               : an error occured.
 */
int netcap_arp_bridge_intf    ( netcap_intf_t* out_intf, struct ether_addr* mac_address, 
                                int bridge_intf_index );
                                 

#endif // _NETCAP_ARP_H_

/*
 * Copyright (c) 2003 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

#ifndef __NETCAP_ICMP_MSG_H_
#define __NETCAP_ICMP_MSG_H_

typedef struct
{
    int   type;
    int   data_len;
    /* This is where the data is stored.  A message is just one contiguous block that starts
     * with the size */
    char  data;
} netcap_icmp_msg_t;

#define NETCAP_ICMP_MSG_BASE_SIZE (( sizeof( netcap_icmp_msg_t ) - sizeof( char )))
#define NETCAP_ICMP_MSG_TYPE      0xEDED5444


/* 
 * data_len: Size of the message you want to allocate for, this should not include
 * the four byes for the length
 */
netcap_icmp_msg_t* netcap_icmp_msg_malloc ( int data_len );

/**
 * msg_size: Size of the msg in bytes, data is actually copied into msg, 
 *           and this value must be greater or equal to than 4 + data_len
 */
int                netcap_icmp_msg_init   ( netcap_icmp_msg_t* msg, int msg_size, char* data, int data_len );

netcap_icmp_msg_t* netcap_icmp_msg_create ( char* data, int data_len );

int                netcap_icmp_msg_free    ( netcap_icmp_msg_t* msg );
int                netcap_icmp_msg_destroy ( netcap_icmp_msg_t* msg );
int                netcap_icmp_msg_raze    ( netcap_icmp_msg_t* msg );

#endif // __NETCAP_ICMP_MSG_H

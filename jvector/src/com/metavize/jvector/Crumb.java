/*
 * Copyright (c) 2003,2004 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.jvector;

public abstract class Crumb
{
    protected static final int DATA_MASK     = 0x1100;
    protected static final int SHUTDOWN_MASK = 0x1200;

    public static final int TYPE_DATA        = DATA_MASK | 1;      // Data crumb, passed as is
    public static final int TYPE_UDP_PACKET  = DATA_MASK | 2;      // UDP Packet, this extends a PacketCrumb
    public static final int TYPE_ICMP_PACKET = DATA_MASK | 3;      // ICMP packet, this extends a PacketCrumb

    public static final int TYPE_SHUTDOWN    = SHUTDOWN_MASK | 1;  // Shutdown
    public static final int TYPE_RESET       = SHUTDOWN_MASK | 2;  // Reset

    public abstract void raze();
    public abstract int  type();

    public boolean isData()
    {
        return ( type() & DATA_MASK ) == DATA_MASK;
    }

    public boolean isShutdown()
    {
        return ( type() & SHUTDOWN_MASK ) == SHUTDOWN_MASK;
    }
}

/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.tran;

import java.net.InetAddress;

import org.apache.log4j.Logger;

public class AddressRange implements Comparable<AddressRange>
{
    private static final Logger logger = Logger.getLogger( AddressRange.class );

    private final long start;
    private final long end;
    /* These are addresses that are globally illegal, used for
     * providing more useful information to the user */
    private final boolean isIllegal;
    private final String description;

    static final int INADDRSZ = 4;
    
    private AddressRange( long start, long end, String description, boolean isIllegal )
    {
        this.start       = start;
        this.end         = end;
        this.description = description;
        this.isIllegal   = isIllegal;
    }
    
    long getStart()
    {
        return this.start;
    }

    long getEnd()
    {
        return this.end;
    }

    String getDescription()
    {
        return this.description;
    }

    boolean getIsIllegal()
    {
        return this.isIllegal;
    }

    public static AddressRange makeNetwork( InetAddress network, InetAddress netmask )
    {
        return makeNetwork( network, netmask, false );
    }

    public static AddressRange makeNetwork( InetAddress network, InetAddress netmask, boolean isIllegal )
    {
        long networkLong = toLong( network );
        long netmaskLong = toLong( netmask );

        long start = ( networkLong & netmaskLong );
        /* The anding gets rid of the negative values */
        long end   = ( start | ( ~netmaskLong & 0xFFFFFFFFL ));

        if ( start > end ) {
            logger.warn( "Made a subnet the incorrect way[" + networkLong + "/" + netmaskLong + "] -> " +
                         start + ":" + end );
            long temp = start;
            start = end;
            end = temp;
        }
        
        return new AddressRange( start, end, network.getHostAddress() + "/" + netmask.getHostAddress(), 
                                 isIllegal );
    }

    public static AddressRange makeRange( InetAddress start, InetAddress end )
    {
        return makeRange( start, end, false );
    }
        
    public static AddressRange makeRange( InetAddress start, InetAddress end, boolean isIllegal )
    {
        long startLong = toLong( start );
        long endLong = toLong( end );
        
        if ( startLong < endLong ) {
            long temp = startLong;
            startLong = endLong;
            endLong = temp;
        }

        return new AddressRange( startLong, endLong, start.getHostAddress() + " - " + end.getHostAddress(), 
                                 isIllegal );
    }

    static long toLong( InetAddress address )
    {
        long val = 0;
        
        byte valArray[] = address.getAddress();
        
        for ( int c = 0 ; c < INADDRSZ ; c++ ) {
            val += ((long)byteToInt(valArray[c])) << ( 8 * ( INADDRSZ - c - 1 ));
        }

        return val;
    }

    static int byteToInt ( byte val ) 
    {
        int num = val;
        if ( num < 0 ) num = ( num & 0x7F ) | 0x80;
        return num;
    }


    public int compareTo( AddressRange otherRange )
    {
        /* If this node starts before the other node, then it is less than the other node */
        if ( this.getStart() < otherRange.getStart()) return -1;
        if ( this.getStart() > otherRange.getStart()) return 1;

        /* Starts are equal, check the ends */
        if ( this.getEnd()   < otherRange.getEnd()) return -1;
        if ( this.getEnd()   > otherRange.getEnd()) return 1;

        /* Nodes are equal */
        return 0;
    }
}

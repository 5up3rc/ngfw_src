
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

package com.metavize.mvvm.tran;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/* This adds the extension that an empty string is parseable as a null IPaddr.
 * This is different from an IPaddr where an empty string is not parseable.
 * This is not a very elegant class, and should be destroyed. (rbs).
 */
public class IPNullAddr extends IPaddr implements Comparable
{
    private static final long serialVersionUID = -741858749430271001L;

    private static final IPNullAddr EMPTY_ADDR = new IPNullAddr( null );
    
    public IPNullAddr( Inet4Address addr )
    {
        super( addr );
    }
    
    public static IPNullAddr parse( String dotNotation ) 
        throws ParseException, UnknownHostException
    {
        /* Trim any whitespace */
        dotNotation = dotNotation.trim();
        
        if ( dotNotation.length() == 0 ) {
            return EMPTY_ADDR;
        } else {
            return new IPNullAddr((Inet4Address)IPaddr.parse( dotNotation ).getAddr());
        }        
    }

    public static IPNullAddr getNullAddr()
    {
        return EMPTY_ADDR;
    }

    public boolean equals( Object o )
    {
        if ( o instanceof IPNullAddr )
	    return true;
	else
	    return false;
    }

    public int compareTo(Object o)
    {
	if( o instanceof IPNullAddr )
	    return 0;
	else
	    return -1;
    }
}


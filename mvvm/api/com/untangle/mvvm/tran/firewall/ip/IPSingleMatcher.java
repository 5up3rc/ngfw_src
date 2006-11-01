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

package com.untangle.mvvm.tran.firewall.ip;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.untangle.mvvm.tran.IPaddr;

import com.untangle.mvvm.tran.ParseException;
import com.untangle.mvvm.tran.firewall.Parser;
import com.untangle.mvvm.tran.firewall.ParsingConstants;

public final class IPSingleMatcher extends IPDBMatcher
{
    private final InetAddress address;

    private IPSingleMatcher( InetAddress address )
    {
        this.address = address;
    }

    public boolean isMatch( InetAddress address )
    {
        return this.address.equals( address );
    }

    public String toDatabaseString()
    {
        return toString();
    }
    
    public String toString()
    {
        return this.address.getHostAddress();
    }

    public static IPDBMatcher makeInstance( InetAddress address )
    {
        if ( address == null ) throw new NullPointerException( "Null address" );
        return new IPSingleMatcher( address );
    }

    public static IPDBMatcher makeInstance( IPaddr address )
    {
        return makeInstance( address.getAddr());
    }

    /* This is just for matching a list of interfaces */
    static final Parser<IPDBMatcher> PARSER = new Parser<IPDBMatcher>() 
    {
        public int priority()
        {
            return 10;
        }
        
        public boolean isParseable( String value )
        {
            return ( !value.contains( ParsingConstants.MARKER_SEPERATOR ) &&
                     !value.contains( IPMatcherUtil.MARKER_RANGE ) &&
                     !value.contains( IPMatcherUtil.MARKER_SUBNET ));
        }
        
        public IPDBMatcher parse( String value ) throws ParseException
        {
            if ( !isParseable( value )) {
                throw new ParseException( "Invalid ip single matcher '" + value + "'" );
            }
            
            try {
                return makeInstance( IPaddr.parse( value ));
            } catch ( UnknownHostException e ) {
                throw new ParseException( e );
            }
        }
    };
}


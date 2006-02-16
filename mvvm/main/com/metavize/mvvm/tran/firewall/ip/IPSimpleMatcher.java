/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id$
 */

package com.metavize.mvvm.tran.firewall.ip;

import java.net.InetAddress;

import com.metavize.mvvm.tran.IPaddr;

import com.metavize.mvvm.tran.ParseException;
import com.metavize.mvvm.tran.firewall.Parser;
import com.metavize.mvvm.tran.firewall.ParsingConstants;

public final class IPSimpleMatcher extends IPDBMatcher
{
    private static final IPDBMatcher ALL_MATCHER     = new IPSimpleMatcher( true );
    private static final IPDBMatcher NOTHING_MATCHER = new IPSimpleMatcher( false );
    
    private final boolean isAll;

    private IPSimpleMatcher( boolean isAll )
    {
        this.isAll = isAll;
    }

    public boolean isMatch( InetAddress address )
    {
        return isAll;
    }

    public String toDatabaseString()
    {
        return toString();
    }
    
    public String toString()
    {
        if ( isAll ) return ParsingConstants.MARKER_ALL;
        return ParsingConstants.MARKER_NOTHING;
    }

    public static IPDBMatcher getAllMatcher()
    {
        return ALL_MATCHER;
    }

    public static IPDBMatcher getNilMatcher()
    {
        return NOTHING_MATCHER;
    }
    
    static final Parser<IPDBMatcher> PARSER = new Parser<IPDBMatcher>() 
    {
        public int priority()
        {
            return 0;
        }
        
        public boolean isParseable( String value )
        {
            return ( value.equalsIgnoreCase( ParsingConstants.MARKER_ANY ) ||
                     value.equalsIgnoreCase( ParsingConstants.MARKER_WILDCARD ) ||
                     value.equalsIgnoreCase( ParsingConstants.MARKER_ALL ) ||
                     value.equalsIgnoreCase( ParsingConstants.MARKER_NOTHING ));
        }
        
        public IPDBMatcher parse( String value ) throws ParseException
        {
            if ( !isParseable( value )) {
                throw new ParseException( "Invalid ip simple matcher '" + value + "'" );
            }
            
            if ( value.equalsIgnoreCase( ParsingConstants.MARKER_ANY ) || 
                 value.equalsIgnoreCase( ParsingConstants.MARKER_WILDCARD ) ||
                 value.equalsIgnoreCase( ParsingConstants.MARKER_ALL )) {
                     return ALL_MATCHER;
                 } else if ( value.equalsIgnoreCase( ParsingConstants.MARKER_NOTHING )) {
                     return NOTHING_MATCHER;
                 }
            
            throw new ParseException( "Invalid ip simple matcher '" + value + "'" );
        }
    };
}


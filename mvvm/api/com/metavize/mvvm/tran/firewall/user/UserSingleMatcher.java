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

package com.metavize.mvvm.tran.firewall.user;

import java.util.Collections;
import java.util.List;

import com.metavize.mvvm.tran.ParseException;
import com.metavize.mvvm.tran.firewall.Parser;
import com.metavize.mvvm.tran.firewall.ParsingConstants;

public final class UserSingleMatcher extends UserDBMatcher
{
    private static final long serialVersionUID = 8513194248419629420L;

    private final String user;

    private UserSingleMatcher( String user )
    {
        this.user = user;
    }

    public boolean isMatch( String user )
    {
        /* ignore case ??? */
        return ( this.user.equalsIgnoreCase( user ));
    }

    public List<String> toDatabaseList()
    {
        return Collections.nCopies( 1, toString());
    }
        
    public String toString()
    {
        return this.user;
    }

    public static UserDBMatcher makeInstance( String user )
    {
        return new UserSingleMatcher( user );
    }

    /* This is just for matching a list of interfaces */
    static final Parser<UserDBMatcher> PARSER = new Parser<UserDBMatcher>() 
    {
        public int priority()
        {
            return 10;
        }
        
        public boolean isParseable( String value )
        {
            return !value.contains( ParsingConstants.MARKER_SEPERATOR );
        }
        
        public UserDBMatcher parse( String value ) throws ParseException
        {
            if ( !isParseable( value )) {
                throw new ParseException( "Invalid port single matcher '" + value + "'" );
            }
            
            return makeInstance( value );
        }
    };
}


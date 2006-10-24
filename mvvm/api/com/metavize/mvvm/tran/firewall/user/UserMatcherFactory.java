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

package com.metavize.mvvm.tran.firewall.user;

import com.metavize.mvvm.tran.ParseException;

import com.metavize.mvvm.tran.firewall.ParsingFactory;

public class UserMatcherFactory
{
    private static final UserMatcherFactory INSTANCE = new UserMatcherFactory();

    private final ParsingFactory<UserDBMatcher> parser;

    private UserMatcherFactory()
    {
        this.parser = new ParsingFactory<UserDBMatcher>( "user matcher" );
        this.parser.registerParsers( UserSimpleMatcher.PARSER, UserSingleMatcher.PARSER,
                                     UserSetMatcher.PARSER );
    }

    public UserDBMatcher getAllMatcher() 
    {
        return UserSimpleMatcher.getAllMatcher();
    }
    
    public UserDBMatcher getNilMatcher() 
    {
        return UserSimpleMatcher.getNilMatcher();
    }

    public UserDBMatcher makeSingleMatcher( String user )
    {
        return UserSingleMatcher.makeInstance( user );
    }

    public UserDBMatcher makeSetMatcher( String ... userArray )
    {
        switch ( userArray.length ) {
        case 0: return UserSimpleMatcher.getNilMatcher();
        case 1: return makeSingleMatcher( userArray[0] );
        default: return UserSetMatcher.makeInstance( userArray );
        }
    }
    
    public static final UserDBMatcher parse( String value ) throws ParseException
    {
        return INSTANCE.parser.parse( value );
    }

    public static final UserMatcherFactory getInstance()
    {
        return INSTANCE;
    }
}

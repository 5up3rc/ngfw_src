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

package com.metavize.mvvm.tran.firewall.time;

import com.metavize.mvvm.tran.ParseException;

import com.metavize.mvvm.tran.firewall.ParsingFactory;

public class DayOfWeekMatcherFactory
{
    private static final DayOfWeekMatcherFactory INSTANCE = new DayOfWeekMatcherFactory();

    private final ParsingFactory<DayOfWeekDBMatcher> parser;

    private DayOfWeekMatcherFactory()
    {
        this.parser = new ParsingFactory<DayOfWeekDBMatcher>( "dayOfWeek matcher" );
        this.parser.registerParsers( DayOfWeekSimpleMatcher.PARSER, DayOfWeekSingleMatcher.PARSER,
                                     DayOfWeekSetMatcher.PARSER );
    }

    public DayOfWeekDBMatcher getAllMatcher() 
    {
        return DayOfWeekSimpleMatcher.getAllMatcher();
    }
    
    public DayOfWeekDBMatcher getNilMatcher() 
    {
        return DayOfWeekSimpleMatcher.getNilMatcher();
    }

    public DayOfWeekDBMatcher makeSingleMatcher( String dayOfWeek )
    {
        return DayOfWeekSingleMatcher.makeInstance( dayOfWeek );
    }

    public DayOfWeekDBMatcher makeSetMatcher( String ... dayOfWeekArray )
    {
        switch ( dayOfWeekArray.length ) {
        case 0: return DayOfWeekSimpleMatcher.getNilMatcher();
        case 1: return makeSingleMatcher( dayOfWeekArray[0] );
        default: return DayOfWeekSetMatcher.makeInstance( dayOfWeekArray );
        }
    }
    
    public static final DayOfWeekDBMatcher parse( String value ) throws ParseException
    {
        return INSTANCE.parser.parse( value );
    }

    public static final DayOfWeekMatcherFactory getInstance()
    {
        return INSTANCE;
    }
}

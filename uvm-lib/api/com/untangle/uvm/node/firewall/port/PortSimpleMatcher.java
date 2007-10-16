/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module.  An independent
 * module is a module which is not derived from or based on this library.
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

package com.untangle.uvm.node.firewall.port;

import com.untangle.uvm.node.ParseException;
import com.untangle.uvm.node.firewall.Parser;
import com.untangle.uvm.node.firewall.ParsingConstants;

/**
 * PortMatchers designed for simple cases (all, nothing or ping sessions).
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 * @version 1.0
 */
public final class PortSimpleMatcher
{
    /* A Port Matcher that matches everything */
    private static final PortDBMatcher ALL_MATCHER = new PortDBMatcher()
        {
            public boolean isMatch( int port )
            {
                return true;
            }

            public String toDatabaseString()
            {
                return toString();
            }
            
            public String toString()
            {
                return ParsingConstants.MARKER_ANY;
            }
        };

    /* A Port Matcher that doesn't match anything */
    private static final PortDBMatcher NOTHING_MATCHER     = new PortDBMatcher()
        {
            public boolean isMatch( int port )
            {
                return false;
            }

            public String toDatabaseString()
            {
                return toString();
            }
            
            public String toString()
            {
                return ParsingConstants.MARKER_NOTHING;
            }
        };

    /* A matcher that matches ping sessions */
    private static final PortDBMatcher PING_MATCHER     = new PortDBMatcher()
        {
            public boolean isMatch( int port )
            {
                return ( port == 0 );
            }

            public String toDatabaseString()
            {
                return toString();
            }
            
            public String toString()
            {
                return ParsingConstants.MARKER_NA;
            }
        };
        
    /**
     * Retrieve the all matcher.
     *
     * @return A matcher that matches every port.
     */
    public static PortDBMatcher getAllMatcher()
    {
        return ALL_MATCHER;
    }

    /**
     * Retrieve the nil matcher.
     *
     * @return A matcher that never matches a port.
     */
    public static PortDBMatcher getNilMatcher()
    {
        return NOTHING_MATCHER;
    }

    /**
     * Retrieve the ping matcher.
     *
     * @return A matcher that matches ping sessions.
     */
    public static PortDBMatcher getPingMatcher()
    {
        return PING_MATCHER;
    }

    /* This is the parser for simple port matchers */
    static final Parser<PortDBMatcher> PARSER = new Parser<PortDBMatcher>() 
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
                     value.equalsIgnoreCase( ParsingConstants.MARKER_NOTHING ) ||
                     value.equalsIgnoreCase( ParsingConstants.MARKER_NA ));            
        }
        
        public PortDBMatcher parse( String value ) throws ParseException
        {
            if ( !isParseable( value )) {
                throw new ParseException( "Invalid port simple matcher '" + value + "'" );
            }
            
            if ( value.equalsIgnoreCase( ParsingConstants.MARKER_ANY ) || 
                 value.equalsIgnoreCase( ParsingConstants.MARKER_WILDCARD ) ||
                 value.equalsIgnoreCase( ParsingConstants.MARKER_ALL )) {
                     return ALL_MATCHER;
                 } else if ( value.equalsIgnoreCase( ParsingConstants.MARKER_NOTHING )) {
                     return NOTHING_MATCHER;
                 } else if ( value.equalsIgnoreCase( ParsingConstants.MARKER_NA )) {
                     return PING_MATCHER;
                 }
            
            throw new ParseException( "Invalid port simple matcher '" + value + "'" );
        }
    };
}

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

package com.untangle.uvm.node.firewall.ip;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.untangle.uvm.node.IPaddr;

import com.untangle.uvm.node.ParseException;
import com.untangle.uvm.node.firewall.Parser;
import com.untangle.uvm.node.firewall.ParsingConstants;

/**
 * An IPMatcher that matches a single IP address.
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 * @version 1.0
 */
public final class IPSingleMatcher implements IPDBMatcher
{
    /* The address that matches */
    private final InetAddress address;

    private IPSingleMatcher( InetAddress address )
    {
        this.address = address;
    }

    /**
     * Determine if <param>address</param> matches the address for
     * this matcher.
     *
     * @param address The address to test.
     * @return True if <param>address</param> matches the address for
     * this matcher.
     */
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

    /**
     * Create a single matcher.
     *
     * @param address The address that should match.
     * @return An IPMatcher that matches IP address <param>address</param>
     */
    public static IPDBMatcher makeInstance( InetAddress address )
    {
        if ( address == null ) throw new NullPointerException( "Null address" );
        return new IPSingleMatcher( address );
    }

    /**
     * Create a single matcher, uses an IPaddr instead of an
     * InetAddress.
     *
     * @param address The address that should match.
     * @return An IPMatcher that matches IP address <param>address</param>
     */
    public static IPDBMatcher makeInstance( IPaddr address )
    {
        return makeInstance( address.getAddr());
    }

    /* This is the parser for a single matcher */
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


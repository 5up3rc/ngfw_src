 
/*
 * $HeadURL:$
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

package com.untangle.uvm.node.firewall;

import com.untangle.uvm.node.ParseException;

/**
 * An interface for a parser.
 *
 * A parser is designed to take a string and return the object that
 * string represents.  This is used by the ParsingFactory.
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 * @version 1.0
 */
public interface Parser<T>
{
    /**
     * Parse a string and create a new object.
     *
     * Attempt to parse a string into an object.  If the object is not
     * parseable, then isParseable should return false.  If the object
     * is parseable, but contains errors, then this should throw a parse
     * exception.  (EG. an IP address with one component greater than 255).
     * This function should never return null.
     *
     * @param value The value to parse.
     * @return Object represented by <param>value</param>.
     */
    public T parse( String value ) throws ParseException;

    /**
     * Determine whether or not this parser is capable of parsing
     * <param>value</param>
     *
     * @param value The value to test.
     * @return True if <param>value</value> can be parsed by this parser.
     */
    public boolean isParseable( String value );

    /**
     * Return the priortity of this parser.  Priority 0 is evaluated first.
     *
     * @return The priority of this parser.  (0 is the highest priority).
     */
    public int priority();
}

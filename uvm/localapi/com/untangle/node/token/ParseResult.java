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

package com.untangle.node.token;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class ParseResult
{
    private final List<Token> results;
    private final ByteBuffer readBuffer;
    private final TokenStreamer tokenStreamer;

    // constructors -----------------------------------------------------------

    public ParseResult(List<Token> results, ByteBuffer readBuffer)
    {
        if (null == results) {
            this.results = new LinkedList<Token>();
        } else {
            this.results = results;
        }
        this.tokenStreamer = null;
        this.readBuffer = readBuffer;
    }

    public ParseResult(Token result, ByteBuffer readBuffer)
    {
        if (null == result) {
            this.results = new LinkedList<Token>();
        } else {
            this.results = Arrays.asList(result);
        }
        this.tokenStreamer = null;
        this.readBuffer = readBuffer;
    }

    public ParseResult(ByteBuffer readBuffer)
    {
        this.results = new LinkedList<Token>();
        this.tokenStreamer = null;
        this.readBuffer = readBuffer;
    }

    public ParseResult(Token result)
    {
        if (null == result) {
            this.results = new LinkedList<Token>();
        } else {
            this.results = Arrays.asList(result);
        }
        this.tokenStreamer = null;
        this.readBuffer = null;
    }

    public ParseResult(List<Token> tokens)
    {
        this.results = null == tokens ? new LinkedList<Token>() : tokens;
        this.tokenStreamer = null;
        this.readBuffer = null;
    }

    public ParseResult(TokenStreamer tokenStreamer, ByteBuffer readBuffer)
    {
        results = null;
        this.tokenStreamer = tokenStreamer;
        this.readBuffer = readBuffer;
    }

    public ParseResult()
    {
        this.results = new LinkedList<Token>();
        this.tokenStreamer = null;
        this.readBuffer = null;
    }

    // accessors --------------------------------------------------------------

    public List<Token> getResults()
    {
        return results;
    }

    public ByteBuffer getReadBuffer()
    {
        return readBuffer;
    }

    public TokenStreamer getTokenStreamer()
    {
        return tokenStreamer;
    }

    public boolean isStreamer()
    {
        return null != tokenStreamer;
    }
}

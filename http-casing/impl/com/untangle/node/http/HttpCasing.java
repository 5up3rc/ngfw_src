/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.node.http;

import java.util.LinkedList;
import java.util.List;

import com.untangle.node.token.Casing;
import com.untangle.node.token.Parser;
import com.untangle.node.token.Unparser;
import com.untangle.uvm.vnet.TCPSession;
import org.apache.log4j.Logger;

/**
 * An HTTP <code>Casing</code>.
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
class HttpCasing implements Casing
{
    private final HttpNodeImpl node;
    private final HttpParser parser;
    private final HttpUnparser unparser;
    private final List<RequestLineToken> requests = new LinkedList<RequestLineToken>();

    private final Logger logger = Logger.getLogger(getClass());

    // constructors -----------------------------------------------------------

    HttpCasing(TCPSession session, boolean clientSide,
               HttpNodeImpl node)
    {
        this.node = node;
        parser = new HttpParser(session, clientSide, this);
        unparser = new HttpUnparser(session, clientSide, this);
    }

    // Casing methods ---------------------------------------------------------

    public Unparser unparser()
    {
        return unparser;
    }

    public Parser parser()
    {
        return parser;
    }

    // package private methods ------------------------------------------------

    HttpNodeImpl getNode()
    {
        return node;
    }

    void queueRequest(RequestLineToken request)
    {
        requests.add(request);
    }

    RequestLineToken dequeueRequest(int statusCode)
    {
        if (0 < requests.size()) {
            return requests.remove(0);
        } else {
            if (4 != statusCode / 100) {
                logger.warn("requests is empty: " + statusCode);
            }
            return null;
        }
    }
}

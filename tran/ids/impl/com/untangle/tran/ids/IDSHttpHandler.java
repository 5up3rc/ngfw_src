/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.ids;

import com.untangle.mvvm.tapi.TCPSession;
import com.untangle.tran.http.HttpStateMachine;
import com.untangle.tran.http.RequestLineToken;
import com.untangle.tran.http.StatusLine;
import com.untangle.tran.token.Chunk;
import com.untangle.tran.token.Header;
import org.apache.log4j.Logger;

class IDSHttpHandler extends HttpStateMachine {

    private final Logger logger = Logger.getLogger(getClass());

    private IDSDetectionEngine engine;

    IDSHttpHandler(TCPSession session, IDSTransformImpl transform) {
        super(session);
        engine = transform.getEngine();
    }

    protected RequestLineToken doRequestLine(RequestLineToken requestLine) {
        IDSSessionInfo info = engine.getSessionInfo(getSession());
        if (info != null) {
            // Null is no longer unusual, it happens whenever we've released the
            // session from the byte pipe.
            String path = requestLine.getRequestUri().normalize().getPath();
            info.setUriPath(path);
        }
        releaseRequest();
        return requestLine;
    }

    protected Header doRequestHeader(Header requestHeader) {
        return requestHeader;
    }

    protected void doRequestBodyEnd() { }

    protected void doResponseBodyEnd() { }

    protected Chunk doResponseBody(Chunk chunk) {
        return chunk;
    }

    protected Header doResponseHeader(Header header) {
        return header;
    }

    protected Chunk doRequestBody(Chunk chunk) {
        return chunk;
    }

    protected StatusLine doStatusLine(StatusLine statusLine) {
        releaseResponse();
        return statusLine;
    }
}

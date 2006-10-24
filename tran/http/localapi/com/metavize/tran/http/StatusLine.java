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

package com.metavize.tran.http;

import java.nio.ByteBuffer;

import com.metavize.tran.token.Token;


/**
 * Holds a RFC 2616 status-line.
 *
 * @author <a href="mailto:amread@metavize.com">Aaron Read</a>
 * @version 1.0
 */
public class StatusLine implements Token
{
    private final String httpVersion;
    private final int statusCode;
    private final String reasonPhrase;

    public StatusLine(String httpVersion, int statusCode, String reasonPhrase)
    {
        this.httpVersion = httpVersion;
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }

    public String getHttpVersion()
    {
        return httpVersion;
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public String getReasonPhrase()
    {
        return reasonPhrase;
    }

    // Token methods ----------------------------------------------------------

    public ByteBuffer getBytes()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(httpVersion).append(" ").append(statusCode)
            .append(" ").append(reasonPhrase).append("\r\n");
        byte[] buf = sb.toString().getBytes();

        return ByteBuffer.wrap(buf);
    }

    public int getEstimatedSize()
    {
        return httpVersion.length() + 3 + reasonPhrase.length();
    }
}

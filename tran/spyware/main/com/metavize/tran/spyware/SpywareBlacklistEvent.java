/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.spyware;

import com.metavize.tran.http.HttpRequestEvent;
import com.metavize.tran.http.RequestLine;
import com.metavize.mvvm.tran.PipelineEndpoints;

/**
 * Log event for a spyware hit.
 *
 * @author
 * @version 1.0
 * @hibernate.class
 * table="TR_SPYWARE_EVT_BLACKLIST"
 * mutable="false"
 */
public class SpywareBlacklistEvent extends SpywareEvent
{
    private RequestLine requestLine; // pipeline endpoints & location

    // constructors -----------------------------------------------------------

    /**
     * Hibernate constructor.
     */
    public SpywareBlacklistEvent() { }

    public SpywareBlacklistEvent(RequestLine requestLine)
    {
        this.requestLine = requestLine;
    }

    // SpywareEvent methods ---------------------------------------------------

    public String getType()
    {
        return "Blacklist";
    }

    public String getReason()
    {
        return "in URL List";
    }

    public String getIdentification()
    {
        HttpRequestEvent hre = requestLine.getHttpRequestEvent();
        String host = null == hre
            ? getPipelineEndpoints().getSServerAddr().toString()
            : hre.getHost();
        return "http://" + host + requestLine.getRequestUri().toString();
    }

    public boolean isBlocked()
    {
        return true;
    }

    public String getLocation()
    {
        return requestLine.getUrl().toString();
    }

    public PipelineEndpoints getPipelineEndpoints()
    {
        return requestLine.getPipelineEndpoints();
    }

    // accessors --------------------------------------------------------------

    /**
     * Request line for this HTTP response pair.
     *
     * @return the request line.
     * @hibernate.many-to-one
     * column="REQUEST_ID"
     * cascade="all"
     */
    public RequestLine getRequestLine()
    {
        return requestLine;
    }

    public void setRequestLine(RequestLine requestLine)
    {
        this.requestLine = requestLine;
    }

    // Syslog methods ---------------------------------------------------------

    // use SpywareEvent appendSyslog, getSyslogId and getSyslogPriority
}

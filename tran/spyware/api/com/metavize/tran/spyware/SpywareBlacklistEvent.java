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

package com.metavize.tran.spyware;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.metavize.mvvm.tran.PipelineEndpoints;
import com.metavize.tran.http.HttpRequestEvent;
import com.metavize.tran.http.RequestLine;
import javax.persistence.Entity;

/**
 * Log event for a spyware hit.
 *
 * @author
 * @version 1.0
 */
@Entity
@org.hibernate.annotations.Entity(mutable=false)
@Table(name="tr_spyware_evt_blacklist", schema="events")
public class SpywareBlacklistEvent extends SpywareEvent
{
    private RequestLine requestLine; // pipeline endpoints & location

    // constructors -----------------------------------------------------------

    public SpywareBlacklistEvent() { }

    public SpywareBlacklistEvent(RequestLine requestLine)
    {
        this.requestLine = requestLine;
    }

    // SpywareEvent methods ---------------------------------------------------

    @Transient
    public String getType()
    {
        return "Blacklist";
    }

    @Transient
    public String getReason()
    {
        return "in URL List";
    }

    @Transient
    public String getIdentification()
    {
        HttpRequestEvent hre = requestLine.getHttpRequestEvent();
        String host = null == hre
            ? getPipelineEndpoints().getSServerAddr().toString()
            : hre.getHost();
        return "http://" + host + requestLine.getRequestUri().toString();
    }

    @Transient
    public boolean isBlocked()
    {
        return true;
    }

    @Transient
    public String getLocation()
    {
        return requestLine.getUrl().toString();
    }

    @Transient
    public PipelineEndpoints getPipelineEndpoints()
    {
        return requestLine.getPipelineEndpoints();
    }

    // accessors --------------------------------------------------------------

    /**
     * Request line for this HTTP response pair.
     *
     * @return the request line.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="request_id")
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

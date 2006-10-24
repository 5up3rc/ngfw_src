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

package com.metavize.tran.protofilter;

import javax.persistence.Column;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.metavize.mvvm.logging.PipelineEvent;
import com.metavize.mvvm.logging.SyslogBuilder;
import com.metavize.mvvm.logging.SyslogPriority;
import com.metavize.mvvm.tran.PipelineEndpoints;
import javax.persistence.Entity;

/**
 * Log event for a proto filter match.
 *
 * @author
 * @version 1.0
 */
@Entity
@org.hibernate.annotations.Entity(mutable=false)
@Table(name="tr_protofilter_evt", schema="events")
public class ProtoFilterLogEvent extends PipelineEvent
{
    private String protocol;
    private boolean blocked;

    // constructors -----------------------------------------------------------

    public ProtoFilterLogEvent() { }

    public ProtoFilterLogEvent(PipelineEndpoints pe, String protocol, boolean blocked)
    {
        super(pe);
        this.protocol = protocol;
        this.blocked = blocked;
    }

    // accessors --------------------------------------------------------------

    /**
     * The protocol, as determined by the protocol filter.
     *
     * @return the protocol name.
     */
    public String getProtocol()
    {
        return protocol;
    }

    public void setProtocol(String protocol)
    {
        this.protocol = protocol;
    }

    /**
     * Whether or not we blocked it.
     *
     * @return whether or not the session was blocked (closed)
     */
    @Column(nullable=false)
    public boolean isBlocked()
    {
        return blocked;
    }

    public void setBlocked(boolean blocked)
    {
        this.blocked = blocked;
    }

    // Syslog methods ---------------------------------------------------------

    public void appendSyslog(SyslogBuilder sb)
    {
        getPipelineEndpoints().appendSyslog(sb);

        sb.startSection("info");
        sb.addField("protocol", getProtocol());
        sb.addField("blocked", isBlocked());
    }

    @Transient
    public String getSyslogId()
    {
        return ""; // XXX
    }

    @Transient
    public SyslogPriority getSyslogPriority()
    {
        // WARNING = traffic altered
        // INFORMATIONAL = statistics or normal operation
        return true == isBlocked() ? SyslogPriority.WARNING : SyslogPriority.INFORMATIONAL;
    }
}

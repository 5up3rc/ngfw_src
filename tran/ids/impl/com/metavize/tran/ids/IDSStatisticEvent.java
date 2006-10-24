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

package com.metavize.tran.ids;

import javax.persistence.Column;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.metavize.mvvm.logging.StatisticEvent;
import com.metavize.mvvm.logging.SyslogBuilder;
import com.metavize.mvvm.logging.SyslogPriority;
import javax.persistence.Entity;

/**
 * Log event for a IDS statistics.
 *
 * @author <a href="mailto:nchilders@metavize.com">nchilders</a>
 * @stolen from rbscott yo
 * @version 1.0
 */
@Entity
@org.hibernate.annotations.Entity(mutable=false)
@Table(name="tr_ids_statistic_evt", schema="events")
public class IDSStatisticEvent extends StatisticEvent {
    private int dnc = 0; // did-not-care
    private int logged = 0; // logged or alerted
    private int blocked = 0;

    // Constructors
    public IDSStatisticEvent() { }

    public IDSStatisticEvent( int dnc, int logged, int blocked )
    {
        this.dnc = dnc;
        this.logged  = logged;
        this.blocked = blocked;
    }

    /**
     * Number of dnc chunks (did-not-cares are not logged or blocked)
     *
     * @return Number of dnc chunks
     */
    @Column(nullable=false)
    public int getDNC() { return dnc; }
    public void setDNC( int dnc ) { this.dnc = dnc; }
    public void incrDNC() { dnc++; }

    /**
     * Number of logged chunks
     *
     * @return Number of logged chunks
     */
    @Column(nullable=false)
    public int getLogged() { return logged; }
    public void setLogged(int logged) { this.logged = logged; }
    public void incrLogged() { logged++; }

    /**
     * Number of blocked chunks
     *
     * @return Number of blocked chunks
     */
    @Column(nullable=false)
    public int getBlocked() { return blocked; }
    public void setBlocked(int blocked) { this.blocked = blocked; }
    public void incrBlocked() { blocked++; }

    /**
     * Returns true if any of the stats are non-zero, whenever all the stats are zero,
     * a new log event is not created.
     */
    public boolean hasStatistics() { return ((dnc + logged + blocked) > 0 ); }

    // Syslog methods ---------------------------------------------------------

    public void appendSyslog(SyslogBuilder sb)
    {
        sb.startSection("info");
        sb.addField("did-not-care", dnc);
        sb.addField("logged", logged);
        sb.addField("blocked", blocked);
    }

    @Transient
    public String getSyslogId()
    {
        return "Statistic";
    }

    @Transient
    public SyslogPriority getSyslogPriority()
    {
        return SyslogPriority.INFORMATIONAL; // statistics or normal operation
    }
}

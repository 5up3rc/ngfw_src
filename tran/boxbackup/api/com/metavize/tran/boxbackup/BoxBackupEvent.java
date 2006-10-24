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

package com.metavize.tran.boxbackup;

import java.util.Date;
import java.util.Iterator;
import javax.persistence.Column;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.metavize.mvvm.logging.LogEvent;
import com.metavize.mvvm.logging.SyslogBuilder;
import com.metavize.mvvm.logging.SyslogPriority;
import javax.persistence.Entity;

/**
 * ...name says it all...
 */
@Entity
@org.hibernate.annotations.Entity(mutable=false)
@Table(name="tr_boxbackup_evt", schema="events")
public class BoxBackupEvent extends LogEvent {

    private static final long serialVersionUID = 5563835539346280962L;

    private boolean m_success;
    private String m_detail;

    public BoxBackupEvent() { }

    public BoxBackupEvent(boolean success,
                          String detail) {
        m_success = success;
        m_detail = detail;
    }

    /**
     * Was the backup a success
     */
    @Column(nullable=false)
    public boolean isSuccess() {
        return m_success;
    }

    public void setSuccess(boolean success) {
        m_success = success;
    }

    /**
     * Detail.  Only really interesting if
     * things fail.
     */
    public String getDetail() {
        return m_detail;
    }

    public void setDetail(String detail) {
        m_detail = detail;
    }

    // Syslog methods ---------------------------------------------------------
    public void appendSyslog(SyslogBuilder sb) {
        sb.startSection("info");
        sb.addField("success", isSuccess());
        sb.addField("detail", getDetail());
    }

    @Transient
    public String getSyslogId()
    {
        return ""; // XXX
    }

    @Transient
    public SyslogPriority getSyslogPriority()
    {
        // NOTICE = box backup failed
        // INFORMATIONAL = statistics or normal operation
        return false == isSuccess() ? SyslogPriority.WARNING : SyslogPriority.INFORMATIONAL;
    }
}

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

package com.untangle.tran.firewall;

import java.io.Serializable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.untangle.mvvm.logging.PipelineEvent;
import com.untangle.mvvm.logging.SyslogBuilder;
import com.untangle.mvvm.logging.SyslogPriority;
import com.untangle.mvvm.tran.PipelineEndpoints;
import javax.persistence.Entity;

/**
 * Log event for the firewall.
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 * @version 1.0
 */
@Entity
@org.hibernate.annotations.Entity(mutable=false)
@Table(name="tr_firewall_evt", schema="events")
public class FirewallEvent extends PipelineEvent implements Serializable
{
    private FirewallRule rule;
    private int     ruleIndex;
    private boolean wasBlocked;

    // Constructors
    public FirewallEvent() { }

    public FirewallEvent( PipelineEndpoints pe,  FirewallRule rule, boolean wasBlocked, int ruleIndex )
    {
        super(pe);

        this.wasBlocked = wasBlocked;
        this.ruleIndex  = ruleIndex;
        this.rule       = rule;
    }

    /**
     * Whether or not the session was blocked.
     *
     * @return If the session was passed or blocked.
     */
    @Column(name="was_blocked", nullable=false)
    public boolean getWasBlocked()
    {
        return wasBlocked;
    }

    public void setWasBlocked( boolean wasBlocked )
    {
        this.wasBlocked = wasBlocked;
    }

    /**
     * Rule index, when this event was triggered.
     *
     * @return current rule index for the rule that triggered this event.
     */
    @Column(name="rule_index", nullable=false)
    public int getRuleIndex()
    {
        return ruleIndex;
    }

    public void setRuleIndex( int ruleIndex )
    {
        this.ruleIndex = ruleIndex;
    }

    /**
     * Firewall rule that triggered this event
     *
     * @return firewall rule that triggered this event
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="rule_id")
    public FirewallRule getRule()
    {
        return rule;
    }

    public void setRule( FirewallRule rule )
    {
        this.rule = rule;
    }

    // Syslog methods ---------------------------------------------------------

    public void appendSyslog(SyslogBuilder sb)
    {
        getPipelineEndpoints().appendSyslog(sb);

        sb.startSection("info");
        sb.addField("reason-rule#", getRuleIndex());
        sb.addField("blocked", getWasBlocked());
    }

    @Transient
    public String getSyslogId()
    {
        return ""; // XXX
    }

    @Transient
    public SyslogPriority getSyslogPriority()
    {
        // INFORMATIONAL = statistics or normal operation
        // WARNING = traffic altered
        return false == getWasBlocked() ? SyslogPriority.INFORMATIONAL : SyslogPriority.WARNING;
    }
}

/*
 * Copyright (c) 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.spam;

import java.io.Serializable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.metavize.mvvm.security.Tid;

/**
 * Settings for the SpamTransform.
 *
 * @author <a href="mailto:amread@metavize.com">Aaron Read</a>
 * @version 1.0
 */
@Entity
@Table(name="tr_spam_settings", schema="settings")
public class SpamSettings implements Serializable
{
    private static final long serialVersionUID = -7246008133224040004L;

    private Long id;
    private Tid tid;

    private SpamSMTPConfig SMTPInbound;
    private SpamSMTPConfig SMTPOutbound;
    private SpamPOPConfig POPInbound;
    private SpamPOPConfig POPOutbound;
    private SpamIMAPConfig IMAPInbound;
    private SpamIMAPConfig IMAPOutbound;

    // constructors -----------------------------------------------------------

    public SpamSettings() { }

    public SpamSettings(Tid tid)
    {
        this.tid = tid;
    }

    // accessors --------------------------------------------------------------

    @Id
    @Column(name="settings_id")
    @GeneratedValue
    private Long getId()
    {
        return id;
    }

    private void setId(Long id)
    {
        this.id = id;
    }

    /**
     * Transform id for these settings.
     *
     * @return tid for these settings
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="tid", nullable=false)
    public Tid getTid()
    {
        return tid;
    }

    public void setTid(Tid tid)
    {
        this.tid = tid;
    }

    /**
     * Inbound SMTP spam settings.
     *
     * @return inbound SMTP settings.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="smtp_inbound", nullable=false)
    public SpamSMTPConfig getSMTPInbound()
    {
        return SMTPInbound;
    }

    public void setSMTPInbound(SpamSMTPConfig SMTPInbound)
    {
        this.SMTPInbound = SMTPInbound;
        return;
    }

    /**
     * Outbound SMTP spam settings.
     *
     * @return outbound SMTP settings.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="smtp_outbound", nullable=false)
    public SpamSMTPConfig getSMTPOutbound()
    {
        return SMTPOutbound;
    }

    public void setSMTPOutbound(SpamSMTPConfig SMTPOutbound)
    {
        this.SMTPOutbound = SMTPOutbound;
        return;
    }

    /**
     * Inbound POP spam settings.
     *
     * @return inbound POP settings.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="pop_inbound", nullable=false)
    public SpamPOPConfig getPOPInbound()
    {
        return POPInbound;
    }

    public void setPOPInbound(SpamPOPConfig POPInbound)
    {
        this.POPInbound = POPInbound;
        return;
    }

    /**
     * Outbound POP spam settings.
     *
     * @return outbound POP settings.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="pop_outbound", nullable=false)
    public SpamPOPConfig getPOPOutbound()
    {
        return POPOutbound;
    }

    public void setPOPOutbound(SpamPOPConfig POPOutbound)
    {
        this.POPOutbound = POPOutbound;
        return;
    }

    /**
     * Inbound IMAP spam settings.
     *
     * @return inbound IMAP settings.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="imap_inbound", nullable=false)
    public SpamIMAPConfig getIMAPInbound()
    {
        return IMAPInbound;
    }

    public void setIMAPInbound(SpamIMAPConfig IMAPInbound)
    {
        this.IMAPInbound = IMAPInbound;
        return;
    }

    /**
     * Outbound IMAP spam settings.
     *
     * @return outbound IMAP settings.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="imap_outbound", nullable=false)
    public SpamIMAPConfig getIMAPOutbound()
    {
        return IMAPOutbound;
    }

    public void setIMAPOutbound(SpamIMAPConfig IMAPOutbound)
    {
        this.IMAPOutbound = IMAPOutbound;
        return;
    }
}

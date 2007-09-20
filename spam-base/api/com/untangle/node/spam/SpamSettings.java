/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module.  An independent
 * module is a module which is not derived from or based on this library.
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

package com.untangle.node.spam;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.untangle.node.util.UvmUtil;
import com.untangle.uvm.security.Tid;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.IndexColumn;

/**
 * Settings for the SpamNode.
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
@Entity
@Table(name="n_spam_settings", schema="settings")
@Inheritance(strategy=InheritanceType.JOINED)
public class SpamSettings implements Serializable
{
    private static final long serialVersionUID = -7246008133224040004L;

    private Long id;
    private Tid tid;

    private SpamSMTPConfig smtpConfig;
    private SpamPOPConfig popConfig;
    private SpamIMAPConfig imapConfig;
    private List<SpamRBL> spamRBLList; // spam only
    private List<SpamAssassinDef> spamAssassinDefList; // spamassassin only
    private List<SpamAssassinLcl> spamAssassinLclList; // spamassassin only

    // constructors -----------------------------------------------------------

    public SpamSettings() {}

    public SpamSettings(Tid tid)
    {
        this.tid = tid;
        spamRBLList = new LinkedList<SpamRBL>();
        spamAssassinDefList = new LinkedList<SpamAssassinDef>();
        spamAssassinLclList = new LinkedList<SpamAssassinLcl>();
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
     * Node id for these settings.
     *
     * @return tid for these settings
     */
    @ManyToOne(fetch=FetchType.EAGER)
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
     * SMTP settings.
     *
     * @return SMTP settings.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="smtp_config", nullable=false)
    public SpamSMTPConfig getSmtpConfig()
    {
        return smtpConfig;
    }

    public void setSmtpConfig(SpamSMTPConfig smtpConfig)
    {
        this.smtpConfig = smtpConfig;
    }

    /**
     * POP spam settings.
     *
     * @return POP settings.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="pop_config", nullable=false)
    public SpamPOPConfig getPopConfig()
    {
        return popConfig;
    }

    public void setPopConfig(SpamPOPConfig popConfig)
    {
        this.popConfig = popConfig;
    }

    /**
     * IMAP spam settings.
     *
     * @return IMAP settings.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="imap_config", nullable=false)
    public SpamIMAPConfig getImapConfig()
    {
        return imapConfig;
    }

    public void setImapConfig(SpamIMAPConfig imapConfig)
    {
        this.imapConfig = imapConfig;
    }

    /**
     * Spam RBL list.
     *
     * @return Spam RBL list.
     */
    @OneToMany(fetch=FetchType.EAGER)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
                  org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JoinTable(name="n_spam_rbl_list",
               joinColumns=@JoinColumn(name="settings_id"),
               inverseJoinColumns=@JoinColumn(name="rule_id"))
    @IndexColumn(name="position")
    public List<SpamRBL> getSpamRBLList()
    {
        return UvmUtil.eliminateNulls(spamRBLList);
    }

    public void setSpamRBLList(List<SpamRBL> spamRBLList)
    {
        this.spamRBLList = spamRBLList;
    }

    /**
     * SpamAssassin System Default Conf list.
     *
     * @return SpamAssassin System Default Conf list.
     */
    @OneToMany(fetch=FetchType.EAGER)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
                  org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JoinTable(name="n_spamassassin_def_list",
               joinColumns=@JoinColumn(name="settings_id"),
               inverseJoinColumns=@JoinColumn(name="rule_id"))
    @IndexColumn(name="position")
    public List<SpamAssassinDef> getSpamAssassinDefList()
    {
        return UvmUtil.eliminateNulls(spamAssassinDefList);
    }

    public void setSpamAssassinDefList(List<SpamAssassinDef> spamAssassinDefList)
    {
        this.spamAssassinDefList = spamAssassinDefList;
    }

    /**
     * SpamAssassin Local Default Conf list.
     *
     * @return SpamAssassin Local Default Conf list.
     */
    @OneToMany(fetch=FetchType.EAGER)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
                  org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @JoinTable(name="n_spamassassin_lcl_list",
               joinColumns=@JoinColumn(name="settings_id"),
               inverseJoinColumns=@JoinColumn(name="rule_id"))
    @IndexColumn(name="position")
    public List<SpamAssassinLcl> getSpamAssassinLclList()
    {
        return UvmUtil.eliminateNulls(spamAssassinLclList);
    }

    public void setSpamAssassinLclList(List<SpamAssassinLcl> spamAssassinLclList)
    {
        this.spamAssassinLclList = spamAssassinLclList;
    }
}

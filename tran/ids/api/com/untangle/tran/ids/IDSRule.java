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

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.untangle.mvvm.tran.Rule;

/**
 * Hibernate object to store IDS rules.
 *
 * @author <a href="mailto:nchilders@untangle.com">Nick Childers</a>
 * @version 1.0
 */
@Entity
@Table(name="tr_ids_rule", schema="settings")
public class IDSRule extends Rule implements Serializable
{
    private static final long serialVersionUID = -7009708957041660234L;

    // Actions (indices to ACTIONS)
    public static final int ALERT = 0;
    public static final int LOG = 1;
    public static final int PASS = 2;
    public static final int BLOCK = 3;
    public static final String[] ACTIONS = { "alert","log","pass","block" };

    //Hibernate Variables
    private String rule;

    private int sid;

    // Used from UI to let us know it's changed.
    private boolean modified;

    private String classification;
    private String url;

    //Variables set at run time
    private transient IDSRuleHeader header;
    private transient IDSRuleSignature signature;
    private transient boolean remove; //Should no longer be in the list

    public IDSRule() {}

    public IDSRule(String rule, String  category, String description) {

        super("Name", category,description,false);

        this.rule = rule;
        this.modified = false;
        this.remove = false;
    }

    @Transient
    public long getKeyValue() { return super.getId(); }
    public void setKeyValue(Long val) { super.setId(val); }

    @Transient
    public boolean getModified() { return modified; }
    public void setModified(boolean val) { modified = val; }

    @Column(name="rule")
    public String getText() { return this.rule; }
    public void setText(String s) { this.rule = s; }

    @Column(nullable=false)
    public int getSid() { return this.sid; }
    public void setSid(int sid) { this.sid = sid; }

    //Non Hibernate functions
    @Transient
    public IDSRuleHeader getHeader() {
        return header;
    }

    public void setHeader(IDSRuleHeader header) {
        this.header = header;
    }

    @Transient
    public IDSRuleSignature getSignature() {
        return signature;
    }

    public void setSignature(IDSRuleSignature signature) {
        this.signature = signature;
        //super.setDescription(getMessage());
    }

    /* every rule signature has classification (so default text is replaced) */
    @Transient
    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
        return;
    }

    /* not all rule signatures have url (so default text may be returned) */
    @Transient
    public String getURL() {
        return url;
    }

    public void setURL(String url) {
        this.url = url;
        return;
    }

    public boolean remove() {
        return remove;
    }

    public void remove(boolean val) {
        remove = val;
    }

    public boolean disabled() {
        return !(isLive() || getLog());
    }

    @Transient
    public int getAction() {
        if (isLive())
            return BLOCK;
        else if (getLog())
            return LOG;
        else
            // XX
            return ALERT;
    }

    public boolean equals(Object o) {
        if (o instanceof IDSRule) {
            IDSRule other = (IDSRule) o;
            // Following isn't totally complete, but is good enough
            // for what we use from Rule. XX
            return (rule == null ? other.rule == null : rule.equals(other.rule)) &&
                isLive() == other.isLive() &&
                getLog() == other.getLog();
        }
        return false;
    }

    public int hashCode()
    {
        // Good enough. XX
        return (null == rule ? 0 : rule.hashCode());
    }
}

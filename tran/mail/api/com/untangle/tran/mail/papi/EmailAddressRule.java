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

package com.untangle.tran.mail.papi;

import java.io.Serializable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.untangle.mvvm.tran.Rule;

/**
 * Class used to hold an email address (needed for
 * hibernate stuff).
 */
@Entity
@Table(name="email_addr_rule", schema="settings")
public class EmailAddressRule extends Rule implements Serializable {

    private static final long serialVersionUID = 7226453350424547957L;

    private String m_addr;

    public EmailAddressRule() {
        this(null);
    }

    public EmailAddressRule(String addr) {
        m_addr = addr;
    }

    public String getAddress() {
        return m_addr;
    }

    public void setAddress(String addr) {
        m_addr = addr;
    }
}

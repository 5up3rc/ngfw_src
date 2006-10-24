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

package com.metavize.tran.httpblocker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Iterator;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.metavize.mvvm.security.Tid;

/**
 * Message to be displayed when a message is blocked.
 *
 * @author <a href="mailto:amread@metavize.com">Aaron Read</a>
 * @version 1.0
 */
@Entity
@Table(name="tr_httpblk_template", schema="settings")
public class BlockTemplate implements Serializable
{
    private static final long serialVersionUID = -2176543704833470091L;

    private static String BLOCK_TEMPLATE;

    private Long id;
    private String header = "Untangle Networks Content Filter";
    private String contact = "your network administrator";

    // constructor ------------------------------------------------------------

    public BlockTemplate() { }

    public BlockTemplate(String header, String contact)
    {
        this.header = header;
        this.contact = contact;
    }

    // accessors --------------------------------------------------------------

    @Id
    @Column(name="message_id")
    @GeneratedValue
    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    /**
     * Customizable banner on the block page.
     *
     * @return the header.
     */
    public String getHeader()
    {
        return header;
    }

    public void setHeader(String header)
    {
        this.header = header;
    }

    /**
     * Contact information.
     */
    public String getContact()
    {
        return contact;
    }

    public void setContact(String contact)
    {
        this.contact = contact;
    }
}

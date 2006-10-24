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

package com.metavize.mvvm.tran;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Index;

/**
 * Rule for matching based on IP addresses and subnets.
 *
 * @author <a href="mailto:amread@metavize.com">Aaron Read</a>
 * @version 1.0
 */
@Entity
@Table(name="string_rule", schema="settings")
public class StringRule extends Rule
{
    private String string;

    // constructors -----------------------------------------------------------

    public StringRule() { }

    // XXX inconstant constuctor
    public StringRule(String string)
    {
        this.string = string;
    }

    // XXX inconstant constuctor
    public StringRule(String string, String name, String category,
                      String description, boolean live)
    {
        super(name, category, description, live);
        this.string = string;
    }

    public StringRule(String string, String name, String category,
                      boolean live)
    {
        super(name, category, live);
        this.string = string;
    }

    // accessors --------------------------------------------------------------

    /**
     * The String.
     *
     * XXX the indexing does not seem to work.
     *
     * @return the string.
     */
    @Index(name="idx_string_rule", columnNames={ "string" })
    public String getString()
    {
        return string;
    }

    public void setString(String string)
    {
        this.string = string;
    }

    // Object methods ---------------------------------------------------------

    public boolean equals(Object o)
    {
        if (!(o instanceof StringRule)) {
            return false;
        }

        StringRule sr = (StringRule)o;
        return string.equals(sr.string);
    }

    public int hashCode()
    {
        return string.hashCode();
    }
}

/*
 * $HeadURL:$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.node.phish;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;

import com.untangle.uvm.security.Tid;
import com.untangle.node.spam.SpamSettings;

/**
 * Settings for the Phish node.
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
@Entity
@PrimaryKeyJoinColumn(name="spam_settings_id")
@Table(name="n_phish_settings", schema="settings")
public class PhishSettings extends SpamSettings implements Serializable
{
    // XXX
    //private static final long serialVersionUID = -7246008133224040004L;

    private boolean enableGooglePhishList = true;

    // constructors -----------------------------------------------------------

    public PhishSettings() {}

    public PhishSettings(Tid tid)
    {
        super(tid);
    }

    // accessors --------------------------------------------------------------

    @Column(name="enable_google_sb")
    public boolean getEnableGooglePhishList()
    {
        return enableGooglePhishList;
    }

    public void setEnableGooglePhishList(boolean enableGooglePhishList)
    {
        this.enableGooglePhishList = enableGooglePhishList;
    }
}

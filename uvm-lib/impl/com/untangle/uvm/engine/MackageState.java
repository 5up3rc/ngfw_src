/*
 * $HeadURL$
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

package com.untangle.uvm.engine;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * State of mackage.
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
@Entity
@Table(name="u_mackage_state", schema="settings")
class MackageState
{
    private Long id;
    private String mackageName;
    private String extraName;
    private boolean enabled;

    MackageState() { }

    MackageState(String mackageName, String extraName, boolean enabled)
    {
        this.mackageName = mackageName;
        this.extraName = extraName;
        this.enabled = enabled;
    }

    @Id
    @Column(name="id")
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
     * Name of mackage.
     *
     * @return name of mackage.
     */
    @Column(name="mackage_name", nullable=false)
    public String getMackageName()
    {
        return mackageName;
    }

    public void setMackageName(String mackageName)
    {
        this.mackageName = mackageName;
    }

    /**
     * Extra name of mackage.
     *
     * @return the mackage's extra name.
     */
    @Column(name="extra_name")
    public String getExtraName()
    {
        return extraName;
    }


    public void setExtraName(String extraName)
    {
        this.extraName = extraName;
    }

    /**
     * Status of node.
     *
     * @return true if and only if mackage is enabled.
     */
    @Column(nullable=false)
    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
}

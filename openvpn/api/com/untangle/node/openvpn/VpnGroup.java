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

package com.untangle.node.openvpn;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.untangle.uvm.node.IPaddr;
import com.untangle.uvm.node.Rule;
import com.untangle.uvm.node.Validatable;
import com.untangle.uvm.node.ValidateException;
import org.hibernate.annotations.Type;

/**
 * A VPN group of address and clients.
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 * @version 1.0
 */
@Entity
@Table(name="n_openvpn_group", schema="settings")
public class VpnGroup extends Rule implements Validatable
{
    private static final long serialVersionUID = 1979887183265796668L;

    /* The interface that clients from the client pool are associated with */
    private byte intf;

    private IPaddr address;
    private IPaddr netmask;
    private boolean useDNS = false;

    public VpnGroup() { }

    /**
     * Should clients use DNS from the server
     */
    @Column(name="use_dns", nullable=false)
    public boolean isUseDNS()
    {
        return useDNS;
    }

    public void setUseDNS(boolean useDNS)
    {
        this.useDNS = useDNS;
    }

    /**
     * Get the pool of addresses for the clients.
     *
     * @return the pool address to send to the client, don't use in
     * bridging mode.
     */
    @Type(type="com.untangle.uvm.type.IPaddrUserType")
    public IPaddr getAddress()
    {
        return this.address;
    }

    public void setAddress( IPaddr address )
    {
        this.address = address;
    }

    /**
     * Get the pool of netmaskes for the clients, in bridging mode
     * this must come from the pool that the interface is bridged
     * with.
     *
     * @return the pool netmask to send to the client
     */
    @Type(type="com.untangle.uvm.type.IPaddrUserType")
    public IPaddr getNetmask()
    {
        return this.netmask;
    }

    public void setNetmask( IPaddr netmask )
    {
        this.netmask = netmask;
    }

    /* XXX Use a string or byte */
    /**
     * @return Default interface to associate VPN traffic with.
     */
    @Transient
    public byte getIntf()
    {
        return this.intf;
    }

    public void setIntf( byte intf )
    {
        this.intf = intf;
    }

    /**
     * This is the name that is used as the common name in the
     * certificate
     */
    @Transient
    public String getInternalName()
    {
        return getName().trim().toLowerCase();
    }

    public void validate() throws ValidateException
    {
        /* XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX */
    }

    /**
     * GUI depends on get name for to string to show the list of
     * clients
     */
    public String toString()
    {
        return getName();
    }
}

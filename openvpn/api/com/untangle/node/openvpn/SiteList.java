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

import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.untangle.uvm.node.AddressRange;
import com.untangle.uvm.node.AddressValidator;
import com.untangle.uvm.node.Validatable;
import com.untangle.uvm.node.ValidateException;

public class SiteList implements Serializable, Validatable
{
    private static final long serialVersionUID = -4628267406258118779L;

    List<VpnSite> siteList;

    public SiteList()
    {
        this( new LinkedList<VpnSite>());
    }

    public SiteList( List<VpnSite> siteList )
    {
        this.siteList = siteList;
    }

    public List<VpnSite> getSiteList()
    {
        return this.siteList;
    }

    public void setSiteList( List<VpnSite> siteList )
    {
        this.siteList = siteList;
    }

    List<AddressRange> buildAddressRange()
    {
        List<AddressRange> checkList = new LinkedList<AddressRange>();

        for ( VpnSite site : this.siteList ) {
            for ( SiteNetwork siteNetwork : site.getExportedAddressList()) {
                checkList.add( AddressRange.makeNetwork( siteNetwork.getNetwork().getAddr(),
                                                         siteNetwork.getNetmask().getAddr()));
            }
        }

        return checkList;
    }

    /**
     * Validate the object, throw an exception if it is not valid */
    public void validate() throws ValidateException
    {
        validate( null );
    }

    void validate( ClientList clientList ) throws ValidateException
    {
        Set<String> nameSet = new HashSet<String>();

        for ( VpnSite site : getSiteList()) {
            site.validate();
            String name = site.getInternalName();
            if ( !nameSet.add( name )) {
                throw new ValidateException( "Client and site names must all be unique: '" + name + "'" );
            }
        }

        /* XXX This assumes that the client list is saved before the site list */
        if ( clientList != null ) {
            for ( VpnClient client : clientList.getClientList()) {
                String name = client.getInternalName();
                if ( !nameSet.add( name )) {
                    throw new ValidateException( "Client and site names must all be unique: '" + name + "'");
                }
            }
        }

        /* XXX Check for overlap, and check for conflicts with the network settings */
        AddressValidator.getInstance().validate( buildAddressRange());
    }
}

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
import java.util.List;
import java.util.LinkedList;

import com.untangle.uvm.node.AddressValidator;
import com.untangle.uvm.node.AddressRange;
import com.untangle.uvm.node.Validatable;
import com.untangle.uvm.node.ValidateException;

public class ExportList implements Serializable, Validatable
{ 
    private static final long serialVersionUID = -6370773131855832786L;
    
    List<ServerSiteNetwork> exportList;

    public ExportList()
    {
        this( new LinkedList<ServerSiteNetwork>());
    }

    public ExportList( List<ServerSiteNetwork> exportList )
    {
        this.exportList = exportList;
    }

    public List<ServerSiteNetwork> getExportList()
    {
        return this.exportList;
    }
    
    public void setExportList( List<ServerSiteNetwork> exportList )
    {
        this.exportList = exportList;
    }

    List<AddressRange> buildAddressRange()
    {
        List<AddressRange> checkList = new LinkedList<AddressRange>();

        for ( ServerSiteNetwork export : this.exportList ) {
            checkList.add( AddressRange.makeNetwork( export.getNetwork().getAddr(), 
                                                     export.getNetmask().getAddr()));
        }

        return checkList;
    }

   
    /** 
     * Validate the object, throw an exception if it is not valid */
    public void validate() throws ValidateException
    {
        for ( ServerSiteNetwork export : this.exportList ) export.validate();
        
        /* Determine if all of the addresses are unique */
        AddressValidator.getInstance().validate( buildAddressRange());
    }
}

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

package com.untangle.uvm.networking.internal;

import java.util.Date;

import com.untangle.uvm.node.Rule;
import com.untangle.uvm.node.IPaddr;
import com.untangle.uvm.node.HostNameList;

import com.untangle.uvm.node.firewall.MACAddress;

import com.untangle.uvm.networking.DnsStaticHostRule;


/**
 * Immutable representation of a static DHCP lease.
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 */
public class DnsStaticHostInternal
{
    private final boolean    isLive;
    private final String     name;
    private final String     category;
    private final String     description;

    private final HostNameList hostnameList;
    private final IPaddr       staticAddress;

    // Constructors 
    public DnsStaticHostInternal( DnsStaticHostRule rule )
    {
        this( rule.isLive(), rule.getName(), rule.getCategory(), rule.getDescription(),
              rule.getHostNameList(), rule.getStaticAddress());
    }

    /* This is only used internally for merging lists together */
    public DnsStaticHostInternal( HostNameList hostnameList, IPaddr staticAddress )
    {
        this( true, Rule.EMPTY_NAME, Rule.EMPTY_CATEGORY, Rule.EMPTY_DESCRIPTION,
              hostnameList, staticAddress );
    }

    public DnsStaticHostInternal( boolean isLive, String name, String category, String description,
                                  HostNameList hostnameList, IPaddr staticAddress )
    {
        this.isLive        = isLive;
        this.name          = name;
        this.category      = category;
        this.description   = description;
        this.hostnameList  = hostnameList;
        this.staticAddress = staticAddress;

    }
    
    public HostNameList getHostNameList()
    {
        return hostnameList;
    }

    /** Get static IP address for this host name list */
    public IPaddr getStaticAddress()
    {
        return this.staticAddress;
    }
    
    /** The following are just so this can be converted back to a rule */
    public boolean isLive()
    {
        return this.isLive;
    }

    public String getName()
    {
        return this.name;
    }
    
    public String getDescription()
    {
        return this.description;
    }

    public String getCategory()
    {
        return this.category;
    }

    public DnsStaticHostRule toRule()
    {
        DnsStaticHostRule rule = new DnsStaticHostRule( getHostNameList(), getStaticAddress());

        rule.setLive( isLive );
        rule.setName( getName());
        rule.setCategory( getCategory());
        rule.setDescription( getDescription());
        
        return rule;
    }
}

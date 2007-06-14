/*
 * $HeadURL:$
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

package com.untangle.uvm.networking;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.untangle.uvm.node.HostNameList;
import com.untangle.uvm.node.IPaddr;
import com.untangle.uvm.node.Rule;
import org.hibernate.annotations.Type;

/**
 * Rule for storing DNS static hosts.
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 * @version 1.0
 */
@Entity
@Table(name="u_dns_static_host_rule", schema="settings")
public class DnsStaticHostRule extends Rule
{
    private static final long serialVersionUID = -9166468521319948021L;

    /** The list of hostnames for this rule */
    private HostNameList hostNameList = HostNameList.getEmptyHostNameList();

    /** The IP address that all of these hostnames resolves to */
    private IPaddr staticAddress = null;

    // Constructors
    public DnsStaticHostRule() { }

    public DnsStaticHostRule(HostNameList hostNameList, IPaddr staticAddress)
    {
        this.hostNameList  = hostNameList;
        this.staticAddress = staticAddress;
    }

    /**
     * This is the list of hostnames that should resolve to
     * <code>staticAddress</code>.
     *
     * @return The list of hostnames that resolve to
     * <code>staticAddress</code>.
     */
    @Column(name="hostname_list")
    @Type(type="com.untangle.uvm.type.HostNameListUserType")
    public HostNameList getHostNameList()
    {
        if ( hostNameList == null )
            hostNameList = HostNameList.getEmptyHostNameList();

        return hostNameList;
    }

    /**
     * Set the list of hostnames that should resolve to
     * <code>staticAddress</code>.
     *
     * @param hostnameList The list of hostnames that resolve to
     * <code>staticAddress</code>.
     */
    public void setHostNameList( HostNameList hostNameList )
    {
        this.hostNameList = hostNameList;
    }

    /**
     * Get the IP address of this entry.
     *
     * @return The IP address for this entry.
     */
    @Column(name="static_address")
    @Type(type="com.untangle.uvm.type.IPaddrUserType")
    public IPaddr getStaticAddress()
    {
        return this.staticAddress;
    }

    /**
     * Set the IP address of this entry.
     *
     * @param staticAddress The IP address for this entry.
     */
    public void setStaticAddress( IPaddr staticAddress )
    {
        this.staticAddress = staticAddress;
    }
}

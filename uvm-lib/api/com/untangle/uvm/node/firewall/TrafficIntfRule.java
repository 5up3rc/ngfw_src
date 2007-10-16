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

package com.untangle.uvm.node.firewall;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

import com.untangle.uvm.node.firewall.intf.IntfDBMatcher;
import com.untangle.uvm.node.firewall.intf.IntfMatcherFactory;
import com.untangle.uvm.node.firewall.ip.IPDBMatcher;
import com.untangle.uvm.node.firewall.port.PortDBMatcher;
import com.untangle.uvm.node.firewall.protocol.ProtocolDBMatcher;
import org.hibernate.annotations.Type;

/**
 * Rule for matching sessions based on interfaces.
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 * @version 1.0
 */
@MappedSuperclass
public abstract class TrafficIntfRule extends TrafficRule
{
    private static final long serialVersionUID = 679729134715419983L;

    /* True if this matches source interface */
    private IntfDBMatcher srcIntf = IntfMatcherFactory.getInstance()
        .getAllMatcher();

    /* True if this matches the destination interface */
    private IntfDBMatcher dstIntf = IntfMatcherFactory.getInstance()
        .getAllMatcher();

    public TrafficIntfRule() { }

    public TrafficIntfRule(boolean isLive, ProtocolDBMatcher protocol,
                           IntfDBMatcher srcIntf, IntfDBMatcher dstIntf,
                           IPDBMatcher srcAddress, IPDBMatcher dstAddress,
                           PortDBMatcher srcPort, PortDBMatcher dstPort)
    {
        super(isLive, protocol, srcAddress, dstAddress, srcPort, dstPort);
        this.srcIntf = srcIntf;
        this.dstIntf = dstIntf;
    }

    /**
     * source IntfDBMatcher
     *
     * @return the source IP matcher.
     */
    @Column(name="src_intf_matcher")
    @Type(type="com.untangle.uvm.type.firewall.IntfMatcherUserType")
    public IntfDBMatcher getSrcIntf()
    {
        return srcIntf;
    }

    public void setSrcIntf( IntfDBMatcher srcIntf )
    {
        this.srcIntf = srcIntf;
    }

    /**
     * destination IntfDBMatcher
     *
     * @return the destination IP matcher.
     */
    @Column(name="dst_intf_matcher")
    @Type(type="com.untangle.uvm.type.firewall.IntfMatcherUserType")
    public IntfDBMatcher getDstIntf()
    {
        return dstIntf;
    }

    public void setDstIntf( IntfDBMatcher dstIntf )
    {
        this.dstIntf = dstIntf;
    }
}

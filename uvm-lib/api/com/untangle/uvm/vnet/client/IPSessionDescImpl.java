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

package com.untangle.uvm.vnet.client;

import java.net.InetAddress;

import com.untangle.uvm.vnet.IPSessionDesc;
import com.untangle.uvm.vnet.SessionStats;
import org.json.JSONBean;

/**
 * Client side IP Session Description.
 *
 * @author <a href="mailto:jdi@untangle.com">John Irwin</a>
 * @version 1.0
 */
@JSONBean.Marker
public class IPSessionDescImpl
    extends SessionDescImpl
    implements IPSessionDesc
{

    protected final byte clientState;
    protected final byte serverState;

    protected final short protocol;

    protected final byte clientIntf;
    protected final byte serverIntf;

    protected final InetAddress clientAddr;
    protected final InetAddress serverAddr;

    protected final int clientPort;
    protected final int serverPort;

    protected IPSessionDescImpl(int id, short protocol, SessionStats stats,
                                byte clientState, byte serverState,
                                byte clientIntf, byte serverIntf,
                                InetAddress clientAddr, InetAddress serverAddr,
                                int clientPort, int serverPort)
    {
        super(id, stats);
        this.protocol = protocol;
        this.clientState = clientState;
        this.serverState = serverState;
        this.clientIntf = clientIntf;
        this.serverIntf = serverIntf;
        this.clientAddr = clientAddr;
        this.serverAddr = serverAddr;
        this.clientPort = clientPort;
        this.serverPort = serverPort;
    }

    @JSONBean.Getter()
    public short protocol()
    {
        return protocol;
    }

    @JSONBean.Getter()
    public byte clientIntf()
    {
        return clientIntf;
    }

    @JSONBean.Getter()
    public byte serverIntf()
    {
        return serverIntf;
    }

    @JSONBean.Getter()
    public byte clientState()
    {
        return clientState;
    }

    @JSONBean.Getter()
    public byte serverState()
    {
        return serverState;
    }

    @JSONBean.Getter()
    public InetAddress clientAddr()
    {
        return clientAddr;
    }

    @JSONBean.Getter()
    public InetAddress serverAddr()
    {
        return serverAddr;
    }

    @JSONBean.Getter()
    public int clientPort()
    {
        return clientPort;
    }

    @JSONBean.Getter()
    public int serverPort()
    {
        return serverPort;
    }
}

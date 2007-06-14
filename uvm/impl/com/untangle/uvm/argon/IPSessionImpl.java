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

package com.untangle.uvm.argon;

import java.net.InetAddress;

public abstract class IPSessionImpl extends SessionImpl implements IPSession 
{
    protected final short protocol;
    protected final InetAddress clientAddr;
    protected final InetAddress serverAddr;
    protected final int clientPort;
    protected final int serverPort;
    protected final byte clientIntf;
    protected final byte serverIntf;

    public IPSessionImpl( IPNewSessionRequest request )
    {
        super( request, request.state() == IPNewSessionRequest.REQUESTED || request.state() == IPNewSessionRequest.ENDPOINTED );

        protocol      = request.protocol();
        clientAddr    = request.clientAddr();
        clientPort    = request.clientPort();
        clientIntf    = request.clientIntf();

        serverPort    = request.serverPort();
        serverAddr    = request.serverAddr();
        serverIntf    = request.serverIntf();
    }

    /* IPSessionDesc */
    /** This should be abstract and reference the sub functions. */
    public short protocol()
    {
        return protocol;
    }

    public InetAddress clientAddr() 
    {
        return clientAddr;
    }
    
    public InetAddress serverAddr()
    {
        return serverAddr;
    }

    public int clientPort()
    {
        return clientPort;
    }
    
    public int serverPort()
    {
        return serverPort;
    }

    public byte clientIntf()
    {     
        return clientIntf;
    }
    
    public byte serverIntf()
    {
        return serverIntf;
    }
    
    /* IPSession */
    public void release()
    {
        /* Maybe someday */
    }

    public void scheduleTimer( long delay ) throws IllegalArgumentException
    {
        /* XX need some implementation */
    }

    public void cancelTimer()
    {
        /* Possible, unless just using the vectoring */
    }
}

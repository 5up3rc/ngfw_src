/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.argon;

import java.net.InetAddress;

import org.apache.log4j.Logger;

import com.metavize.jnetcap.Endpoint;
import com.metavize.jnetcap.Endpoints;
import com.metavize.jnetcap.NetcapSession;

import com.metavize.mvvm.localapi.LocalIntfManager;

class NetcapIPSessionDescImpl implements IPSessionDesc
{
    protected final SessionGlobalState sessionGlobalState;
    protected final Logger logger =  Logger.getLogger(getClass());

    protected final InetAddress clientAddr;
    protected final InetAddress serverAddr;
    protected final int clientPort;
    protected final int serverPort;
    /* XXXXXXXX Get rid of the server and client interface,
       there should just be one interface for the side */
    protected final byte clientIntf;
    protected final byte serverIntf;

    NetcapIPSessionDescImpl( SessionGlobalState sessionGlobalState, boolean ifClientSide )
    {
        Endpoints side;

        NetcapSession session;

        this.sessionGlobalState = sessionGlobalState;

        session = this.sessionGlobalState.netcapSession();

        if ( ifClientSide ) {
            side = session.clientSide();
        } else {
            side = session.serverSide();
        }

        Endpoint client = side.client();
        Endpoint server = side.server();

        LocalIntfManager lim = Argon.getInstance().getIntfManager();

        this.clientAddr = client.host();
        this.clientPort = client.port();
        this.clientIntf = lim.toArgon( session.clientSide().interfaceId());

        this.serverAddr = server.host();
        this.serverPort = server.port();
        this.serverIntf = lim.toArgon( session.serverSide().interfaceId());
    }

    /**
     * Returns the ID of this session.</p>
     */
    public int id()
    {
        return sessionGlobalState.id();
    }

    public String user()
    {
        return sessionGlobalState.user();
    }

    /**
     * Returns the protocol for the session.</p>
     * @return a <code>byte</code> giving one of the protocols (see Netcap.IPPROTO_*)
     */
    public short protocol()
    {
        return sessionGlobalState.protocol();
    }

    /**
     * Returns an argon interface for the client.</p>
     *
     * @return a <code>byte</code> giving the client interface of the session.
     */
    public byte clientIntf()
    {
        return clientIntf;
    }

    /**
     * Returns an argon interface for the server.</p>
     *
     * @return a <code>byte</code> giving the server interface of the session.
     */
    public byte serverIntf()
    {
        return serverIntf;
    }

    /**
     * Gets the Client Address of this session. </p>
     *
     * @return  the client address
     */
    public InetAddress clientAddr()
    {
        return clientAddr;
    }

    /**
     * Gets the Server Address of this session. </p>
     *
     * @return  the server addr.
     */
    public InetAddress serverAddr()
    {
        return serverAddr;
    }

    // We provide these here, confident that nothing other than UDP or TCP
    // will be a session, others will be stateless.  (Or this will just be 0)

    /**
     * Gets the client port for this session.</p>
     * @return the client port.
     */
    public int clientPort()
    {
        return clientPort;
    }

    /**
     * Gets the server port for this session.</p>
     * @return the server port.
     */
    public int serverPort()
    {
        return serverPort;
    }

    public String toString()
    {
        return String.format( "netcap session: [%d]%s:%d -> [%d]%s:%d",
                              this.clientIntf, this.clientAddr.getHostAddress(), this.clientPort,
                              this.serverIntf, this.serverAddr.getHostAddress(), this.serverPort );
    }

    /**
     * Number of bytes received from the client.
     */
    public long c2tBytes()
    {
        return sessionGlobalState.clientSideListener().rxBytes;
    }

    /**
     * Number of bytes transmitted to the server.
     */
    public long t2sBytes()
    {
        return sessionGlobalState.serverSideListener().txBytes;
    }

    /**
     * Number of bytes received from the server.
     */
    public long s2tBytes()
    {
        return sessionGlobalState.serverSideListener().rxBytes;
    }

    /**
     * Number of bytes transmitted to the client.
     */
    public long t2cBytes()
    {
        return sessionGlobalState.clientSideListener().txBytes;
    }

    /**
     * Number of chunks received from the client.
     */
    public long c2tChunks()
    {
        return sessionGlobalState.clientSideListener().rxChunks;
    }

    /**
     * Number of chunks transmitted to the server.
     */
    public long t2sChunks()
    {
        return sessionGlobalState.serverSideListener().txChunks;
    }

    /**
     * Number of chunks received from the server.
     */
    public long s2tChunks()
    {
        return sessionGlobalState.serverSideListener().rxChunks;
    }

    /**
     * Number of chunks transmitted to the client.
     */
    public long t2cChunks()
    {
        return sessionGlobalState.clientSideListener().txChunks;
    }
}

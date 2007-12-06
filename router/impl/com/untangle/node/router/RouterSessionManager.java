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
package com.untangle.node.router;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.untangle.uvm.vnet.IPNewSessionRequest;
import com.untangle.uvm.vnet.IPSession;
import com.untangle.uvm.vnet.MPipeException;
import com.untangle.uvm.vnet.Protocol;
import com.untangle.uvm.vnet.TCPSession;
import org.apache.log4j.Logger;
import com.untangle.uvm.node.script.ScriptRunner;
import java.io.*;

class RouterSessionManager
{
    Map<Integer,RouterSessionData> map = new ConcurrentHashMap<Integer,RouterSessionData>();

    Map<SessionRedirectKey,SessionRedirect> redirectMap =
        new ConcurrentHashMap<SessionRedirectKey,SessionRedirect>();

    private final Logger logger = Logger.getLogger( this.getClass());
    RouterImpl node;

    RouterSessionManager( RouterImpl node )
    {
        this.node = node;
    }

    void registerSession( IPNewSessionRequest request, Protocol protocol,
                          InetAddress clientAddr, int clientPort,
                          InetAddress serverAddr, int serverPort )
    {
        RouterSessionData data =
            new RouterSessionData( clientAddr, clientPort,
                                request.clientAddr(), request.clientPort(),
                                serverAddr, serverPort,
                                request.serverAddr(), request.serverPort());

        if (logger.isDebugEnabled()) {
            logger.debug( "Registering session: " + request.id());
        }

        /* Insert the data into the map */
        RouterSessionData tmp;
        if (( tmp = map.put( request.id(), data )) != null ) {
            logger.error( "Duplicate session key: " + tmp );
        }
	
    }

    void releaseSession( IPSession session, Protocol protocol )
    {
        RouterSessionData sessionData;
        if (logger.isDebugEnabled()) {
            logger.debug( "Releasing session: " + session.id());
        }
        if (( sessionData = map.remove( session.id())) == null ) {
            logger.error( "Released an unmanaged session: " + session );
            return;
        }

        /* Have to release all of the SessionRedirect */
        for ( Iterator<SessionRedirect> iter = sessionData.redirectList().iterator() ; iter.hasNext() ; ) {
            SessionRedirect sessionRedirect = iter.next();
	    if (logger.isDebugEnabled()) {
		logger.debug( "Releasing sessionRedirect ");
	    }
            /* Remove the item from the iterating list */
            iter.remove();

            SessionRedirect currentRedirect = redirectMap.remove( sessionRedirect.key );

            /* Remove the key from the redirect hash map */
            if ( currentRedirect != null && currentRedirect != sessionRedirect  ) {
                logger.error( "Redirect map mismatch" );
            }

            /* Cleanup the redirect */
            sessionRedirect.cleanup( node );
        }
    }

    RouterSessionData getSessionData( TCPSession session )
    {
        return map.get( session.id());
    }

    /**
     * Request to redirect a session.
     */
    void registerSessionRedirect( RouterSessionData data, SessionRedirectKey key, SessionRedirect redirect )
    {
        /* Add the redirect to the list monitored by this session */
        data.addRedirect( redirect );

        if ( logger.isDebugEnabled()) {
            logger.debug( "Registering[" + key + "],[" + redirect + "]" );
        }

        /* Add the redict to the map of redirects */
        redirectMap.put( key, redirect );
    }

    /**
     * Check to see if this session should be redirected because of one of the
     * it is in the session redirect map
     */
    boolean isSessionRedirect( IPNewSessionRequest request, Protocol protocol, RouterImpl node ) 
    {
        SessionRedirectKey key = new SessionRedirectKey( request, protocol );
        SessionRedirect redirect;

	if ( logger.isDebugEnabled()) {
	    logger.debug( "Looking up session: " + key );
	}

        if (( redirect = redirectMap.remove( key )) == null ) {
            return false;
        }
	if ( logger.isDebugEnabled()) {
	    logger.debug( "Session redirect match: " + redirect );
	}

        /* Remove the redirect rule once it is matched */
	// free the reserved port!!!
	redirect.cleanup(node);

        return true;
    }
}

/* For a temporary session redirect you know everything about the session except for the
 * client port */
class SessionRedirectKey
{
    final Protocol    protocol;
    final InetAddress serverAddr;
    final int         serverPort;
    final int         hashCode;

    SessionRedirectKey( Protocol protocol, InetAddress serverAddr, int serverPort )
    {
        this.protocol   = protocol;
        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
        hashCode = calculateHashCode();
    }

    SessionRedirectKey( IPNewSessionRequest request, Protocol protocol )
    {
        this( protocol, request.serverAddr(), request.serverPort());
    }

    SessionRedirectKey( TCPSession session )
    {
        this( Protocol.TCP, session.serverAddr(), session.serverPort());
    }

    SessionRedirectKey( IPSession session, Protocol protocol )
    {
        this( protocol, session.serverAddr(), session.serverPort());
    }

    public int hashCode()
    {
        return hashCode;
    }

    public boolean equals( Object o )
    {
        if (!( o instanceof SessionRedirectKey )) return false;

        SessionRedirectKey key = (SessionRedirectKey)o;
        
        if ( this.protocol != key.protocol || 
             !this.serverAddr.equals( key.serverAddr ) || 
             this.serverPort != key.serverPort ) {
            return false;
        }

        return true;
    }

    public String toString()
    {
        return "SessionRedirectKey| [" + protocol +"] " + "/" + serverAddr + ":" + serverPort;
    }


    private int calculateHashCode()
    {
        int result = 17;
        result = ( 37 * result ) + protocol.hashCode();
        result = ( 37 * result ) + serverAddr.hashCode();
        result = ( 37 * result ) + serverPort;

        return result;
    }
}



class SessionRedirect
{
    /* For each item, the item is null or zero if it is unused */
    final InetAddress clientAddr;
    final int         clientPort;

    final InetAddress serverAddr;
    final int         serverPort;

    // True once this redirect has been used.
    boolean           isExpired = false;

    // Set to a non-zero value to reserve a port
    int               reservedPort;
    private final Logger logger = Logger.getLogger( this.getClass());
    final SessionRedirectKey key;

    private String CreateRedirectCommand;
    private String RemoveRedirectCommand;

    SessionRedirect( InetAddress clientAddr, int clientPort,
                     InetAddress serverAddr, int serverPort,
                     int reservedPort, InetAddress myAddr,
		     SessionRedirectKey key )
    {
	createRedirectRule(clientAddr, clientPort,
			   serverAddr, serverPort,
			   reservedPort, myAddr);

        this.clientAddr   = clientAddr;
        this.clientPort   = clientPort;

        this.serverAddr   = serverAddr;
        this.serverPort   = serverPort;

        this.reservedPort = reservedPort;

        this.key          = key;
    }

    /* XXX I think this function is no longer used */
    synchronized void redirect( IPNewSessionRequest request, RouterImpl node ) throws MPipeException
    {
        RouterAttachment attachment = (RouterAttachment)request.attachment();

        if ( isExpired ) throw new MPipeException( node.getRouterMPipe(), "Expired redirect" );

        /* Have to take this off the list, if it is reserved */
        if ( reservedPort > 0 ) {
            if ( attachment.releasePort() != 0 ) {
                /* This will be cleaned up when the session is cleaned up */
                throw new MPipeException( node.getRouterMPipe(), "Session is already using a NAT port" );
            }
        }

        /* Modify the client */
        if ( this.clientAddr != null ) {
            request.clientAddr( clientAddr );
        }

        if ( this.clientPort != 0 ) {
            request.clientPort( clientPort );
        }

        /* Modify the server */
        if ( this.serverAddr != null ) {
            request.serverAddr( serverAddr );
        }

        if ( this.serverPort != 0 ) {
            request.serverPort( serverPort );
        }

        if ( this.reservedPort > 0 ) {
            attachment.releasePort( reservedPort );
            this.reservedPort = 0;
        }

        /* Indicate to never use this redirect again */
        isExpired = true;
    }

    public String toString()
    {
        return "SessionRedirect| " + clientAddr + ":" + clientPort + "/" + serverAddr + ":" + serverPort;
    }

    synchronized void cleanup( RouterImpl node )
    {

        if ( reservedPort > 0 ) {
            node.getHandler().releasePort( key.protocol, reservedPort );
	    removeRedirectRule();
        }
	
        reservedPort = 0;
    }
    private synchronized void createRedirectRule( InetAddress clientAddr, int clientPort,
						  InetAddress serverAddr, int serverPort,
						  int reservedPort, InetAddress myAddr
						  )
    {
        if (logger.isDebugEnabled()) {
	    logger.debug("clientAddr:"+clientAddr);
	    logger.debug("clientPort:"+clientPort);
	    logger.debug("serverAddr:"+serverAddr);
	    logger.debug("serverPort:"+serverPort);
	    logger.debug("reservedPort:"+reservedPort);
	}
	CreateRedirectCommand =
	    "iptables -A PREROUTING -t nat -p tcp "
	    +" -s "+clientAddr.getHostAddress()
	    +" -d "+myAddr.getHostAddress()
	    +" --dport "+reservedPort 
	    +" -j DNAT --to-destination "+serverAddr.getHostAddress()
	    +":"+serverPort;
	RemoveRedirectCommand =
	    "iptables -D PREROUTING -t nat -p tcp "
	    +" -s "+clientAddr.getHostAddress()
	    +" -d "+myAddr.getHostAddress()
	    +" --dport "+reservedPort 
	    +" -j DNAT --to-destination "+serverAddr.getHostAddress()
	    +":"+serverPort;
	if (logger.isDebugEnabled()) {
	    logger.debug(CreateRedirectCommand);
	}
	try{
	    BufferedWriter writer = new BufferedWriter(new FileWriter(new File("/tmp/foo")));
	    writer.write("#!/bin/bash\n");
	    writer.write(CreateRedirectCommand);
	    writer.newLine();
	    writer.close();
	    ScriptRunner.getInstance().exec("/tmp/foo");
	}catch(java.io.IOException err){
	    logger.debug(err);
	}catch(com.untangle.uvm.node.NodeException err){
	    logger.debug(err);
	}
    }

    private synchronized void removeRedirectRule()
    {
	logger.debug(RemoveRedirectCommand);
	try{
	    BufferedWriter writer = new BufferedWriter(new FileWriter(new File("/tmp/foo")));
	    writer.write("#!/bin/bash\n");
	    writer.write(RemoveRedirectCommand);
	    writer.newLine();
	    writer.close();
	    ScriptRunner.getInstance().exec("/tmp/foo");
	}catch(java.io.IOException err){
	    logger.debug(err);
	}catch(com.untangle.uvm.node.NodeException err){
	    logger.debug(err);
	}
    }

}


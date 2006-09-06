/*
 * Copyright (c) 2003, 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.argon;

import java.util.Collections;
import java.util.List;
import java.util.LinkedList;

import java.net.InetAddress;

import org.apache.log4j.Logger;

import com.metavize.jnetcap.Endpoint;
import com.metavize.jnetcap.Endpoints;
import com.metavize.jnetcap.NetcapSession;

import com.metavize.mvvm.tapi.Protocol;

import com.metavize.mvvm.tran.firewall.InterfaceRedirect;

final class InterfaceOverride
{
    private static final List<InterfaceRedirect> EMPTY_OVERRIDE_LIST = Collections.emptyList();
    private static final InterfaceOverride INSTANCE = new InterfaceOverride();

    private List<InterfaceRedirect> overrideList = new LinkedList<InterfaceRedirect>();

    private final Logger logger = Logger.getLogger( this.getClass());
    
    private InterfaceOverride()
    {
    }

    void updateDestinationInterface( NetcapSession netcapSession )
    {
        byte currentInterface  = netcapSession.serverSide().interfaceId();

        byte overrideInterface = getDestinationInterface( netcapSession );
        if ( currentInterface != overrideInterface ) {
            logger.info( "Found redirect for session, setting to interface: " + overrideInterface );
            netcapSession.serverInterfaceId( overrideInterface );
        }
    }


    /**
     * Returns a netcap interface that the session should go out on
     */
    byte getDestinationInterface( NetcapSession netcapSession )
    {        
        Endpoints clientSide = netcapSession.clientSide();
        Endpoints serverSide = netcapSession.serverSide();
        
        Endpoint src = clientSide.client();
        Endpoint dst = clientSide.server();
                
        InetAddress srcAddress = src.host();
        int srcPort = src.port();

        byte srcIntf = IntfConverter.toArgon( clientSide.interfaceId());
        
        InetAddress dstAddress = dst.host();
        int dstPort = dst.port();
        
        /* This is the interface the routing table thinks it is should go out on. */
        byte dstIntf = IntfConverter.toArgon( serverSide.interfaceId());

        Protocol protocol = Protocol.getInstance((int)netcapSession.protocol());

        if ( protocol == null ) {
            logger.error( "Unknown protocol: " + netcapSession.protocol());
            logger.error( "unable to apply override rules" );
            return serverSide.interfaceId();
        }
        
        /* Retrieve the override list, use this value for the rest of way */
        List<InterfaceRedirect> currentOverrideList = this.overrideList;
        
        logger.debug( "Testing " + currentOverrideList.size() + " Redirects" );
        
        for ( InterfaceRedirect redirect : currentOverrideList ) {
            if ( redirect.isMatch( protocol, srcIntf, dstIntf, srcAddress, dstAddress, srcPort, dstPort )) {
                byte intf = IntfConverter.toNetcap( redirect.argonIntf( dstIntf ));
                logger.debug( "Found redirect for session, setting to interface: " + intf );
                return intf;
            }
        }
        
        return IntfConverter.toNetcap( dstIntf );
    }

    void setOverrideList( List<InterfaceRedirect> overrideList )
    {
        if ( overrideList == null ) {
            logger.warn( "null override list, using the empty list" );
            overrideList = EMPTY_OVERRIDE_LIST;
        }

        this.overrideList = overrideList;
    }

    void clearOverrideList()
    {
        this.overrideList = EMPTY_OVERRIDE_LIST;
    }
            
    static InterfaceOverride getInstance()
    {
        return INSTANCE;
    }
}

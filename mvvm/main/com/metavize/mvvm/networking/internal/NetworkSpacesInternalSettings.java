/*
 * Copyright (c) 2003, 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id$
 */

package com.metavize.mvvm.networking.internal;

import java.util.List;
import java.util.LinkedList;
import java.util.Collections;

import com.metavize.mvvm.networking.RedirectRule;
import com.metavize.mvvm.networking.SetupState;

import com.metavize.mvvm.tran.IPaddr;

/* This is an immutable settings object used locally.  This has
 * already been processed and cannot be modified.  It represents the
 * current configuration of the MVVM.  All of the lists inside of this
 * are immutable, and the objects they point to are immutable. */
public class NetworkSpacesInternalSettings
{
    private final SetupState setupState;

    /* Indicator for whether the network space settings are enabled */
    private final boolean isEnabled;

    private final List<InterfaceInternal> interfaceList;

    /* List of interfaces and their mappings to use if network spaces was enabled */
    private final List<InterfaceInternal> enabledList;

    private final List<NetworkSpaceInternal> networkSpaceList;
    private final List<RouteInternal> routingTable;
    private final List<RedirectInternal> redirectList;

    /* These may or may not come from DHCP, they are the actual values
     * that have been set. */
    private final IPaddr dns1;
    private final IPaddr dns2;
    private final IPaddr defaultRoute;
    
    /* This is the space where services (dhcp/dns) are bound to */
    private final NetworkSpaceInternal serviceSpace;

    private NetworkSpacesInternalSettings( SetupState setupState, boolean isEnabled,
                                           List<InterfaceInternal> interfaceList,
                                           List<InterfaceInternal> enabledList,
                                           List<NetworkSpaceInternal> networkSpaceList,
                                           List<RouteInternal> routingTable,
                                           List<RedirectInternal> redirectList,
                                           IPaddr dns1, IPaddr dns2, IPaddr defaultRoute,
                                           NetworkSpaceInternal serviceSpace )
    {
        this.setupState       = setupState;
        this.isEnabled        = isEnabled;
        this.interfaceList    = Collections.unmodifiableList( new LinkedList( interfaceList ));
        this.enabledList      = Collections.unmodifiableList( new LinkedList( enabledList ));
        this.networkSpaceList = Collections.unmodifiableList( new LinkedList( networkSpaceList ));
        this.routingTable     = Collections.unmodifiableList( new LinkedList( routingTable ));
        this.redirectList     = Collections.unmodifiableList( new LinkedList( redirectList ));

        this.dns1          = dns1;
        this.dns2          = dns2;
        this.defaultRoute  = defaultRoute;
        this.serviceSpace  = serviceSpace;
    }
    
    public SetupState getSetupState()
    {
        return this.setupState;
    }

    public boolean getIsEnabled()
    {
        return this.isEnabled;
    }
    
    public List<InterfaceInternal> getInterfaceList()
    {
        return this.interfaceList;
    }

    public List<InterfaceInternal> getEnabledList()
    {
        return this.enabledList;
    }

    public List<NetworkSpaceInternal> getNetworkSpaceList()
    {
        return this.networkSpaceList;
    }
    
    public List<RouteInternal> getRoutingTable()
    {
        return this.routingTable;
    }

    public IPaddr getDefaultRoute()
    {
        return this.defaultRoute;
    }
    
    /* Get an immutable copy of the redirects */
    public List<RedirectInternal> getRedirectList()
    {
        return this.redirectList;
    }

    /** A new list of the redirect rules. */
    public List<RedirectRule> getRedirectRuleList()
    {
        List<RedirectRule> list = new LinkedList<RedirectRule>();
        
        for ( RedirectInternal internal : getRedirectList()) list.add( internal.toRule());

        return list;
    }

    public IPaddr getDns1()
    {
        return this.dns1;
    }

    public IPaddr getDns2()
    {
        return this.dns2;
    }
    
    /* This is the space where all of the services (dhcp/dns) should be running on */
    public NetworkSpaceInternal getServiceSpace()
    {
        return this.serviceSpace;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append( "Network Settings\n" );
        sb.append( "setup-state: " + getSetupState() + " isEnabled: " + getIsEnabled());
        
        sb.append( "\nInterfaces:\n" );
        for ( InterfaceInternal intf : getInterfaceList()) sb.append( intf + "\n" );
        
        sb.append( "Network Spaces:\n" );
        for ( NetworkSpaceInternal space : getNetworkSpaceList()) sb.append( space + "\n" );
    
        sb.append( "Routing table:\n" );
        for ( RouteInternal route : getRoutingTable()) sb.append( route + "\n" );
        
        sb.append( "dns1:     " + getDns1());
        sb.append( "\ndns2:     " + getDns2());
        sb.append( "\ngateway:  " + getDefaultRoute());
        sb.append( "\nservices: " + getServiceSpace().getIndex());

        return sb.toString();
    }

    public static NetworkSpacesInternalSettings 
        makeInstance( SetupState setupState, boolean isEnabled, List<InterfaceInternal> interfaceList,
                      List<InterfaceInternal> enabledList,
                      List<NetworkSpaceInternal> networkSpaceList,
                      List<RouteInternal> routingTable,
                      List<RedirectInternal> redirectList,
                      IPaddr dns1, IPaddr dns2, IPaddr defaultRoute )
    {
        /* Set the service space to either the first network space, or the first network space
         * that is running nat */
        NetworkSpaceInternal serviceSpace = networkSpaceList.get( 0 );
        if ( isEnabled ) {
            for ( NetworkSpaceInternal space : networkSpaceList ) {
                if ( space.getIsNatEnabled()) {
                    serviceSpace = space;
                    break;
                }
            }
        }

        return new 
            NetworkSpacesInternalSettings( setupState, isEnabled, 
                                           interfaceList, enabledList, networkSpaceList, routingTable, 
                                           redirectList, dns1, dns2, defaultRoute, serviceSpace );
    }
}


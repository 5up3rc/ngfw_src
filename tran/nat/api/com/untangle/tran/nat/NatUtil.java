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
package com.untangle.tran.nat;

import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;

import org.apache.log4j.Logger;

import com.untangle.mvvm.tran.IPaddr;

import com.untangle.mvvm.networking.IPNetwork;
import com.untangle.mvvm.networking.NetworkUtil;
import com.untangle.mvvm.networking.RedirectRule;

import com.untangle.mvvm.tran.firewall.intf.IntfDBMatcher;
import com.untangle.mvvm.tran.firewall.intf.IntfMatcherFactory;
import com.untangle.mvvm.tran.firewall.ip.IPMatcherFactory;
import com.untangle.mvvm.tran.firewall.ip.IPDBMatcher;
import com.untangle.mvvm.tran.firewall.port.PortMatcherFactory;

class NatUtil
{
    static final NatUtil INSTANCE = new NatUtil();

    static final IPaddr DEFAULT_NAT_ADDRESS = NetworkUtil.DEFAULT_NAT_ADDRESS;
    static final IPaddr DEFAULT_NAT_NETMASK = NetworkUtil.DEFAULT_NAT_NETMASK;
    
    static final IPaddr DEFAULT_DMZ_ADDRESS;
    
    static final IPaddr DEFAULT_DHCP_START = NetworkUtil.DEFAULT_DHCP_START;
    static final IPaddr DEFAULT_DHCP_END   = NetworkUtil.DEFAULT_DHCP_END;

    /* These are values for setup */
    static final IPaddr SETUP_INTERNAL_ADDRESS;
    static final IPaddr SETUP_INTERNAL_SUBNET;
    static final IPaddr SETUP_DHCP_START;
    static final IPaddr SETUP_DHCP_END;
    
    static final List<IPDBMatcher> LOCAL_MATCHER_LIST;

    /* Four hours, this parameter is actually unused */
    static final int DEFAULT_LEASE_TIME_SEC = NetworkUtil.DEFAULT_LEASE_TIME_SEC;

    private final Logger logger = Logger.getLogger( this.getClass());

    private NatUtil()
    {
    }

    List<RedirectRule> getGlobalRedirectList( List<RedirectRule> fullList )
    {
        List<RedirectRule> list = new LinkedList<RedirectRule>();
        
        for ( RedirectRule item : fullList ) {
            if ( !item.isLocalRedirect()) list.add( item );
        }
        
        return list;
    }
    
    /* This removes all of the global items from full list and replaces them with the items
     * in global list */
    List<RedirectRule> setGlobalRedirectList( List<RedirectRule> fullList, List<RedirectRule> globalList )
    {
        List<RedirectRule> list = new LinkedList<RedirectRule>();

        /* Add all of new global redirects to the list */
        for ( RedirectRule item  : globalList ) {
            if ( item.isLocalRedirect()) {
                logger.info( "Local redirect in global list, ignoring" );
                continue;
            }

            list.add( item );
        }

        /* Add all of the local redirects to the new list */
        for ( RedirectRule item : fullList ) {
            if ( item.isLocalRedirect()) list.add( item );
        }

        return list;
    }

    List<RedirectRule> getLocalRedirectList( List<RedirectRule> fullList )
    {
        List<RedirectRule> list = new LinkedList<RedirectRule>();
        
        for ( RedirectRule item : fullList ) {
            if ( item.isLocalRedirect()) list.add( item );
        }
        
        return list;
    }

    List<RedirectRule> setLocalRedirectList( List<RedirectRule> fullList, List<RedirectRule> localList )
    {
        List<RedirectRule> list = new LinkedList<RedirectRule>();

        /* Add all of the global redirects to the new list */
        for ( RedirectRule item : fullList ) {
            if ( !item.isLocalRedirect()) list.add( item );
        }

        /* Add all of local redirects to the end of the list */
        for ( RedirectRule item  : localList ) {
            if ( !item.isLocalRedirect()) {
                logger.info( "Global redirect in local list, ignoring" );
                continue;
            }
            
            /* Set all of the unset properties */
            IntfDBMatcher intfAllMatcher = IntfMatcherFactory.getInstance().getAllMatcher();

            item.setSrcIntf( intfAllMatcher );
            item.setDstIntf( intfAllMatcher );
            
            item.setSrcAddress( IPMatcherFactory.getInstance().getAllMatcher());
            item.setSrcPort( PortMatcherFactory.getInstance().getAllMatcher());
            
            list.add( item );
        }

        return list;        
    }

    List<IPDBMatcher> getEmptyLocalMatcherList()
    {
        return LOCAL_MATCHER_LIST;
    }

    public static NatUtil getInstance()
    {
        return INSTANCE;
    }

    static
    {
        IPaddr dmz;
        IPaddr setupInternalAddress;
        IPaddr setupInternalSubnet;
        IPaddr setupDhcpStart;
        IPaddr setupDhcpEnd;

        try {
            dmz        = IPaddr.parse( "192.168.1.2" );
            
            setupInternalAddress = IPaddr.parse( "192.168.1.81" );
            setupInternalSubnet = IPaddr.parse( "255.255.255.240" );
            setupDhcpStart = IPaddr.parse( "192.168.1.82" );
            setupDhcpEnd = IPaddr.parse( "192.168.1.94" );
        } catch( Exception e ) {
            System.err.println( "Unable to initialize one of the ip addrs" );
            e.printStackTrace();
            dmz = null;
            setupInternalAddress = null;
            setupInternalSubnet = null;
            setupDhcpStart = null;
            setupDhcpEnd = null;
        }
        
        DEFAULT_DMZ_ADDRESS = dmz;

        SETUP_INTERNAL_ADDRESS = setupInternalAddress;
        SETUP_INTERNAL_SUBNET  = setupInternalSubnet;
        SETUP_DHCP_START       = setupDhcpStart;
        SETUP_DHCP_END         = setupDhcpEnd;

        /* Setup the default list of local matchers, perhaps this should be in the IPMatcherFactory? */
        List<IPDBMatcher> list = new LinkedList<IPDBMatcher>();
        
        IPMatcherFactory ipmf = IPMatcherFactory.getInstance();

        /* always add the local and the all public matcher. */
        list.add( ipmf.getLocalMatcher());
        list.add( ipmf.getAllPublicMatcher());
        
        LOCAL_MATCHER_LIST = Collections.unmodifiableList( list );
    }
}

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

import com.metavize.mvvm.tran.ValidateException;
import com.metavize.mvvm.tran.IPaddr;

import com.metavize.mvvm.networking.Interface;
import com.metavize.mvvm.networking.IPNetwork;
import com.metavize.mvvm.networking.IPNetworkRule;
import com.metavize.mvvm.networking.NetworkSpace;

public class NetworkSpaceInternal
{
    /** True if this network space is enabled */
    private final boolean isEnabled;

    /* The bunesiess papers associated with this network space, if you
     * don't know about papers (aka, your name is not robert or ian)
     * don't mess with this). */
    private final long businessPapers;

    /** List of networks on this network space */
    private final List<IPNetwork> networkList;
    
    /* This is the primary address for this network space, it may come
     * from DHCP or from the network list, if always is never null and
     * never the empty address. */
    private final IPNetwork primaryAddress;
    
    /** List of interfaces inside of this network space */
    private final List<InterfaceInternal> interfaceList;
    
    /* True if DHCP is enabled for this network space */
    private final boolean isDhcpEnabled;

    /* True if traffic can leave this network space */
    private final boolean isTrafficForwarded;
    
    /* MTU of the interfaces inside of the network space */
    private final int mtu;

    /* Index of this network space in the list of spaces */
    private final int index;
    
    /* nat settings */
    private final boolean isNatEnabled;
    
    /* The address to NAT to.  If DHCP modifies the address to nat to,
     * then a new network space internal object is created. */
    private final IPaddr natAddress;

    /* The index of the space the NAT address came from or -1, if it is static */
    private final int natSpaceIndex;

    /* The value of the nat address (this is the less interesting value, the value that is
     * stored inside of the database) */
    private final IPaddr natDBAddress;
    
    /* DMZ settings */
    /* True if DMZ host is enabled. */
    private final boolean isDmzHostEnabled;

    /* True if DMZ host events should be logged */
    private final boolean isDmzHostLoggingEnabled;

    /* IP address of the host to send dmz host requests to */
    private final IPaddr dmzHost;

    /* The name of the device for this network space, this could be a
     * bridge like br1, or a physicaly device like eth1 */
    private final String deviceName;

    /* True if this network space is a bridge */
    private final boolean isBridge;

    /* */
    private final String name;
    private final String category;
    private final String description;


    /* List of interfaces in this network space */
    private NetworkSpaceInternal( NetworkSpace networkSpace, List<Interface> intfList, 
                                  IPNetwork primaryAddress, String deviceName, int index,
                                  IPaddr natAddress, int natSpaceIndex )
        throws ValidateException
    {
        /* Set whether or not the space is enabled */
        this.isEnabled = networkSpace.isLive();

        /* Set the business papears */
        this.businessPapers = networkSpace.getBusinessPapers();

        /* Get the network list */
        /* Convert the list to ip network */
        /* This is going to lose the name/description/category info */
        List<IPNetwork> networkList = new LinkedList<IPNetwork>();
        for ( IPNetworkRule rule : (List<IPNetworkRule>)networkSpace.getNetworkList()) {
            networkList.add( rule.getIPNetwork());
        }
        this.networkList = Collections.unmodifiableList( networkList );

        /* Build the interface list */
        List<InterfaceInternal> interfaceList = new LinkedList<InterfaceInternal>();
        
        for ( Interface intf : intfList ) {
            interfaceList.add( InterfaceInternal.makeInterfaceInternal( intf, this ));
        }

        /* Convert the list to an immutable list */
        this.interfaceList = Collections.unmodifiableList( interfaceList );

        /* Set the primary address */
        this.primaryAddress = primaryAddress;

        /* set whether or not DHCP is enabled */
        this.isDhcpEnabled = networkSpace.getIsDhcpEnabled();
        
        /* Set whether or not traffic is forwarded */
        this.isTrafficForwarded = networkSpace.getIsTrafficForwarded();
        
        /* Set the MTU */
        this.mtu = networkSpace.getMtu();
        
        /* Set the index of the network space */
        this.index = index;

        /* indicate whether or not nat is enabled */
        this.isNatEnabled = networkSpace.getIsNatEnabled();

        /* Set the address to nat to. */
        this.natAddress = natAddress;

        /* Set the space index */
        this.natSpaceIndex = natSpaceIndex;
        
        /* Set the value for the database (may be null) */
        this.natDBAddress = networkSpace.getNatAddress();

        /* indicate whether or not dmz host is enabled */
        this.isDmzHostEnabled = networkSpace.getIsDmzHostEnabled();

        /* indicate whether or not dmz host is enabled */
        this.isDmzHostLoggingEnabled = networkSpace.getIsDmzHostLoggingEnabled();

        /* Set the dmz host */
        this.dmzHost = networkSpace.getDmzHost();

        /* Set the device name */
        this.deviceName = deviceName;

        /* Set whether or not this is a bridge */
        this.isBridge = ( this.interfaceList.size() > 1 );

        /** stuff from a rule */
        this.name = networkSpace.getName();
        this.category = networkSpace.getCategory();
        this.description = networkSpace.getDescription();
    }
    
    public boolean getIsEnabled()
    {
        return this.isEnabled;
    }

    public long getBusinessPapers()
    {
        return this.businessPapers;
    }
    
    public List<IPNetwork> getNetworkList()
    {
        return this.networkList;
    }

    public List<IPNetworkRule> getNetworkRuleList()
    {
        List<IPNetworkRule> networkList = new LinkedList<IPNetworkRule>();
        for ( IPNetwork network : getNetworkList()) networkList.add( new IPNetworkRule( network ));
        return networkList;
    }

    public IPNetwork getPrimaryAddress()
    {
        return this.primaryAddress;
    }

    public boolean getIsDhcpEnabled()
    {
        return this.isDhcpEnabled;
    }

    public boolean getIsTrafficForwarded()
    {
        return this.isTrafficForwarded;
    }

    public List<InterfaceInternal> getInterfaceList()
    {
        return this.interfaceList;
    }

    public int getMtu()
    {
        return this.mtu;
    }
    
    public int getIndex()
    {
        return this.index;
    }

    public boolean getIsNatEnabled()
    {
        return this.isNatEnabled;
    }

    public IPaddr getNatAddress()
    {
        return this.natAddress;
    }

    public int getNatSpaceIndex()
    {
        return this.natSpaceIndex;
    }
    
    public boolean getIsDmzHostEnabled()
    {
        return this.isDmzHostEnabled;
    }

        public boolean getIsDmzHostLoggingEnabled()
    {
        return this.isDmzHostLoggingEnabled;
    }
    
    public IPaddr getDmzHost()
    {
        return this.dmzHost;
    }
       
    public String getDeviceName()
    {
        return this.deviceName;
    }

    public boolean isBridge()
    {
        return this.isBridge;
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

    /* Create a new network space, use with caution, since the links between
     * interfaces and other network spaces cannot be maintained */
    public NetworkSpace toNetworkSpace()
    {
        /* Create a copy of the network list, since the current list is not modifiable */
        NetworkSpace s = new NetworkSpace( getIsEnabled(), getNetworkRuleList(), getIsDhcpEnabled(),
                                           getIsTrafficForwarded(), getMtu(),
                                           getIsNatEnabled(), this.natDBAddress,
                                           getIsDmzHostEnabled(), getIsDmzHostLoggingEnabled(), getDmzHost());

        s.setName( getName());
        s.setDescription( getDescription());
        s.setCategory( getCategory());
        s.setBusinessPapers( getBusinessPapers());
        
        return s;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        
        sb.append( "name:      "     + getName());
        sb.append( "\nisEnabled:   " + getIsEnabled());
        sb.append( "\nnetworks:    " + getNetworkList());
        sb.append( "\nprimary:     " + getPrimaryAddress());
        sb.append( "\ndhcp:        " + getIsDhcpEnabled());
        sb.append( "\nforwarded:   " + getIsTrafficForwarded());
        sb.append( "\ninterfaces:  [" );
        String intfList = "";
        for ( InterfaceInternal intf :  getInterfaceList()) intfList += intf.getArgonIntf() + " ";
        sb.append( intfList.trim());
        sb.append( "]" );
        sb.append( "\nmtu:         " + getMtu()); 
        sb.append( "\nindex:       " + getIndex());
        sb.append( "\nnat:         " + getIsNatEnabled());
        sb.append( "\nnat-address: " + getNatAddress());
        sb.append( "\nnat-index:   " + getNatSpaceIndex());
        sb.append( "\ndmz-host:    " + getIsDmzHostEnabled() + " logging " + getIsDmzHostLoggingEnabled() + 
                   "["  + getDmzHost() + "]" );
        sb.append( "\ndevice:      " + getDeviceName());

        return sb.toString();
    }

    public static NetworkSpaceInternal makeInstance( NetworkSpace networkSpace, 
                                                     List<Interface> intfList,
                                                     IPNetwork primaryAddress, String deviceName,
                                                     int index, IPaddr natAddress, int natSpaceIndex )
        throws ValidateException
    {
        return new NetworkSpaceInternal( networkSpace, intfList, primaryAddress, deviceName, index, 
                                         natAddress, natSpaceIndex );
    }
}

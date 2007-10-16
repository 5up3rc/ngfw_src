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

package com.untangle.uvm.networking;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.untangle.uvm.node.AddressRange;
import com.untangle.uvm.node.AddressValidator;
import com.untangle.uvm.node.HostName;
import com.untangle.uvm.node.IPaddr;
import com.untangle.uvm.node.ParseException;
import com.untangle.uvm.node.ValidateException;
import com.untangle.uvm.node.firewall.ip.IPMatcher;
import com.untangle.uvm.node.firewall.ip.IPMatcherFactory;

/**
 * A number of utilities for working with IP addresses and network
 * settings.
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 * @version 1.0
 */
public class NetworkUtil
{
    private static final NetworkUtil INSTANCE;

    /** An empty IP address, 0.0.0.0 */
    public static final IPaddr  EMPTY_IPADDR;

    /* Default address for the outside restricted outside network */
    public static final IPaddr  DEF_OUTSIDE_NETWORK;

    /* Default address for the outside restricted outside netmask */
    public static final IPaddr  DEF_OUTSIDE_NETMASK;

    /* Default local domain */
    public static final HostName LOCAL_DOMAIN_DEFAULT;

    /* The address to use when a DHCP request fails */
    public static final IPaddr  BOGUS_DHCP_ADDRESS;

    /* The netmask to use if a DHCP request fails */
    public static final IPaddr  BOGUS_DHCP_NETMASK;

    /* Default start of the DHCP range */
    public static final IPaddr DEFAULT_DHCP_START;

    /* Default end of the DHCP range. */
    public static final IPaddr DEFAULT_DHCP_END;

    /* Default NAT address */
    public static final IPaddr DEFAULT_NAT_ADDRESS;

    /* Default NAT netmask */
    public static final IPaddr DEFAULT_NAT_NETMASK;

    /* This is the address to use during setup */
    public static final IPaddr SETUP_ADDRESS;

    /* ??? which one */
    public static final HostName DEFAULT_HOSTNAME;

    /* Default lease time for the DHCP server */
    public static int DEFAULT_LEASE_TIME_SEC = 4 * 60 * 60;

    /* The default name for the primary space */
    public static final String DEFAULT_SPACE_NAME_PRIMARY = "public";

    /* The default name for the NAT space. */
    public static final String DEFAULT_SPACE_NAME_NAT     = "private";

    /* Default HTTPS port */
    public static final int    DEF_HTTPS_PORT = 443;

    /* Default setting for allowing internal access via http */
    public static final boolean DEF_IS_INSIDE_INSECURE_EN = true;

    /* Default setting for allowing external access via https */  
    public static final boolean DEF_IS_OUTSIDE_EN         = true;

    /* Default setting for whether or not external access is restricted */
    public static final boolean DEF_IS_OUTSIDE_RESTRICTED = false;

    /* Default setting for remote support */
    public static final boolean DEF_IS_SSH_EN             = false;

    /* Default setting for sending exception reports */
    public static final boolean DEF_IS_EXCEPTION_REPORTING_EN = false;

    /* Default setting for TCP Window scaling */
    public static final boolean DEF_IS_TCP_WIN_EN         = false;

    /* Default setting for whether public administration is allowed */
    public static final boolean DEF_OUTSIDE_ADMINISTRATION = false;

    /* Default setting for whether users can retrieve their quarantine
     * from the internet. */
    public static final boolean DEF_OUTSIDE_QUARANTINE = true;
    
    /* Default setting for whether administrators can access reports from the internet. */
    public static final boolean DEF_OUTSIDE_REPORTING = false;

    /* The list of private networks, this is defined by RFC 1918 */
    private static final String PRIVATE_NETWORK_STRINGS[] =
    {
        "192.168.0.0/16", "10.0.0.0/8", "172.16.0.0/12"
    };

    /* ports 9500 -> 9650 are redirect ports, 10000 -> 60000 are reserved for NAT */
    public static final int INTERNAL_OPEN_HTTPS_PORT = 64157;

    /* Exception message triggered when the user enters and invalid
     * public address string. */
    private static final String PUBLIC_ADDRESS_EXCEPTION =
        "A public address is an ip address, optionally followed by a port.  (e.g. 1.2.3.4:445 or 1.2.3.4)";

    /* XXX This should be final, but there is a bug in javac that won't let it be. */
    
    /* A list of matchers that can be uesd to determine if an address
     * is in the private network. */
    private List<IPMatcher> privateNetworkList;

    NetworkUtil()
    {
        List<IPMatcher> matchers = new LinkedList<IPMatcher>();
        IPMatcherFactory imf = IPMatcherFactory.getInstance();

        for ( String matcherString : PRIVATE_NETWORK_STRINGS ) {
            try {
                matchers.add( imf.parse( matcherString ));
            } catch ( Exception e ) {
                System.err.println( "Unable to parse: " + matcherString );
            }
        }

        this.privateNetworkList = Collections.unmodifiableList( matchers );
    }

    /**
     * Get a list of the interfaces that belong to a particular
     * network space.  This is useful when the
     * <code>interface.getNetworkSpace()</code> has been updated, but
     * the <code>networkSpace.getInterfaceList()</code> has not.
     *
     * @param settings The settings to get.
     * @param space The network space to get all of the interfaces
     * for.
     */
    public List<Interface> getInterfaceList( NetworkSpacesSettings settings, NetworkSpace space )
    {
        List<Interface> list = new LinkedList<Interface>();

        long papers = space.getBusinessPapers();
        for ( Interface intf : settings.getInterfaceList()) {
            NetworkSpace intfSpace = intf.getNetworkSpace();
            if ( intfSpace.equals( space ) || ( intfSpace.getBusinessPapers() == papers )) list.add( intf );
        }

        return list;
    }

    /**
     * Return true if <code>address/netmask</code> is in one of the
     * private ranges from RFC 1918.
     *
     * @param address The network to test.
     * @param netmaks The netmask of the network.
     * @return True iff <code>address/netmask</code> is considered a
     * private address.
     */
    public boolean isPrivateNetwork( IPaddr address, IPaddr netmask )
    {
        if (( address == null ) || ( netmask == null )) return false;

        IPaddr base = address.and( netmask );

        for ( IPMatcher matcher : this.privateNetworkList ) {
            if ( matcher.isMatch( base.getAddr())) return true;
        }

        return false;
    }

    /**
     * Validate that a network configuration doesn't have any errors.
     *
     * @param settings The settings to test.
     * @exception ValidationException Occurs if there is an error in
     * <code>settings</code>
     */
    public void validate( NetworkSpacesSettings settings ) throws ValidateException
    {
        int index = 0;
        Set<String> nameSet = new HashSet<String>();

        /* Calculate if a space is live, a space is not alive if it is not mapped to. */
        for ( NetworkSpace space : settings.getNetworkSpaceList()) {
            space.setLive( true );
            /* The external interface is always mapped to the primary space */
            if ( space.getIsPrimary()) continue;
            if ( getInterfaceList( settings, space ).size() == 0 ) space.setLive( false );
        }

        for ( NetworkSpace space : settings.getNetworkSpaceList()) {
            if ( index == 0 && !space.isLive()) {
                throw new ValidateException( "The primary network space must be active." );
            }

            String name = space.getName().trim();
            space.setName( name );

            if ( !nameSet.add( name )) {
                throw new ValidateException( "Two network spaces cannot have the same name[" + name + "]" );
            }

            /* If dhcp is not enabled, there must be at least one address */
            validate( space );

            index++;
        }

        if ( settings.getNetworkSpaceList().size() < 1 ) {
            throw new ValidateException( "There must be at least one network space" );
        }

        /* Have to validate that all of the next hops are reachable */
        for ( Route route : (List<Route>)settings.getRoutingTable()) validate( route );

        for ( Interface intf : settings.getInterfaceList()) {
            NetworkSpace space = intf.getNetworkSpace();
            if ( space == null ) {
                throw new ValidateException( "Interface " + intf.getName() + " has an empty network space" );
            }
        }

        /* XXX Check the reverse, make sure each interface is in one of the network spaces
         * in the list */
        // throw new ValidateException( "Implement me" );

        /* XXX !!!!!!!!!!! Check to see if the serviceSpace has a primary address */
    }

    /**
     * Validate that a network space doesn't have any errors.
     *
     * @param space The network space to test.
     * @exception ValidationException Occurs if there is an error in
     * <code>space</code>.
     */
    public void validate( NetworkSpace space ) throws ValidateException
    {
        boolean isDhcpEnabled = space.getIsDhcpEnabled();
        List<IPNetworkRule> networkList = (List<IPNetworkRule>)space.getNetworkList();

        if (( space.getName() == null ) || ( space.getName().trim().length() == 0 )) {
            throw new ValidateException( "A network space should have a non-empty an empty name" );
        }

        if ( space.isLive()) {
            if ( !isDhcpEnabled && (( networkList == null ) || ( networkList.size() < 1 ))) {
                throw new ValidateException( "A network space should either have at least one address,"+
                                             " or use DHCP." );
            }

            AddressValidator av = AddressValidator.getInstance();
            int index = 0;
            for ( IPNetworkRule network : networkList ) {
                index++;
                InetAddress address = network.getNetwork().getAddr();
                if ( av.isIllegalAddress( address )) {
                    throw new ValidateException( "The network address: " + address.getHostAddress() +
                                                 " in network space: " + space.getName() + " is not valid." );
                }
            }

            if ( space.getIsNatEnabled()) {
                if ( isDhcpEnabled ) {
                    throw new ValidateException( "A network space running NAT should not get its address" +
                                                 " from a DHCP server." );
                }

                NetworkSpace natSpace = space.getNatSpace();
                if ( natSpace == null ) {
                    throw new ValidateException( "The network space '" + space.getName() +
                                                 "' running NAT must have a NAT space" );
                }

                if ( natSpace.getBusinessPapers() == space.getBusinessPapers()) {
                    throw new ValidateException( "The network space '" + space.getName() +
                                                 "' running NAT must mapped to a different space" );
                }

                if ( !natSpace.isLive() ) {
                    throw new ValidateException( "The network space '" + space.getName() +
                                                 "' running NAT must mapped to an enabled NAT space" );
                }
            }
        }

        IPaddr dmzHost = space.getDmzHost();
        if ( space.getIsDmzHostEnabled() && (( dmzHost == null ) || dmzHost.isEmpty())) {
            throw new ValidateException( "If DMZ is enabled, the DMZ host should also be set" );
        }

    }

    /**
     * Validate that a route doesn't have any errors.
     *
     * @param route The route to validate.
     * @exception ValidationException Occurs if there is an error in
     * <code>route</code>.
     */
    public void validate( Route route ) throws ValidateException
    {
        IPNetwork network = route.getDestination();
        /* Try to convert the netmask to CIDR notation, if it fails this isn't a valid route */
        network.getNetmask().toCidr();

        if ( route.getNextHop().isEmpty()) throw new ValidateException( "The next hop is empty" );
    }

    /**
     * Validate that a network doesn't have any errors.
     *
     * @param network The network to validate.
     * @exception ValidationException Occurs if there is an error in
     * <code>route</code>.
     */
    public void validate( IPNetwork network ) throws ValidateException
    {
        /* implement me, test if the netmask is okay, etc. */
    }

    /**
     * Test if the IPNetwork has a unicast address.
     * this is a bogus function that should be removed.
     *
     * @param network The network to check.
     * @return True iff <code>network</code> is a unicast address.
     */
    public boolean isUnicast( IPNetwork network )
    {
        byte[] address = network.getNetwork().getAddr().getAddress();

        /* Magic numbers, -127, because of unsigned bytes */
        return (( address[3] != 0 ) && ( address[3] != -127 ));
    }

    /**
     * Convert network to a string that is parseable by the 'ip' command.
     * 
     * @param network The network to convert.
     * @return The string representation of <code>network</code>.
     */
    public String toRouteString( IPNetwork network ) throws NetworkException
    {
        /* XXX This is kind of hokey and should be precalculated at creation time */
        IPaddr netmask = network.getNetmask();

        try {
            int cidr = netmask.toCidr();

            IPaddr networkAddress = network.getNetwork().and( netmask );
            /* Very important, the ip command barfs on spaces. */
            return networkAddress.toString() + "/" + cidr;
        } catch ( ParseException e ) {
            throw new NetworkException( "Unable to convert the netmask " + netmask + " into a cidr suffix" );
        }
    }

    /**
     * Create a string for <code>publicAddress:publicPort</code>.
     * This will automatically remove port for 443, and stuff like that.
     * 
     * @param publicAddress The address part of the public address.
     * @param publicPort The port part of the public address.
     * @return The public address string.
     */
    public String generatePublicAddress( IPaddr publicAddress, int publicPort )
    {
        if ( publicAddress == null || publicAddress.isEmpty()) return "";

        if ( publicPort == DEF_HTTPS_PORT ) return publicAddress.toString();

        return publicAddress.toString() + ":" + publicPort;
    }

    /**
     * Convert an IP Network to an AddressRange, this is used for
     * validation.
     *
     * @param network The network to convert.
     * @return An <code>AddressRange</code> that contains all of the
     * IP addresses in <code>network</code>.
     */
    public AddressRange makeAddressRange( IPNetworkRule network )
    {
        return AddressRange.makeNetwork( network.getNetwork().getAddr(), network.getNetmask().getAddr());
    }

    /**
     * Determine if a hostname is most likely resolvable by an
     * external DNS server.
     *
     * @param hostName The hostname to test.
     * @return True if <code>hostName</code> is most likely a
     * publically resolvable address.
     */
    public static boolean isHostnameLikelyPublic(String hostName)
    {
        /* This is a pretty weak test, and should be updated to test
         * for .com, etc */
        if (!hostName.contains("."))
            return false;
        if (hostName.endsWith(".domain"))
            return false;
        return true;
    }

    public static NetworkUtil getInstance()
    {
        return INSTANCE;
    }

    static
    {
        IPaddr emptyAddr      = null;
        IPaddr outsideNetwork = null;
        IPaddr outsideNetmask = null;
        IPaddr bogusAddress   = null;
        IPaddr bogusNetmask   = null;
        IPaddr dhcpStart      = null;
        IPaddr dhcpEnd        = null;

        IPaddr natAddress     = null;
        IPaddr natNetmask     = null;

        IPaddr setupAddress = null;

        HostName h, l;

        try {
            emptyAddr      = IPaddr.parse( "0.0.0.0" );
            outsideNetwork = IPaddr.parse( "1.2.3.4" );
            outsideNetmask = IPaddr.parse( "255.255.255.0" );
            bogusAddress   = IPaddr.parse( "169.254.210.50" );
            bogusNetmask   = IPaddr.parse( "255.255.255.0" );

            dhcpStart      = IPaddr.parse( "192.168.1.100" );
            dhcpEnd        = IPaddr.parse( "192.168.1.200" );

            natAddress = IPaddr.parse( "192.168.1.1" );
            natNetmask = IPaddr.parse( "255.255.255.0" );

            setupAddress = IPaddr.parse( "192.168.1.1" );
        } catch( Exception e ) {
            System.err.println( "this should never happen: " + e );
            emptyAddr = null;
            dhcpStart = dhcpEnd = null;
            bogusAddress = bogusNetmask = null;
            natAddress = natNetmask = null;
            /* THIS SHOULD NEVER HAPPEN */
        }

        try {
            h = HostName.parse( "local.domain" );
            l = HostName.parse( "untangle.local.domain" );
        } catch ( ParseException e ) {
            /* This should never happen */
            System.err.println( "Unable to initialize LOCAL_DOMAIN_DEFAULT: " + e );
            h = null;
            l = null;
        }
        EMPTY_IPADDR        = emptyAddr;
        DEF_OUTSIDE_NETWORK = outsideNetwork;
        DEF_OUTSIDE_NETMASK = outsideNetmask;

        DEFAULT_DHCP_START  = dhcpStart;
        DEFAULT_DHCP_END    = dhcpEnd;

        BOGUS_DHCP_ADDRESS  = bogusAddress;
        BOGUS_DHCP_NETMASK  = bogusNetmask;

        DEFAULT_NAT_ADDRESS = natAddress;
        DEFAULT_NAT_NETMASK = natNetmask;

        SETUP_ADDRESS = setupAddress;

        LOCAL_DOMAIN_DEFAULT = h;
        DEFAULT_HOSTNAME = l;

        INSTANCE = new NetworkUtil();
    }
}

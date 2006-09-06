/*
 * Copyright (c) 2003, 2004, 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.networking;

import java.util.List;

import com.metavize.mvvm.ArgonException;
import com.metavize.mvvm.networking.internal.InterfaceInternal;
import com.metavize.mvvm.networking.internal.NetworkSpaceInternal;
import com.metavize.mvvm.networking.internal.NetworkSpacesInternalSettings;
import com.metavize.mvvm.networking.internal.RemoteInternalSettings;
import com.metavize.mvvm.networking.internal.RouteInternal;
import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.tran.script.ScriptWriter;
import org.apache.log4j.Logger;

class InterfacesScriptWriter extends ScriptWriter
{
    private final Logger logger = Logger.getLogger(getClass());

    private final NetworkSpacesInternalSettings settings;
    private final RemoteInternalSettings remote;

    private boolean isDhcpEnabled = false;

    private static final String INTERFACE_HEADER =
        COMMENT + METAVIZE_HEADER +
        COMMENT + " network interfaces\n\n" +
        COMMENT + " Loopback network interface\n" +
        "auto lo\n" +
        "iface lo inet loopback\n\n";

    private static final String BUNNICULA_BASE  = System.getProperty( "bunnicula.home" );
    private static final String BUNNICULA_CONF  = System.getProperty( "bunnicula.conf" );
    private static final String FLUSH_CONFIG    = BUNNICULA_BASE + "/networking/flush-interfaces";
    private static final String POST_SCRIPT     = BUNNICULA_BASE + "/networking/post-script";

    private static final String DHCP_BOGUS_ADDRESS = "169.254.210.5";

    /* String used to indicate that pump should only update the address of the interface */
    /* XXX this isn't presently supported, checking the options for dhclient. */
    // static final String DHCP_FLAG_ADDRESS_ONLY = " --no-gateway --no-resolvconf ";
    /* XXX */

    InterfacesScriptWriter( NetworkSpacesInternalSettings settings, RemoteInternalSettings remote )
    {
        super();
        this.settings = settings;
        this.remote   = remote;
    }

    /* This function should only be called once */
    void addNetworkSettings() throws NetworkException, ArgonException
    {
        boolean isFirst = true;
        for ( NetworkSpaceInternal space : this.settings.getNetworkSpaceList()) {
            /* If the space is not enabled, keep on trucking */
            if ( !space.getIsEnabled()) continue;

            /* Add a seperator betwixt each active space */
            if ( isFirst ) {
                if ( space.getIsDhcpEnabled()) isDhcpEnabled = true;
            } else {
                appendLine( "" );
            }

            addNetworkSpace( space, isFirst );
            isFirst = false;

            if ( !this.settings.getIsEnabled()) break;
        }

        /* Add this after the last interface, done this way so there isn't a space
         * in between the interface and the final commands */
        addRoutingTable();

        /* Add the post configuration script */
        addPostConfigurationScript();
    }

    private void addNetworkSpace( NetworkSpaceInternal space, boolean isFirst )
        throws NetworkException, ArgonException
    {
        NetworkUtilPriv nu = NetworkUtilPriv.getPrivInstance();
        List<InterfaceInternal> interfaceList = (List<InterfaceInternal>)space.getInterfaceList();
        String name = space.getDeviceName();
        boolean isBridge = space.isBridge();
        int mtu = space.getMtu();

        appendLine( "auto " + name );

        List<IPNetwork> networkList = (List<IPNetwork>)space.getNetworkList();
        IPNetwork primaryAddress = null;

        appendLine( "iface " + name + " inet manual" );

        /* Insert the flush command for the first network space */
        if ( isFirst ) {
            appendCommands( "pre-up if [ -r " + FLUSH_CONFIG + " ]; then sh " + FLUSH_CONFIG + "; fi" );
        }

        /* If this is a bridge, then add the necessary settings for the bridge */
        if ( isBridge ) {
            appendCommands( "up brctl addbr " + name );

            /* Build a list of all of the ports inside of the bridge */
            for ( InterfaceInternal intf : interfaceList ) {
                String dev = intf.getIntfName();
                appendCommands( "up brctl addif " + name + " " + dev,
                                "up ifconfig " + dev + " 0.0.0.0 mtu " + mtu + " up" );

                EthernetMedia media = intf.getEthernetMedia();

                String check = "ethtool " + dev + " | ";

                if ( media.isAuto()) {
                    check += " grep -i -e 'auto-negotiation: on' | wc -l | grep -q 1 || ";
                    appendCommands( "up " + check + " ethtool -s " + dev + " autoneg on" );
                } else {
                    /* The m is after speed so there isn't a false positive of 10 finding 100 */
                    check += " grep -i -e 'auto-negotiation: off\\|speed: " + media.getSpeed() +
                        "m\\|duplex: " + media.getDuplex() + "' | wc -l | grep -q 3 || ";
                    appendCommands( "up " + check + " ethtool -s " + dev +
                                    " autoneg off" + " speed " + media.getSpeed() +
                                    " duplex " + media.getDuplex());
                }
            }
        } else {
            if ( interfaceList.size() > 0 ) {
                InterfaceInternal intf = interfaceList.get( 0 );
                EthernetMedia media = intf.getEthernetMedia();
                if ( media.isAuto()) {
                    appendCommands( "up ethtool -s " + name + " autoneg on" );
                } else {
                    appendCommands( "up ethtool -s " + name + " autoneg off" + " speed " + media.getSpeed() +
                                    " duplex " + media.getDuplex());
                }
            } else {
                logger.warn( "Interface list for space" + space.getIndex() + " has no interfaces" );
            }
        }

        /* Add the primary address */
        if ( space.getIsDhcpEnabled()) {
            /* This jibberish guarantees that if the device doesn't come up, it still
             * gets an address */
            String flags = "";

            /* If this is not the primary space, then the gateway and
             * /etc/resolv.conf should not be updated. */
            /* XXXX Presently not supported, I believe dhclient only works
             * with one call for all of them */
            // if ( !isFirst ) flags = DHCP_FLAG_ADDRESS_ONLY;

            String command = "up ";

            if ( isBridge ) {
                /* Give some time for the bridge to come alive */
                command += "sleep 5;  ";
            }

            command += " dhclient " + flags + " " + name;

            appendCommands( command );
            appendCommands( "up ifconfig " + name + " mtu " + mtu );
        } else {
            primaryAddress = space.getPrimaryAddress();
            appendCommands( "up ifconfig " + name + " " + primaryAddress.getNetwork() +
                            " netmask " + primaryAddress.getNetmask() + " mtu " + mtu );
        }

        /* Add all of the aliases */
        int aliasIndex = 0;
        for ( IPNetwork network : networkList ) {
            /* Only add the primary address is DHCP is not enabled */
            if ( !space.getIsDhcpEnabled() && network.equals( primaryAddress )) continue;

            if ( nu.isUnicast( network )) {
                String aliasName = name + ":" + aliasIndex++;
                appendCommands( "up ifconfig " + aliasName + " " + network.getNetwork() +
                                " netmask " + network.getNetmask() + " mtu " + mtu );
            } else {
                appendCommands( "up ip route add to " + nu.toRouteString( network ) + " dev " + name );
            }
        }
    }

    private void addRoutingTable() throws NetworkException
    {
        NetworkUtilPriv nup = NetworkUtilPriv.getPrivInstance();

        /* Add the default route and all of the other routes after configuring the
         * last network space */
        IPaddr defaultRoute = this.settings.getDefaultRoute();

        /* Add the routing table */
        for ( RouteInternal route : (List<RouteInternal>)this.settings.getRoutingTable()) {
            /* Ignore disabled routes */
            if ( !route.getIsEnabled()) continue;

            IPNetwork destination = route.getDestination();
            IPaddr nextHop = route.getNextHop();

            /* Ignore empty routing entries */
            if ( destination.equals( IPNetwork.getEmptyNetwork()) ||
                 ( destination.getNetwork() == null || destination.getNetwork().isEmpty()) ||
                 ( destination.getNetmask() == null || destination.getNetmask().isEmpty()) ||
                 ( nextHop == null || nextHop.isEmpty())) {
                logger.warn( "Ignoring empty routing entry: " + route );
            }

            // !!! This is currently not supported.
            // if ( route.getNetworkSpace() != null ) {
            // logger.warn( "Custom routing rules with per network space are presently not supported" );
            //}

            appendCommands( "up ip route add to " + nup.toRouteString( destination ) + " via " + nextHop );
        }

        /* Add the default route last */
        if ( !isDhcpEnabled && ( defaultRoute != null ) && ( !defaultRoute.isEmpty())) {
            appendCommands( "up ip route add to default via " + defaultRoute );
        }

        appendCommands( "up ip route flush table cache" );
    }

    private void addPostConfigurationScript()
    {
        if ( this.remote == null ) {
            logger.warn( "null remote settings, ignoring post configuration script" );
            return;
        }

        String script = this.remote.getPostConfigurationScript();

        if (( script != null ) && ( script.trim().length() > 0 )) {
            appendCommands( "up if [ -r " + POST_SCRIPT + " ]; then sh " + POST_SCRIPT + "; fi" );
        }
    }

    /* A helper to append commands that can't fail */
    private void appendCommands( String ... commandArray )
    {
        for ( String command : commandArray ) appendLine( "\t" + command + " || true" );
    }

    @Override
    protected String header()
    {
        return INTERFACE_HEADER;
    }
}

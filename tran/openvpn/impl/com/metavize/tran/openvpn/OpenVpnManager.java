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
package com.metavize.tran.openvpn;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import com.metavize.mvvm.ArgonException;
import com.metavize.mvvm.IntfConstants;
import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.NetworkManager;
import com.metavize.mvvm.networking.LocalNetworkManager;
import com.metavize.mvvm.networking.NetworkException;
import com.metavize.mvvm.networking.internal.ServicesInternalSettings;
import com.metavize.mvvm.tran.HostName;
import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.tran.TransformException;
import com.metavize.mvvm.tran.script.ScriptException;
import com.metavize.mvvm.tran.script.ScriptRunner;
import com.metavize.mvvm.tran.script.ScriptWriter;
import org.apache.log4j.Logger;

// import static com.metavize.tran.openvpn.Constants.*;

class OpenVpnManager
{
    static final String OPENVPN_CONF_DIR      = "/etc/openvpn";
    private static final String OPENVPN_SERVER_FILE   = OPENVPN_CONF_DIR + "/server.conf";
    private static final String OPENVPN_CCD_DIR       = OPENVPN_CONF_DIR + "/ccd";
    private static final String CLIENT_CONF_FILE_BASE = Constants.PACKAGES_DIR + "/client-";

    private static final String VPN_START_SCRIPT = Constants.SCRIPT_DIR + "/start-openvpn";
    private static final String VPN_STOP_SCRIPT  = Constants.SCRIPT_DIR + "/stop-openvpn";
    private static final String GENERATE_DISTRO_SCRIPT = Constants.SCRIPT_DIR + "/generate-distro";

    /* Most likely want to bind to the outside address when using NAT */
    private static final String FLAG_LOCAL       = "local";

    /* XXX Have to expose this in the GUI */
    private static final String FLAG_PORT        = "port";

    private static final String FLAG_PROTOCOL    = "proto";
    private static final String DEFAULT_PROTOCOL = "udp";
    private static final String FLAG_DEVICE      = "dev";
    private static final String DEVICE_BRIDGE    = "tap0";
    private static final String DEVICE_ROUTING   = "tun0";

    private static final String FLAG_ROUTE        = "route";
    private static final String FLAG_IFCONFIG     = "ifconfig";
    private static final String FLAG_CLI_IFCONFIG = "ifconfig-push";
    private static final String FLAG_CLI_ROUTE    = "iroute";
    private static final String FLAG_BRIDGE_GROUP = "server-bridge";

    private static final String FLAG_PUSH         = "push";
    private static final String FLAG_EXPOSE_CLI   = "client-to-client";

    private static final String FLAG_MAX_CLI      = "max-clients";

    static final String FLAG_REMOTE               = "remote";
    private static final String DEFAULT_CIPHER    = "AES-128-CBC";

    private static final String FLAG_CERT         = "cert";
    private static final String FLAG_KEY          = "key";

    /* The directory where the key material ends up for a client */
    private static final String CLI_KEY_DIR       = "metavize-data";

    /* Ping every x seconds */
    private static final int DEFAULT_PING_TIME      = 10;

    /* If a ping response isn't received in this amount time, assume the connection is dead */
    private static final int DEFAULT_PING_TIMEOUT   = 120;

    /* Default verbosity in the log messages(0-9) *
     * 0 -- No output except fatal errors.
     * 1 to 4 -- Normal usage range.
     * 5  --  Output  R  and W characters to the console for each packet read and write, uppercase is
     * used for TCP/UDP packets and lowercase is used for TUN/TAP packets.
     * 6 to 11 -- Debug info range (see errlevel.h for additional information on debug levels). */
    private static final int DEFAULT_VERBOSITY   = 1;

    /* XXX Just pick one that is unused (this is openvpn + 1) */
    static final int MANAGEMENT_PORT     = 1195;

    /* Key management directives */
    private static final String SERVER_DEFAULTS[] = new String[] {
        "mode server",
        "ca   data/ca.crt",
        "cert data/server.crt",
        "key  data/server.key",
        "dh   data/dh.pem",
        // XXX This is only valid if you specify a pool
        // "ifconfig-pool-persist ipp.txt",
        "client-config-dir ccd",
        "keepalive " + DEFAULT_PING_TIME + " " + DEFAULT_PING_TIMEOUT,
        "cipher " + DEFAULT_CIPHER,
        "user nobody",
        "group nogroup",

        /* Only talk to clients with a client configuration file */
        /* XXX This may need to go away to support pools */
        "ccd-exclusive",
        "tls-server",
        "comp-lzo",

        /* XXX Be careful, restarts that change the key will not take this into account */
        "persist-key",
        "persist-tun",

        "status openvpn-status.log",
        "verb " + DEFAULT_VERBOSITY,

        /* Stop logging repeated messages (after 20). */
        "mute 20",

        /* Allow management from localhost */
        "management 127.0.0.1 " + MANAGEMENT_PORT
    };

    private static final String CLIENT_DEFAULTS[] = new String[] {
        "client",
        "proto udp",
        "resolv-retry 20",
        "keepalive " + DEFAULT_PING_TIME + " " + DEFAULT_PING_TIMEOUT,
        "nobind",
        "mute-replay-warnings",
        "ns-cert-type server",
        "cipher " + DEFAULT_CIPHER,
        "comp-lzo",
        "verb 2",
        "persist-key",
        "persist-tun",
        "verb " + DEFAULT_VERBOSITY,
        /* Exit if unable to connect to the server */
        "tls-exit",
        "ca " + CLI_KEY_DIR + "/ca.crt"
    };

    private static final String WIN_CLIENT_DEFAULTS[]  = new String[] {};
    private static final String WIN_EXTENSION          = "ovpn";


    private static final String UNIX_CLIENT_DEFAULTS[] = new String[] {
        // ??? Questionable because not all installs will have these users and groups.
        // "user nobody",
        // "group nogroup"
    };
    private static final String UNIX_EXTENSION         = "conf";

    private final Logger logger = Logger.getLogger( this.getClass());

    OpenVpnManager()
    {
    }

    void start( VpnSettings settings ) throws TransformException
    {
        logger.info( "Starting openvpn server" );

        ScriptRunner.getInstance().exec( VPN_START_SCRIPT );

        try {
            boolean isBridgeMode = settings.isBridgeMode();
            String intf = ( isBridgeMode ) ? DEVICE_BRIDGE : DEVICE_ROUTING;
            MvvmContextFactory.context().argonManager().registerIntf( IntfConstants.VPN_INTF, intf );

            /* ** XXXXXXX Bridge mode is unsupported */

            // if ( isBridgeMode ) {
            // am.enableInternalBridgeIntf( MvvmContextFactory.context().networkingManager().get(), intf );
            // }
            MvvmContextFactory.context().networkManager().updateAddress();
        } catch ( ArgonException e ) {
            throw new TransformException( e );
        } catch ( Exception e ) {
            throw new TransformException( e );
        }
    }

    void restart( VpnSettings settings ) throws TransformException
    {
        /* The start script handles the case where it has to be stopped */
        start( settings );
    }

    void stop() throws TransformException
    {
        logger.info( "Stopping openvpn server" );
        ScriptRunner.getInstance().exec( VPN_STOP_SCRIPT );

        try {
            //
            // am.disableInternalBridgeIntf( MvvmContextFactory.context().networkingManager().get());
            MvvmContextFactory.context().networkManager().updateAddress();
        } catch ( Exception e ) {
            throw new TransformException( e );
        }
    }

    void configure( VpnSettings settings ) throws TransformException
    {
        /* Nothing to start */
        if ( settings.getIsEdgeGuardClient()) return;

        writeSettings( settings );
        writeClientFiles( settings );
    }

    private void writeSettings( VpnSettings settings ) throws TransformException
    {
        ScriptWriter sw = new VpnScriptWriter();

        /* Insert all of the default parameters */
        sw.appendLines( SERVER_DEFAULTS );

        /* May want to expose this in the GUI */
        sw.appendVariable( FLAG_PORT, String.valueOf( VpnSettings.DEFAULT_PUBLIC_PORT ));

        /* Bridging or routing */
        if ( settings.isBridgeMode()) {
            sw.appendVariable( FLAG_DEVICE, DEVICE_BRIDGE );
        } else {
            sw.appendVariable( FLAG_DEVICE, DEVICE_ROUTING );
            IPaddr localEndpoint  = settings.getServerAddress();
            IPaddr remoteEndpoint = getRemoteEndpoint( localEndpoint );

            sw.appendVariable( FLAG_IFCONFIG, "" + localEndpoint + " " + remoteEndpoint );
            writePushRoute( sw, localEndpoint, null );

            /* Get all of the routes for all of the different groups */
            writeGroups( sw, settings );
        }

        writeExports( sw, settings );

        int maxClients = settings.getMaxClients();
        if ( maxClients > 0 ) sw.appendVariable( FLAG_MAX_CLI, String.valueOf( maxClients ));

        sw.writeFile( OPENVPN_SERVER_FILE );
    }

    private void writeExports( ScriptWriter sw, VpnSettings settings )
    {
        sw.appendComment( "Exports" );

        /* XXX This may need additional entries in the routing table,
         * because the edgeguard must also know how to route this
         * traffic
         * not sure about this comment, the entries seem to get
         * pushed automatically. */
        for ( ServerSiteNetwork siteNetwork : settings.getExportedAddressList()) {
            writePushRoute( sw, siteNetwork.getNetwork(), siteNetwork.getNetmask());
        }

        /* The client configuration file is written in writeClientFiles */
        for ( VpnSite site : (List<VpnSite>)settings.getSiteList()) {
            if ( !site.isEnabled()) continue;

            for ( ClientSiteNetwork siteNetwork : site.getExportedAddressList()) {
                IPaddr network = siteNetwork.getNetwork();
                IPaddr netmask = siteNetwork.getNetmask();

                writeRoute( sw, network, netmask );
                writePushRoute( sw, network, netmask );
            }
        }

        sw.appendLine();
    }

    private void writeGroups( ScriptWriter sw, VpnSettings settings )
    {
        /* XXX Need some group consolidation */
        /* XXX Need some checking for overlapping groups */
        /* XXX Do not exports groups that are not used */

        sw.appendComment( "Groups" );

        for ( VpnGroup group : (List<VpnGroup>)settings.getGroupList()) {
            writeRoute( sw, group.getAddress(), group.getNetmask());
        }

        sw.appendLine();
    }

    /**
     * Create all of the client configuration files
     */
    void writeClientConfigurationFiles( VpnSettings settings, VpnClient client )
        throws TransformException
    {
        NetworkManager nm = MvvmContextFactory.context().networkManager();

        String publicAddress = nm.getPublicAddress();
        writeClientConfigurationFile( settings, client, UNIX_CLIENT_DEFAULTS, UNIX_EXTENSION );
        writeClientConfigurationFile( settings, client, WIN_CLIENT_DEFAULTS,  WIN_EXTENSION );

        if (logger.isDebugEnabled()) {
            logger.debug( "Executing: " + GENERATE_DISTRO_SCRIPT );
        }
        try {
            String key = client.getDistributionKey();
            if ( key == null ) key = "";

            ScriptRunner.getInstance().exec( GENERATE_DISTRO_SCRIPT, client.getInternalName(),
                                             key, publicAddress,
                                             String.valueOf( client.getDistributeUsb()),
                                             String.valueOf( client.getIsEdgeGuard()));
        } catch ( ScriptException e ) {
            if ( e.getCode() == Constants.USB_ERROR_CODE ) {
                throw new UsbUnavailableException( "Unable to connect or write to USB device" );
            } else {
                throw e;
            }
        }
    }

    /*
     * Write a client configuration file (unix or windows)
     */
    private void writeClientConfigurationFile( VpnSettings settings, VpnClient client,
                                               String[] defaults, String extension )
    {
        ScriptWriter sw = new VpnScriptWriter();

        /* Insert all of the default parameters */
        sw.appendLines( CLIENT_DEFAULTS );
        sw.appendLines( defaults );

        if ( settings.isBridgeMode()) {
            sw.appendVariable( FLAG_DEVICE, DEVICE_BRIDGE );
        } else {
            sw.appendVariable( FLAG_DEVICE, DEVICE_ROUTING );
        }

        String name = client.getInternalName();

        sw.appendVariable( FLAG_CERT, CLI_KEY_DIR + "/" + name + ".crt" );
        sw.appendVariable( FLAG_KEY,  CLI_KEY_DIR + "/" + name + ".key" );

        /* VPN configuratoins needs information from the networking settings. */
        NetworkManager networkManager = MvvmContextFactory.context().networkManager();

        /* This is kind of janky */
        String publicAddress = networkManager.getPublicAddress();

        /* Strip off the port, (This guarantees if they set it to a hostname the value will be
         * correct) */
        publicAddress = publicAddress.split( ":" )[0];

        publicAddress = publicAddress.trim();

        sw.appendVariable( FLAG_REMOTE, publicAddress + " " + settings.getPublicPort());

        sw.writeFile( CLIENT_CONF_FILE_BASE + name + "." + extension );
    }

    private void writeClientFiles( VpnSettings settings )
    {
        /* Delete the old client files */
        try {
            File baseDirectory = new File( OPENVPN_CCD_DIR );
            if ( baseDirectory.exists()) {
                for ( File clientConfig : baseDirectory.listFiles()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug( "Deleting the file: " + clientConfig );
                    }
                    clientConfig.delete();
                }
            } else {
                baseDirectory.mkdir();
            }
        } catch ( Exception e ) {
            logger.error( "Unable to delete the previous client configuration files." );
        }
        ServicesInternalSettings sis = MvvmContextFactory.context().
            networkManager().getServicesInternalSettings();

        for ( VpnClient client : (List<VpnClient>)settings.getClientList()) {
            if ( !client.isEnabled()) continue;

            ScriptWriter sw = new VpnScriptWriter();

            IPaddr localEndpoint  = client.getAddress();
            IPaddr remoteEndpoint = getRemoteEndpoint( localEndpoint );
            String name           = client.getInternalName();

            logger.info( "Writing client configuration file for [" + name + "]" );

            /* XXXX This won't work for a bridge configuration */
            sw.appendVariable( FLAG_CLI_IFCONFIG, "" + localEndpoint + " " + remoteEndpoint );

            if(client.getGroup().isUseDNS()) {
              List<IPaddr> dnsServers = sis.getDnsServerList();

              for(IPaddr addr : dnsServers) {
                  sw.appendVariable( "push", "\"dhcp-option DNS " + addr.toString() + "\"");
              }

              if(sis.getIsEnabled() && sis.getIsDnsEnabled()) {
                  HostName localDomain = sis.getDnsLocalDomain();
                  if(localDomain != null) {
                      sw.appendVariable( "push", "\"dhcp-option DOMAIN " + localDomain.toString() + "\"");
                  }
              }
            }


            sw.writeFile( OPENVPN_CCD_DIR + "/" + name );
        }

        for ( VpnSite site : (List<VpnSite>)settings.getSiteList()) {
            if ( !site.isEnabled()) continue;
            ScriptWriter sw = new VpnScriptWriter();

            IPaddr localEndpoint  = site.getAddress();
            IPaddr remoteEndpoint = getRemoteEndpoint( localEndpoint );
            String name           = site.getInternalName();

            logger.info( "Writing site configuration file for [" + name + "]" );

            /* XXXX This won't work for a bridge configuration */
            sw.appendVariable( FLAG_CLI_IFCONFIG, "" + localEndpoint + " " + remoteEndpoint );

            for ( ClientSiteNetwork siteNetwork : site.getExportedAddressList()) {
                writeClientRoute( sw, siteNetwork.getNetwork(), siteNetwork.getNetmask());
            }

            sw.writeFile( OPENVPN_CCD_DIR + "/" + name );
        }
    }

    private void writePushRoute( ScriptWriter sw, IPaddr address, IPaddr netmask )
    {
        if ( address == null ) {
            logger.warn( "attempt to write route with null address" );
            return;
        }

        writePushRoute( sw, address.getAddr(), ( netmask == null ) ? null : netmask.getAddr());
    }

    private void writePushRoute( ScriptWriter sw, InetAddress address )
    {
        writePushRoute( sw, address, null );
    }

    private void writePushRoute( ScriptWriter sw, InetAddress address, InetAddress netmask )
    {
        if ( address == null ) {
            logger.warn( "attempt to write a push route with null address" );
            return;
        }

        String value = "\"route ";
        if ( netmask != null ) {
            /* the route command complains you do not pass in the base address */
            value += IPaddr.and( new IPaddr((Inet4Address)address ), new IPaddr((Inet4Address)netmask ));
            value += " " + netmask.getHostAddress();
        } else {
            value += address.getHostAddress();
        }

        value += "\"";

        sw.appendVariable( FLAG_PUSH,  value );
    }

    private void writeRoute( ScriptWriter sw, IPaddr address, IPaddr netmask )
    {
        writeRoute( sw, FLAG_ROUTE, address, netmask );
    }

    private void writeClientRoute( ScriptWriter sw, IPaddr address, IPaddr netmask )
    {
        writeRoute( sw, FLAG_CLI_ROUTE, address, netmask );
    }

    private void writeRoute( ScriptWriter sw, String type, IPaddr address, IPaddr netmask )
    {
        if ( address == null || address.getAddr() == null ) {
            logger.warn( "attempt to write a route with a null address" );
            return;
        }

        String value = "";

        if ( netmask != null && ( netmask.getAddr() != null )) {
            value += IPaddr.and( address, netmask );
            value += " " + netmask;
        } else {
            value += address;
        }

        sw.appendVariable( type, value );
    }

    /* A safe function (exceptionless) for InetAddress.getByAddress */
    IPaddr getByAddress( byte[] data )
    {
        try {
            return new IPaddr((Inet4Address)InetAddress.getByAddress( data ));
        } catch ( UnknownHostException e ) {
            logger.error( "Something happened, array should be 4 actually " + data.length + " bytes", e );
        }
        return null;
    }

    /* For Tunnel nodes, this gets the corresponding remote endpoint, see the openvpn howto for
     * the definition of a remote and local endpoint */
    IPaddr getRemoteEndpoint( IPaddr localEndpoint )
    {
        byte[] data = localEndpoint.getAddr().getAddress();
        data[3] += 1;
        return getByAddress( data );
    }
}

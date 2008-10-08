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

package com.untangle.node.openvpn;

import com.untangle.uvm.node.HostAddress;
import com.untangle.uvm.node.IPaddr;
import com.untangle.uvm.node.Node;
import com.untangle.uvm.node.Validator;

import com.untangle.uvm.node.NodeException;
import com.untangle.uvm.node.ValidateException;

import com.untangle.uvm.logging.EventManager;


import java.util.List;

public interface VpnNode extends Node
{
    public enum ConfigFormat
    {
        SETUP_EXE,
        ZIP;
    };
    
    public void setVpnSettings( VpnSettings settings );
    public VpnSettings getVpnSettings();

    /* Create a client certificate, if the client already has a certificate
     * this will automatically revoke their old one */
    public VpnClientBase generateClientCertificate( VpnSettings settings, VpnClientBase client );

    /* Revoke a client license */
    public VpnClientBase revokeClientCertificate( VpnSettings settings, VpnClientBase client );

    /* Need the address to log where the request came from */
    public String lookupClientDistributionKey( String key, IPaddr address );

    /* Returns a URL to use to download the admin key. */
    public String getAdminDownloadLink( String clientName, ConfigFormat format )
        throws NodeException;
     
    /* Returns true if this is the correct authentication key for
     * downloading keys as the administrator */
    public boolean isAdminKey( String key );

    /** Log a distribution event */
    public void addClientDistributionEvent( IPaddr clientAddress, String clientName );

    /* Send out the client distribution */
    public void distributeClientConfig( VpnClientBase client ) throws NodeException;

    public enum ConfigState { UNCONFIGURED, CLIENT, SERVER_BRIDGE, SERVER_ROUTE }
    public ConfigState getConfigState();
    public HostAddress getVpnServerAddress();

    public void startConfig(ConfigState state) throws ValidateException;

    /* Retrieve the link to use as a post to upload client configuration files. */
    public String getAdminClientUploadLink();
    public void completeConfig() throws Exception;

    /* This installs a client configuration that is somewhere on the
     * file system.  On success, this makes a copy of the
     * configuration. */
    public void installClientConfig( String path ) throws Exception;

    public void downloadConfig( HostAddress address, int port, String key ) throws Exception;
    public void generateCertificate( CertificateParameters parameters ) throws Exception;
    public GroupList getAddressGroups() throws Exception;
    public void setAddressGroups( GroupList parameters ) throws Exception;
    public ExportList getExportedAddressList();
    public void setExportedAddressList( ExportList parameters ) throws Exception;
    public void setClients( ClientList parameters ) throws Exception;
    public void setSites( SiteList parameters ) throws Exception;

    /**
     * Access the EventManager for ClientConnectEvents
     */
    public EventManager<ClientConnectEvent> getClientConnectEventManager();
    /**
     * Access the EventManager for VpnStatisticEvents
     */    
    public EventManager<VpnStatisticEvent> getVpnStatisticEventManager();
    /**
     * Access the EventManager for ClientDistributionEvents
     */    
    public EventManager<ClientDistributionEvent> getClientDistributionEventManager();
    public Validator getValidator();
}

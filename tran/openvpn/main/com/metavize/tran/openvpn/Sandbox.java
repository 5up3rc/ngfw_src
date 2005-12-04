/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.openvpn;

import com.metavize.mvvm.security.Tid;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;

import org.apache.log4j.Logger;

import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.tran.TransformException;
import com.metavize.mvvm.tran.ValidateException;

import com.metavize.mvvm.tran.script.ScriptRunner;
import com.metavize.mvvm.tran.script.ScriptException;


/* XXX Probably want to make this an abstract class and make this a little more generic */
class Sandbox
{
    private final Logger logger = Logger.getLogger( Sandbox.class );
    
    private static final int DEFAULT_MAX_CLIENTS = 20;
    private static final boolean DEFAULT_KEEP_ALIVE  = true;
    private static final boolean DEFAULT_EXPOSE_CLIENTS = true;

    private static final String DOWNLOAD_SCRIPT = Constants.SCRIPT_DIR + "/get-client";
    private static final String LS_CLIENTS_SCRIPT = Constants.SCRIPT_DIR + "/list-usb-configs";
    private static final String CLIENT_LIST_FILE = Constants.MISC_DIR + "/client-list";

    private CertificateParameters certificateParameters;
    private GroupList  groupList;
    private ExportList exportList;
    private ClientList clientList;
    private SiteList   siteList;
    private final VpnTransform.ConfigState configState;

    private final Map<String,VpnGroup> resolveGroupMap = new HashMap<String,VpnGroup>();
    
    Sandbox( VpnTransform.ConfigState configState )
    {
        this.configState = configState;
    }

    List<String> getAvailableUsbList() throws TransformException
    {
        BufferedReader in = null;
        
        try {
            /* Run the script to generate a list of the clients that are available */
            ScriptRunner.getInstance().exec( LS_CLIENTS_SCRIPT );

            /* Get the List */
            in = new BufferedReader( new FileReader( CLIENT_LIST_FILE ));
            List<String> availableList = new LinkedList<String>();

            /* Add each line to the available list */
            String str;
            while (( str = in.readLine()) != null ) availableList.add( str.trim());
            
            return availableList;
        } catch ( ScriptException e ) {
            switch ( e.getCode()) {                
            case Constants.USB_ERROR_CODE:
                throw new UsbUnavailableException( "Unable to read the USB device." );

            default:
                logger.warn( "Unable to read USB files", e );
                throw new TransformException( "Unable to read the USB device." );
            }
        } catch ( FileNotFoundException e ) {
            /* It is a problem, but no need to let the client know this */
            logger.warn( "File " + CLIENT_LIST_FILE + " does not exist" );
        } catch ( Exception e ) {
            logger.warn( "Unable to read USB files", e );
            throw new TransformException( "Unable to read USB files." );
        } finally {
            try {
                if ( in != null ) in.close();
            } catch ( Exception e ) {
                /* who cares, it is attempting to close the file */
            }
        }

        /* This is for any exceptions that are logged, but not errors */
        return new LinkedList<String>();
    }

    void downloadConfig( IPaddr address, int port, String key ) throws Exception
    {
        /* The key must be a valid hexadecimal number, this is 
         * an easy check to detect spaces, and anyother weird characters
         */
        try {
            Long.parseLong( key, 16 );
        } catch ( NumberFormatException e ) { 
            throw new ValidateException( "A key must only contain numbers and the letters A-F: " + key );
        }
        String serverSocketAddress = address.toString();
        
        if ( port != 443 && port != 0 ) serverSocketAddress += ":" + port;

        execDownloadScript( false, serverSocketAddress, key );
    }

    void downloadConfigUsb( String name ) throws Exception
    {
        VpnClient.validateName( name );
        execDownloadScript( true, name, "" );
    }

    private void execDownloadScript( boolean isUsb, String arg1, String arg2 ) throws Exception
    {
        try {
            ScriptRunner.getInstance().exec( DOWNLOAD_SCRIPT, String.valueOf( isUsb ), arg1, arg2 );
        } catch ( ScriptException e ) {
            switch ( e.getCode()) {
            case Constants.DOWNLOAD_ERROR_CODE: 
                throw new DownloadException( "Unable to download client." );
                
            case Constants.START_ERROR:
                throw new StartException( "Test connection with OpenVPN server failed." );

            case Constants.USB_ERROR_CODE:
                throw new UsbUnavailableException( "Unable to read the USB device." );

            default:
                logger.warn( "Unable to install client configuration", e );
                throw new TransformException( "Unable to install client configuration" );
            }
        } catch ( Exception e ) {
            logger.warn( "Unable to install client configuration", e );
            throw new TransformException( "Unable to install client configuration." );
        }
    }
    
    void generateCertificate( CertificateParameters parameters ) throws Exception
    {
        parameters.validate();

        this.certificateParameters = parameters;
    }

    GroupList getGroupList() throws Exception {
        if ( this.groupList == null ) throw new ValidateException( "Groups haven't been created yet" );
        return this.groupList;
    }

    void setGroupList( GroupList parameters ) throws Exception
    {
        parameters.validate();
        
        this.groupList = parameters;

        /* Update the resolve group map */
        resolveGroupMap.clear();

        for ( VpnGroup group : this.groupList.getGroupList()) {
            if ( resolveGroupMap.put( group.getName(), group ) != null ) {
                /* This shouldn't happen because this is validated in parameters.validate */
                throw new ValidateException( "Group name must be unique: '" + group.getName() + "'" );
            }

        }        
    }

    void setExportList( ExportList parameters ) throws Exception
    {
        parameters.validate();

        this.exportList = parameters;
    }

    void setClientList( ClientList parameters ) throws Exception
    {
        parameters.validate();
        
        fixGroups( parameters.getClientList());
        this.clientList = parameters;
    }

    void setSiteList( SiteList parameters ) throws Exception
    {
        /* Validate the site list against the client list */
        parameters.validate( clientList );

        fixGroups( parameters.getSiteList());
        this.siteList = parameters;
    }

    private void fixGroups( List newClientList )
        throws ValidateException
    {
        for ( VpnClient client : (List<VpnClient>)newClientList ) {
            String name = client.getGroup().getName();
            VpnGroup newGroup = resolveGroupMap.get( name );
            if ( newGroup == null ) {
                throw new ValidateException( "The group '" + name + "' is not in the group list" );
            }
            client.setGroup( newGroup );
        }
    }

    VpnSettings completeConfig( Tid tid ) throws Exception
    {
        /* Create new settings */
        VpnSettings settings = new VpnSettings( tid );

        switch ( configState ) {
        case SERVER_BRIDGE:
            throw new ValidateException( "Bridge mode is presently unsupported" );
            // settings.setBridgeMode( true );
            // break;
        case SERVER_ROUTE:
            settings.setBridgeMode( false );
            settings.setIsEdgeGuardClient( false );
            break;

        case CLIENT:
            settings.setIsEdgeGuardClient( true );
            settings.setBridgeMode( false ); /* This would come from the other box */

            /* Nothing left to do */
            return settings;
            
        default:
            throw new ValidateException( "Invalid state for sandbox: " + this.configState );
        }

        /* Certificate parameters */
        settings.setOrganization( this.certificateParameters.getOrganization());
        settings.setDomain( this.certificateParameters.getDomain());
        settings.setCountry( this.certificateParameters.getCountry());
        settings.setProvince( this.certificateParameters.getState());
        settings.setLocality( this.certificateParameters.getLocality());
        settings.setCaKeyOnUsb( this.certificateParameters.getStoreCaUsb());

        /* Group list */
        settings.setGroupList( this.groupList.getGroupList());

        /* Client list */
        settings.setClientList( this.clientList.getClientList());
        
        settings.setSiteList( this.siteList.getSiteList());

        settings.setExportedAddressList( this.exportList.getExportList());

        /* Hiding these from the user right now */
        settings.setMaxClients( DEFAULT_MAX_CLIENTS );
        settings.setKeepAlive( DEFAULT_KEEP_ALIVE );
        settings.setExposeClients( DEFAULT_EXPOSE_CLIENTS );
        
        settings.validate();
        
        return settings;
    }
}

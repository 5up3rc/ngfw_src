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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import org.hibernate.Query;
import org.hibernate.Session;

import com.metavize.mvvm.ArgonException;
import com.metavize.mvvm.IntfConstants;
import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.MvvmLocalContext;

import com.metavize.mvvm.localapi.ArgonInterface;
import com.metavize.mvvm.localapi.LocalIntfManager;

import com.metavize.mvvm.networking.internal.NetworkSpacesInternalSettings;
import com.metavize.mvvm.networking.internal.InterfaceInternal;
import com.metavize.mvvm.networking.internal.PPPoEConnectionInternal;
import com.metavize.mvvm.networking.internal.PPPoESettingsInternal;

import com.metavize.mvvm.tran.ValidateException;
import com.metavize.mvvm.tran.script.ScriptWriter;
import static com.metavize.mvvm.tran.script.ScriptWriter.COMMENT;
import static com.metavize.mvvm.tran.script.ScriptWriter.METAVIZE_HEADER;

import com.metavize.mvvm.util.DataLoader;
import com.metavize.mvvm.util.DataSaver;

class PPPoEManagerImpl
{
    /* This is the prefix where the peers file should be placed */
    private static final String CONNECTION_NAME_PREFIX = "connection-";
    private static final String PEERS_FILE_PREFIX = "/etc/ppp/peers/" + CONNECTION_NAME_PREFIX;
    private static final String PAP_SECRETS_FILE = "/etc/ppp/pap-secrets";
    
    /* The current settings */
    private PPPoESettingsInternal settings;
    
    /* The list of interfaces that have presently been registered as a PPP interface */
    private List<ArgonInterface> registeredIntfList = new LinkedList<ArgonInterface>();

    private final Logger logger = Logger.getLogger(getClass());
    
    PPPoEManagerImpl()
    {
    }

    /* ----------------- Public  ----------------- */
    public PPPoESettingsInternal getInternalSettings()
    {
        return this.settings;
    }

    /* Retrieve the global settings, this is used to modify all of the PPPoE Connections */
    public PPPoESettings getSettings()
    {
        if ( this.settings == null ) {
            logger.warn( "null PPPoE Settings, returning new settings object" );
            return new PPPoESettings();
        }
        return this.settings.toSettings();
    }

    public void setSettings( PPPoESettings newSettings ) throws PPPoEException
    {
        try {
            saveSettings( PPPoESettingsInternal.makeInstance( newSettings ));
        } catch ( ValidateException e ) {
            throw new PPPoEException( "Error saving settings", e );
        }

        registerIntfs();
    }

    /* Retrieve the PPPoE configuration for the external interface */
    public PPPoEConnectionRule getExternalSettings()
    {
        /* This returns the first external interface connection, or an empty connection rule
         * if there isn't one */

        for ( PPPoEConnectionInternal connection : this.settings.getConnectionList()) {
            if ( connection.getArgonIntf() == IntfConstants.EXTERNAL_INTF ) {
                return connection.toRule();
            }
        }

        PPPoEConnectionRule rule = new PPPoEConnectionRule();
        rule.setLive( false );
        rule.setArgonIntf( IntfConstants.EXTERNAL_INTF );
        return rule;
    }

    public void setExternalSettings( PPPoEConnectionRule newValue ) throws PPPoEException
    {
        PPPoESettings newSettings = getSettings();

        /* First remove all instances that point to external */
        List<PPPoEConnectionRule> connectionList = newSettings.getConnectionList();
        for ( Iterator<PPPoEConnectionRule> iter = connectionList.iterator() ; iter.hasNext() ; ) {
            PPPoEConnectionRule rule = iter.next();
            
            if ( rule.getArgonIntf() == IntfConstants.EXTERNAL_INTF ) iter.remove();
        }

        /* Next if the setting is not null, add it to the list */
        if ( newValue != null ) {
            /* Verify that the argon interface is set properly */
            if ( newValue.getArgonIntf() != IntfConstants.EXTERNAL_INTF ) {
                logger.warn( "external connection doesn't use external interface[" + 
                             newValue.getArgonIntf() +   "], setting to external" );
                newValue.setArgonIntf( IntfConstants.EXTERNAL_INTF );
            }
            connectionList.add( newValue );
        }

        /* Update the connection list */
        newSettings.setConnectionList( connectionList );

        /* Actually save the settings */
        setSettings( newSettings );
    }
        
    /* ----------------- Package ----------------- */
    void init()
    {
        try {
            this.settings = loadSettings();
            if ( null == this.settings ) saveDefaults();

        } catch ( ValidateException e ) {
            /* Loaded invalid settings, replacing with bogus replacements */
            logger.warn( "Invalid PPPoESettings in the database, using null", e );
            this.settings = null;
        }

        /* Possibly register a listener */
        try {
            registerIntfs();
        } catch ( PPPoEException e ) {
            logger.warn( "Unable to register ppp interfaces at startup, disabling" );
            resetIntfs();
        }
    }
    
    /* Write all the config files */
    void writeConfigFiles( NetworkSpacesInternalSettings networkSettings ) throws PPPoEException
    {
        if ( !this.settings.getIsEnabled()) return;

        /* determine if dns should be overriden */
        boolean usePeerDns = false;
        for ( InterfaceInternal intf : networkSettings.getInterfaceList()) {
            /* if DHCP is enabled on the external space, then use peer dns */
            if ( intf.getArgonIntf().getArgon() == IntfConstants.EXTERNAL_INTF ) {
                if ( intf.getNetworkSpace().getIsDhcpEnabled()) usePeerDns = true;
            }
        }
        
        /* Write out all of the peers files */
        for ( PPPoEConnectionInternal connection : this.settings.getConnectionList()) {
            if ( !connection.isLive()) continue;
            PPPoEPeerWriter peer = new PPPoEPeerWriter( connection, usePeerDns );
            try {
                peer.addConnection();
                peer.writeFile( PEERS_FILE_PREFIX + connection.getArgonIntf());
            } catch ( ArgonException e ) {
                throw new PPPoEException( "Unable to write configuration file for [" + connection + "]", e );
                
            }
        }
        
        /* Write out the username/password information, and properly set the permissions */
        PPPoEPapSecretsWriter secrets = new PPPoEPapSecretsWriter( this.settings );
        secrets.addSettings();
        secrets.writeFile( PAP_SECRETS_FILE, "600" );
    }

    void isConnected()
    {
    }

    /* This re-registers all of the interfaces as PPP devices */
    synchronized void registerIntfs() throws PPPoEException
    {
        /* Unregister all of the active interfaces */
        unregisterIntfs();

        /* Don't need to do anything if the connection is not enabled */
        if (( null == this.settings ) || !this.settings.getIsEnabled()) return;

        LocalIntfManager lim = MvvmContextFactory.context().localIntfManager();
        
        List<ArgonInterface> registeredIntfList = new LinkedList<ArgonInterface>();
        
        for ( PPPoEConnectionInternal connection : this.settings.getConnectionList()) {
            /* Skip all of the connections that are not active */
            if ( !connection.isLive()) continue;
            
            /* Retrieve the current interface mapping */
            try {
                ArgonInterface intf = lim.getIntfByArgon( connection.getArgonIntf());
                registeredIntfList.add( intf );
            } catch ( ArgonException e ) {
                logger.warn( "Unable to lookup an argon interface for: " + connection, e );
                continue;
            }

            /* Register the device */
            try {
                lim.registerSecondaryIntf( connection.getDeviceName(), connection.getArgonIntf());
            } catch ( ArgonException e ) {
                logger.warn( "Unable to register the interface for: " + connection, e );
                /* Set the registered interface list before throwing the exception so
                 * that unregister will replace all of the correct interfaces */
                this.registeredIntfList = registeredIntfList;
                throw new PPPoEException( "Unable to register the interface for: " + connection, e );
            }
        }
        
        this.registeredIntfList = registeredIntfList;
    }

    synchronized void unregisterIntfs()
    {
        if ( null == this.registeredIntfList ) {
            logger.info( "there are no registered interfaces" );
            return;
        }

        LocalIntfManager lim = MvvmContextFactory.context().localIntfManager();
        
        /* Iterate each of the cached entries and replace with their original values */
        for ( ArgonInterface intf : this.registeredIntfList ) {
            try {
                lim.unregisterSecondaryIntf( intf.getArgon());
            } catch ( ArgonException e ) {
                logger.warn( "Unable to register the interface for: " + intf + " continuing.", e );
                resetIntfs();
            }
        }

        /* Set to null to avoid unregistering twice */
        this.registeredIntfList = null;
    }

    /* This is for the ohh no situtation, just a way to get back to the interfaces at startup */
    synchronized void resetIntfs()
    {
        MvvmContextFactory.context().localIntfManager().resetSecondaryIntfs();
        
        this.registeredIntfList = null;
    }

    /* Append the necessary values to the /etc/network/interfaces file */
    void addInterfaceConfig( InterfacesScriptWriter w )
    {
        /* need to kill all running instances of ppp */
        w.appendCommands( "up poff -a" );

        /* Nothing to do if the settings are not enabled */
        if ( !this.settings.getIsEnabled()) return;
        
        for ( PPPoEConnectionInternal connection : this.settings.getConnectionList()) {
            /* Ignore the connections that are not active */
            if ( !connection.isLive()) continue;
            w.appendCommands( "up pon " + CONNECTION_NAME_PREFIX + connection.getArgonIntf());
        }
    }

    /* ----------------- Private ----------------- */
    /* Load the settings from the database */
    private PPPoESettingsInternal loadSettings() throws ValidateException
    {
        DataLoader<PPPoESettings> loader = new DataLoader<PPPoESettings>( "PPPoESettings",
                                                                          MvvmContextFactory.context());
        PPPoESettings dbSettings = loader.loadData();

        /* No database settings */
        if ( dbSettings == null ) {
            logger.info( "There are no network database settings" );
            return null;
        }

        return PPPoESettingsInternal.makeInstance( dbSettings );
    }

    /* Save the settings to the database */
    private void saveSettings( PPPoESettingsInternal newSettings )
    {
        DataSaver<PPPoESettings> saver =
            new PPPoESettingsDataSaver( MvvmContextFactory.context());

        if ( saver.saveData( newSettings.toSettings()) == null ) {
            logger.error( "Unable to save the pppoe settings." );
            return;
        }

        this.settings = newSettings;
    }

    /* Save the initial default settings */
    private void saveDefaults() throws ValidateException
    {
        PPPoESettings settings = new PPPoESettings();
        settings.setIsEnabled( true );
        saveSettings( PPPoESettingsInternal.makeInstance( settings ));
    }

    /* Data saver used to delete other instances of the object */
    private static class PPPoESettingsDataSaver extends DataSaver<PPPoESettings>
    {
        PPPoESettingsDataSaver( MvvmLocalContext local )
        {
            super( local );
        }

        @Override
        protected void preSave( Session s )
        {
            Query q = s.createQuery( "from PPPoESettings" );
            for ( Iterator iter = q.iterate() ; iter.hasNext() ; ) {
                PPPoESettings settings = (PPPoESettings)iter.next();
                s.delete( settings );
            }
        }
    }

    /* These are done this way so it would be easy to break them into their own files when
     * this gets too large */
    private static class PPPoEPeerWriter extends ScriptWriter
    {
        private static final String PEERS_HEADER =
            COMMENT + METAVIZE_HEADER + "\n" +
            COMMENT + " PPP configuration file.\n\n" +
            "noipdefault\n" +
            "hide-password\n" +
            "noauth\n" +
            "persist\n";

        
        private final PPPoEConnectionInternal connection;
        private final boolean usePeerDns;

        PPPoEPeerWriter( PPPoEConnectionInternal connection, boolean usePeerDns )
        {
            super();
            this.connection = connection;
            this.usePeerDns = usePeerDns;
        }

        void addConnection() throws ArgonException
        {
            /* If this is the external interface, replace the default route */
            byte argonIndex = connection.getArgonIntf();
            ArgonInterface ai = MvvmContextFactory.context().localIntfManager().getIntfByArgon( argonIndex );
            
            /* Only update the default route if this is the final interface */
            if ( argonIndex == IntfConstants.EXTERNAL_INTF ) {
                appendLine( "defaultroute" );
                appendLine( "replacedefaultroute" );

                /* only append this on the external interface if use peer dns is enabled. */
                if ( this.usePeerDns ) appendLine( "usepeerdns" );
            }

            appendLine( "plugin rp-pppoe.so " + ai.getPhysicalName());
            appendLine( "unit " + argonIndex );
            appendLine( "user \"" + connection.getUsername() + "\"" );
            
            
            String secretField = connection.getSecretField().trim();
            
            /* append the secret field if necessary */
            if ( secretField.length() > 0 ) appendLine( secretField );
        }

        @Override
        protected String header()
        {
            return PEERS_HEADER;
        }
    }

    /* These are done this way so it would be easy to break them into their own files when
     * this gets too large */
    private static class PPPoEPapSecretsWriter extends ScriptWriter
    {
        private static final String PAP_HEADER =
            COMMENT + METAVIZE_HEADER + "\n" +
            COMMENT + " PAP configuration file.\n\n";
        
        private final PPPoESettingsInternal settings;
        
        PPPoEPapSecretsWriter( PPPoESettingsInternal settings )
        {
            super();
            this.settings = settings;
        }
        
        void addSettings()
        {
            /* Add the username and password for each user */
            for ( PPPoEConnectionInternal connection : this.settings.getConnectionList()) {
                if ( !connection.isLive()) continue ;
                appendLine( "\"" + connection.getUsername() + "\" * \"" + connection.getPassword() + "\"" );
            }
        }

        @Override
        protected String header()
        {
            return PAP_HEADER;
        }

    }
}


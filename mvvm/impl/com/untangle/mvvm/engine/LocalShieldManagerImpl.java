/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.mvvm.engine;

import com.untangle.mvvm.shield.ShieldNodeSettings;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import com.untangle.jnetcap.Shield;

import com.untangle.mvvm.ArgonException;
import com.untangle.mvvm.localapi.LocalShieldManager;

class LocalShieldManagerImpl implements LocalShieldManager
{
    private boolean isShieldEnabled = true;
    private String shieldFile       = null;

    private final Logger logger = Logger.getLogger( this.getClass());

    LocalShieldManagerImpl()
    {
    }

    /* Toggle whether or not the shield is enabled, the real
     * determininant is actually controlled by Netcap.init which is
     * called from Argon. (It is presently hard to turn the shield on
     * and then off.) */
    public void setIsShieldEnabled( boolean isEnabled )
    {
        this.isShieldEnabled = isEnabled;
    }

    /* Set the file used to configure the shield */
    public void setShieldConfigurationFile( String file )
    {
        this.shieldFile = file;
        shieldReconfigure();
    }

    public void shieldStatus( InetAddress ip, int port, int interval )
    {
        /* Do nothing if the shield is not enabled */
        if ( !this.isShieldEnabled ) {
            logger.debug( "The shield is disabled" );
            return;
        }

        /* Verify the port is in a valid range */
        if ( port < 0 || port > 0xFFFF ) throw new IllegalArgumentException( "Invalid port: " + port );

        do {
            Shield.getInstance().status( ip, port );

            try {
                if ( interval > 0 ) Thread.sleep( interval );
            } catch ( InterruptedException e ) {
                logger.debug( "Shield status interrupted" );
                break;
            }
        } while ( interval > 0 );
    }

    public void shieldReconfigure()
    {
        /* Do nothing if the shield is not enabled */
        if ( !this.isShieldEnabled ) {
            logger.debug( "The shield is disabled" );
            return;
        }

        if ( this.shieldFile  != null ) Shield.getInstance().config( this.shieldFile );
    }

    /* Update the shield node settings */
    public void setShieldNodeSettings( List<ShieldNodeSettings> shieldNodeSettingsList ) 
        throws ArgonException
    {
        /* Do nothing if the shield is not enabled */
        if ( !this.isShieldEnabled ) {
            logger.debug( "The shield is disabled" );
            return;
        }

        List <com.untangle.jnetcap.ShieldNodeSettings> settingsList = 
            new LinkedList<com.untangle.jnetcap.ShieldNodeSettings>();

        for ( ShieldNodeSettings settings : shieldNodeSettingsList ) {
            InetAddress netmask;
            try {
                byte full = (byte)255;
                netmask = InetAddress.getByAddress( new byte[]{ full, full, full, full } );
            } catch( UnknownHostException e ) {
                logger.error( "Unable to parse default netmask", e );
                throw new ArgonException( e );
            }

            if ( settings == null ) {
                logger.error( "NULL Settings in list\n" );
                continue;
            }

            if ( !settings.isLive()) {
                logger.debug( "Ignoring disabled settings" );
                continue;
            }

            if ( settings.getAddress() == null || settings.getAddress().isEmpty()) {
                logger.error( "Settings with empty address, ignoring" );
                continue;
            }
            
            if ( settings.getNetmask() != null && !settings.getNetmask().isEmpty()) {
                logger.warn( "Settings with non-empty netmask, ignoring netmask using 255.255.255.255" );
            }
            
            logger.debug( "Adding shield node setting " + settings.getAddress().toString() + "/" + 
                          netmask.getHostAddress() + " divider: " + settings.getDivider());
                        
            settingsList.add( new com.untangle.jnetcap.ShieldNodeSettings( settings.getDivider(), 
                                                                           settings.getAddress().getAddr(), 
                                                                           netmask ));
        }

        try {
            Shield.getInstance().setNodeSettings( settingsList );
        } catch ( Exception e ) {
            throw new ArgonException( "Unable to set the shield node settingss", e );
        }
    }
}

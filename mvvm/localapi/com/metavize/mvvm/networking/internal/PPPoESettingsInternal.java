/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.networking.internal;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.metavize.mvvm.networking.PPPoESettings;
import com.metavize.mvvm.networking.PPPoEConnectionRule;

import com.metavize.mvvm.tran.ValidateException;

/**
 * Immutable PPPoE Settings values.
 *
 * @author <a href="mailto:rbscott@metavize.com">Robert Scott</a>
 * @version 1.0
 */
public class PPPoESettingsInternal
{
    /** Set to true in order to enable the PPPoE connection */
    private final boolean isEnabled;
    
    private final List<PPPoEConnectionInternal> connectionList;

    private PPPoESettingsInternal( PPPoESettings settings, List<PPPoEConnectionInternal> connectionList )
    {
        this.isEnabled = settings.getIsEnabled();
        this.connectionList = 
            Collections.unmodifiableList( new LinkedList<PPPoEConnectionInternal>( connectionList ));        
    }

    public boolean getIsEnabled()
    {
        return this.isEnabled;
    }
    
    public List<PPPoEConnectionInternal> getConnectionList()
    {
        return this.connectionList;
    }
    
    /* Return a new settings objects prepopulated with these values */
    public PPPoESettings toSettings()
    {
        PPPoESettings settings = new PPPoESettings();
        settings.setIsEnabled( getIsEnabled());
        
        List<PPPoEConnectionRule> connectionRuleList = new LinkedList<PPPoEConnectionRule>();
        
        for ( PPPoEConnectionInternal internal : getConnectionList()) {
            connectionRuleList.add( internal.toRule());
        }

        settings.setConnectionList( connectionRuleList );
        
        return settings;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "PPPoE Internal Settings[" + getIsEnabled() + "]\n" );
        
        for ( PPPoEConnectionInternal connection : getConnectionList()) sb.append( connection + "\n" );

        sb.append( "PPPoE Internal Settings END" );

        return sb.toString();
    }

    public static PPPoESettingsInternal makeInstance( PPPoESettings settings )
        throws ValidateException
    {
        settings.validate();
        
        List<PPPoEConnectionInternal> connectionList = new LinkedList<PPPoEConnectionInternal>();
        
        for ( PPPoEConnectionRule rule : settings.getConnectionList()) {
            /* Use the argon interface as the suffix for the device
             * name, they do not have to be sequential. */
            String deviceName = ( rule.isLive()) ? ( "ppp" + rule.getArgonIntf() ) : null;
            connectionList.add( PPPoEConnectionInternal.makeInstance( rule, deviceName ));
        }

        return new PPPoESettingsInternal( settings, connectionList );
    }
}
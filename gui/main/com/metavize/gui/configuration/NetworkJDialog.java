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

package com.metavize.gui.configuration;

import com.metavize.gui.widgets.dialogs.*;
import com.metavize.gui.widgets.editTable.*;
import com.metavize.gui.util.*;

import com.metavize.mvvm.client.MvvmRemoteContextFactory;
import com.metavize.mvvm.NetworkingConfiguration;

import java.awt.*;
import java.util.*;
import javax.swing.table.*;
import javax.swing.*;



public class NetworkJDialog extends MConfigJDialog {
    
    private static final String NAME_NETWORKING_CONFIG = "Networking Config";
    private static final String NAME_NETWORK_SETTINGS  = "External Address";
    private static final String NAME_ALIAS_PANEL       = "External Address Aliases";
    private static final String NAME_HOSTNAME          = "Hostname";


    public NetworkJDialog( ) {
        this.setTitle(NAME_NETWORKING_CONFIG);
	compoundSettings = new NetworkCompoundSettings();
    }

    protected Dimension getMinSize(){
	return new Dimension(640, 550);
    }
    
    protected void generateGui(){        
        // NETWORK SETTINGS //////
        NetworkIPJPanel ipJPanel = new NetworkIPJPanel(this);
	addScrollableTab(null, NAME_NETWORK_SETTINGS, null, ipJPanel, false, true);
	addSavable(NAME_NETWORK_SETTINGS, ipJPanel);
	addRefreshable(NAME_NETWORK_SETTINGS, ipJPanel);
        	
        // ALIASES /////
        NetworkAliasJPanel aliasJPanel = new NetworkAliasJPanel();
	addTab(NAME_ALIAS_PANEL, null, aliasJPanel );
	addSavable(NAME_ALIAS_PANEL, aliasJPanel );
	addRefreshable(NAME_ALIAS_PANEL, aliasJPanel );

	// HOSTNAME //////
        NetworkHostnameJPanel hostnameJPanel = new NetworkHostnameJPanel();
	addScrollableTab(null, NAME_HOSTNAME, null, hostnameJPanel, false, true);
	addSavable(NAME_HOSTNAME, hostnameJPanel);
	addRefreshable(NAME_HOSTNAME, hostnameJPanel);
    }   

    protected boolean shouldSave(){
        NetworkSaveSettingsProceedJDialog networkSaveSettingsProceedJDialog = new NetworkSaveSettingsProceedJDialog(this);
        return networkSaveSettingsProceedJDialog.isProceeding();	
    }

    protected void saveAll() throws Exception {
	int previousTimeout = MvvmRemoteContextFactory.factory().getTimeout();
	MvvmRemoteContextFactory.factory().setTimeout(Util.RECONFIGURE_NETWORK_TIMEOUT_MILLIS);
	super.saveAll();
	MvvmRemoteContextFactory.factory().setTimeout(previousTimeout);
	// UPDATE STORE
	Util.getPolicyStateMachine().updateStoreModel();
    }

}

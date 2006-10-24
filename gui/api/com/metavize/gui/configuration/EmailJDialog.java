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

package com.metavize.gui.configuration;

import com.metavize.gui.widgets.dialogs.*;
import com.metavize.gui.widgets.editTable.*;
import com.metavize.gui.util.*;

import com.metavize.gui.transform.*;
import com.metavize.mvvm.tran.*;
import com.metavize.mvvm.security.Tid;

import java.lang.reflect.Constructor;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Component;
import java.awt.BorderLayout;
import java.util.*;
import javax.swing.table.*;
import javax.swing.*;


public class EmailJDialog extends MConfigJDialog {

    private static final String NAME_EMAIL_CONFIG            = "Email Config";
    private static final String NAME_OUTGOING_SETTINGS       = "Outgoing Server";
    private static final String NAME_SAFE_LIST               = "From-Safe List";
    private static final String NAME_SAFE_LIST_GLOBAL        = "Global";
    private static final String NAME_SAFE_LIST_USER          = "Per User";
    private static final String NAME_QUARANTINE_SETTINGS     = "Quarantine";
    private static final String NAME_ALL_ACCOUNTS            = "Release & Purge";
    private static final String NAME_QUARANTINABLE_ADDRESSES = "Quarantinable Addresses";
    private static final String NAME_QUARANTINABLE_FORWARDS  = "Quarantinable Forwards";
    private static final String NAME_GENERAL_SETTINGS        = "General Settings";

    private EmailCompoundSettings emailCompoundSettings;

    public EmailJDialog( Frame parentFrame ) {
        super(parentFrame);
        setTitle(NAME_EMAIL_CONFIG);
        compoundSettings = new EmailCompoundSettings();
        emailCompoundSettings = (EmailCompoundSettings) compoundSettings;
        INSTANCE = this;
    }

    protected Dimension getMinSize(){
        return new Dimension(640, 550);
    }

    
    private static EmailJDialog INSTANCE;
    public static EmailJDialog instance(){ return INSTANCE; }

    protected void generateGui(){
        // OUTGOING SERVER /////
        EmailOutgoingJPanel emailOutgoingJPanel = new EmailOutgoingJPanel();
	addScrollableTab(null, NAME_OUTGOING_SETTINGS, null, emailOutgoingJPanel, false, true);
	addSavable(NAME_OUTGOING_SETTINGS, emailOutgoingJPanel );
	addRefreshable(NAME_OUTGOING_SETTINGS, emailOutgoingJPanel );

	// EMAIL TRANSFORM SETTINGS //
	if( emailCompoundSettings.getMailTransformCompoundSettings() != null ){

	    // SAFELIST GLOBAL & USER //////
	    JTabbedPane safelistJTabbedPane = addTabbedPane(NAME_SAFE_LIST, null);
	    Component whitelistGlobalComponent = emailCompoundSettings.getSafelistGlobalComponent();
	    safelistJTabbedPane.addTab(NAME_SAFE_LIST_GLOBAL, null, whitelistGlobalComponent);
	    addSavable(NAME_SAFE_LIST_GLOBAL, (Savable) whitelistGlobalComponent);
	    addRefreshable(NAME_SAFE_LIST_GLOBAL, (Refreshable) whitelistGlobalComponent);
	    Component whitelistAllUsersComponent = emailCompoundSettings.getSafelistAllUsersComponent();
	    safelistJTabbedPane.addTab(NAME_SAFE_LIST_USER, null, whitelistAllUsersComponent);
	    addRefreshable(NAME_SAFE_LIST_USER, (Refreshable) whitelistAllUsersComponent);
	
	    // QUARANTINE ///////
	    JTabbedPane quarantineJTabbedPane = addTabbedPane(NAME_QUARANTINE_SETTINGS, null);

	    // QUARANTINE RELEASE & PURGE //////
	    Component quarantineAllComponent = emailCompoundSettings.getQuarantineReleaseAndPurgeComponent();
	    quarantineJTabbedPane.addTab(NAME_ALL_ACCOUNTS, null, quarantineAllComponent);
	    addRefreshable(NAME_ALL_ACCOUNTS, (Refreshable) quarantineAllComponent);

	    // QUARANTINABLE ADDRESSES //////
	    Component quarantinableAddressesComponent = emailCompoundSettings.getQuarantinableAddressesComponent();
	    quarantineJTabbedPane.addTab(NAME_QUARANTINABLE_ADDRESSES, null, quarantinableAddressesComponent);
	    addSavable(NAME_QUARANTINABLE_ADDRESSES, (Savable) quarantinableAddressesComponent);
	    addRefreshable(NAME_QUARANTINABLE_ADDRESSES, (Refreshable) quarantinableAddressesComponent);

	    // QUARANTINABLE FORWARDS //////
	    Component quarantinableForwardsComponent = emailCompoundSettings.getQuarantinableForwardsComponent();
	    quarantineJTabbedPane.addTab(NAME_QUARANTINABLE_FORWARDS, null, quarantinableForwardsComponent);
	    addSavable(NAME_QUARANTINABLE_FORWARDS, (Savable) quarantinableForwardsComponent);
	    addRefreshable(NAME_QUARANTINABLE_FORWARDS, (Refreshable) quarantinableForwardsComponent);

	    // QUARANTINE GENERAL SETTINGS //////
	    Component quarantineGeneralSettingsComponent = emailCompoundSettings.getQuarantineGeneralSettingsComponent();
	    quarantineJTabbedPane.addTab(NAME_GENERAL_SETTINGS, null, quarantineGeneralSettingsComponent);
	    addSavable(NAME_GENERAL_SETTINGS, (Savable) quarantineGeneralSettingsComponent);
	    addRefreshable(NAME_GENERAL_SETTINGS, (Refreshable) quarantineGeneralSettingsComponent);
	}
    }

    protected void refreshAll() throws Exception{
	super.refreshAll();
	if( emailCompoundSettings.getMailTransformCompoundSettings() != null ){
	    emailCompoundSettings.loadSafelists();
	    emailCompoundSettings.loadSafelistCounts();
	    emailCompoundSettings.loadInboxList();
	}

    }
}

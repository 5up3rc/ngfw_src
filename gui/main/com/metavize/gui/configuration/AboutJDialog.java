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

import java.net.URL;
import java.awt.*;
import java.util.*;
import javax.swing.table.*;
import javax.swing.*;

import com.metavize.mvvm.security.*;
import com.metavize.mvvm.*;

import com.metavize.gui.util.StringConstants;
import com.metavize.gui.transform.Refreshable;

public class AboutJDialog extends MConfigJDialog {

    private static final String NAME_ABOUT_CONFIG      = "Setup Info";
    private static final String NAME_ABOUT_INFO        = "Version/Revision";
    private static final String NAME_LICENSE_INFO      = "License Agreement";
    private static final String NAME_REGISTRATION_INFO = "Registration";
    private static final String NAME_TIMEZONE_PANEL    = "Timezone";


    public AboutJDialog( ) {
        this.setTitle(NAME_ABOUT_CONFIG);
	compoundSettings = new AboutCompoundSettings();
    }

    public void generateGui(){        
        // ABOUT /////////////
	AboutAboutJEditorPane aboutAboutJEditorPane = new AboutAboutJEditorPane();
	JScrollPane aboutAboutJScrollPane = addScrollableTab(null, NAME_ABOUT_INFO, null, aboutAboutJEditorPane, false, true);
	aboutAboutJEditorPane.setContainingJScrollPane(aboutAboutJScrollPane);
	addRefreshable(NAME_ABOUT_INFO, aboutAboutJEditorPane);

        // LISCENSE ////////////
	AboutLicenseJEditorPane aboutLicenseJEditorPane = new AboutLicenseJEditorPane();
	JScrollPane aboutLicenseJScrollPane = addScrollableTab(null, NAME_LICENSE_INFO, null, aboutLicenseJEditorPane, false, true);
	aboutLicenseJEditorPane.setContainingJScrollPane(aboutLicenseJScrollPane);
	addRefreshable(NAME_LICENSE_INFO, aboutLicenseJEditorPane);
      
	// REGISTRATION //////////
	AboutRegistrationJPanel aboutRegistrationJPanel = new AboutRegistrationJPanel();
	addScrollableTab(null, NAME_REGISTRATION_INFO, null, aboutRegistrationJPanel, false, true);
	addSavable(NAME_REGISTRATION_INFO, aboutRegistrationJPanel);
	addRefreshable(NAME_REGISTRATION_INFO, aboutRegistrationJPanel);
	
	
	// TIME ZONE //////
        AboutTimezoneJPanel timezoneJPanel = new AboutTimezoneJPanel();
	addScrollableTab(null, NAME_TIMEZONE_PANEL, null, timezoneJPanel, false, true);
	addSavable(NAME_TIMEZONE_PANEL, timezoneJPanel);
	addRefreshable(NAME_TIMEZONE_PANEL, timezoneJPanel);
    }
    
    private class AboutLicenseJEditorPane extends JEditorPane
	implements Refreshable<AboutCompoundSettings> {
	private JScrollPane containingJScrollPane;
	public AboutLicenseJEditorPane(){
	    setEditable(false);
	    setFont(new java.awt.Font("Arial", 0, 11));
	}
	public void setContainingJScrollPane(JScrollPane jScrollPane){
	    containingJScrollPane = jScrollPane;
	}
	public void doRefresh(AboutCompoundSettings aboutCompoundSettings){
	    try{
		setPage(aboutCompoundSettings.getLicenseURL());
	    }
	    catch(Exception e){
		Util.handleExceptionNoRestart("Error setting license", e);
	    }
	    containingJScrollPane.getVerticalScrollBar().setValue(0);
	}
    }

    private class AboutAboutJEditorPane extends JEditorPane
	implements Refreshable<AboutCompoundSettings> {
	private JScrollPane containingJScrollPane;
	public AboutAboutJEditorPane(){
	    setContentType("text/html");
	    setEditable(false);
	    setFont(new java.awt.Font("Arial", 0, 11));
	}
	public void setContainingJScrollPane(JScrollPane jScrollPane){
	    containingJScrollPane = jScrollPane;
	}
	public void doRefresh(AboutCompoundSettings aboutCompoundSettings){
	    String buildString = "<html><b>Build:</b> " + aboutCompoundSettings.getInstalledVersion()
		+ aboutCompoundSettings.getAboutText() + "</html>";
	    try{
		setText(buildString);
	    }
	    catch(Exception e){
		Util.handleExceptionNoRestart("Error setting about info", e);
	    }
	    containingJScrollPane.getVerticalScrollBar().setValue(0);
	}
    }

}

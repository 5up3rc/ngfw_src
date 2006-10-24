/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.gui.configuration;

import com.metavize.gui.util.Util;
import com.metavize.gui.transform.CompoundSettings;
import com.metavize.mvvm.security.RegistrationInfo;
import com.metavize.mvvm.toolbox.MackageDesc;

import java.util.TimeZone;
import java.util.Date;
import java.net.URL;

public class AboutCompoundSettings implements CompoundSettings {

    // REGISTRATION INFO //
    private RegistrationInfo registrationInfo;
    public RegistrationInfo getRegistrationInfo(){ return registrationInfo; }
    public void setRegistrationInfo(RegistrationInfo riIn){ registrationInfo = riIn; }

    // INSTALLED VERISON //
    private String installedVersion;
    public String getInstalledVersion(){ return installedVersion; }

    // LICENSE //
    private URL licenseURL;
    public URL getLicenseURL(){ return licenseURL; }

    // ABOUT //
    private String aboutText = "<br><br><b>Readme:</b> http://www.untanglenetworks.com/egquickstart<br><br><b>Website: </b>http://www.untanglenetworks.com";
    public String getAboutText(){ return aboutText; }
    
    // TIMEZONE //
    private TimeZone timeZone;
    private Date date;
    public TimeZone getTimeZone(){ return timeZone; }
    public void setTimeZone(TimeZone tzIn){ timeZone = tzIn; };
    public Date getDate(){ return date; }
	
    public void save() throws Exception {
	Util.getAdminManager().setRegistrationInfo(registrationInfo);
	Util.getAdminManager().setTimeZone(timeZone);
    }

    public void refresh() throws Exception {
	registrationInfo = Util.getAdminManager().getRegistrationInfo();
	if( registrationInfo == null ){ // for upgrading pre 3.2 boxes
	    registrationInfo = new RegistrationInfo();
	}
	MackageDesc mackageDesc = Util.getToolboxManager().mackageDesc("mvvm");
	if( mackageDesc == null )
	    installedVersion = "unknown";
	else
	    installedVersion = mackageDesc.getInstalledVersion();
	
	licenseURL = Util.getClassLoader().getResource("License.txt");
	timeZone = Util.getAdminManager().getTimeZone();
	date = Util.getAdminManager().getDate();
    }

    public void validate() throws Exception {

    }

}

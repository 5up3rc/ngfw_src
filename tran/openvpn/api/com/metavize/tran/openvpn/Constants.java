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
package com.metavize.tran.openvpn;

import com.metavize.mvvm.tran.Transform;

public class Constants
{
    static final String BUNNICULA_BASE = System.getProperty( "bunnicula.home" );
    static final String BUNNICULA_CONF = System.getProperty( "bunnicula.conf.dir" );
    
    static final String SCRIPT_DIR     = BUNNICULA_BASE + "/openvpn";
    static final String DATA_DIR       = SCRIPT_DIR;

    /* Base configuration directory, all of the files should go into 
     * one of the sub directories */
    static final String CONF_DIR = BUNNICULA_CONF + "/openvpn";

    /* The directory for all of the client packages, needed by the servlet */
    public static final String PACKAGES_DIR = CONF_DIR + "/client-packages";
    
    /* The PKI infrastructure */
    static final String PKI_DIR = CONF_DIR + "/pki";

    /* Miscellaneous configuration files */
    static final String MISC_DIR = CONF_DIR + "/misc";
    
    /* Triggered when there is a VPN session that is blocked */
    /* XXXXXXX Probably want to log block events */
    static final int BLOCK_COUNTER   = Transform.GENERIC_0_COUNTER;
    
    /* Triggered when there is a VPN session that is passed */
    static final int PASS_COUNTER    = Transform.GENERIC_1_COUNTER;

    /* Triggered whenever a client connects to the VPN */
    static final int CONNECT_COUNTER = Transform.GENERIC_2_COUNTER;

    /* Error codes from scripts */

    /* Unable to download the client */
    static final int DOWNLOAD_ERROR_CODE = 250;

    /* Unable to start the openvpn server, may just be the other end is 
     * not started */
    static final int START_ERROR         = 251;

    /* Error reading from the usb device */
    static final int USB_ERROR_CODE      = 252;

}

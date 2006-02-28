/*
 * Copyright (c) 2003, 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id$
 */

package com.metavize.mvvm.networking.internal;

import java.io.Serializable;

import com.metavize.mvvm.networking.NetworkUtil;
import com.metavize.mvvm.networking.RemoteSettings;
import com.metavize.mvvm.networking.RemoteSettingsImpl;

import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.tran.Equivalence;
import com.metavize.mvvm.tran.Validatable;
import com.metavize.mvvm.tran.ValidateException;

public class RemoteInternalSettings
{
    // !!!!! private static final long serialVersionUID = 172494253701617361L;
    
    /**
     * True if SSH remote debugging is enabled.
     */
    private final boolean isSshEnabled;

    /**
     * True if exception emails are to be emailed
     */
    private final boolean isExceptionReportingEnabled;
    
    /**
     * True if TCP Window Scaling is enabled.
     * disabled by default.
     * See: http://oss.sgi.com/archives/netdev/2004-07/msg00121.html or bug 163
     */
    private final boolean isTcpWindowScalingEnabled;

    private final boolean isInsideInsecureEnabled;
    private final boolean isOutsideAccessEnabled;
    private final boolean isOutsideAccessRestricted;

    private final IPaddr outsideNetwork;
    private final IPaddr outsideNetmask;
    
    /* This is a script that gets executed after the bridge configuration runs */
    private final String postConfigurationScript;
    
    /* These are the values that are stored in the database */
    private final String hostname;

    /* Only really used to persist the state */
    private final boolean isPublicAddressEnabled;
    private final IPaddr publicAddress;
    private final int    publicPort;
    private final boolean isHostnamePublic;
    private final int publicHttpsPort;
    
    /* These are the computed values, and change with time */
    private final String currentPublicAddress;
        
    public RemoteInternalSettings( RemoteSettings remote, String realPublicAddress )
    {
        this.isSshEnabled  = remote.isSshEnabled();
        this.isExceptionReportingEnabled = remote.isExceptionReportingEnabled();
        this.isTcpWindowScalingEnabled = remote.isTcpWindowScalingEnabled();
        this.isInsideInsecureEnabled = remote.isInsideInsecureEnabled();
        this.isOutsideAccessEnabled = remote.isOutsideAccessEnabled();
        this.isOutsideAccessRestricted = remote.isOutsideAccessRestricted();
        this.outsideNetwork = remote.outsideNetwork();
        this.outsideNetmask = remote.outsideNetmask();
        this.postConfigurationScript = remote.getPostConfigurationScript();
        this.hostname       = remote.getHostname();
        this.isPublicAddressEnabled = remote.getIsPublicAddressEnabled();
        this.publicAddress  = remote.getPublicIPaddr();
        this.publicPort     = remote.getPublicPort();
        this.publicHttpsPort = remote.httpsPort();
        this.currentPublicAddress = realPublicAddress;
        this.isHostnamePublic = remote.getIsHostnamePublic();
    }

    /* Set the post configuration script */
    public String getPostConfigurationScript()
    {
        return this.postConfigurationScript;
    }
    
    public boolean isSshEnabled()
    {
        return this.isSshEnabled;
    }

    public boolean isExceptionReportingEnabled()
    {
        return this.isExceptionReportingEnabled;
    }
    
    public boolean isTcpWindowScalingEnabled()
    {
        return isTcpWindowScalingEnabled;
    }

    public boolean isInsideInsecureEnabled()
    {
        return isInsideInsecureEnabled;
    }

    public boolean isOutsideAccessEnabled()
    {
        return isOutsideAccessEnabled;
    }

    public boolean isOutsideAccessRestricted()
    {
        return isOutsideAccessRestricted;
    }

    /**
     * The netmask of the network/host that is allowed to administer the box from outside
     * This is ignored if outside access is not enabled, null for just
     * one host.
     */

    public IPaddr outsideNetwork()
    {
        return this.outsideNetwork;
    }

    public IPaddr outsideNetmask()
    {
        return this.outsideNetmask;
    }

    public int getPublicHttpsPort()
    {
        return this.publicHttpsPort;
    }

    /** The hostname for the box(this is the hostname that goes into certificates). */
    public String getHostname()
    {
        return this.hostname;
    }

    public boolean getIsHostnamePublic()
    {
        return this.isHostnamePublic;
    }

    public boolean getIsPublicAddressEnabled()
    {
        return this.isPublicAddressEnabled;
    }

    /** @return the public url for the box, this is the address (may be hostname or ip address) */
    public String getPublicAddress()
    {
        return NetworkUtil.getInstance().generatePublicAddress( getPublicIPaddr(), getPublicPort());
    }      

    /** @return the public url for the box, this is the address (may be hostname or ip address) */
    public IPaddr getPublicIPaddr()
    {
        return this.publicAddress;
    }

    public int getPublicPort()
    {
        return this.publicPort;
    }
  
    public String getCurrentPublicAddress()
    {
        return this.currentPublicAddress;
    }
    
    public RemoteSettingsImpl toSettings()
    {
        RemoteSettingsImpl rs = new RemoteSettingsImpl();
        
        rs.isSshEnabled( isSshEnabled());
        rs.isExceptionReportingEnabled( isExceptionReportingEnabled());
        rs.isTcpWindowScalingEnabled( isTcpWindowScalingEnabled());
        rs.isInsideInsecureEnabled( isInsideInsecureEnabled());
        rs.isOutsideAccessEnabled( isOutsideAccessEnabled());
        rs.isOutsideAccessRestricted( isOutsideAccessRestricted());
        rs.outsideNetwork( outsideNetwork());
        rs.outsideNetmask( outsideNetmask());
        rs.setPostConfigurationScript( getPostConfigurationScript());
        rs.setHostname( getHostname());
        rs.setIsPublicAddressEnabled( getIsPublicAddressEnabled());
        rs.setPublicIPaddr( getPublicIPaddr());
        rs.setPublicPort( getPublicPort());
        rs.httpsPort( getPublicHttpsPort());
        rs.setIsHostnamePublic( getIsHostnamePublic());

        return rs;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "script:      " + getPostConfigurationScript());               
        sb.append( "\nssh:         " + isSshEnabled());
        sb.append( "\nexceptions:  " + isExceptionReportingEnabled());
        sb.append( "\ntcp window:  " + isTcpWindowScalingEnabled());
        sb.append( "\ninside-in:   " + isInsideInsecureEnabled());
        sb.append( "\noutside:     " + isOutsideAccessEnabled()); 
        sb.append( "\nrestriced:   " + isOutsideAccessRestricted());
        sb.append( "\nrestriction: " + outsideNetwork() + "/" + outsideNetmask());
        sb.append( "\nHTTPS:       " + getPublicHttpsPort());
        sb.append( "\npublic:      " + getIsPublicAddressEnabled() + " " + getCurrentPublicAddress() + 
                   " / " + getPublicAddress());
        sb.append( "\nhostname:    " + getHostname());

        return sb.toString();
    }
}

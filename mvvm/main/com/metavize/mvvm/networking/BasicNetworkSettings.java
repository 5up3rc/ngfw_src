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

package com.metavize.mvvm.networking;

import java.util.List;

import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.InterfaceAlias;

public interface BasicNetworkSettings
{
    /* Get if DHCP is enabled */
    public boolean isDhcpEnabled();
    
    /* Set if DHCP is enabled */
    public void isDhcpEnabled( boolean newValue );

    /* Get the hostname of the box */
    public String hostname();

    /* Set the hostname of the box */
    public void hostname( String newValue );

    /* Get the address of the box */
    public IPaddr host();
    
    /* Set the address of the box */
    public void host( IPaddr newValue );

    /* Get the netmask of the box */
    public IPaddr netmask();
    
    /* Set the netmask of the box */
    public void netmask( IPaddr newValue );

    /* Get the gateway of the box */
    public IPaddr gateway();
    
    /* Set the gateway of the box */
    public void gateway( IPaddr newValue );

    /* Get the dns1 of the box */
    public IPaddr dns1();
    
    /* Set the dns1 of the box */
    public void dns1( IPaddr newValue );
    
    /* Get the dns2 of the box */
    public IPaddr dns2();
    
    /* Set the dns2 of the box */
    public void dns2( IPaddr newValue );

    /* Get whether or not the configuration includes a second DNS setting */
    public boolean hasDns2();

    /* Get the list of aliases */
    public List<InterfaceAlias> getAliasList();

    /* Set the list of aliases */
    public void setAliasList( List<InterfaceAlias> newValue );
}

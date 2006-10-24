/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id: NetworkManagerImpl.java 6455 2006-07-15 20:35:04Z rbscott $
 */

package com.metavize.mvvm.networking;

import org.apache.log4j.Logger;

import jcifs.Config;
import jcifs.netbios.NbtAddress;

import com.metavize.mvvm.tran.IPaddr;

import com.metavize.mvvm.networking.internal.NetworkSpaceInternal;
import com.metavize.mvvm.networking.internal.NetworkSpacesInternalSettings;


/* XXX Each of the listeners should be moved into their own files, or perhaps into a separate */
class CifsListener implements NetworkSettingsListener
{
    private final Logger logger = Logger.getLogger(getClass());

    private static final String BROADCAST_PROPERTY    = "jcifs.netbios.baddr";
    private static final String SMB_BIND_PROPERTY     = "jcifs.smb.client.laddr";
    private static final String NETBIOS_BIND_PROPERTY = "jcifs.netbios.laddr";
    
    public void event( NetworkSpacesInternalSettings settings )
    {     
        NetworkSpaceInternal internal = settings.getServiceSpace();
        
        if ( internal == null ) return;
        
        IPNetwork network = internal.getPrimaryAddress();
        
        IPaddr address   = network.getNetwork();
        IPaddr netmask   = network.getNetmask();
        IPaddr broadcast = address.or( netmask.inverse());
                
        Config.setProperty( BROADCAST_PROPERTY,    broadcast.toString());
        Config.setProperty( SMB_BIND_PROPERTY,     address.toString());
        Config.setProperty( NETBIOS_BIND_PROPERTY, address.toString());

        NbtAddress.updateLocalAddress();
    }
}

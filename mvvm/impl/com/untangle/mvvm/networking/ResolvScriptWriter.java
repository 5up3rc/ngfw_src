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

package com.untangle.mvvm.networking;

import java.net.UnknownHostException;

import com.untangle.mvvm.networking.internal.NetworkSpacesInternalSettings;
import com.untangle.mvvm.tran.IPaddr;
import com.untangle.mvvm.tran.ParseException;
import com.untangle.mvvm.tran.script.ScriptWriter;
import org.apache.log4j.Logger;

import static com.untangle.mvvm.tran.script.ScriptWriter.COMMENT;
import static com.untangle.mvvm.tran.script.ScriptWriter.METAVIZE_HEADER;

class ResolvScriptWriter extends ScriptWriter
{
    private static final IPaddr LOCALHOST;

    private final Logger logger = Logger.getLogger(getClass());

    static final String NS_PARAM = "nameserver";

    private static final String RESOLV_HEADER =
        COMMENT + METAVIZE_HEADER +
        COMMENT + " name resolution settings.\n";

    private final NetworkSpacesInternalSettings settings;

    ResolvScriptWriter( NetworkSpacesInternalSettings settings )
    {
        super();
        this.settings = settings;
    }

    void addNetworkSettings()
    {
        // ???? Just always write the file, just in case
        // if ( this.settings.isDhcpEnabled()) return;
        
        /* insert localhost */
        addDns( LOCALHOST );
        addDns( settings.getDns1());
        addDns( settings.getDns2());
    }

    private void addDns( IPaddr dns )
    {
        if ( dns == null || dns.isEmpty()) return;

        appendVariable( NS_PARAM, dns.toString());
    }

    /**
     * In the /etc/resolv.conf file a variable is just separated by a space
     */
    @Override
    public void appendVariable( String variable, String value )
    {
        if ( variable == null || value == null ) {
            logger.warn( "Variable["  + variable + "] or value["+ value + "] is null" );
            return;
        }

        variable = variable.trim();
        value    = value.trim();

        appendLine( variable + " " + value );
    }

    @Override
    protected String header()
    {
        return RESOLV_HEADER;
    }

    static 
    {
        IPaddr addr = null;
        try {
            addr = IPaddr.parse( "127.0.0.1" );
        } catch ( ParseException e ) {
            System.out.println( "unable to parse localhost, not binding to local dns server" );
            addr = null;
        } catch ( UnknownHostException e ) {
            System.out.println( "unable to parse localhost, not binding to local dns server" );
            addr = null;
        }

        LOCALHOST = addr;

    }
}

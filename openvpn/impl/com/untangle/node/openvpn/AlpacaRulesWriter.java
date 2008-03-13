/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.untangle.node.openvpn;

import java.util.List;

import com.untangle.uvm.node.script.ScriptWriter;
import org.apache.log4j.Logger;

import static com.untangle.uvm.node.script.ScriptWriter.COMMENT;
import static com.untangle.uvm.node.script.ScriptWriter.UNTANGLE_HEADER;

class AlpacaRulesWriter extends ScriptWriter
{
    private final Logger logger = Logger.getLogger(getClass());

    private static final String RULES_HEADER =
        COMMENT + UNTANGLE_HEADER + "\n" +
        COMMENT + " VPN Access Packet Filter Rules\n\n";

    AlpacaRulesWriter()
    {
        super();
    }

    void appendExportedAddresses( List<ServerSiteNetwork> exportedAddressList )
    {
        for ( ServerSiteNetwork n : exportedAddressList ) {
            appendLine( "insert_vpn_export " + n.getNetwork() + " " + n.getNetmask());
        }
    }

    protected String header()
    {
        return RULES_HEADER;
    }    
}

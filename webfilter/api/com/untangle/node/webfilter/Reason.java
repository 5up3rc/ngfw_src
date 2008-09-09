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

package com.untangle.node.webfilter;


/**
 * Reason a Request or Response was blocked.
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
public enum Reason
{
    BLOCK_CATEGORY('D', "in Categories Block list"),
    BLOCK_URL('U', "in URLs Block list"),
    BLOCK_EXTENSION('E', "in File Extensions Block list"),
    BLOCK_MIME('M', "in MIME Types Block list"),
    BLOCK_ALL('A', "blocking all traffic"),
    BLOCK_IP_HOST('H', "hostname is an IP address"),
    PASS_URL('I', "in URLs Pass list"),
    PASS_CLIENT('C', "in Clients Pass list"),
    
    /**
     * None is to help the GUI deal with the concept of none. Don't
     * log this to the database, I know where you live.
     */
    DEFAULT('N', "no rule applied");
    
    private static final long serialVersionUID = -1388743204136725990L;


    private final char key;
    private final String reason;

    private Reason(char key, String reason)
    {
        this.key = key;
        this.reason = reason;
    }

    public char getKey(){
        return key;
    }

	public String getReason() {
		return reason;
	}

    public static Reason getInstance(char key)
    {
    	Reason[] values = values();
    	for (int i = 0; i < values.length; i++) {
    		if (values[i].getKey() == key){
    			return values[i];
    		}
		}
    	return null;
    }

}

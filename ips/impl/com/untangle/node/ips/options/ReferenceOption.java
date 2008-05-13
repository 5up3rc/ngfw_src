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

package com.untangle.node.ips.options;

import java.util.regex.*;

import com.untangle.node.ips.IPSRuleSignatureImpl;

/**
 * This class matches the reference option found in snort based rule
 * signatures.
 *
 * @Author Nick Childers
 */
public class ReferenceOption extends IPSOption {
    private static final Pattern URLP = Pattern.compile("url,", Pattern.CASE_INSENSITIVE);

    public ReferenceOption(IPSRuleSignatureImpl signature, String params) {
        super(signature, params);

        Matcher urlm = URLP.matcher(params);
        if (true == urlm.find()) {
            String url = "http://" + params.substring(urlm.end()).trim();
            signature.setURL(url);
        }
    }
}

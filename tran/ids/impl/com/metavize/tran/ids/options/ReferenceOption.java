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

package com.metavize.tran.ids.options;

import java.util.regex.*;

import com.metavize.tran.ids.IDSRuleSignature;

/**
 * This class matches the reference option found in snort based rule
 * signatures.
 *
 * @Author Nick Childers
 */
public class ReferenceOption extends IDSOption {
    private static final Pattern URLP = Pattern.compile("url,", Pattern.CASE_INSENSITIVE);

    public ReferenceOption(IDSRuleSignature signature, String params) {
        super(signature, params);

        Matcher urlm = URLP.matcher(params);
        if (true == urlm.find()) {
            String url = "http://" + params.substring(urlm.end()).trim();
            signature.setURL(url);
        }
    }
}

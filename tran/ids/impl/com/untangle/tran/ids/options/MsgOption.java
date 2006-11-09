/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.ids.options;

import java.util.regex.*;

import com.untangle.mvvm.tapi.event.*;
import com.untangle.tran.ids.IDSRuleSignature;

public class MsgOption extends IDSOption {

    private static final String BLEEDING_PREFIX = "BLEEDING-EDGE";

    public MsgOption(IDSRuleSignature signature, String params) {
        super(signature, params);
        // reomve useless 'BLEEDING-EDGE' prefix
        if (params.length() > BLEEDING_PREFIX.length()) {
            String beginParams = params.substring(0, BLEEDING_PREFIX.length());
            if (beginParams.equalsIgnoreCase(BLEEDING_PREFIX))
                params = params.substring(BLEEDING_PREFIX.length()).trim();
        }
        signature.setMessage(params);
    }
}

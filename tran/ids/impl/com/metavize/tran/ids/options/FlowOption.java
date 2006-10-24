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

import com.metavize.mvvm.tapi.event.*;
import com.metavize.tran.ids.IDSRuleSignature;
import com.metavize.tran.ids.IDSSessionInfo;

public class FlowOption extends IDSOption {

    /**
     * The options "only_stream" and "established" would  have *no* effect.
     * So I ignore them.
     */

    private static Pattern noStream = Pattern.compile("no_stream",Pattern.CASE_INSENSITIVE);
    private static Pattern[] validParams = {
        Pattern.compile("from_server", Pattern.CASE_INSENSITIVE),
        Pattern.compile("from_client", Pattern.CASE_INSENSITIVE),
        Pattern.compile("to_client", Pattern.CASE_INSENSITIVE),
        Pattern.compile("to_server", Pattern.CASE_INSENSITIVE)
    };

    private boolean matchFromServer = false;

    public FlowOption(IDSRuleSignature signature, String params) {
        super(signature, params);
        if(noStream.matcher(params).find()) {
            signature.remove(true);
        }

        for(int i=0; i < validParams.length; i++) {
            if(validParams[i].matcher(params).find())
                matchFromServer = (i%2 == 0);
        }
    }

    public boolean runnable() {
        return true;
    }

    public boolean run(IDSSessionInfo sessionInfo) {
        boolean fromServer = sessionInfo.isServer();
        boolean returnValue = !(fromServer ^ matchFromServer);
        return (negationFlag ^ returnValue);
    }
}

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

import com.metavize.tran.ids.IDSRuleSignature;
import org.apache.log4j.Logger;

public class NocaseOption extends IDSOption {

    private final Logger logger = Logger.getLogger(getClass());

    public NocaseOption(IDSRuleSignature signature, String params) {
        super(signature, params);
        String[] parents = new String [] { "ContentOption", "UricontentOption" };
        IDSOption option = signature.getOption(parents, this);
        if(option == null) {
            logger.warn("Unable to find content option to set nocase for sig: " + signature.rule().getText());
            return;
        }

        if (option instanceof ContentOption) {
            ContentOption content = (ContentOption) option;
            content.setNoCase();
        } else if (option instanceof UricontentOption) {
            UricontentOption uricontent = (UricontentOption) option;
            uricontent.setNoCase();
        }
    }
}

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

package com.untangle.gui.pipeline;

import com.untangle.gui.util.Util;
import com.untangle.gui.transform.CompoundSettings;
import com.untangle.mvvm.policy.PolicyConfiguration;
import com.untangle.mvvm.IntfEnum;

public class PolicyCompoundSettings implements CompoundSettings {

    // POLICY CONFIGURATION //
    private PolicyConfiguration policyConfiguration;
    public PolicyConfiguration getPolicyConfiguration(){ return policyConfiguration; }

    // INTF ENUM //
    private IntfEnum intfEnum;
    public IntfEnum getIntfEnum(){ return intfEnum; }

    public void save() throws Exception {
	Util.getPolicyManager().setPolicyConfiguration(policyConfiguration);
    }

    public void refresh() throws Exception {
	policyConfiguration = Util.getPolicyManager().getPolicyConfiguration();
	intfEnum = Util.getIntfManager().getIntfEnum();
    }

    public void validate() throws Exception {

    }

}

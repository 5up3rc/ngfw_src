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

package com.untangle.mvvm.localapi;

import com.untangle.mvvm.api.IPSessionDesc;

import com.untangle.mvvm.policy.Policy;

public interface SessionMatcher
{
    /**
     * Tells if the session matches */
    boolean isMatch( Policy policy, IPSessionDesc clientSide, IPSessionDesc serverSide );
}

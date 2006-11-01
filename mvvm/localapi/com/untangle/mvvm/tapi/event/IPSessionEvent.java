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

package com.untangle.mvvm.tapi.event;

import com.untangle.mvvm.tapi.MPipe;
import com.untangle.mvvm.tapi.IPSession;

public class IPSessionEvent extends SessionEvent {

    public IPSessionEvent(MPipe mPipe, IPSession session)
    {
        super(mPipe, session);
    }

    public IPSession ipsession()
    {
        return (IPSession)getSource();
    }

}

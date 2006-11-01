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
package com.untangle.tran.firewall;

import com.untangle.mvvm.logging.EventManager;
import com.untangle.mvvm.tran.Transform;

public interface Firewall extends Transform
{
    FirewallSettings getFirewallSettings();
    void setFirewallSettings( FirewallSettings settings );

    EventManager<FirewallEvent> getEventManager();
}

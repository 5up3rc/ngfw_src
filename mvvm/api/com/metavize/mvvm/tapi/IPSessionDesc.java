/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.tapi;

import java.net.InetAddress;
import com.metavize.mvvm.api.SessionEndpoints;

public interface IPSessionDesc extends com.metavize.mvvm.api.IPSessionDesc, SessionDesc, SessionEndpoints
{
    /**
     * Sessions are inbound when the inbound side of the policy is selected.  This is decided
     * at welding time and is no longer dependent only on the client/server interfaces.
     *
     * @return true if the session is inbound, false if it is outbound
     */
    boolean isInbound();

    /**
     * Sessions are outbound when the outbound side of the policy is selected.  This is decided
     * at welding time and is no longer dependent only on the client/server interfaces.  This is
     * the inverse of <code>isInbound</code>
     *
     * @return true if the session is outbound, false if it is inbound
     */
    boolean isOutbound();

    /**
     * IP clients and servers have a state of <code>CLOSED</code> when both the input and
     * output sides are dead.
     *
     */
    static final byte CLOSED = 0;
    static final byte EXPIRED = 0;

    /**
     * IP client and servers have a state of <code>OPEN</code> when both the input and output
     * sides are alive.
     */
    static final byte OPEN = 4;

    byte clientState();
    byte serverState();

    // boolean isClientClosed();
    // boolean isServerClosed();

}

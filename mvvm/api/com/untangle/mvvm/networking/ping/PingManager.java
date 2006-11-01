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

package com.untangle.mvvm.networking.ping;

import com.untangle.mvvm.networking.NetworkException;
import com.untangle.mvvm.tran.ValidateException;

public interface PingManager
{
    /* Both of the commands take strings, and SHOULD NOT take
     * InetAddresses since the name lookup should be performed on the
     * server side, not on the client side */

    /* Issue a ping and return the results, this is a blocking call. */
    public PingResult ping( String addressString ) throws ValidateException, NetworkException;
    
    /* Issue count pings and return the results, this is a blocking call. */
    public PingResult ping( String addressString, int count ) throws ValidateException, NetworkException;
}

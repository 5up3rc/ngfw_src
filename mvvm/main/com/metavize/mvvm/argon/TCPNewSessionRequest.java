/*
 * Copyright (c) 2003 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id: TCPNewSessionRequest.java,v 1.2 2005/01/06 01:01:29 jdi Exp $
 */

package com.metavize.mvvm.argon;

import com.metavize.jnetcap.*;

public interface TCPNewSessionRequest extends IPNewSessionRequest, TCPSessionDesc
{
    /**
     * <code>acked</code> returns true if the new session has already been ACKed to the client.
     * This occurs when the SYN shield has been activated.</p>
     *
     * If false, the SYN has not yet been ACKed.  In this case, the option to
     * <code>rejectReturnRst</code> is still available and if used will look to the client
     * as if no server was listening on that port.</p>
     *
     * @return True if the session was acked, false otherwise.
     */
    boolean acked();
    
    /**
     * <code>rejectReturnRst</code> rejects the new connection and sends a RST to the client.
     * Note that if <code>acked</code> is true, then a simple close is done instead.
     */
    void rejectReturnRst();
}

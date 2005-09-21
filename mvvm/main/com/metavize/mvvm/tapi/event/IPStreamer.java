/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.tapi.event;

import java.nio.ByteBuffer;

public interface IPStreamer
{
    // void aborted(Throwable x);

    /**
     * <code>closeWhenDone</code> is called after EOF is reached.  If it returns true,
     * then a FIN is sent at the end, beginning the closing process.  If it returns false,
     * no FIN is sent and the transform resumes normal event handling.
     *
     * XXX -- we need an event to let them know they've returned from streaming mode.
     *
     * @return a <code>boolean</code> true if a FIN should be sent at the end of the stream.
     */
    boolean closeWhenDone();
}

/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.jvector;

public interface SinkEndpointListener
{
    /**
     * An event that occurs each time the endpoint transmits a part
     * of a crumb.
     * @param sink - The sink that triggered the event.
     * @param numBytes - The number of bytes transmitted.
     */
    void dataEvent( Sink sink, int numBytes );


    /**
     * An event that occurs when the source or sink shuts down
     * @param sink - The sink that triggered the event.
     */
    void shutdownEvent( Sink sink );
}

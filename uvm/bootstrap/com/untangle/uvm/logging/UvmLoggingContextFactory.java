/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id: UvmRepositorySelector.java 8515 2007-01-03 00:13:24Z amread $
 */

package com.untangle.uvm.logging;

/**
 * Factory for retrieving the current logging context. Allows for
 * access to the {@link UvmLoggingContext} on demand. Returns the
 * {@link UvmLoggingContext} in effect at the time the {@link #get()}
 * method is called. Thus, it is only guaranteed to work if called
 * syncronously from the thread that accesses {@link #get()}.
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
public interface UvmLoggingContextFactory
{
    /**
     * Returns the {@link UvmLoggingContext} in effect for this
     * thread at the moment the get() method is called.
     *
     * @return the effective {@link UvmLoggingContext}.
     */
    UvmLoggingContext get();
}

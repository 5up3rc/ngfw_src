/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.user;

public interface Assistant
{
    /* Lookup user information about a session filling in as much of
     * the user information as possible.
     */
    public void lookup( UserInfo info );

    /* retrieve the priority of this assistant, higher numbers are lower priority */
    public int priority();
}


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

package com.metavize.mvvm.logging;

public abstract class StatisticEvent extends LogEvent
{
    /**
     * Return true if the current event has any interesting statistics that should be logged
     * now.  For instance, if you are tracking deltas, and all the deltas are zero, this should
     * return false;
     */
    public abstract boolean hasStatistics();
}

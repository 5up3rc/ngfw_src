/*
 * Copyright (c) 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.toolbox;

import java.io.Serializable;

public class InstallTimeout implements InstallProgress, Serializable
{
    // XXX serial UID

    private final long time;

    public InstallTimeout(long time)
    {
        this.time = time;
    }

    // accessors --------------------------------------------------------------

    public long getTime()
    {
        return time;
    }

    // InstallProgress methods ------------------------------------------------

    public void accept(ProgressVisitor visitor)
    {
        visitor.visitInstallTimeout(this);
    }
}

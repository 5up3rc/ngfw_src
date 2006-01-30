/*
 * Copyright (c) 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm;

public class MackageInstallRequest extends ToolboxMessage
{
    private final String mackageName;

    public MackageInstallRequest(String mackageName)
    {
        this.mackageName = mackageName;
    }

    public String getMackageName()
    {
        return mackageName;
    }

    // ToolboxMessage methods -------------------------------------------------

    public void accept(ToolboxMessageVisitor v)
    {
        v.visitMackageInstallRequest(this);
    }
}

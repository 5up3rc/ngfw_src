/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.virus;

import com.untangle.mvvm.logging.RepositoryDesc;
import com.untangle.mvvm.logging.SimpleEventFilter;

public class VirusHttpFilter implements SimpleEventFilter<VirusEvent>
{
    private static final RepositoryDesc REPO_DESC = new RepositoryDesc("HTTP Events");

    private final String warmQuery;

    // constructors -----------------------------------------------------------

    VirusHttpFilter(String vendorName)
    {
        warmQuery = "FROM VirusHttpEvent evt "
        + "WHERE evt.vendorName = '" + vendorName + "' "
        + "AND evt.requestLine.pipelineEndpoints.policy = :policy "
        + "ORDER BY evt.timeStamp DESC";
    }

    // SimpleEventFilter methods ----------------------------------------------

    public RepositoryDesc getRepositoryDesc()
    {
        return REPO_DESC;
    }

    public String[] getQueries()
    {
        return new String[] { warmQuery };
    }

    public boolean accept(VirusEvent e)
    {
        return e instanceof VirusHttpEvent;
    }
}

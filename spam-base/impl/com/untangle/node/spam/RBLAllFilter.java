/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.node.spam;

import com.untangle.uvm.logging.RepositoryDesc;
import com.untangle.uvm.logging.SimpleEventFilter;

public class RBLAllFilter implements SimpleEventFilter<SpamSMTPRBLEvent>
{
    private static final RepositoryDesc REPO_DESC = new RepositoryDesc("All Events");

    private final String rblQuery;

    // constructors -----------------------------------------------------------

    public RBLAllFilter()
    {
        rblQuery = "FROM SpamSMTPRBLEvent evt WHERE evt.pipelineEndpoints.policy = :policy ORDER BY evt.timeStamp DESC";
    }

    // SimpleEventFilter methods ----------------------------------------------

    public RepositoryDesc getRepositoryDesc()
    {
        return REPO_DESC;
    }

    public String[] getQueries()
    {
        return new String[] { rblQuery };
    }

    public boolean accept(SpamSMTPRBLEvent e)
    {
        return true;
    }
}

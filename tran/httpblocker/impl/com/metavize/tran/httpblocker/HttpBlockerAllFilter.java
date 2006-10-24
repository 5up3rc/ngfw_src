/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.httpblocker;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.metavize.mvvm.logging.ListEventFilter;
import com.metavize.mvvm.logging.RepositoryDesc;
import com.metavize.tran.http.RequestLine;
import org.hibernate.Query;
import org.hibernate.Session;

public class HttpBlockerAllFilter implements ListEventFilter<HttpBlockerEvent>
{
    private static final String RL_QUERY = "FROM RequestLine rl ORDER BY rl.httpRequestEvent.timeStamp DESC";
    private static final String EVT_QUERY = "FROM HttpBlockerEvent evt WHERE evt.requestLine = :requestLine";

    private static final RepositoryDesc REPO_DESC
        = new RepositoryDesc("All HTTP Traffic");

    public RepositoryDesc getRepositoryDesc()
    {
        return REPO_DESC;
    }

    public boolean accept(HttpBlockerEvent e)
    {
        return true;
    }

    public void warm(Session s, List<HttpBlockerEvent> l, int limit,
                     Map<String, Object> params)
    {
        Query q = s.createQuery(RL_QUERY);
        for (String param : q.getNamedParameters()) {
            Object o = params.get(param);
            if (null != o) {
                q.setParameter(param, o);
            }
        }

        q.setMaxResults(limit);

        int c = 0;
        for (Iterator i = q.iterate(); i.hasNext() && ++c < limit; ) {
            RequestLine rl = (RequestLine)i.next();
            Query evtQ = s.createQuery(EVT_QUERY);
            evtQ.setEntity("requestLine", rl);
            HttpBlockerEvent evt = (HttpBlockerEvent)evtQ.uniqueResult();
            if (null == evt) {
                evt = new HttpBlockerEvent(rl, null, null, null, true);
            }

            l.add(evt);
        }
    }
}

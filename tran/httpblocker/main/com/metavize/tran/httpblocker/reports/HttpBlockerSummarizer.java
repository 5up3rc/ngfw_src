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

package com.metavize.tran.httpblocker.reports;

import java.sql.*;

import com.metavize.mvvm.reporting.BaseSummarizer;
import com.metavize.mvvm.reporting.Util;
import org.apache.log4j.Logger;

public class HttpBlockerSummarizer extends BaseSummarizer {

    private final Logger logger = Logger.getLogger(getClass());

    public HttpBlockerSummarizer() { }

    public String getSummaryHtml(Connection conn, Timestamp startDate, Timestamp endDate)
    {
        long hitCount = 0l;
        long blockCount = 0l;
        long webTraffic = 0l; // filtered web traffic (to other transforms)

        try {
        String sql;
        PreparedStatement ps;
        ResultSet rs;

            sql = "SELECT COUNT(*) FROM tr_http_evt_req WHERE time_stamp >= ? AND time_stamp < ?";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            rs = ps.executeQuery();
            rs.first();
            hitCount = rs.getLong(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_httpblk_evt_blk WHERE time_stamp >= ? AND time_stamp < ?";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            rs = ps.executeQuery();
            rs.first();
            blockCount = rs.getLong(1);
            rs.close();
            ps.close();

            sql = "SELECT SUM(p2s_bytes) FROM tr_http_evt_req AS evt, tr_http_req_line AS line, pl_stats AS stats WHERE evt.time_stamp >= ? AND evt.time_stamp < ? AND evt.request_id = line.request_id AND line.pl_endp_id = stats.pl_endp_id";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            rs = ps.executeQuery();
            rs.first();
            webTraffic = rs.getLong(1);
            rs.close();
            ps.close();
        } catch (SQLException exn) {
            logger.warn("could not summarize", exn);
        }

        addEntry("Filtered web traffic", Util.trimNumber("Bytes",webTraffic));

        addEntry("&nbsp;", "&nbsp;");

        addEntry("Scanned domain visits", Util.trimNumber("",hitCount));
        addEntry("&nbsp;&nbsp;&nbsp;Blocked domain visits", Util.trimNumber("",blockCount), Util.percentNumber(blockCount,hitCount));
        addEntry("&nbsp;&nbsp;&nbsp;Passed domain visits", Util.trimNumber("",hitCount-blockCount), Util.percentNumber(hitCount-blockCount,hitCount));

        // XXXX
        String tranName = "Web Content Control";

        return summarizeEntries(tranName);
    }
}

/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.virus.reports;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import com.metavize.tran.virus.VirusScanner;
import com.metavize.mvvm.reporting.BaseSummarizer;
import com.metavize.mvvm.reporting.Util;
import org.apache.log4j.Logger;

public class VirusSummarizer extends BaseSummarizer {

    private static final Logger logger = Logger.getLogger(VirusSummarizer.class);

    public VirusSummarizer() { }

    public String getSummaryHtml(Connection conn, Timestamp startDate,
                                 Timestamp endDate)
    {
        // XXX shouldn't have this constant here.
        VirusScanner scanner = (VirusScanner) extraParams.get("scanner");

        String virusVendor = scanner.getVendorName();
        logger.info("Virus Vendor: " + virusVendor);

        String sigVersion = scanner.getSigVersion();
        logger.info("Virus Definitions: " + sigVersion);

        int httpScanned = 0;
        int httpBlocked = 0;
        int ftpScanned = 0;
        int ftpBlocked = 0;
	int emailScanned = 0;
	int emailBlocked = 0;

        try {
            String sql;
	    PreparedStatement ps;
	    ResultSet rs;

	    sql = "SELECT COUNT(*) FROM tr_virus_evt_http WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ?";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, virusVendor);
            rs = ps.executeQuery();
            rs.first();
            httpScanned = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_virus_evt_http WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ? AND NOT clean";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, virusVendor);
            rs = ps.executeQuery();
            rs.first();
            httpBlocked = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_virus_evt WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ?";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, virusVendor);
            rs = ps.executeQuery();
            rs.first();
            ftpScanned = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_virus_evt WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ? AND NOT clean";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, virusVendor);
            rs = ps.executeQuery();
            rs.first();
            ftpBlocked = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_virus_evt_mail WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ? AND NOT clean";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, virusVendor);
            rs = ps.executeQuery();
            rs.first();
            emailBlocked = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_virus_evt_smtp WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ? AND NOT clean";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, virusVendor);
            rs = ps.executeQuery();
            rs.first();
            emailBlocked += rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_virus_evt_mail WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ?";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, virusVendor);
            rs = ps.executeQuery();
            rs.first();
            emailScanned = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_virus_evt_smtp WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ?";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, virusVendor);
            rs = ps.executeQuery();
            rs.first();
            emailScanned += rs.getInt(1);
            rs.close();
            ps.close();

            rs.close();
            ps.close();

        } catch (SQLException exn) {
            logger.warn("could not summarize", exn);
        }

        addEntry("Virus Definitions", sigVersion);
        addEntry("&nbsp;","&nbsp;");
        addEntry("Scanned Web downloads", Util.trimNumber("",httpScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Infected & Blocked", Util.trimNumber("",httpBlocked), Util.percentNumber(httpBlocked, httpScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Clean & Passed", Util.trimNumber("",httpScanned-httpBlocked), Util.percentNumber(httpScanned-httpBlocked, httpScanned));

        addEntry("&nbsp;","&nbsp;");

        addEntry("Scanned FTP downloads", Util.trimNumber("",ftpScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Infected & Blocked", Util.trimNumber("",ftpBlocked), Util.percentNumber(ftpBlocked, ftpScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Clean & Passed", Util.trimNumber("",ftpScanned-ftpBlocked), Util.percentNumber(ftpScanned-ftpBlocked, ftpScanned));

        addEntry("&nbsp;","&nbsp;");

        addEntry("Scanned Email downloads", Util.trimNumber("",emailScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Infected & Blocked", Util.trimNumber("",emailBlocked), Util.percentNumber(emailBlocked, emailScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Clean & Passed", Util.trimNumber("",emailScanned-emailBlocked), Util.percentNumber(emailScanned-emailBlocked, emailScanned));

        // XXXX
        String tranName = "Virus";

        return summarizeEntries(tranName);
    }
}

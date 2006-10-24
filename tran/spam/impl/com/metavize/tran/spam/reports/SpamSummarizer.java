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

package com.metavize.tran.spam.reports;

import java.sql.*;

import com.metavize.mvvm.reporting.BaseSummarizer;
import com.metavize.mvvm.reporting.Util;
import org.apache.log4j.Logger;

public class SpamSummarizer extends BaseSummarizer {

    private final Logger logger = Logger.getLogger(getClass());

    public SpamSummarizer() { }

    public String getSummaryHtml(Connection conn, Timestamp startDate, Timestamp endDate)
    {
        // XXX shouldn't have this constant here.
        String spamVendor = (String) extraParams.get("spamVendor");

        logger.info("Spam Vendor: " + spamVendor);

        int smtpScanned = 0;
        int smtpMarked = 0;
        int smtpBlocked = 0;
        int smtpPassed = 0;
        int smtpQuarantined = 0;
        int popimapScanned = 0;
        int popimapMarked = 0;
        int popimapPassed = 0;

        try {
            String sql;
            PreparedStatement ps;
            ResultSet rs;

            sql = "SELECT COUNT(*) FROM tr_spam_evt_smtp WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ?";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, spamVendor);
            rs = ps.executeQuery();
            rs.first();
            smtpScanned = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_spam_evt_smtp WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ? AND is_spam AND action = 'M'";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, spamVendor);
            rs = ps.executeQuery();
            rs.first();
            smtpMarked = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_spam_evt_smtp WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ? AND is_spam AND action = 'P'";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, spamVendor);
            rs = ps.executeQuery();
            rs.first();
            smtpPassed = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_spam_evt_smtp WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ? AND is_spam AND action = 'B'";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, spamVendor);
            rs = ps.executeQuery();
            rs.first();
            smtpBlocked = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_spam_evt_smtp WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ? AND is_spam AND action = 'Q'";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, spamVendor);
            rs = ps.executeQuery();
            rs.first();
            smtpQuarantined = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_spam_evt WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ?";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, spamVendor);
            rs = ps.executeQuery();
            rs.first();
            popimapScanned = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_spam_evt WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ? AND is_spam AND action = 'P'";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, spamVendor);
            rs = ps.executeQuery();
            rs.first();
            popimapPassed = rs.getInt(1);
            rs.close();
            ps.close();

            sql = "SELECT COUNT(*) FROM tr_spam_evt WHERE time_stamp >= ? AND time_stamp < ? AND vendor_name = ? AND is_spam AND action = 'M'";
            ps = conn.prepareStatement(sql);
            ps.setTimestamp(1, startDate);
            ps.setTimestamp(2, endDate);
            ps.setString(3, spamVendor);
            rs = ps.executeQuery();
            rs.first();
            popimapMarked = rs.getInt(1);
            rs.close();
            ps.close();

        } catch (SQLException exn) {
            logger.warn("could not summarize", exn);
        }

        addEntry("Scanned emails (SMTP)", Util.trimNumber("",smtpScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Spam & Quarantined", Util.trimNumber("",smtpQuarantined), Util.percentNumber(smtpQuarantined, smtpScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Spam & Blocked", Util.trimNumber("",smtpBlocked), Util.percentNumber(smtpBlocked, smtpScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Spam & Marked", Util.trimNumber("",smtpMarked), Util.percentNumber(smtpMarked, smtpScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Spam & Passed", Util.trimNumber("",smtpPassed), Util.percentNumber(smtpPassed, smtpScanned));
        int totalSpam = smtpQuarantined + smtpBlocked + smtpMarked + smtpPassed;
        addEntry("&nbsp;&nbsp;&nbsp;Clean & Passed", Util.trimNumber("",smtpScanned-totalSpam), Util.percentNumber(smtpScanned-totalSpam, smtpScanned));

        addEntry("&nbsp;","&nbsp;");

        addEntry("Scanned emails (POP/IMAP)", Util.trimNumber("",popimapScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Spam & Marked", Util.trimNumber("",popimapMarked), Util.percentNumber(popimapMarked, popimapScanned));
        addEntry("&nbsp;&nbsp;&nbsp;Spam & Passed", Util.trimNumber("",popimapPassed), Util.percentNumber(popimapPassed, popimapScanned));
        totalSpam = popimapMarked + popimapPassed;
        addEntry("&nbsp;&nbsp;&nbsp;Clean & Passed", Util.trimNumber("",popimapScanned-totalSpam), Util.percentNumber(popimapScanned-totalSpam, popimapScanned));

        // XXXX
        String tranName = "SpamGuard";

        return summarizeEntries(tranName);
    }
}

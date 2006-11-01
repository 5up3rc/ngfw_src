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

package com.untangle.mvvm.reporting;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.jar.*;

import com.untangle.mvvm.MvvmContextFactory;
import com.untangle.mvvm.MvvmLocalContext;
import com.untangle.mvvm.reporting.summary.*;
import com.untangle.mvvm.engine.DataSourceFactory;
import com.untangle.mvvm.security.Tid;
import com.untangle.mvvm.tran.Transform;
import com.untangle.mvvm.tran.TransformContext;
import com.untangle.mvvm.tran.TransformState;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.*;
import org.apache.log4j.Logger;
import org.jfree.chart.*;
import org.xml.sax.*;


public class Reporter implements Runnable
{
    private static final String SYMLINK_CMD = "/bin/ln -s";
    private final Logger logger = Logger.getLogger(getClass());

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEEE MMMM d, yyyy");
    private static final SimpleDateFormat CUTOFF_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final SimpleDateFormat DAYNAME_FORMAT = new SimpleDateFormat("yyyy_MM_dd");
    private static final SimpleDateFormat DAYDATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    // The base, containing one directory for each day's generated reports
    private static File outputBaseDir;

    // This is the current midnight -- we're generating reports up to this time.
    private java.util.Date midnight;

    private int daysToKeep; 

    // Today
    private static File outputDir;

    private static Settings settings;
    private boolean needToDie;

    private Reporter() {}

    public Reporter(String outputBaseDirName, java.util.Date midnight, int daysToKeep)
    {
        this.outputBaseDir = new File(outputBaseDirName);
        this.midnight = midnight;
        this.daysToKeep = daysToKeep;
        needToDie = false;

        Logger logger = Logger.getLogger(Reporter.class);

        Util.init(midnight);
    }

    public void setNeedToDie()
    {
        needToDie = true;
    }

    private void generateDaysToAdd(Connection conn, File outputDir)
        throws IOException, SQLException
    {
        File dtaFile = new File(outputDir, "daystoadd");
        dtaFile.delete(); // discard any old dta file
        dtaFile.createNewFile(); // create new dta file
        PrintWriter writer = new PrintWriter(new FileWriter(dtaFile));

        // Find all present reports.
        Set<String> existing = new HashSet<String>();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT day_name FROM reports.report_data_days");
        while (rs.next())
            existing.add(rs.getString(1));
        rs.close();
        stmt.close();
            
        // Go back to lastmonth and add any days missing to reports 
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(Util.lastmonth.getTime());
        while (cal.getTimeInMillis() <= Util.lastday.getTime()) {
            String dayname = DAYNAME_FORMAT.format(cal.getTime());
            if (existing.contains(dayname)) {
                logger.debug("Ignoring existing day " + dayname);
            } else {
                logger.debug("Adding day " + dayname);
                Calendar cal2 = (Calendar) cal.clone();
                cal2.add(Calendar.DAY_OF_YEAR, 1);
                writer.printf("%s %s %s\n", dayname, DAYDATE_FORMAT.format(cal.getTime()),
                              DAYDATE_FORMAT.format(cal2.getTime()));
            }
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        writer.close();
    }

    public void prepare()
        throws SQLException, IOException
    {
        Calendar cal = Calendar.getInstance();

        // Figure out the real output Dir. Note we use 'lastday' as that really
        // means midnight of the day we are reporting on, thus 'todayName'.
        cal.setTimeInMillis(Util.lastday.getTime());
        String todayName = Util.getDateDirName(cal);
        outputDir = new File(outputBaseDir, todayName);
        outputDir.mkdirs();

        try {
            File current = new File(outputBaseDir, "current");
            current.delete();
            String command = SYMLINK_CMD + " " + outputDir.getPath() + " " + current.getPath();
            Process p = Runtime.getRuntime().exec(command);

        } catch (IOException exn) {
            logger.error("Unable to create current link", exn);
        }

        Connection conn = null;
        try {
            conn = DataSourceFactory.factory().getConnection();
            setConnectionProperties(conn);
            logger.debug("beginning dhcp generate address map");
            DhcpMap.get().generateAddressMap(conn, Util.lastmonth, Util.midnight);
            settings = new Settings(conn, cal);
            generateDaysToAdd(conn, outputDir);
            logger.debug("ending dhcp generate address map");
        } finally {
            if (conn != null) {
                try {
                    DataSourceFactory.factory().closeConnection(conn);
                } catch (Exception x) { }
                conn = null;
            }
        }

        String emailDetail = "export MV_EG_EMAIL_DETAIL=";
        String daily = "export MV_EG_DAILY_REPORT=";
        String weekly = "export MV_EG_WEEKLY_REPORT=";
        String monthly = "export MV_EG_MONTHLY_REPORT=";

        if (false == settings.getEmailDetail()) {
            emailDetail += "n";
        } else {
            emailDetail += "y";
        }

        if (false == settings.getDaily()) {
            daily += "n";
        } else {
            daily += "y";
        }

        if (false == settings.getWeekly()) {
            weekly += "n";
        } else {
            weekly += "y";
        }

        if (false == settings.getMonthly()) {
            monthly += "n";
        } else {
            monthly += "y";
        }
        //logger.info("email detail: " + emailDetail);
        //logger.info("daily: " + daily);
        //logger.info("weekly: " + weekly);
        //logger.info("monthly: " + monthly);

        try {
            File envFile = new File(outputDir, "settings.env");
            envFile.delete(); // discard any old env file
            envFile.createNewFile(); // create new env file
            FileWriter envFWriter = new FileWriter(envFile);
            BufferedWriter envBWriter = new BufferedWriter(envFWriter);

            envBWriter.write(emailDetail);
            envBWriter.newLine();
            envBWriter.write(daily);
            envBWriter.newLine();
            envBWriter.write(weekly);
            envBWriter.newLine();
            envBWriter.write(monthly);
            envBWriter.newLine();
            addParam(envBWriter, "MV_EG_REPORT_END", Util.midnight);
            addParam(envBWriter, "MV_EG_DAY_START", Util.lastday);
            addParam(envBWriter, "MV_EG_WEEK_START", Util.lastweek);
            addParam(envBWriter, "MV_EG_MONTH_START", Util.lastmonth);

            envBWriter.close();
            envFWriter.close();

            
        } catch (IOException exn) {
            logger.error("Unable to delete old env file, create new env file, or write to new env file for update-reports", exn);
        }

    }

    public void run() {
        MvvmLocalContext mvvm = MvvmContextFactory.context();
        List<Tid> tids = mvvm.transformManager().transformInstances();
        List<TransformContext> toreport = new ArrayList<TransformContext>();
        for (Tid t : tids) {
            TransformContext tctx = mvvm.transformManager().transformContext(t);
            if (tctx == null) {
                logger.error("Null transform context, ignoring");
                continue;
            }
            Transform tran = tctx.transform();
            if (tran == null) {
                logger.error("NULL Transform Context");
                continue;
            }
            if (tran.getRunState() == TransformState.RUNNING) {
                String name = tctx.getTransformDesc().getName();
                // Make sure we only include one of each.
                boolean foundit = false;
                for (TransformContext already : toreport) {
                    if (name.equals(already.getTransformDesc().getName())) {
                        foundit = true;
                        break;
                    }
                }
                if (!foundit) {
                    logger.debug("Adding " + name + " to list of reports to generate");
                    toreport.add(tctx);
                }
            }
        }
        Connection conn = null;
        try {
            conn = DataSourceFactory.factory().getConnection();
            setConnectionProperties(conn);
            logger.debug("beginning report generation");
            generateNewReports(conn, toreport);
            logger.debug("ending report generation");
        } catch (Exception x) {
            logger.error("Exception while running", x);
            return;
        } finally {
            if (conn != null) {
                try {
                    DataSourceFactory.factory().closeConnection(conn);
                } catch (Exception x) { }
                conn = null;
            }
        }
        if (needToDie) {
            logger.warn("exiting early by request");
            return;
        }
        logger.debug("purging old reports");
        purgeOldReports();
    }

    private void generateNewReports(Connection conn, List<TransformContext> toreport)
        throws IOException, JRScriptletException, SQLException, ClassNotFoundException
    {
        Thread ct = Thread.currentThread();
        ClassLoader oldCl = ct.getContextClassLoader();

        for (TransformContext tctx : toreport) {
            if (needToDie) {
                logger.warn("exiting early by request");
                return;
            }
            String tranName = tctx.getTransformDesc().getName();
            try {
                ClassLoader tcl = tctx.getClassLoader();
                ct.setContextClassLoader(tcl);
                logger.info("Running TranReporter for " + tranName);
                TranReporter tranReporter = new TranReporter(outputDir, tctx, settings);
                tranReporter.process(conn);
            } catch (Exception exn) {
                logger.warn("bad transform: " + tranName, exn);
            } finally {
                ct.setContextClassLoader(oldCl);
            }
        }
    }

    private void purgeOldReports()
    {
        // Since daysToKeep will always be at least 1, we'll always keep today's.
        Calendar firstPurged = (Calendar) Util.reportNow.clone();
        firstPurged.add(Calendar.DAY_OF_YEAR, -1 - daysToKeep);
        Calendar c = (Calendar) firstPurged.clone();
        int missCount = 0;
        while (true) {
            String dirName = Util.getDateDirName(c);
            File dir = new File(outputBaseDir, dirName);
            if (dir.exists())
                Util.deleteDir(dir);
            else
                missCount++;
            if (missCount > 100)
                // Allow for 100 missed days, in case they turned off the box for a couple months.
                break;
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
    }

    // Optimize postgres settings for this connection to do report generation.
    private static void setConnectionProperties(Connection conn)
        throws SQLException
    {
        Statement stmt = conn.createStatement();
        stmt.executeUpdate("set sort_mem=16384");
        stmt.executeUpdate("set effective_cache_size=3000");
    }

    private static void addParam(BufferedWriter w, String name, java.util.Date value) throws IOException
    {
        w.write("export " + name + "=");
        w.write("'" + DATE_FORMAT.format(value) + "'");
        w.newLine();
    }
}

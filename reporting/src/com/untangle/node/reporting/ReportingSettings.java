/**
 * $Id$
 */
package com.untangle.node.reporting;

import java.util.List;
import java.util.LinkedList;
import java.io.Serializable;

import org.json.JSONObject;
import org.json.JSONString;

import com.untangle.uvm.node.IPMaskedAddress;
import com.untangle.uvm.node.DayOfWeekMatcher;
import com.untangle.uvm.node.EventLogEntry;

/**
 * Settings for the Reporting Node.
 */
@SuppressWarnings("serial")
public class ReportingSettings implements Serializable, JSONString
{
    private Integer dbRetention = 7; // days
    private Integer fileRetention = 90; // days
    private Integer generationHour = 2;
    private Integer generationMinute = 0;
    private boolean emailDetail = false;
    private Integer attachmentSizeLimit = 10; // MB

    private LinkedList<ReportingHostnameMapEntry> hostnameMap = new LinkedList<ReportingHostnameMapEntry>();
    private LinkedList<ReportingUser> reportingUsers = new LinkedList<ReportingUser>();
    private LinkedList<AlertRule> alertRules = null;
    
    private DayOfWeekMatcher generateDailyReports = new DayOfWeekMatcher("any");
    private DayOfWeekMatcher generateWeeklyReports = new DayOfWeekMatcher("sunday");
    private boolean generateMonthlyReports = false;

    private String dbHost = "localhost";
    private int    dbPort = 5432;
    private String dbUser = "postgres";
    private String dbPassword = "foo";
    private String dbName = "uvm";
        
    private boolean syslogEnabled = false;
    private String syslogHost;
    private int syslogPort = 514;
    private String syslogProtocol = "UDP";

    private LinkedList<ReportEntry> reportEntries  = new LinkedList<ReportEntry>();
    private LinkedList<EventLogEntry> eventEntries  = new LinkedList<EventLogEntry>();
    
    public ReportingSettings() { }

    public LinkedList<ReportingHostnameMapEntry> getHostnameMap() { return hostnameMap; }
    public void setHostnameMap( LinkedList<ReportingHostnameMapEntry> hostnameMap ) { this.hostnameMap = hostnameMap; }

    public int getDbRetention() { return dbRetention; }
    public void setDbRetention( int dbRetention ) { this.dbRetention = dbRetention; }

    public int getGenerationHour() { return generationHour; }
    public void setGenerationHour( int generationHour ) { this.generationHour = generationHour; }

    public int getGenerationMinute() { return generationMinute; }
    public void setGenerationMinute( int generationMinute ) { this.generationMinute = generationMinute; }

    public int getFileRetention() { return fileRetention; }
    public void setFileRetention( int fileRetention ) { this.fileRetention = fileRetention; }

    public boolean getEmailDetail() { return emailDetail; }
    public void setEmailDetail( boolean emailDetail ) { this.emailDetail = emailDetail; }

    public int getAttachmentSizeLimit() { return attachmentSizeLimit; }
    public void setAttachmentSizeLimit( int limit ) { attachmentSizeLimit = limit; }

    public LinkedList<ReportingUser> getReportingUsers() { return this.reportingUsers; }
    public void setReportingUsers( LinkedList<ReportingUser> reportingUsers ) { this.reportingUsers = reportingUsers; }

    public LinkedList<AlertRule> getAlertRules() { return this.alertRules; }
    public void setAlertRules( LinkedList<AlertRule> newValue ) { this.alertRules = newValue; }
    
    public DayOfWeekMatcher getGenerateDailyReports() { return this.generateDailyReports; }
    public void setGenerateDailyReports( DayOfWeekMatcher generateDailyReports ) { this.generateDailyReports = generateDailyReports; }

    public DayOfWeekMatcher getGenerateWeeklyReports() { return this.generateWeeklyReports; }
    public void setGenerateWeeklyReports( DayOfWeekMatcher generateWeeklyReports ) { this.generateWeeklyReports = generateWeeklyReports; }

    public boolean getGenerateMonthlyReports() { return this.generateMonthlyReports; }
    public void setGenerateMonthlyReports( boolean generateMonthlyReports ) { this.generateMonthlyReports = generateMonthlyReports; }

    public boolean isSyslogEnabled() { return syslogEnabled; }
    public void setSyslogEnabled( boolean syslogEnabled ) { this.syslogEnabled = syslogEnabled; }

    public LinkedList<ReportEntry> getReportEntries() { return reportEntries; }
    public void setReportEntries( LinkedList<ReportEntry> newValue ) { this.reportEntries = newValue; }

    public LinkedList<EventLogEntry> getEventEntries() { return eventEntries; }
    public void setEventEntries( LinkedList<EventLogEntry> newValue ) { this.eventEntries = newValue; }
    

    /**
     * Syslog destination hostname.
     */
    public String getSyslogHost() { return syslogHost; }
    public void setSyslogHost( String syslogHost ) { this.syslogHost = syslogHost; }

    /**
     * Syslog destination port.
     */
    public int getSyslogPort() { return syslogPort; }
    public void setSyslogPort( int syslogPort ) { this.syslogPort = syslogPort; }

    /**
     * Syslog protocol.
     */
    public String getSyslogProtocol() { return syslogProtocol; }
    public void setSyslogProtocol( String syslogProtocol ) { this.syslogProtocol = syslogProtocol; }

    public String getDbHost() { return dbHost; }
    public void setDbHost( String dbHost ) { this.dbHost = dbHost; }

    public int getDbPort() { return dbPort; }
    public void setDbPort( int dbPort ) { this.dbPort = dbPort; }
    
    public String getDbUser() { return dbUser; }
    public void setDbUser( String dbUser ) { this.dbUser = dbUser; }

    public String getDbPassword() { return dbPassword; }
    public void setDbPassword( String dbPassword ) { this.dbPassword = dbPassword; }

    public String getDbName() { return dbName; }
    public void setDbName( String dbName ) { this.dbName = dbName; }
    
    public String toJSONString()
    {
        JSONObject jO = new JSONObject(this);
        return jO.toString();
    }
}

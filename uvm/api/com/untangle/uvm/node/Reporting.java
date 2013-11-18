/**
 * $Id: Reporting.java,v 1.00 2012/05/08 16:07:19 dmorris Exp $
 */
package com.untangle.uvm.node;

import java.util.ArrayList;

import com.untangle.uvm.logging.LogEvent;

public interface Reporting
{
    void logEvent( LogEvent evt );

    void forceFlush();

    ArrayList<org.json.JSONObject> getEvents( final String query, final Long policyId, final int limit );

    java.sql.ResultSet getEventsResultSet( final String query, final Long policyId, final int limit );
    
    void createSchemas();

    double getAvgWriteTimePerEvent();

    long getWriteDelaySec();
}

/*
 * $Id$
 */
package com.untangle.node.protofilter;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import com.untangle.uvm.node.EventLogQuery;
import com.untangle.uvm.node.NodeMetric;
import com.untangle.uvm.node.NodeSettings;
import com.untangle.uvm.node.NodeProperties;
import com.untangle.uvm.util.I18nUtil;
import com.untangle.uvm.vnet.NodeBase;
import com.untangle.uvm.vnet.Affinity;
import com.untangle.uvm.vnet.Fitting;
import com.untangle.uvm.vnet.PipelineConnector;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.SettingsManager;

public class ProtoFilterImpl extends NodeBase implements ProtoFilter
{
    private static final String STAT_SCAN = "scan";
    private static final String STAT_DETECT = "detect";
    private static final String STAT_BLOCK = "block";

    private final EventHandler handler = new EventHandler( this );

    private final PipelineConnector connector;
    private final PipelineConnector[] connectors;

    private final Logger logger = Logger.getLogger(ProtoFilterImpl.class);

    private ProtoFilterSettings nodeSettings = null;

    private EventLogQuery allEventQuery;
    private EventLogQuery blockedEventQuery;
    
    // constructors -----------------------------------------------------------

    public ProtoFilterImpl( NodeSettings nodeSettings, NodeProperties nodeProperties )
    {
        super( nodeSettings, nodeProperties );

        this.addMetric(new NodeMetric(STAT_SCAN, I18nUtil.marktr("Chunks scanned")));
        this.addMetric(new NodeMetric(STAT_DETECT, I18nUtil.marktr("Sessions logged")));
        this.addMetric(new NodeMetric(STAT_BLOCK, I18nUtil.marktr("Sessions blocked")));
        
        this.connector = UvmContextFactory.context().pipelineFoundry().create("protofilter", this, null, handler, Fitting.OCTET_STREAM, Fitting.OCTET_STREAM, Affinity.CLIENT, 0);
        this.connectors = new PipelineConnector[] { connector };
        
        this.allEventQuery = new EventLogQuery(I18nUtil.marktr("All Events"),
                                               "SELECT * FROM reports.sessions " + 
                                               "WHERE policy_id = :policyId " +
                                               "AND protofilter_protocol IS NOT NULL " +
                                               "ORDER BY time_stamp DESC");

        this.blockedEventQuery = new EventLogQuery(I18nUtil.marktr("Blocked Events"),
                                                   "SELECT * FROM reports.sessions " + 
                                                   "WHERE policy_id = :policyId " +
                                                   "AND protofilter_blocked IS TRUE " +
                                                   "ORDER BY time_stamp DESC");
    }

    // ProtoFilter methods ----------------------------------------------------

    public ProtoFilterSettings getSettings()
    {
        if( this.nodeSettings == null )
            logger.error("Settings not yet initialized. State: " + this.getRunState() );
        return this.nodeSettings;
    }

    public void setSettings(final ProtoFilterSettings newSettings)
    {
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        String nodeID = this.getNodeSettings().getId().toString();
        String settingsFile = System.getProperty("uvm.settings.dir") + "/untangle-node-protofilter/settings_" + nodeID + ".js";

        try {
            settingsManager.save( settingsFile, newSettings );
        } catch (Exception exn) {
            logger.error("Could not save ProtoFilter settings", exn);
            return;
        }

        this.nodeSettings = newSettings;
        
        reconfigure();
    }

    public int getPatternsTotal()
    {
        return(nodeSettings.getPatterns().size());
    }

    public int getPatternsLogged()
    {
        LinkedList<ProtoFilterPattern>list = nodeSettings.getPatterns();
        int count = 0;
        
            for(int x = 0;x < list.size();x++)
            {
            ProtoFilterPattern curr = list.get(x);
            if (curr.getLog()) count++;
            }
        
        return(count);
    }

    public int getPatternsBlocked()
    {
        LinkedList<ProtoFilterPattern>list = nodeSettings.getPatterns();
        int count = 0;
        
            for(int x = 0;x < list.size();x++)
            {
            ProtoFilterPattern curr = list.get(x);
            if (curr.isBlocked()) count++;
            }
        
        return(count);
    }

    public EventLogQuery[] getEventQueries()
    {
        return new EventLogQuery[] { this.allEventQuery, this.blockedEventQuery };
    }        

    @Override
    protected PipelineConnector[] getConnectors()
    {
        return this.connectors;
    }

    /*
     * First time initialization
     */
    @Override
    public void initializeSettings()
    {
        ProtoFilterSettings settings = new ProtoFilterSettings();
        settings.setPatterns(new LinkedList<ProtoFilterPattern>());
        setSettings(settings);
    }

    protected void postInit()
    {
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();

        String nodeID = this.getNodeSettings().getId().toString();

        String settingsFile = System.getProperty("uvm.settings.dir") + "/untangle-node-protofilter/settings_" + nodeID + ".js";
        ProtoFilterSettings readSettings = null;
        
        logger.info("Loading settings from " + settingsFile);
        
        try {
            readSettings =  settingsManager.load( ProtoFilterSettings.class, settingsFile);
        } catch (Exception exn) {
            logger.error("Could not read node settings", exn);
        }

        try {
            if (readSettings == null) {
                logger.warn("No settings found... initializing with defaults");
                initializeSettings();
            } else {
                nodeSettings = readSettings;
                reconfigure();
            }
        } catch (Exception exn) {
            logger.error("Could not apply node settings", exn);
        }
    }

    public void reconfigure()
    {
        HashSet<ProtoFilterPattern> enabledPatternsSet = new HashSet<ProtoFilterPattern>();

        logger.info("Reconfigure()");

        if (nodeSettings == null) {
            throw new RuntimeException("Failed to get ProtoFilter settings: " + nodeSettings);
        }

        LinkedList<ProtoFilterPattern> curPatterns = nodeSettings.getPatterns();
        if (curPatterns == null)
            logger.error("NULL pattern list. Continuing anyway...");
        else {
            for(int x = 0;x < curPatterns.size();x++) {
                ProtoFilterPattern pat = curPatterns.get(x);

                if ( pat.getLog() || pat.getAlert() || pat.isBlocked() ) {
                    logger.info("Matching on pattern \"" + pat.getProtocol() + "\"");
                    enabledPatternsSet.add(pat);
                }
            }
        }

        handler.patternSet(enabledPatternsSet);
        handler.byteLimit(nodeSettings.getByteLimit());
        handler.chunkLimit(nodeSettings.getChunkLimit());
        handler.stripZeros(nodeSettings.isStripZeros());
    }

    void incrementScanCount()
    {
        this.incrementMetric(STAT_SCAN);
    }

    void incrementBlockCount()
    {
        this.incrementMetric(STAT_BLOCK);
    }

    void incrementDetectCount()
    {
        this.incrementMetric(STAT_DETECT);
    }
}

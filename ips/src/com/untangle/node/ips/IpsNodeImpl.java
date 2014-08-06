/**
 * $Id$
 */
package com.untangle.node.ips;

import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import org.apache.log4j.Logger;

import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.SettingsManager;
import com.untangle.uvm.util.I18nUtil;
import com.untangle.uvm.vnet.NodeBase;
import com.untangle.uvm.vnet.Affinity;
import com.untangle.uvm.vnet.Fitting;
import com.untangle.uvm.vnet.PipelineConnector;
import com.untangle.uvm.node.EventLogQuery;
import com.untangle.uvm.node.NodeMetric;

public class IpsNodeImpl extends NodeBase implements IpsNode
{
    private final Logger logger = Logger.getLogger(getClass());

    private static final String STAT_SCAN = "scan";
    private static final String STAT_DETECT = "detect";
    private static final String STAT_BLOCK = "block";
    
    private IpsSettings settings = null;
    final IpsStatistics statistics;

    private final EventHandler handler;
    private final PipelineConnector  octetConnector;
    private final PipelineConnector httpConnector;
    private final PipelineConnector [] connectors;

    private IpsDetectionEngine engine;

    private EventLogQuery allEventQuery;
    private EventLogQuery blockedEventQuery;

    public IpsNodeImpl( com.untangle.uvm.node.NodeSettings nodeSettings, com.untangle.uvm.node.NodeProperties nodeProperties )
    {
        super( nodeSettings, nodeProperties );

        engine = new IpsDetectionEngine(this);
        handler = new EventHandler(this);
        statistics = new IpsStatistics();

        // Put the octet stream close to the server so that it is after the http processing.

        this.octetConnector = UvmContextFactory.context().pipelineFoundry().create("ips-octet", this, null, handler, Fitting.OCTET_STREAM, Fitting.OCTET_STREAM, Affinity.SERVER, 10);
        this.httpConnector = UvmContextFactory.context().pipelineFoundry().create("ips-http", this, null, new IpsHttpHandler(this), Fitting.HTTP_TOKENS, Fitting.HTTP_TOKENS, Affinity.SERVER, 0);
        this.connectors = new PipelineConnector[] { octetConnector, httpConnector };

        this.allEventQuery = new EventLogQuery(I18nUtil.marktr("All Events"),
                                               "SELECT * FROM reports.sessions " + 
                                               "WHERE policy_id = :policyId " +
                                               "AND ips_description IS NOT NULL " +
                                               "ORDER BY time_stamp DESC");

        this.blockedEventQuery = new EventLogQuery(I18nUtil.marktr("Blocked Events"),
                                                   "SELECT * FROM reports.sessions " + 
                                                   "WHERE policy_id = :policyId " +
                                                   "AND ips_blocked IS TRUE " +
                                                   "ORDER BY time_stamp DESC");

        List<RuleClassification> classifications = FileLoader.loadClassifications();
        engine.setClassifications(classifications);

        this.addMetric(new NodeMetric(STAT_SCAN, I18nUtil.marktr("Sessions scanned")));
        this.addMetric(new NodeMetric(STAT_DETECT, I18nUtil.marktr("Sessions logged")));
        this.addMetric(new NodeMetric(STAT_BLOCK, I18nUtil.marktr("Sessions blocked")));
    }

    @Override
    protected PipelineConnector[] getConnectors()
    {
        return this.connectors;
    }

    public IpsStatistics getStatistics()
    {
        return statistics;
    }

    public IpsSettings getSettings()
    {
        return settings;
    }

    public void setSettings(IpsSettings newSettings)
    {
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        String nodeID = this.getNodeSettings().getId().toString();
        String settingsName = System.getProperty("uvm.settings.dir") + "/untangle-node-ips/settings_" + nodeID + ".js";

        try {
            settingsManager.save( IpsSettings.class, settingsName, newSettings );
        } catch (Exception exn) {
            logger.error("Could not save node settings", exn);
            return;
        }

        this.settings = newSettings;
        this.settings.updateStatistics(statistics);
        reconfigure();
    }

    public EventLogQuery[] getEventQueries()
    {
        return new EventLogQuery[] { this.allEventQuery, this.blockedEventQuery };
    }

    public void initializeNodeSettings()
    {
        logger.info("Loading Variables...");

        IpsSettings settings = new IpsSettings();
        settings.setVariables(IpsRuleManager.getDefaultVariables());
        settings.setImmutables(IpsRuleManager.getImmutableVariables());

        logger.info("Loading Rules...");
        IpsRuleManager manager = new IpsRuleManager(this); // A fake one for now.  XXX
        Set<IpsRule> ruleSet = FileLoader.loadAllRuleFiles(manager);
        List<IpsRule> ruleList = new LinkedList<IpsRule>( ruleSet );
        
        settings.setRules(ruleList);

        setSettings(settings);
        logger.info(ruleSet.size() + " rules loaded");

    }

    public IpsDetectionEngine getEngine()
    {
        return engine;
    }

    // protected methods -------------------------------------------------------

    protected void postStop()
    {
        engine.stop();
    }

    protected void preStart()
    {
        logger.info("Pre Start");
    }

    protected void postInit()
    {
        logger.info("Post init");

        readNodeSettings();
        reconfigure();
    }

    // private methods ---------------------------------------------------------

    private void readNodeSettings()
    {
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        String nodeID = this.getNodeSettings().getId().toString();
        String settingsFile = System.getProperty("uvm.settings.dir") + "/untangle-node-ips/settings_" + nodeID + ".js";
        IpsSettings readSettings = null;

        logger.info("Loading settings from " + settingsFile);

        try {
            readSettings =  settingsManager.load( IpsSettings.class, settingsFile);
        } catch (Exception exn) {
            logger.error("Could not read node settings", exn);
        }

        try {
            if (readSettings == null) {
                logger.warn("No settings found... initializing with defaults");
                initializeNodeSettings();
            }
            else {
                this.settings = readSettings;
                this.settings.updateStatistics(statistics);
                reconfigure();
            }
        } catch (Exception exn) {
            logger.error("Could not apply node settings", exn);
        }
    }

    private void reconfigure()
    {
        engine.onReconfigure();

        engine.clearRules();
        for(IpsRule rule : settings.getRules()) {
            engine.addRule(rule);
        }
    }

    public void incrementScanCount()
    {
        this.incrementMetric(STAT_SCAN);
    }

    public void incrementDetectCount()
    {
        this.incrementMetric(STAT_DETECT);
    }

    public void incrementBlockCount()
    {
        this.incrementMetric(STAT_BLOCK);
    }
}

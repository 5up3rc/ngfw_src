/**
 * $Id$
 */
package com.untangle.uvm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Date;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

import org.jabsorb.JSONSerializer;
import org.json.JSONObject;

import com.untangle.uvm.SettingsManager;
import com.untangle.uvm.CertCacheManager;
import com.untangle.uvm.UvmContext;
import com.untangle.uvm.TomcatManager;
import com.untangle.uvm.LocalDirectory;
import com.untangle.uvm.BrandingManager;
import com.untangle.uvm.OemManager;
import com.untangle.uvm.AlertManager;
import com.untangle.uvm.CertificateManager;
import com.untangle.uvm.DaemonManager;
import com.untangle.uvm.NetworkManager;
import com.untangle.uvm.NetcapManager;
import com.untangle.uvm.ExecManager;
import com.untangle.uvm.MetricManager;
import com.untangle.uvm.UvmException;
import com.untangle.uvm.UvmState;
import com.untangle.uvm.SessionMonitor;
import com.untangle.uvm.HostTable;
import com.untangle.uvm.node.LicenseManager;
import com.untangle.uvm.node.Reporting;
import com.untangle.uvm.node.DayOfWeekMatcher;
import com.untangle.uvm.node.NodeManager;
import com.untangle.uvm.node.PolicyManager;
import com.untangle.uvm.logging.LogEvent;
import com.untangle.uvm.servlet.ServletUtils;
import com.untangle.uvm.servlet.UploadHandler;
import com.untangle.uvm.servlet.ServletFileManager;

/**
 * This is the root API providing the Untangle VM functionality for applications
 * and the user interface
 */
public class UvmContextImpl extends UvmContextBase implements UvmContext
{
    private static final UvmContextImpl CONTEXT = new UvmContextImpl();

    private static final String REBOOT_SCRIPT = "/sbin/reboot";
    private static final String SHUTDOWN_SCRIPT = "/sbin/shutdown";
    private static final String TIMESYNC_SCRIPT = System.getProperty("uvm.bin.dir") + "/ut-force-time-sync";
    private static final String UPGRADE_PID_FILE = "/var/run/uvm-upgrade.pid";
    private static final String UPGRADE_HTML_FILE = "/var/www/uvm-upgrade.html";
    private static final String UPGRADE_SPLASH_SCRIPT = System.getProperty("uvm.bin.dir") + "/ut-show-upgrade-splash";;

    private static final String CREATE_UID_SCRIPT = System.getProperty("uvm.bin.dir") + "/ut-createUID.py";
    private static final String UID_FILE = System.getProperty("uvm.conf.dir") + "/uid";
    private static final String WIZARD_SETTINGS_FILE = System.getProperty("uvm.conf.dir") + "/" + "wizard.js";
    private static final String IS_REGISTERED_FLAG_FILE = System.getProperty("uvm.conf.dir") + "/is-registered-flag";
    private static final String VOUCHER_FLAG_FILE = System.getProperty("uvm.conf.dir") + "/voucher-flag";

    private static final String PROPERTY_STORE_URL = "uvm.store.url";
    private static final String DEFAULT_STORE_URL = "https://www.untangle.com/store/open.php";
    private static final String PROPERTY_HELP_URL = "uvm.help.url";
    private static final String DEFAULT_HELP_URL = "http://wiki.untangle.com/get.php";
    private static final String PROPERTY_LEGAL_URL = "uvm.legal.url";
    private static final String DEFAULT_LEGAL_URL = "http://www.untangle.com/legal";

    private static final Object startupWaitLock = new Object();

    private static final Logger logger = Logger.getLogger(UvmContextImpl.class);

    private static String uid;

    private UvmState state;
    private AdminManagerImpl adminManager;
    private LoggingManagerImpl loggingManager;
    private MailSenderImpl mailSender;
    private NetworkManagerImpl networkManager;
    private ConnectivityTesterImpl connectivityTester;
    private PipelineFoundryImpl pipelineFoundry;
    private RackManagerImpl rackManager;
    private NodeManagerImpl nodeManager;
    private CertificateManagerImpl certificateManager;
    private DaemonManagerImpl daemonManager;
    private BrandingManagerImpl brandingManager;
    private SkinManagerImpl skinManager;
    private MetricManagerImpl metricManager;
    private LanguageManagerImpl languageManager;
    private DefaultLicenseManagerImpl defaultLicenseManager;
    private InheritableThreadLocal<HttpServletRequest> threadRequest;
    private TomcatManagerImpl tomcatManager;
    private ServletFileManagerImpl servletFileManager;
    private SettingsManagerImpl settingsManager;
    private CertCacheManagerImpl certCacheManager;
    private OemManagerImpl oemManager;
    private AlertManagerImpl alertManager;
    private SessionMonitorImpl sessionMonitor;
    private BackupManager backupManager;
    private LocalDirectoryImpl localDirectory;
    private ExecManagerImpl execManager;
    private SystemManagerImpl systemManager;
    private JSONSerializer serializer;
    private Reporting reportingNode = null;
    private HostTableImpl hostTableImpl = null;
    private NetFilterLogger netFilterLogger = null;
    private long lastLoggedWarningTime = System.currentTimeMillis();

    private volatile List<String> annotatedClasses = new LinkedList<String>();
    
    // constructor ------------------------------------------------------------

    private UvmContextImpl()
    {
        state = UvmState.LOADED;
    }

    // static factory ---------------------------------------------------------

    static UvmContextImpl getInstance()
    {
        return CONTEXT;
    }

    public static UvmContext context()
    {
        return CONTEXT;
    }

    // singletons -------------------------------------------------------------

    public LocalDirectory localDirectory()
    {
        return this.localDirectory;
    }

    public BrandingManager brandingManager()
    {
        return this.brandingManager;
    }

    public SkinManagerImpl skinManager()
    {
        return this.skinManager;
    }

    public MetricManager metricManager()
    {
        return this.metricManager;
    }

    public LanguageManagerImpl languageManager()
    {
        return this.languageManager;
    }

    public CertificateManager certificateManager()
    {
        return this.certificateManager;
    }

    public DaemonManager daemonManager()
    {
        return this.daemonManager;
    }

    public RackManagerImpl rackManager()
    {
        return this.rackManager;
    }

    public NodeManager nodeManager()
    {
        return this.nodeManager;
    }

    public LoggingManagerImpl loggingManager()
    {
        return this.loggingManager;
    }

    public MailSenderImpl mailSender()
    {
        return this.mailSender;
    }

    public AdminManagerImpl adminManager()
    {
        return this.adminManager;
    }

    public SystemManagerImpl systemManager()
    {
        return this.systemManager;
    }

    public NetworkManager networkManager()
    {
        return this.networkManager;
    }

    public ConnectivityTesterImpl getConnectivityTester()
    {
        return this.connectivityTester;
    }

    public NetcapManager netcapManager()
    {
        return NetcapManagerImpl.getInstance();
    }

    public LicenseManager licenseManager()
    {
        NodeManager nodeManager = this.nodeManager();
        if ( nodeManager == null )
            return this.defaultLicenseManager;
        LicenseManager licenseManager = (LicenseManager) nodeManager.node("untangle-node-license");
        if (licenseManager == null)
            return this.defaultLicenseManager;

        return licenseManager;
    }

    public ServletFileManager servletFileManager()
    {
        return this.servletFileManager;
    }

    public SettingsManager settingsManager()
    {
        return this.settingsManager;
    }

    public CertCacheManager certCacheManager()
    {
        return this.certCacheManager;
    }

    public OemManager oemManager()
    {
        return this.oemManager;
    }

    public AlertManager alertManager()
    {
        return this.alertManager;
    }

    public PipelineFoundryImpl pipelineFoundry()
    {
        return this.pipelineFoundry;
    }

    public SessionMonitor sessionMonitor()
    {
        return this.sessionMonitor;
    }

    public ExecManager execManager()
    {
        return this.execManager;
    }

    public ExecManager createExecManager()
    {
        ExecManagerImpl execManager = new ExecManagerImpl();
        execManager.setSerializer(serializer);
        return execManager;
    }

    public InheritableThreadLocal<HttpServletRequest> threadRequest()
    {
        return threadRequest;
    }

    public TomcatManager tomcatManager()
    {
        return tomcatManager;
    }

    public HostTable hostTable()
    {
        return this.hostTableImpl;
    }

    public void waitForStartup()
    {
        synchronized (startupWaitLock) {
            while (state == UvmState.LOADED || state == UvmState.INITIALIZED) {
                try {
                    startupWaitLock.wait();
                } catch (InterruptedException exn) {
                    // reevaluate exit condition
                }
            }
        }
    }

    public JSONSerializer getSerializer()
    {
        return this.serializer;
    }

    // service methods --------------------------------------------------------

    /* For autonumbering anonymous threads. */
    private static class ThreadNumber
    {
        private static int threadInitNumber = 1;

        public static synchronized int nextThreadNum()
        {
            return threadInitNumber++;
        }
    }

    public Thread newThread(final Runnable runnable)
    {
        return newThread(runnable, "UTThread-" + ThreadNumber.nextThreadNum());
    }

    public Thread newThread(final Runnable runnable, final String name)
    {
        Runnable task = new Runnable()
        {
            public void run()
            {
                try {
                    runnable.run();
                } catch (OutOfMemoryError exn) {
                    UvmContextImpl.getInstance().fatalError("UvmContextImpl", exn);
                } catch (Exception exn) {
                    logger.error("Exception running: " + runnable, exn);
                }
            }
        };
        return new Thread(task, name);
    }

    public void shutdown()
    {
        Thread t = newThread(new Runnable()
        {
            public void run()
            {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException exn) {
                }
                logger.info("Exiting");
                System.exit(0);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public void rebootBox()
    {
        Integer exitValue = this.execManager().execResult(REBOOT_SCRIPT);
        if (0 != exitValue) {
            logger.error("Unable to reboot (" + exitValue + ")");
        } else {
            logger.info("Rebooted at admin request");
        }
    }

    public void shutdownBox()
    {
        Integer exitValue = this.execManager().execResult(SHUTDOWN_SCRIPT + " -h now");
        if (0 != exitValue) {
            logger.error("Unable to shutdown (" + exitValue + ")");
        } else {
            logger.info("Shutdown at admin request");
        }
    }

    public int forceTimeSync()
    {
        Integer exitValue = this.execManager().execResult(TIMESYNC_SCRIPT);
        if (0 != exitValue) {
            logger.error("Unable to synchronize time (" + exitValue + ")");
        } else {
            logger.info("Synchronized time");
        }
        return exitValue;
    }

    public UvmState state()
    {
        return state;
    }

    public String version()
    {
        return com.untangle.uvm.Version.getVersion();
    }

    public String getFullVersion()
    {
        return com.untangle.uvm.Version.getFullVersion();
    }

    public boolean isExpertMode()
    {
        File expertModeFlagFile = new File( System.getProperty("uvm.conf.dir") + "/expert-mode-flag" );
        return expertModeFlagFile.exists();
    }
    
    public boolean isWizardComplete()
    {
        return getWizardSettings().getWizardComplete();
    }

    public void wizardComplete()
    {
        WizardSettings wizardSettings = getWizardSettings();
        wizardSettings.setWizardComplete( true );
        wizardSettings.setCompletedStep(null);
        setWizardSettings( wizardSettings );
    }
    
    public WizardSettings getWizardSettings()
    {
        WizardSettings wizardSettings = null;
        try {
            wizardSettings = settingsManager.load( WizardSettings.class, WIZARD_SETTINGS_FILE );
        } catch (SettingsManager.SettingsException e) {
            logger.warn("Failed to load settings:",e);
        }

        if ( wizardSettings == null ) {
            wizardSettings = new WizardSettings();

            /* If the old flag file exists, the wizard is complete */
            try {
                File wizardCompleteFlagFile = new File( System.getProperty("uvm.conf.dir") + "/wizard-complete-flag" );
                if (wizardCompleteFlagFile.exists())
                    wizardSettings.setWizardComplete( true );
            } catch (Exception e) {}

            setWizardSettings( wizardSettings );
        }


        return wizardSettings;
    }
    
    public void setWizardSettings( WizardSettings wizardSettings )
    {
        String settingsFileName = System.getProperty("uvm.conf.dir") + "/" + "wizard.js";
        try {
            settingsManager.save( WIZARD_SETTINGS_FILE, wizardSettings );
        } catch (SettingsManager.SettingsException e) {
            logger.warn("Failed to save settings.",e);
        }
    }

    public boolean isRegistered()
    {
        File keyFile = new File(IS_REGISTERED_FLAG_FILE);
        return keyFile.exists();
    }

    public void setRegistered()
    {
        File keyFile = new File(IS_REGISTERED_FLAG_FILE);
        if (!keyFile.exists()) {
            try {
                keyFile.createNewFile();
            } catch (Exception e) {
                logger.error("Failed to create registration file", e);
            }
        }
    }

    public boolean isVoucher()
    {
        File keyFile = new File(VOUCHER_FLAG_FILE);
        return keyFile.exists();
    }

    /**
     * Returns true if this is a developer build in the development environment
     */
    public boolean isDevel()
    {
        String val = System.getProperty("com.untangle.isDevel");
        if (val == null)
            return false;
        return "true".equals(val.trim().toLowerCase());
    }

    /**
     * Returns true if this is a netbooted install on Untangle internal network
     */
    public boolean isNetBoot()
    {
        File installerSyslog = new File("/var/log/installer/syslog");
        if (installerSyslog.exists()) {
            try {
                java.util.Scanner scanner = new java.util.Scanner(installerSyslog);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();

                    if (line.contains("BOOTIF") && line.contains("netboot.preseed"))
                        return true;
                }
            } catch (Exception e) {
                logger.warn("Exception in isNetBoot()", e);
            }
        }

        return false;
    }

    public Map<String, String> getTranslations(String module)
    {
        return languageManager.getTranslations(module);
    }

    public String getCompanyName()
    {
        return this.brandingManager.getCompanyName();
    }

    public String getStoreUrl()
    {
        String url = System.getProperty(PROPERTY_STORE_URL);
        if (url == null)
            url = DEFAULT_STORE_URL;
        return url;
    }

    public boolean isStoreAvailable()
    {
        for (int tries = 0; tries < 3; tries++) {
            try {
                URL storeUrl = new URL(getStoreUrl());
                String host = storeUrl.getHost();
                InetAddress addr = InetAddress.getByName(host);
                InetSocketAddress remoteAddress = new InetSocketAddress(addr, 80);
                Socket sock = new Socket();
                sock.connect(remoteAddress, 5000);
                sock.close();
                return true;
            } catch (Exception e) {
                logger.warn("Failed to connect to store: " + e);
            }
        }
        return false;
    }

    public String getHelpUrl()
    {
        String url = System.getProperty(PROPERTY_HELP_URL);
        if (url == null)
            url = DEFAULT_HELP_URL;
        return url;
    }

    public String getLegalUrl()
    {
        String url = System.getProperty(PROPERTY_LEGAL_URL);
        if (url == null)
            url = DEFAULT_LEGAL_URL;
        return url;
    }

    public void logEvent(LogEvent evt)
    {
        if (this.reportingNode == null)
            getReportingNode();
        if (this.reportingNode == null)
            return;

        this.reportingNode.logEvent(evt);
    }

    public String getServerUID()
    {
        if (UvmContextImpl.uid == null) {
            try {
                File keyFile = new File(UID_FILE);
                if (keyFile.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(keyFile));
                    UvmContextImpl.uid = reader.readLine();
                    return UvmContextImpl.uid;
                }
            } catch (IOException x) {
                logger.error("Unable to get pop id: ", x);
            }
        }
        return UvmContextImpl.uid;
    }

    /**
     * This call returns one big JSONObject with references to all the important
     * information This is used to avoid lots of separate synchornous calls via
     * the Web UI. Reducing all these seperate calls to initialize the UI
     * reduces startup time
     */
    public org.json.JSONObject getWebuiStartupInfo()
    {
        org.json.JSONObject json = new org.json.JSONObject();

        try {
            json.put("languageManager", this.languageManager());
            json.put("skinManager", this.skinManager());
            json.put("nodeManager", this.nodeManager());
            json.put("policyManager", this.nodeManager().node("untangle-node-policy"));
            json.put("rackManager", this.rackManager());
            json.put("alertManager", this.alertManager());
            json.put("adminManager", this.adminManager());
            json.put("systemManager", this.systemManager());
            json.put("hostTable", this.hostTable());
            json.put("sessionMonitor", this.sessionMonitor());
            json.put("networkManager", this.networkManager());
            json.put("metricManager", this.metricManager());
            json.put("brandingManager", this.brandingManager());
            json.put("execManager", this.execManager());

            json.put("languageSettings", this.languageManager().getLanguageSettings());
            json.put("version", this.version());
            json.put("translations", this.languageManager().getTranslations("untangle-libuvm"));
            json.put("skinSettings", this.skinManager().getSettings());
            json.put("hostname", this.networkManager().getNetworkSettings().getHostName());
            json.put("companyName", this.brandingManager().getCompanyName());
            json.put("fullVersionAndRevision", this.adminManager().getFullVersionAndRevision());
            json.put("storeUrl", this.getStoreUrl());
            json.put("isRegistered", this.isRegistered());
            json.put("isExpertMode", this.isExpertMode());
            json.put("timeZoneOffset", this.systemManager().getTimeZoneOffset());
        } catch (Exception e) {
            logger.error("Error generating WebUI startup object", e);
        }
        return json;
    }

    public org.json.JSONObject getConditionQuickAddHints()
    {
        LinkedList<HostTableEntry> hosts = this.hostTableImpl.getHosts();

        PolicyManager policyManager = (PolicyManager)this.nodeManager().node("untangle-node-policy");
        
        LinkedList<String> hostnames = new LinkedList<String>();
        LinkedList<String> usernames = new LinkedList<String>();
        LinkedList<String> addresses = new LinkedList<String>();
        LinkedList<String> policies = new LinkedList<String>();

        for ( HostTableEntry host : hosts ) {
            String username = host.getUsername();
            String hostname = host.getHostname();
            String address  = host.getAddress().getHostAddress();
            if ( hostname != null && !"".equals(hostname) && !hostnames.contains(hostname) )
                hostnames.add(hostname);
            if ( username != null && !"".equals(username) && !usernames.contains(username) )
                usernames.add(username);
            if ( address != null && !"".equals(address) && !addresses.contains(address) )
                addresses.add(address);
        }

        try {
            org.json.JSONObject json = new org.json.JSONObject();

            json.put("hostname", hostnames.toArray( new String[0] ));
            json.put("username", usernames.toArray( new String[0] ));
            json.put("c_client_addr", addresses.toArray( new String[0] ));

            if ( policyManager != null ) {
                json.put("policy_id", policyManager.getPolicyIds());
            }
            
            return json;
        } catch (Exception e) {
            logger.warn("Error generating quick add hints",e);
        }

        return null;
    }

    /**
     * This call returns one big JSONObject with references to all the important
     * information This is used to avoid lots of separate synchornous calls via
     * the Setup UI. Reducing all these seperate calls to initialize the UI
     * reduces startup time
     */
    public org.json.JSONObject getSetupStartupInfo()
    {
        org.json.JSONObject json = new org.json.JSONObject();

        try {
            json.put("adminManager", this.adminManager());
            json.put("networkManager", this.networkManager());
            json.put("connectivityTester", this.getConnectivityTester());
            json.put("rackManager", this.rackManager());
            json.put("systemManager", this.systemManager());
            json.put("mailSender", this.mailSender());
        } catch (Exception e) {
            logger.error("Error generating Setup startup object", e);
        }
        return json;
    }
    
    // UvmContextBase methods --------------------------------------------------

    @Override
    protected void init()
    {
        this.serializer = new JSONSerializer();
        serializer.setFixupDuplicates(false);
        serializer.setMarshallNullAttributes(false);

        this.execManager = new ExecManagerImpl();

        this.settingsManager = new SettingsManagerImpl();

        try {
            ServletUtils.getInstance().registerSerializers(serializer);
            settingsManager.setSerializer(serializer);
            execManager.setSerializer(serializer);
        } catch (Exception e) {
            throw new IllegalStateException("register serializers should never fail!", e);
        }

        createUID();

        this.certCacheManager = new CertCacheManagerImpl();

        this.sessionMonitor = new SessionMonitorImpl();

        this.hostTableImpl = new HostTableImpl();
        
        this.netFilterLogger = new NetFilterLogger();

        this.servletFileManager = new ServletFileManagerImpl();

        this.languageManager = new LanguageManagerImpl();

        this.backupManager = new BackupManager();

        this.oemManager = new OemManagerImpl();

        this.loggingManager = new LoggingManagerImpl();

        this.threadRequest = new InheritableThreadLocal<HttpServletRequest>();

        this.tomcatManager = new TomcatManagerImpl(this, threadRequest, System.getProperty("uvm.home"), System.getProperty("uvm.web.dir"), System.getProperty("uvm.log.dir"));

        this.adminManager = new AdminManagerImpl();

        this.networkManager = new NetworkManagerImpl();

        this.defaultLicenseManager = new DefaultLicenseManagerImpl();

        this.mailSender = MailSenderImpl.mailSender();

        this.rackManager = RackManagerImpl.rackManager();

        this.pipelineFoundry = PipelineFoundryImpl.foundry();

        this.localDirectory = new LocalDirectoryImpl();

        this.brandingManager = new BrandingManagerImpl();

        this.skinManager = new SkinManagerImpl();

        this.systemManager = new SystemManagerImpl();

        this.nodeManager = new NodeManagerImpl();

        this.metricManager = new MetricManagerImpl();

        // Retrieve the connectivity tester
        this.connectivityTester = ConnectivityTesterImpl.getInstance();

        this.certificateManager = new CertificateManagerImpl();

        this.daemonManager = new DaemonManagerImpl();

        this.alertManager = new AlertManagerImpl();

        // start vectoring
        NetcapManagerImpl.getInstance().run();

        // Start statistic gathering
        metricManager.start();

        state = UvmState.INITIALIZED;
    }

    @Override
    protected void postInit()
    {
        mailSender.postInit();

        logger.debug("restarting nodes");
        nodeManager.init();

        tomcatManager.writeWelcomeFile();

        tomcatManager.startTomcat();

        logger.debug("postInit complete");
        synchronized (startupWaitLock) {
            state = UvmState.RUNNING;
            startupWaitLock.notifyAll();
        }

        hideUpgradeSplash();

        networkManager.insertRules();
    }

    @Override
    protected void destroy()
    {
        // the will be removed again by the wrapper
        // this is just so traffic will pass while the untangle-vm shutsdown
        networkManager.removeRules();

        state = UvmState.DESTROYED;

        try {
            metricManager.stop();
        } catch (Exception exn) {
            logger.error("could not stop MetricManager", exn);
        }

        // stop vectoring
        try {
            NetcapManagerImpl.getInstance().destroy();
        } catch (Exception exn) {
            logger.warn("could not destroy Netcap", exn);
        }

        // stop nodes
        try {
            nodeManager.destroy();
        } catch (Exception exn) {
            logger.warn("could not destroy NodeManager", exn);
        }

        try {
            tomcatManager.stopTomcat();
        } catch (Exception exn) {
            logger.warn("could not stop tomcat", exn);
        }

        logger.info("UvmContext destroyed");
    }

    // package protected methods ----------------------------------------------

    protected boolean refreshLibs()
    {
        return main.refreshLibs();
    }

    protected void fatalError(String throwingLocation, Throwable x)
    {
        main.fatalError(throwingLocation, x);
    }

    protected boolean loadUvmResource(String name)
    {
        return main.loadUvmResource(name);
    }

    // private methods --------------------------------------------------------

    /**
     * Create a UID file if one does not already exist
     */
    private void createUID()
    {
        File uidFile = new File(System.getProperty("uvm.conf.dir") + "/uid");

        if (uidFile.exists())
            return;

        String extraOptions = "";

        // if its devel env just return
        if (isDevel())
            return;

        // if its an untangle netboot, point to internal package server
        if (isNetBoot()) {
            extraOptions += " -u \"package-server.\" ";
            extraOptions += " -d \"nightly\" ";
        } else {
            extraOptions += " -d \"stable-" + com.untangle.uvm.Version.getVersion().replaceAll("\\.","") + "\" ";
        }

        extraOptions += " -f \"" + System.getProperty("uvm.conf.dir") + "/uid" + "\" ";

        if (com.untangle.uvm.Version.getVersionName() != null)
            extraOptions += " -n \"" + com.untangle.uvm.Version.getVersionName() + "\" ";

        Integer exitValue = this.execManager().execResult(CREATE_UID_SCRIPT + extraOptions);
        if (0 != exitValue) {
            logger.error("Unable to create UID (" + exitValue + ")");
            return;
        } else {
            logger.info("UID Created.");
        }

        // restart pyconnector now that the UID has been generated
        this.execManager().execResult("/etc/init.d/untangle-pyconnector restart");

        return;
    }

    private void hideUpgradeSplash()
    {
        /**
         * This changes apache to show the regular screen again if it is
         * currently showing the upgrade log (which is displayed during
         * upgrades)
         */

        /**
         * The PID file seems to sometimes mysteriously disappear so also check
         * for the HTML file
         */
        File upgradePidFile = new File(UPGRADE_PID_FILE);
        File upgradeHtmlFile = new File(UPGRADE_HTML_FILE);

        /* If the upgrade is in progress */
        if (upgradePidFile.exists() || upgradeHtmlFile.exists()) {
            logger.info("Upgrade complete. Removing upgrade splash screen...");

            try {
                Integer exitValue = this.execManager().execResult(UPGRADE_SPLASH_SCRIPT + " stop");
                if (0 != exitValue) {
                    logger.warn("Upgrade complete. Removing upgrade splash screen... failed");
                } else {
                    logger.info("Upgrade complete. Removing upgrade splash screen... done");
                }
            } catch (Exception e) {
                logger.warn("Upgrade complete. Removing upgrade splash screen... failed", e);
            }
        }
    }

    private void getReportingNode()
    {
        synchronized (this) {
            if (this.reportingNode == null) {
                try {
                    // nodeManager not initialized yet
                    if ( this.nodeManager == null ) {
                        if (System.currentTimeMillis() - this.lastLoggedWarningTime > 10000) {
                            logger.warn("Reporting node not found, discarding event(s)");
                            this.lastLoggedWarningTime = System.currentTimeMillis();
                        }
                        return;
                    }
                    
                    this.reportingNode = (Reporting) this.nodeManager.node("untangle-node-reporting");
                    // no reporting node
                    if (this.reportingNode == null) {
                        if (System.currentTimeMillis() - this.lastLoggedWarningTime > 10000) {
                            logger.warn("Reporting node not found, discarding event(s)");
                            this.lastLoggedWarningTime = System.currentTimeMillis();
                        }
                        return;
                    }
                } catch (Exception e) {
                    logger.warn("Unable to initialize reportingNode", e);
                    return;
                }
            }
        }
    }
}

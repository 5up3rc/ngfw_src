/**
 * $Id$
 */
package com.untangle.node.web_filter;

import java.net.InetAddress;
import java.util.List;
import java.util.LinkedList;
import org.json.JSONString;

import org.apache.log4j.Logger;

import com.untangle.uvm.SettingsManager;
import com.untangle.uvm.UvmContext;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.node.NodeSettings;
import com.untangle.uvm.node.NodeProperties;
import com.untangle.uvm.node.GenericRule;
import com.untangle.uvm.node.NodeMetric;
import com.untangle.uvm.util.I18nUtil;
import com.untangle.uvm.vnet.NodeBase;
import com.untangle.uvm.vnet.Affinity;
import com.untangle.uvm.vnet.Fitting;
import com.untangle.uvm.vnet.PipelineConnector;
import com.untangle.uvm.vnet.NodeTCPSession;
import com.untangle.uvm.vnet.Token;
import com.untangle.node.http.HeaderToken;

/**
 * The base implementation of the Web Filter.
 * The web filter lite and web filter implementation inherit this
 */
public abstract class WebFilterBase extends NodeBase implements WebFilter
{
    private static final String STAT_SCAN = "scan";
    private static final String STAT_BLOCK = "block";
    private static final String STAT_PASS = "pass";
    
    protected static final Logger logger = Logger.getLogger(WebFilterBase.class);
    
    protected final PipelineConnector connector;
    protected final PipelineConnector[] connectors;

    protected final WebFilterReplacementGenerator replacementGenerator;

    protected volatile WebFilterSettings settings;

    protected final UnblockedSitesMonitor unblockedSitesMonitor;

    public WebFilterBase( NodeSettings nodeSettings, NodeProperties nodeProperties )
    {
        super( nodeSettings, nodeProperties );
        
        this.replacementGenerator = buildReplacementGenerator();

        this.addMetric(new NodeMetric(STAT_SCAN, I18nUtil.marktr("Pages scanned")));
        this.addMetric(new NodeMetric(STAT_BLOCK, I18nUtil.marktr("Pages blocked")));
        this.addMetric(new NodeMetric(STAT_PASS, I18nUtil.marktr("Pages passed")));

        this.connector = UvmContextFactory.context().pipelineFoundry().create("web-filter", this, null, new WebFilterHandler( this ), Fitting.HTTP_TOKENS, Fitting.HTTP_TOKENS, Affinity.CLIENT, 1);
        this.connectors = new PipelineConnector[] { connector };
        
        String nodeName = this.getName();
        
        this.unblockedSitesMonitor = new UnblockedSitesMonitor(this);
    }

    public String getUnblockMode()
    {
        return settings.getUnblockMode();
    }

    public boolean isHttpsEnabledSni()
    {
        return settings.getEnableHttpsSni();
    }

    public boolean isHttpsEnabledSniCertFallback()
    {
        return settings.getEnableHttpsSniCertFallback();
    }

    public boolean isHttpsEnabledSniIpFallback()
    {
        return settings.getEnableHttpsSniIpFallback();
    }

    public WebFilterBlockDetails getDetails( String nonce )
    {
        return replacementGenerator.getNonceData(nonce);
    }

    public boolean unblockSite( String nonce, boolean global )
    {
        WebFilterBlockDetails bd = replacementGenerator.removeNonce(nonce);

        if (WebFilterSettings.UNBLOCK_MODE_NONE.equals(settings.getUnblockMode())) {
            logger.debug("attempting to unblock in WebFilterSettings.UNBLOCK_MODE_NONE");
            return false;
        } else if (WebFilterSettings.UNBLOCK_MODE_HOST.equals(settings.getUnblockMode())) {
            if (global) {
                logger.debug("attempting to unblock global in WebFilterSettings.UNBLOCK_MODE_HOST");
                return false;
            }
        } else if (WebFilterSettings.UNBLOCK_MODE_GLOBAL.equals(settings.getUnblockMode())) {
            // its all good
        }
        else  {
            logger.error("missing case: " + settings.getUnblockMode());
        }

        if (null == bd) {
            logger.debug("no BlockDetails for nonce");
            return false;
        } else if (global) {
            String site = bd.getWhitelistHost();
            if (null == site) {
                logger.warn("cannot unblock null host");
                return false;
            } else {
                logger.warn("permanently unblocking site: " + site);
                GenericRule sr = new GenericRule(site, site, "user unblocked", "unblocked by user", true);
                settings.getPassedUrls().add(sr);
                _setSettings(settings);

                return true;
            }
        } else {
            String site = bd.getWhitelistHost();
            if (null == site) {
                logger.warn("cannot unblock null host");
                return false;
            } else {
                logger.info("Temporarily unblocking site: " + site);
                InetAddress addr = bd.getClientAddress();

                unblockedSitesMonitor.addUnblockedSite(addr, site);
                getDecisionEngine().addUnblockedSite(addr, site);

                return true;
            }
        }
    }

    public WebFilterSettings getSettings()
    {
        return this.settings;
    }
    
    public void setSettings( WebFilterSettings settings )
    {
        _setSettings(settings);
    }

    public List<GenericRule> getCategories()
    {
        return settings.getCategories();
    }

    public void setCategories( List<GenericRule> newCategories )
    {
        this.settings.setCategories(newCategories);

        _setSettings(this.settings);
    }

    public List<GenericRule> getBlockedExtensions()
    {
        return settings.getBlockedExtensions();
    }

    public void setBlockedExtensions( List<GenericRule> blockedExtensions )
    {
        this.settings.setBlockedExtensions(blockedExtensions);

        _setSettings(this.settings);
    }

    public List<GenericRule> getBlockedMimeTypes()
    {
        return settings.getBlockedMimeTypes();
    }

    public void setBlockedMimeTypes( List<GenericRule> blockedMimeTypes )
    {
        this.settings.setBlockedMimeTypes(blockedMimeTypes);

        _setSettings(this.settings);
    }

    public List<GenericRule> getBlockedUrls()
    {
        return settings.getBlockedUrls();
    }

    public void setBlockedUrls( List<GenericRule> blockedUrls )
    {
        this.settings.setBlockedUrls(blockedUrls);

        _setSettings(this.settings);
    }

    public List<GenericRule> getPassedClients() 
    {
        return settings.getPassedClients();
    }

    public void setPassedClients( List<GenericRule> passedClients )
    {
        this.settings.setPassedClients(passedClients);

        _setSettings(this.settings);
    }

    public List<GenericRule> getPassedUrls()
    {
        return settings.getPassedUrls();
    }

    public void setPassedUrls( List<GenericRule> passedUrls )
    {
        this.settings.setPassedUrls(passedUrls);

        _setSettings(this.settings);
    }

    public abstract DecisionEngine getDecisionEngine();

    public abstract String getNodeTitle();

    public abstract String getName();
    public abstract String getAppName();

    public Token[] generateResponse( String nonce, NodeTCPSession session, String uri, HeaderToken header )
    {
        return replacementGenerator.generateResponse( nonce, session, uri, header );
    }

    public abstract void initializeSettings( WebFilterSettings settings );

    public void initializeCommonSettings( WebFilterSettings settings )
    {
        if (logger.isDebugEnabled()) {
            logger.debug(getNodeSettings() + " init settings");
        }

        settings.setBlockedExtensions(_buildDefaultFileExtensionList());

        settings.setBlockedMimeTypes(_buildDefaultMimeTypeList());
    }

    public void incrementScanCount()
    {
        this.incrementMetric(STAT_SCAN);
    }

    public void incrementBlockCount()
    {
        this.incrementMetric(STAT_BLOCK);
    }

    public void incrementPassCount()
    {
        this.incrementMetric(STAT_PASS);
    }

    protected WebFilterReplacementGenerator buildReplacementGenerator()
    {
        return new WebFilterReplacementGenerator(getNodeSettings());
    }

    @Override
    protected PipelineConnector[] getConnectors()
    {
        return this.connectors;
    }

    @Override
    protected void postInit()
    {
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        String nodeID = this.getNodeSettings().getId().toString();
        WebFilterSettings readSettings = null;
        String settingsFileName = System.getProperty("uvm.settings.dir") + "/untangle-node-" + this.getAppName() + "/" + "settings_" + nodeID + ".js";
        
        try {
            readSettings = settingsManager.load( WebFilterSettings.class, settingsFileName );
        } catch (SettingsManager.SettingsException e) {
            logger.warn("Failed to load settings:",e);
        }

        /**
         * If there are still no settings, just initialize
         */
        if (readSettings == null) {
            logger.warn("No settings found - Initializing new settings.");

            WebFilterSettings settings = new WebFilterSettings();

            this.initializeCommonSettings(settings);
            this.initializeSettings(settings);

            _setSettings(settings);

        }
        else {
            logger.info("Loading Settings...");

            // UPDATE settings if necessary
            
            this.settings = readSettings;
            logger.debug("Settings: " + this.settings.toJSONString());
        }
    }


    @Override
    protected void preStart()
    {
        getDecisionEngine().removeAllUnblockedSites();
        unblockedSitesMonitor.start();
    }

    @Override
    protected void postStop()
    {
        unblockedSitesMonitor.stop();
        getDecisionEngine().removeAllUnblockedSites();
    }

    protected String generateNonce( WebFilterBlockDetails details )
    {
        return replacementGenerator.generateNonce(details);
    }

    protected Token[] generateResponse( String nonce, NodeTCPSession session )
    {
        return replacementGenerator.generateResponse( nonce, session );
    }

    /**
     * Set the current settings to new Settings
     * And save the settings to disk
     */
    protected void _setSettings( WebFilterSettings newSettings )
    {
        /**
         * Prepare settings for saving
         * This makes sure certain things are always true, such as flagged == true if blocked == true
         */
        if (newSettings.getCategories() != null) {
            for (GenericRule rule : newSettings.getCategories()) {
                if (rule.getBlocked()) rule.setFlagged(Boolean.TRUE);
            }
        }
        if (newSettings.getBlockedUrls() != null) {
            for (GenericRule rule : newSettings.getBlockedUrls()) {
                if (rule.getBlocked()) rule.setFlagged(Boolean.TRUE);
            }
        }
        if (newSettings.getBlockedMimeTypes() != null) {
            for (GenericRule rule : newSettings.getBlockedMimeTypes()) {
                if (rule.getBlocked()) rule.setFlagged(Boolean.TRUE);
            }
        }
        if (newSettings.getBlockedExtensions() != null) {
            for (GenericRule rule : newSettings.getBlockedExtensions()) {
                if (rule.getBlocked()) rule.setFlagged(Boolean.TRUE);
            }
        }

        /**
         * Save the settings
         */
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        String nodeID = this.getNodeSettings().getId().toString();
        try {
            settingsManager.save( System.getProperty("uvm.settings.dir") + "/" + "untangle-node-" + this.getAppName() + "/" + "settings_" + nodeID + ".js", newSettings );
        } catch (SettingsManager.SettingsException e) {
            logger.warn("Failed to save settings.",e);
            return;
        }

        /**
         * Change current settings
         */
        this.settings = newSettings;
        try {logger.debug("New Settings: \n" + new org.json.JSONObject(this.settings).toString(2));} catch (Exception e) {}
    }

    private List<GenericRule> _buildDefaultMimeTypeList()
    {
        List<GenericRule> mimeTypes = new LinkedList<GenericRule>();
        mimeTypes.add(new GenericRule("application/octet-stream", "unspecified data", "byte stream", "application/octet-stream mime type", null, Boolean.FALSE, Boolean.FALSE));

        mimeTypes.add(new GenericRule("application/x-msdownload", "Microsoft download", "executable", "application/x-msdownload mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/exe", "executable", "executable", "application/exe mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-exe", "executable", "executable", "application/x-exe mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/dos-exe", "DOS executable", "executable", "application/dos-exe mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-winexe", "Windows executable", "executable", "application/x-winexe mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/msdos-windows", "MS-DOS executable", "executable", "application/msdos-windows mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-msdos-program", "MS-DOS program", "executable", "application/x-msdos-program mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-oleobject", "Microsoft OLE Object", "executable", "application/x-oleobject mime type", null, Boolean.FALSE, Boolean.FALSE));

        mimeTypes.add(new GenericRule("application/x-java-applet", "Java Applet", "executable", "application/x-java-applet mime type", null, Boolean.FALSE, Boolean.FALSE));

        mimeTypes.add(new GenericRule("audio/mpegurl", "MPEG audio URLs", "audio", "audio/mpegurl mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-mpegurl", "MPEG audio URLs", "audio", "audio/x-mpegurl mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/mp3", "MP3 audio", "audio", "audio/mp3 mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-mp3", "MP3 audio", "audio", "audio/x-mp3 mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/mpeg", "MPEG audio", "audio", "audio/mpeg mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/mpg", "MPEG audio", "audio", "audio/mpg mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-mpeg", "MPEG audio", "audio", "audio/x-mpeg mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-mpg", "MPEG audio", "audio", "audio/x-mpg mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-ogg", "Ogg Vorbis", "audio", "application/x-ogg mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/m4a", "MPEG 4 audio", "audio", "audio/m4a mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/mp2", "MP2 audio", "audio", "audio/mp2 mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/mp1", "MP1 audio", "audio", "audio/mp1 mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/ogg", "Ogg Vorbis", "audio", "application/ogg mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/wav", "Microsoft WAV", "audio", "audio/wav mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-wav", "Microsoft WAV", "audio", "audio/x-wav mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-pn-wav", "Microsoft WAV", "audio", "audio/x-pn-wav mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/aac", "Advanced Audio Coding", "audio", "audio/aac mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/midi", "MIDI audio", "audio", "audio/midi mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/mpeg", "MPEG audio", "audio", "audio/mpeg mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/aiff", "AIFF audio", "audio", "audio/aiff mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-aiff", "AIFF audio", "audio", "audio/x-aiff mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-pn-aiff", "AIFF audio", "audio", "audio/x-pn-aiff mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-pn-windows-acm", "Windows ACM", "audio", "audio/x-pn-windows-acm mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-pn-windows-pcm", "Windows PCM", "audio", "audio/x-pn-windows-pcm mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/basic", "8-bit u-law PCM", "audio", "audio/basic mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-pn-au", "Sun audio", "audio", "audio/x-pn-au mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/3gpp", "3GPP", "audio", "audio/3gpp mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/3gpp-encrypted", "encrypted 3GPP", "audio", "audio/3gpp-encrypted mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/scpls", "streaming mp3 playlists", "audio", "audio/scpls mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-scpls", "streaming mp3 playlists", "audio", "audio/x-scpls mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/smil", "SMIL", "audio", "application/smil mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/sdp", "Streaming Download Project", "audio", "application/sdp mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-sdp", "Streaming Download Project", "audio", "application/x-sdp mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/amr", "AMR codec", "audio", "audio/amr mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/amr-encrypted", "AMR encrypted codec", "audio", "audio/amr-encrypted mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/amr-wb", "AMR-WB codec", "audio", "audio/amr-wb mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/amr-wb-encrypted", "AMR-WB encrypted codec", "audio", "audio/amr-wb-encrypted mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-rn-3gpp-amr", "3GPP codec", "audio", "audio/x-rn-3gpp-amr mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-rn-3gpp-amr-encrypted", "3GPP-AMR encrypted codec", "audio", "audio/x-rn-3gpp-amr-encrypted mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-rn-3gpp-amr-wb", "3gpp-AMR-WB codec", "audio", "audio/x-rn-3gpp-amr-wb mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-rn-3gpp-amr-wb-encrypted", "3gpp-AMR_WB encrypted codec", "audio", "audio/x-rn-3gpp-amr-wb-encrypted mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/streamingmedia", "Streaming Media", "audio", "application/streamingmedia mime type", null, Boolean.FALSE, Boolean.FALSE));

        mimeTypes.add(new GenericRule("video/mpeg", "MPEG video", "video", "video/mpeg mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-ms-wma", "Windows Media", "video", "audio/x-ms-wma mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("video/quicktime", "QuickTime", "video", "video/quicktime mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("video/x-ms-asf", "Microsoft ASF", "video", "video/x-ms-asf mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("video/x-msvideo", "Microsoft AVI", "video", "video/x-msvideo mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("video/x-sgi-mov", "SGI movie", "video", "video/x-sgi-mov mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("video/3gpp", "3GPP video", "video", "video/3gpp mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("video/3gpp-encrypted", "3GPP encrypted video", "video", "video/3gpp-encrypted mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("video/3gpp2", "3GPP2 video", "video", "video/3gpp2 mime type", null, Boolean.FALSE, Boolean.FALSE));

        mimeTypes.add(new GenericRule("audio/x-realaudio", "RealAudio", "audio", "audio/x-realaudio mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("text/vnd.rn-realtext", "RealText", "text", "text/vnd.rn-realtext mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/vnd.rn-realaudio", "RealAudio", "audio", "audio/vnd.rn-realaudio mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-pn-realaudio", "RealAudio plug-in", "audio", "audio/x-pn-realaudio mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("image/vnd.rn-realpix", "RealPix", "image", "image/vnd.rn-realpix mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/vnd.rn-realmedia", "RealMedia", "video", "application/vnd.rn-realmedia mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/vnd.rn-realmedia-vbr", "RealMedia VBR", "video", "application/vnd.rn-realmedia-vbr mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/vnd.rn-realmedia-secure", "secure RealMedia", "video", "application/vnd.rn-realmedia-secure mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/vnd.rn-realaudio-secure", "secure RealAudio", "audio", "application/vnd.rn-realaudio-secure mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/x-realaudio-secure", "secure RealAudio", "audio", "audio/x-realaudio-secure mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("video/vnd.rn-realvideo-secure", "secure RealVideo", "video", "video/vnd.rn-realvideo-secure mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("video/vnd.rn-realvideo", "RealVideo", "video", "video/vnd.rn-realvideo mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/vnd.rn-realsystem-rmj", "RealSystem media", "video", "application/vnd.rn-realsystem-rmj mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/vnd.rn-realsystem-rmx", "RealSystem secure media", "video", "application/vnd.rn-realsystem-rmx mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("audio/rn-mpeg", "MPEG audio", "audio", "audio/rn-mpeg mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-shockwave-flash", "Macromedia Shockwave", "multimedia", "application/x-shockwave-flash mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-director", "Macromedia Shockwave", "multimedia", "application/x-director mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-authorware-bin", "Macromedia Authorware binary", "multimedia", "application/x-authorware-bin mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-authorware-map", "Macromedia Authorware shocked file", "multimedia", "application/x-authorware-map mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-authorware-seg", "Macromedia Authorware shocked packet", "multimedia", "application/x-authorware-seg mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/futuresplash", "Macromedia FutureSplash", "multimedia", "application/futuresplash mime type", null, Boolean.FALSE, Boolean.FALSE));

        mimeTypes.add(new GenericRule("application/zip", "ZIP", "archive", "application/zip mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-lzh", "LZH archive", "archive", "application/x-lzh mime type", null, Boolean.FALSE, Boolean.FALSE));

        mimeTypes.add(new GenericRule("image/gif", "Graphics Interchange Format", "image", "image/gif mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("image/png", "Portable Network Graphics", "image", "image/png mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("image/jpeg", "JPEG", "image", "image/jpeg mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("image/bmp", "Microsoft BMP", "image", "image/bmp mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("image/tiff", "Tagged Image File Format", "image", "image/tiff mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("image/x-freehand", "Macromedia Freehand", "image", "image/x-freehand mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("image/x-cmu-raster", "CMU Raster", "image", "image/x-cmu-raster mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("image/x-rgb", "RGB image", "image", "image/x-rgb mime type", null, Boolean.FALSE, Boolean.FALSE));

        mimeTypes.add(new GenericRule("text/css", "cascading style sheet", "text", "text/css mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("text/html", "HTML", "text", "text/html mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("text/plain", "plain text", "text", "text/plain mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("text/richtext", "rich text", "text", "text/richtext mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("text/tab-separated-values", "tab separated values", "text", "text/tab-separated-values mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("text/xml", "XML", "text", "text/xml mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("text/xsl", "XSL", "text", "text/xsl mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("text/x-sgml", "SGML", "text", "text/x-sgml mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("text/x-vcard", "vCard", "text", "text/x-vcard mime type", null, Boolean.FALSE, Boolean.FALSE));

        mimeTypes.add(new GenericRule("application/mac-binhex40", "Macintosh BinHex", "archive", "application/mac-binhex40 mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-stuffit", "Macintosh Stuffit archive", "archive", "application/x-stuffit mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/macwriteii", "MacWrite Document", "document", "application/macwriteii mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/applefile", "Macintosh File", "archive", "application/applefile mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/mac-compactpro", "Macintosh Compact Pro", "archive", "application/mac-compactpro mime type", null, Boolean.FALSE, Boolean.FALSE));

        mimeTypes.add(new GenericRule("application/x-bzip2", "block compressed", "compressed", "application/x-bzip2 mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-shar", "shell archive", "archive", "application/x-shar mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-gtar", "gzipped tar archive", "archive", "application/x-gtar mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-gzip", "gzip compressed", "compressed", "application/x-gzip mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-tar", "4.3BSD tar archive", "archive", "application/x-tar mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-ustar", "POSIX tar archive", "archive", "application/x-ustar mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-cpio", "old cpio archive", "archive", "application/x-cpio mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-bcpio", "POSIX cpio archive", "archive", "application/x-bcpio mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-sv4crc", "System V cpio with CRC", "archive", "application/x-sv4crc mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-compress", "UNIX compressed", "compressed", "application/x-compress mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-sv4cpio", "System V cpio", "archive", "application/x-sv4cpio mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-sh", "UNIX shell script", "executable", "application/x-sh mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-csh", "UNIX csh script", "executable", "application/x-csh mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-tcl", "Tcl script", "executable", "application/x-tcl mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/x-javascript", "JavaScript", "executable", "application/x-javascript mime type", null, Boolean.FALSE, Boolean.FALSE));

        mimeTypes.add(new GenericRule("application/x-excel", "Microsoft Excel", "document", "application/x-excel mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/mspowerpoint", "Microsoft Powerpoint", "document", "application/mspowerpoint mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/msword", "Microsoft Word", "document", "application/msword mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/wordperfect5.1", "Word Perfect", "document", "application/wordperfect5.1 mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/rtf", "Rich Text Format", "document", "application/rtf mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/pdf", "Adobe Acrobat", "document", "application/pdf mime type", null, Boolean.FALSE, Boolean.FALSE));
        mimeTypes.add(new GenericRule("application/postscript", "Postscript", "document", "application/postscript mime type", null, Boolean.FALSE, Boolean.FALSE));

        return mimeTypes;
    }

    private List<GenericRule> _buildDefaultFileExtensionList()
    {
        List<GenericRule> fileExtensions = new LinkedList<GenericRule>();

        // this third column is more of a description than a category, the way the client is using it
        // the second column is being used as the "category"
        fileExtensions.add(new GenericRule("exe", "executable", "an executable file format" , ".exe file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("ocx", "executable", "an executable file format", ".ocx file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("dll", "executable", "an executable file format", ".dll file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("cab", "executable", "an ActiveX executable file format", ".cab file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("bin", "executable", "an executable file format", ".bin file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("com", "executable", "an executable file format", ".com file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("jpg", "image", "an image file format", ".jpg file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("png", "image", "an image file format", ".png file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("gif", "image", "an image file format", ".gif file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("jar", "java", "a Java file format", ".jar file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("class", "java", "a Java file format", ".class file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("swf", "flash", "the flash file format", ".swf file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("mp3", "audio", "an audio file format", ".mp3 file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("wav", "audio", "an audio file format", ".wav file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("wmf", "audio", "an audio file format", ".wmf file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("mpg", "video", "a video file format", ".mpg file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("mov", "video", "a video file format", ".mov file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("avi", "video", "a video file format", ".avi file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("hqx", "archive", "an archived file format", ".hqx file", null, Boolean.FALSE, Boolean.FALSE));
        fileExtensions.add(new GenericRule("cpt", "compression", "a compressed file format", ".cpt file", null, Boolean.FALSE, Boolean.FALSE));

        return fileExtensions;
    }
}

/*
 * $Id$
 */
package com.untangle.node.license;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Iterator;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;

import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.SettingsManager;
import com.untangle.uvm.vnet.NodeBase;
import com.untangle.uvm.vnet.PipelineConnector;
import com.untangle.uvm.util.Pulse;
import com.untangle.uvm.util.I18nUtil;
import com.untangle.uvm.node.License;
import com.untangle.uvm.node.LicenseManager;

public class LicenseManagerImpl extends NodeBase implements LicenseManager
{
    private static final String LICENSE_URL_PROPERTY = "uvm.license.url";
    private static final String DEFAULT_LICENSE_URL = "https://license.untangle.com/license.php";
    
    private static final String EXPIRED = "expired";

    /* update every 4 hours, leaves an hour window */
    private static final long TIMER_DELAY = 1000 * 60 * 60 * 4;

    private final Logger logger = Logger.getLogger(getClass());

    private final PipelineConnector[] connectors = new PipelineConnector[] {};

    /**
     * Map from the product name to the latest valid license available for this product
     * This is where the fully evaluated license are stored
     * This map stores the evaluated (validated) licenses
     */
    private Map<String, License> licenseMap = new ConcurrentHashMap<String, License>();

    /**
     * A list of all known licenses
     * These are fully evaluated (validated) license
     */
    private List<License> licenseList = new LinkedList<License>();

    /**
     * The current settings
     * Contains a list of all known licenses store locally
     * Note: the licenses in the settings don't have metadata
     */
    private LicenseSettings settings;

    /**
     * Sync task
     */
    private final LicenseSyncTask task = new LicenseSyncTask();

    /**
     * Pulse that syncs the license, this is a daemon task.
     */
    private Pulse pulse = null;

    public LicenseManagerImpl( com.untangle.uvm.node.NodeSettings nodeSettings, com.untangle.uvm.node.NodeProperties nodeProperties )
    {
        super( nodeSettings, nodeProperties );

        this.pulse = new Pulse("uvm-license", true, task);
        this.pulse.start(TIMER_DELAY);
        this._readLicenses();
        this._mapLicenses();
        try {
            reloadLicenses();
        } catch (Exception e) {
            logger.warn("Failed to reload licenses: ", e);
        }
    }

    @Override
    protected void preStop()
    {
        super.preStop();
        logger.debug("preStop()");
    }

    @Override
    protected void postStart()
    {
        logger.debug("postStart()");

        /* Reload the licenses */
        try {
            UvmContextFactory.context().licenseManager().reloadLicenses();
        } catch ( Exception ex ) {
            logger.warn( "Unable to reload the licenses." );
        }
    }

    @Override
    protected PipelineConnector[] getConnectors()
    {
        return this.connectors;
    }
    
    /**
     * Reload all of the licenses from the file system.
     */
    @Override
    public final void reloadLicenses()
    {
        // we actually want to block here so call reloadLicenses directly instead of
        // firing the pulse
        _syncLicensesWithServer();
    }

    @Override
    public final License getLicense(String identifier)
    {
        if (isGPLApp(identifier))
            return null;

        License license = this.licenseMap.get(identifier);
        if (license != null)
            return license;
        
        /**
         * Special for development environment
         * Assume all licenses are valid
         * This should be removed if you want to test the licensing in the dev environment
         */
        if (UvmContextFactory.context().isDevel()) {
            logger.warn("Creating development license: " + identifier);
            license = new License(identifier, "0000-0000-0000-0000", identifier, "Development", 0, 9999999999l, "development", 1, Boolean.TRUE, "Developer");
            this.licenseMap.put(identifier,license);
            return license;
        }

        logger.warn("No license found for: " + identifier);

        /**
         * This returns an invalid license for all other requests
         * Note: this includes the free apps, however they don't actually check the license so it won't effect behavior
         * The UI will request the license of all app (including free)
         */
        license = new License(identifier, "0000-0000-0000-0000", identifier, "Subscription", 0, 0, "invalid", 1, Boolean.FALSE, I18nUtil.marktr("No License Found"));
        this.licenseMap.put(identifier,license); /* add it to the map for faster response next time */
        return license;
    }

    @Override
    public final boolean isLicenseValid(String identifier)
    {
        if (isGPLApp(identifier))
            return true;

        License lic = getLicense(identifier);
        if (lic == null)
            return false;
        Boolean isValid = lic.getValid();
        if (isValid == null)
            return false;
        else
            return isValid;
    }

    @Override
    public final List<License> getLicenses()
    {
        return this.licenseList;
    }
    
    @Override
    public final boolean hasPremiumLicense()
    {
        return this.settings.getLicenses().size() > 0;
    }

    @Override
    public int getSeatLimit()
    {
        if ( UvmContextFactory.context().isDevel() )
            return -1;

        int seats = -1;
        for (License lic : this.settings.getLicenses()) {
            if ( ! lic.getValid() || lic.getTrial() ) // only count valid non-trials
                continue;
            if ( lic.getSeats() <= 0 ) //ignore invalid seat ranges
                continue;
            if ( lic.getSeats() > seats && seats > 0 ) //if there is already a lower count limit, ignore this one
                continue;
            seats = lic.getSeats();
        }
        return seats;
    }

    public void requestTrialLicense( String nodeName ) throws Exception
    {
        // if already have a valid license, just return
        if ( UvmContextFactory.context().licenseManager().isLicenseValid( nodeName ) )
            return;
        
        /**
         * the API specifies libitem, however libitems no longer exist
         * specify the node, but also hit the old API with the old libitem name
         */
        String licenseUrl = System.getProperty( "uvm.license.url" );
        if ( licenseUrl == null )
            licenseUrl = "https://license.untangle.com/license.php";

        String urlStr  = licenseUrl + "?action=startTrial&uid=" + UvmContextFactory.context().getServerUID() + "&node=" + nodeName;
        String oldName = nodeName.replace("node","libitem").replace("casing","libitem");
        String urlStr2 = licenseUrl + "?action=startTrial&uid=" + UvmContextFactory.context().getServerUID() + "&libitem=" + oldName;

        URL url;
        HttpClient hc;
        HttpMethod get;
        
        try {
            logger.info("Requesting Trial: " + urlStr);
            url = new URL(urlStr);
            hc = new HttpClient();
            get = new GetMethod(url.toString());
            hc.executeMethod(get);

            logger.info("Requesting Trial: " + urlStr);
            url = new URL(urlStr2);
            hc = new HttpClient();
            get = new GetMethod(url.toString());
            hc.executeMethod(get);
        } catch ( java.net.UnknownHostException e ) {
            logger.warn("Exception requesting trial license:" + e.toString());
            throw ( new Exception( "Unable to fetch trial license: DNS lookup failed.", e ) );
        } catch ( java.net.ConnectException e ) {
            logger.warn("Exception requesting trial license:" + e.toString());
            throw ( new Exception( "Unable to fetch trial license: Connection timeout.", e ) );
        } catch ( Exception e ) {
            logger.warn("Exception requesting trial license:" + e.toString());
            throw ( new Exception( "Unable to fetch trial license: " + e.toString(), e ) );
        }
         
        try { UvmContextFactory.context().licenseManager().reloadLicenses(); } catch ( Exception e ) {}
    }

    public Object getSettings()
    {
        /* These are controlled using the methods in the uvm class */
        return null;
    }

    public void setSettings(Object settings)
    {
        /* These are controlled using the methods in the uvm class */
    }


    /**
     * Initialize the settings
     * (By default there are no liceneses)
     */
    private void _initializeSettings()
    {
        logger.info("Initializing Settings...");

        List<License> licenses = new LinkedList<License>();
        this.settings = new LicenseSettings(licenses);

        this._saveSettings(this.settings);
    }

    /**
     * Read the licenses and load them into the current settings object
     */
    private synchronized void _readLicenses()
    {
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        
        try {
            this.settings = settingsManager.load( LicenseSettings.class, System.getProperty("uvm.conf.dir") + "/licenses/licenses.js" );
        } catch (SettingsManager.SettingsException e) {
            logger.error("Unable to read license file: ", e );
        }

        if (this.settings == null)
            _initializeSettings();

        /**
         * Re-compute metadata - we don't want to use value in file (could have been changed)
         */
        if (this.settings.getLicenses() != null) {
            for (License lic : this.settings.getLicenses()) {
                _setValidAndStatus(lic);
            }
        }

        return;
    }

    /**
     * This gets all the current revocations from the license server for this UID
     * and removes any licenses that have been revoked
     */
    @SuppressWarnings("unchecked") //LinkedList<LicenseRevocation> <-> LinkedList
    private synchronized void _checkRevocations()
    {
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        LinkedList<LicenseRevocation> revocations;
        boolean changed = false;

        int numDevices = _getEstimatedNumDevices();
        String uvmVersion = UvmContextFactory.context().version();
        
        logger.info("REFRESH: Checking Revocations...");
        
        try {
            String urlStr = _getLicenseUrl() + "?" + "action=getRevocations" + "&" + "uid=" + UvmContextFactory.context().getServerUID() + "&" + "numDevices=" + numDevices + "&" + "version=" + uvmVersion;
            logger.info("Downloading: \"" + urlStr + "\"");

            Object o = settingsManager.loadUrl(LinkedList.class, urlStr);
            revocations = (LinkedList<LicenseRevocation>)o;
        } catch (SettingsManager.SettingsException e) {
            logger.error("Unable to read license file: ", e );
            return;
        } catch (ClassCastException e) {
            logger.error("getRevocations returned unexpected response",e);
            return;
        }
            
        for (LicenseRevocation revoke : revocations) {
            changed |= _revokeLicense(revoke);
        }

        if ( changed )
            _saveSettings(settings);

        logger.info("REFRESH: Checking Revocations... done (modified: " + changed + ")");

        return;
    }

    /** 
     * This remove a license from the list of current licenses
     * Returns true if a license was removed, false otherwise
     */
    private synchronized boolean _revokeLicense(LicenseRevocation revoke)
    {
        if (this.settings == null || this.settings.getLicenses() == null) {
            logger.error("Invalid settings:" + this.settings);
            return false;
        }
        if (revoke == null) {
            logger.error("Invalid argument:" + revoke);
            return false;
        }

        /**
         * See if you find a match in the current licenses
         * If so, remove it
         */
        Iterator<License> itr = this.settings.getLicenses().iterator();
        while ( itr.hasNext() ) {
            License existingLicense = itr.next();
            if (existingLicense.getName().equals(revoke.getName())) {
                logger.warn("Revoking License: " + revoke.getName());
                itr.remove();
                return true;
            }
        }

        return false;
    }

    /**
     * This downloads a list of current licenese from the license server
     * Any new licenses are added. Duplicate licenses are updated if the new one grants better privleges
     */
    @SuppressWarnings("unchecked") //LinkedList<License> <-> LinkedList
    private synchronized void _downloadLicenses()
    {
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        LinkedList<License> licenses;
        boolean changed = false;

        int numDevices = _getEstimatedNumDevices();
        String uvmVersion = UvmContextFactory.context().version();
        
        logger.info("REFRESH: Downloading new Licenses...");
        
        try {
            String urlStr = _getLicenseUrl() + "?" + "action=getLicenses" + "&" + "uid=" + UvmContextFactory.context().getServerUID() + "&" + "numDevices=" + numDevices + "&" + "version=" + uvmVersion;
            logger.info("Downloading: \"" + urlStr + "\"");

            Object o = settingsManager.loadUrl(LinkedList.class, urlStr);
            licenses = (LinkedList<License>)o;
        } catch (SettingsManager.SettingsException e) {
            logger.error("Unable to read license file: ", e );
            return;
        } catch (ClassCastException e) {
            logger.error("getRevocations returned unexpected response",e);
            return;
        }
        
        for (License lic : licenses) {
            changed |= _insertOrUpdate(lic);
        }

        if ( changed ) 
            _saveSettings(settings);

        logger.info("REFRESH: Downloading new Licenses... done (changed: " + changed + ")");

        return;
    }

    /**
     * This takes the passed argument and inserts it into the current licenses
     * If there is currently an existing license for that product it will be removed
     * Returns true if a license was added or modified, false otherwise
     */
    private synchronized boolean _insertOrUpdate(License license)
    {
        boolean insertNewLicense = true;
        
        if (this.settings == null || this.settings.getLicenses() == null) {
            logger.error("Invalid settings:" + this.settings);
            return false;
        }
        if (license == null) {
            logger.error("Invalid argument:" + license);
            return false;
        }

        /**
         * See if you find a match in the current licenses
         * If so, the new one replaces it so remove the existing one
         */
        Iterator<License> itr = this.settings.getLicenses().iterator();
        while ( itr.hasNext() ) {
            License existingLicense = itr.next();
            if (existingLicense.getName().equals(license.getName())) {

                /**
                 * As a measure of safety we only replace an existing license under certain circumstances
                 * This is so we are careful to only increase entitlements during this phase
                 */
                boolean replaceLicense = false;
                insertNewLicense = false;

                /**
                 * Check the validity of the current license
                 * If it isn't valid, we might as well try the new one
                 * Note: we have to use getLicenses to do this because the settings don't store validity
                 */
                if ( (getLicense(existingLicense.getName()) != null) && !(getLicense(existingLicense.getName()).getValid()) ) {
                    logger.info("REFRESH: Replacing license " + license + " - old one is invalid");
                    replaceLicense = true;
                }

                /**
                 * If the current one is a trial, and the new one is not, use the new one
                 */
                if ( !(License.LICENSE_TYPE_TRIAL.equals(license.getType())) && License.LICENSE_TYPE_TRIAL.equals(existingLicense.getType())) {
                    logger.info("REFRESH: Replacing license " + license + " - old one is trial");
                    replaceLicense = true;
                }

                /**
                 * If the new one has a later end date, use the new one
                 */
                if ( license.getEnd() > existingLicense.getEnd() ) {
                    logger.info("REFRESH: Replacing license " + license + " - new one has later end date");
                    replaceLicense = true;
                }

                if (replaceLicense) {
                    itr.remove();
                    insertNewLicense = true;
                } else {
                    logger.info("REFRESH: Keeping current license: " + license);
                }
            }
        }

        /**
         * if a match hasnt been found it needs to be added
         */
        if (insertNewLicense) {
            logger.info("REFRESH: Inserting new license   : " + license);
            List<License> licenses = this.settings.getLicenses();
            licenses.add(license);
            return true;
        }

        return false;
    }
    
    /**
     * update the app to License Map
     */
    private synchronized void _mapLicenses()
    {
        /* Create a new map of all of the valid licenses */
        Map<String, License> newMap = new ConcurrentHashMap<String, License>();
        LinkedList<License> newList = new LinkedList<License>();

        if (this.settings != null) {
            for (License lic : this.settings.getLicenses()) {
                /**
                 * Create a duplicate - we're about to fill in metadata
                 * But we don't want to mess with the original
                 */
                License license = new License(lic);

                /**
                 * Complete Meta-data
                 */
                _setValidAndStatus(license);
            
                String identifier = license.getName();
                License current = newMap.get(identifier);

                /* current license is newer and better */
                if ((current != null) && (current.getEnd() > license.getEnd()))
                    continue;

                logger.info("Adding License: " + license.getName() + " to Map. (valid: " + license.getValid() + ")");
            
                newMap.put(identifier, license);
                newList.add(license);
            }
        }

        this.licenseMap = newMap;
        this.licenseList = newList;
    }

    /**
     * Verify the validity of a license
     */
    private boolean _isLicenseValid(License license)
    {
        long now = (System.currentTimeMillis()/1000);

        /* check if the license hasn't started yet (start date in future) */
        if (license.getStart() > now) {
            logger.warn( "The license: " + license + " isn't valid yet (" + license.getStart() + " > " + now + ")");
            license.setStatus("Invalid (Start Date in Future)"); /* XXX i18n */
            return false;
        }

        /* check if it is already expired */
        if ((license.getEnd() < now)) {
            logger.warn( "The license: " + license + " has expired (" + license.getEnd() + " < " + now + ")");
            license.setStatus("Invalid (Expired)"); /* XXX i18n */
            return false;
        }

        /* check the UID */
        if (license.getUID() == null || !license.getUID().equals(UvmContextFactory.context().getServerUID())) {
            logger.warn( "The license: " + license + " does not match this server's UID (" + license.getUID() + " != " + UvmContextFactory.context().getServerUID() + ")");
            license.setStatus("Invalid (UID Mismatch)"); /* XXX i18n */
            return false;
        }

        String input = null;
        if ( license.getKeyVersion() == 1 ) {
            input = license.getKeyVersion() + license.getUID() + license.getName() + license.getType() + license.getStart() + license.getEnd() + "the meaning of life is 42";
        } else if ( license.getKeyVersion() == 3 ) {
            input = license.getKeyVersion() + license.getUID() + license.getName() + license.getType() + license.getStart() + license.getEnd() + nullToEmptyStr(license.getSeats()) + "the meaning of life is 42";
        }
        else {
            // versions v1,v3 are supported. v2 is for ICC
            // any other version is unknown
            license.setStatus("Invalid (Invalid Key Version)");
            return false;
        }
        //logger.info("KEY Input: " + input);

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (java.security.NoSuchAlgorithmException e) {
            logger.warn( "Unknown Algorith MD5", e);
            license.setStatus("Invalid (Invalid Algorithm)");
            return false;
        }
        byte[] digest = md.digest(input.getBytes());
        String output = _toHex(digest);
        //logger.info("KEY Output: " + output);
        //logger.info("KEY Expect: " + license.getKey());

        if (!license.getKey().equals(output)) {
            logger.warn( "Invalid key: " + output );
            license.setStatus("Invalid (Invalid Key)");
            return false;
        }

        logger.debug("License " + license + " is valid.");
        return true;
    }

    /**
     * Convert the bytes to a hex string
     */
    private String _toHex(byte data[])
    {
        String response = "";
        for (byte b : data) {
            int c = b;
            if (c < 0)
                c = c + 0x100;
            response += String.format("%02x", c);
        }

        return response;
    }

    /**
     * Set the current settings to new Settings
     * Also save the settings to disk if save is true
     */
    private void _saveSettings(LicenseSettings newSettings)
    {
        /**
         * Compute metadata before saving
         */
        for (License lic : newSettings.getLicenses()) {
            _setValidAndStatus(lic);
        }

        /**
         * Save the settings
         */
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        try {
            settingsManager.save(LicenseSettings.class, System.getProperty("uvm.conf.dir") + "/licenses/licenses.js", newSettings);
        } catch (SettingsManager.SettingsException e) {
            logger.warn("Failed to save settings.",e);
            return;
        }

        /**
         * Change current settings
         */
        this.settings = newSettings;

    }
    
    /**
     * Returns an estimate of # devices on the network
     * This is not meant to be very accurate - it is just an estimate
     */
    private int _getEstimatedNumDevices()
    {
        return UvmContextFactory.context().hostTable().getCurrentLicensedSize();
    }

    /**
     * Returns the url for the license server API
     */
    private String _getLicenseUrl()
    {
        String urlStr = System.getProperty(LICENSE_URL_PROPERTY);
        
        if (urlStr == null)
            urlStr = DEFAULT_LICENSE_URL;

        return urlStr;
    }

    /**
     * syncs the license server state with local state
     */
    private void _syncLicensesWithServer()
    {
        logger.info("Reloading licenses..." );

        synchronized (LicenseManagerImpl.this) {
            _readLicenses();

            if (! UvmContextFactory.context().isDevel()) {
                _downloadLicenses();
                _checkRevocations();
            }
                
            _mapLicenses();
        }

        logger.info("Reloading licenses... done" );
    }
    
    private class LicenseSyncTask implements Runnable
    {
        public void run()
        {
            _syncLicensesWithServer();    
        }
    }
    
    private boolean isGPLApp(String identifier)
    {
        if ("untangle-node-adblocker".equals(identifier)) return true;
        else if ("untangle-node-clam".equals(identifier)) return true;
        else if ("untangle-node-capture".equals(identifier)) return true;
        else if ("untangle-node-firewall".equals(identifier)) return true;
        else if ("untangle-node-ips".equals(identifier)) return true;
        else if ("untangle-node-openvpn".equals(identifier)) return true;
        else if ("untangle-node-phish".equals(identifier)) return true;
        else if ("untangle-node-protofilter".equals(identifier)) return true;
        else if ("untangle-node-router".equals(identifier)) return true;
        else if ("untangle-node-reporting".equals(identifier)) return true;
        else if ("untangle-node-shield".equals(identifier)) return true;
        else if ("untangle-node-spamassassin".equals(identifier)) return true;
        else if ("untangle-node-webfilter".equals(identifier)) return true;

        if ("untangle-node-license".equals(identifier)) return true;
        else if ("untangle-casing-http".equals(identifier)) return true;
        else if ("untangle-casing-ftp".equals(identifier)) return true;
        else if ("untangle-casing-smtp".equals(identifier)) return true;
        
        return false;
    }

    private void _setValidAndStatus(License license)
    {
        if (_isLicenseValid(license)) {
            license.setValid(Boolean.TRUE);
            license.setStatus("Valid");
        } else {
            license.setValid(Boolean.FALSE);
        }
    }

    private static String nullToEmptyStr( Object foo )
    {
        if ( foo == null )
            return "";
        else
            return foo.toString();
    }
}

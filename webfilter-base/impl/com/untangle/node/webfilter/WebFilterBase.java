/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.node.webfilter;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.untangle.node.http.HttpRequestEvent;
import com.untangle.node.http.RequestLine;
import com.untangle.node.http.UserWhitelistMode;
import com.untangle.node.token.Header;
import com.untangle.node.token.Token;
import com.untangle.node.token.TokenAdaptor;
import com.untangle.node.util.PartialListUtil;
import com.untangle.uvm.LocalAppServerManager;
import com.untangle.uvm.LocalUvmContext;
import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.logging.EventLogger;
import com.untangle.uvm.logging.EventLoggerFactory;
import com.untangle.uvm.logging.EventManager;
import com.untangle.uvm.logging.ListEventFilter;
import com.untangle.uvm.logging.SimpleEventFilter;
import com.untangle.uvm.message.BlingBlinger;
import com.untangle.uvm.message.Counters;
import com.untangle.uvm.message.LocalMessageManager;
import com.untangle.uvm.node.IPMaddrRule;
import com.untangle.uvm.node.IPMaddrValidator;
import com.untangle.uvm.node.MimeType;
import com.untangle.uvm.node.MimeTypeRule;
import com.untangle.uvm.node.NodeContext;
import com.untangle.uvm.node.StringRule;
import com.untangle.uvm.node.Validator;
import com.untangle.uvm.util.I18nUtil;
import com.untangle.uvm.util.OutsideValve;
import com.untangle.uvm.util.TransactionWork;
import com.untangle.uvm.vnet.AbstractNode;
import com.untangle.uvm.vnet.Affinity;
import com.untangle.uvm.vnet.Fitting;
import com.untangle.uvm.vnet.PipeSpec;
import com.untangle.uvm.vnet.SoloPipeSpec;
import com.untangle.uvm.vnet.TCPSession;
import org.apache.catalina.Valve;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;

/**
 * Implementation of the Web Filter.
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
public abstract class WebFilterBase extends AbstractNode implements WebFilter
{
    private static int deployCount = 0;

    private final Logger logger = Logger.getLogger(getClass());

    protected final WebFilterFactory factory = new WebFilterFactory(this);

    private final PipeSpec httpPipeSpec = new SoloPipeSpec
        ("http-blocker", this, new TokenAdaptor(this, factory), Fitting.HTTP_TOKENS,
         Affinity.CLIENT, 0);
    private final PipeSpec[] pipeSpecs = new PipeSpec[] { httpPipeSpec };

    private final WebFilterReplacementGenerator replacementGenerator;

    private final EventLogger<WebFilterEvent> eventLogger;

    private volatile WebFilterSettings settings;

    private final PartialListUtil listUtil = new PartialListUtil();
    private final BlacklistCategoryHandler categoryHandler = new BlacklistCategoryHandler();

    private final BlingBlinger scanBlinger;
    private final BlingBlinger passBlinger;
    private final BlingBlinger blockBlinger;
    private final BlingBlinger passLogBlinger;

    // constructors -----------------------------------------------------------

    public WebFilterBase()
    {
        replacementGenerator = new WebFilterReplacementGenerator(getTid());
        NodeContext tctx = getNodeContext();
        eventLogger = EventLoggerFactory.factory().getEventLogger(tctx);
        SimpleEventFilter sef = new WebFilterBlockedFilter(this);
        eventLogger.addSimpleEventFilter(sef);
        ListEventFilter lef = new WebFilterAllFilter(this);
        eventLogger.addListEventFilter(lef);
        sef = new WebFilterWhitelistFilter(this);
        eventLogger.addSimpleEventFilter(sef);
        lef = new WebFilterPassedFilter(this);
        eventLogger.addListEventFilter(lef);

        LocalMessageManager lmm = LocalUvmContextFactory.context()
            .localMessageManager();
        Counters c = lmm.getCounters(getTid());
        scanBlinger = c.addActivity("scan", I18nUtil.marktr("Pages scanned"), null, I18nUtil.marktr("SCAN"));
        blockBlinger = c.addActivity("block", I18nUtil.marktr("Pages blocked"), null, I18nUtil.marktr("BLOCK"));
        passBlinger = c.addActivity("pass", I18nUtil.marktr("Pages passed"), null, I18nUtil.marktr("PASS"));
        passLogBlinger = c.addMetric("log", I18nUtil.marktr("Passed by policy"), null);
        lmm.setActiveMetricsIfNotSet(getTid(), scanBlinger, blockBlinger, passBlinger, passLogBlinger);
    }

    // WebFilter methods ------------------------------------------------------

    public Object getSnmpValue(int id)
    {
        switch (id) {
        case 7: // scan
            return scanBlinger.getCount();
        case 8: // block
            return blockBlinger.getCount();
        case 9: // pass
            return passBlinger.getCount();
        default:
            logger.warn("unknown snmp id: " + id);
            return null;
        }
    }


    public WebFilterSettings getWebFilterSettings()
    {
        if (settings == null)
            logger.error("Settings not yet initialized. State: " + getNodeContext().getRunState());
        return settings;
    }

    public void setWebFilterSettings(final WebFilterSettings settings)
    {
        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    s.saveOrUpdate(settings);
                    WebFilterBase.this.settings = settings;
                    return true;
                }

                public Object getResult() { return null; }
            };
        getNodeContext().runTransaction(tw);

        getBlacklist().configure(settings);
        reconfigure();
    }

    public EventManager<WebFilterEvent> getEventManager()
    {
        return eventLogger;
    }

    public UserWhitelistMode getUserWhitelistMode()
    {
        return settings.getBaseSettings().getUserWhitelistMode();
    }

    public boolean isHttpsEnabled()
    {
        return settings.getBaseSettings().getEnableHttps();
    }

    public WebFilterBlockDetails getDetails(String nonce)
    {
        return replacementGenerator.getNonceData(nonce);
    }

    // XXX factor with SpywareImpl.unblockSite
    public boolean unblockSite(String nonce, boolean global)
    {
        WebFilterBlockDetails bd = replacementGenerator.removeNonce(nonce);

        switch (settings.getBaseSettings().getUserWhitelistMode()) {
        case NONE:
            logger.debug("attempting to unblock in UserWhitelistMode.NONE");
            return false;
        case USER_ONLY:
            if (global) {
                logger.debug("attempting to unblock global in UserWhitelistMode.USER_ONLY");
                return false;
            }
        case USER_AND_GLOBAL:
            // its all good
            break;
        default:
            logger.error("missing case: " + settings.getBaseSettings().getUserWhitelistMode());
            break;
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
                StringRule sr = new StringRule(site, site, "user whitelisted",
                                               "whitelisted by user", true);
                settings.getPassedUrls().add(sr);
                setWebFilterSettings(settings);

                return true;
            }
        } else {
            String site = bd.getWhitelistHost();
            if (null == site) {
                logger.warn("cannot unblock null host");
                return false;
            } else {
                String url = "http://" + site;
                logger.warn("temporarily unblocking site: " + site);
                InetAddress addr = bd.getClientAddress();

                getBlacklist().addWhitelistHost(addr, site);

                return true;
            }
        }
    }

    /**
     * Causes the blacklist to populate its arrays.
     */
    public void reconfigure()
    {
        LocalUvmContextFactory.context().newThread(new Runnable() {
                public void run() {
                    getBlacklist().reconfigure();
                }
            }).start();
    }

    public WebFilterBaseSettings getBaseSettings() {
        if (settings == null) {
            return null;
        } else {
            return settings.getBaseSettings();
        }
    }

    public void setBaseSettings(final WebFilterBaseSettings baseSettings) {
        TransactionWork tw = new TransactionWork() {
                public boolean doWork(Session s) {
                    settings.setBaseSettings(baseSettings);
                    s.merge(settings);
                    return true;
                }

                public Object getResult() {
                    return null;
                }
            };
        getNodeContext().runTransaction(tw);
    }

    public List<BlacklistCategory> getBlacklistCategories(int start, int limit,
                                                          String... sortColumns) {
        return listUtil.getItems(
                                 "select blacklistCategory from WebFilterSettings hbs " +
                                 "join hbs.blacklistCategories as blacklistCategory where hbs.tid = :tid ",
                                 getNodeContext(), getTid(), "blacklistCategory", start, limit, sortColumns);
    }

    public List<StringRule> getBlockedExtensions(int start, int limit,
                                                 String... sortColumns) {
        return listUtil.getItems(
                                 "select hbs.blockedExtensions from WebFilterSettings hbs where hbs.tid = :tid ",
                                 getNodeContext(), getTid(), start, limit, sortColumns);
    }

    public List<MimeTypeRule> getBlockedMimeTypes(int start, int limit,
                                                  String... sortColumns) {
        return listUtil.getItems(
                                 "select blockedMimeType from WebFilterSettings hbs " +
                                 "join hbs.blockedMimeTypes as blockedMimeType where hbs.tid = :tid ",
                                 getNodeContext(), getTid(), "blockedMimeType", start, limit, sortColumns);
    }

    public List<StringRule> getBlockedUrls(int start, int limit,
                                           String... sortColumns) {
        return listUtil.getItems(
                                 "select hbs.blockedUrls from WebFilterSettings hbs where hbs.tid = :tid ",
                                 getNodeContext(), getTid(), start, limit, sortColumns);
    }

    public List<IPMaddrRule> getPassedClients(int start, int limit,
                                              String... sortColumns) {
        return listUtil.getItems(
                                 "select hbs.passedClients from WebFilterSettings hbs where hbs.tid = :tid ",
                                 getNodeContext(), getTid(), start, limit, sortColumns);
    }

    public List<StringRule> getPassedUrls(int start, int limit,
                                          String... sortColumns) {
        return listUtil.getItems(
                                 "select hbs.passedUrls from WebFilterSettings hbs where hbs.tid = :tid ",
                                 getNodeContext(), getTid(), start, limit, sortColumns);
    }

    public void updateBlacklistCategories(List<BlacklistCategory> added,
                                          List<Long> deleted, List<BlacklistCategory> modified) {
        updateCategories(getWebFilterSettings().getBlacklistCategories(), added, deleted, modified);
    }

    public void updateBlockedExtensions(List<StringRule> added,
                                        List<Long> deleted, List<StringRule> modified) {
        updateRules(getWebFilterSettings().getBlockedExtensions(), added,
                    deleted, modified);
    }

    public void updateBlockedMimeTypes(List<MimeTypeRule> added,
                                       List<Long> deleted, List<MimeTypeRule> modified) {
        updateRules(getWebFilterSettings().getBlockedMimeTypes(), added, deleted, modified);
    }

    public void updateBlockedUrls(List<StringRule> added, List<Long> deleted,
                                  List<StringRule> modified) {
        updateRules(getWebFilterSettings().getBlockedUrls(), added, deleted, modified);
    }

    public void updatePassedClients(List<IPMaddrRule> added,
                                    List<Long> deleted, List<IPMaddrRule> modified) {
        updateRules(getWebFilterSettings().getPassedClients(), added, deleted, modified);
    }

    public void updatePassedUrls(List<StringRule> added, List<Long> deleted,
                                 List<StringRule> modified) {
        updateRules(getWebFilterSettings().getPassedUrls(), added, deleted, modified);
    }

    public void updateAll(final WebFilterBaseSettings baseSettings,
                          final List[] passedClients, final List[] passedUrls, final List[] blockedUrls,
                          final List[] blockedMimeTypes, final List[] blockedExtensions,
                          final List[] blacklistCategories) {

        TransactionWork tw = new TransactionWork() {
                public boolean doWork(Session s) {
                    if (baseSettings != null) {
                        settings.setBaseSettings(baseSettings);
                    }

                    listUtil.updateCachedItems(getWebFilterSettings().getPassedClients(), passedClients);
                    listUtil.updateCachedItems(getWebFilterSettings().getPassedUrls(), passedUrls);
                    listUtil.updateCachedItems(getWebFilterSettings().getBlockedUrls(), blockedUrls);
                    listUtil.updateCachedItems(getWebFilterSettings().getBlockedMimeTypes(), blockedMimeTypes);
                    listUtil.updateCachedItems(getWebFilterSettings().getBlockedExtensions(), blockedExtensions);
                    listUtil.updateCachedItems(getWebFilterSettings().getBlacklistCategories(), categoryHandler, blacklistCategories);

                    settings = (WebFilterSettings)s.merge(settings);

                    return true;
                }

                public Object getResult() {
                    return null;
                }
            };
        getNodeContext().runTransaction(tw);


        getBlacklist().configure(settings);
        reconfigure();
    }

    public Validator getValidator() {
        return new IPMaddrValidator();
    }

    protected abstract Blacklist getBlacklist();

    protected abstract String getVendor();

    public abstract String getNodeTitle();

    // Node methods ------------------------------------------------------

    @Override
    protected PipeSpec[] getPipeSpecs()
    {
        return pipeSpecs;
    }

    public void initializeSettings()
    {
        if (logger.isDebugEnabled()) {
            logger.debug(getTid() + " init settings");
        }

        WebFilterSettings settings = new WebFilterSettings(getTid());

        Set s = new HashSet<StringRule>();

        // this third column is more of a description than a category, the way the client is using it
        // the second column is being used as the "category"
        s.add(new StringRule("exe", "executable", "an executable file format" , false));
        s.add(new StringRule("ocx", "executable", "an executable file format", false));
        s.add(new StringRule("dll", "executable", "an executable file format", false));
        s.add(new StringRule("cab", "executable", "an ActiveX executable file format", false));
        s.add(new StringRule("bin", "executable", "an executable file format", false));
        s.add(new StringRule("com", "executable", "an executable file format", false));
        s.add(new StringRule("jpg", "image", "an image file format", false));
        s.add(new StringRule("png", "image", "an image file format", false));
        s.add(new StringRule("gif", "image", "an image file format", false));
        s.add(new StringRule("jar", "java", "a Java file format", false));
        s.add(new StringRule("class", "java", "a Java file format", false));
        s.add(new StringRule("swf", "flash", "the flash file format", false));
        s.add(new StringRule("mp3", "audio", "an audio file format", false));
        s.add(new StringRule("wav", "audio", "an audio file format", false));
        s.add(new StringRule("wmf", "audio", "an audio file format", false));
        s.add(new StringRule("mpg", "video", "a video file format", false));
        s.add(new StringRule("mov", "video", "a video file format", false));
        s.add(new StringRule("avi", "video", "a video file format", false));
        s.add(new StringRule("hqx", "archive", "an archived file format", false));
        s.add(new StringRule("cpt", "compression", "a compressed file format", false));

        settings.setBlockedExtensions(s);

        s = new HashSet<MimeTypeRule>();
        s.add(new MimeTypeRule(new MimeType("application/octet-stream"), "unspecified data", "byte stream", false));

        s.add(new MimeTypeRule(new MimeType("application/x-msdownload"), "Microsoft download", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/exe"), "executable", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-exe"), "executable", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/dos-exe"), "DOS executable", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-winexe"), "Windows executable", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/msdos-windows"), "MS-DOS executable", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-msdos-program"), "MS-DOS program", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-oleobject"), "Microsoft OLE Object", "executable", false));

        s.add(new MimeTypeRule(new MimeType("application/x-java-applet"), "Java Applet", "executable", false));

        s.add(new MimeTypeRule(new MimeType("audio/mpegurl"), "MPEG audio URLs", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-mpegurl"), "MPEG audio URLs", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mp3"), "MP3 audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-mp3"), "MP3 audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mpeg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mpg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-mpeg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-mpg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/x-ogg"), "Ogg Vorbis", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/m4a"), "MPEG 4 audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mp2"), "MP2 audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mp1"), "MP1 audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/ogg"), "Ogg Vorbis", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/wav"), "Microsoft WAV", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-wav"), "Microsoft WAV", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-wav"), "Microsoft WAV", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/aac"), "Advanced Audio Coding", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/midi"), "MIDI audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mpeg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/aiff"), "AIFF audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-aiff"), "AIFF audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-aiff"), "AIFF audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-windows-acm"), "Windows ACM", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-windows-pcm"), "Windows PCM", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/basic"), "8-bit u-law PCM", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-au"), "Sun audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/3gpp"), "3GPP", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/3gpp-encrypted"), "encrypted 3GPP", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/scpls"), "streaming mp3 playlists", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-scpls"), "streaming mp3 playlists", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/smil"), "SMIL", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/sdp"), "Streaming Download Project", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/x-sdp"), "Streaming Download Project", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/amr"), "AMR codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/amr-encrypted"), "AMR encrypted codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/amr-wb"), "AMR-WB codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/amr-wb-encrypted"), "AMR-WB encrypted codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-rn-3gpp-amr"), "3GPP codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-rn-3gpp-amr-encrypted"), "3GPP-AMR encrypted codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-rn-3gpp-amr-wb"), "3gpp-AMR-WB codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-rn-3gpp-amr-wb-encrypted"), "3gpp-AMR_WB encrypted codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/streamingmedia"), "Streaming Media", "audio", false));

        s.add(new MimeTypeRule(new MimeType("video/mpeg"), "MPEG video", "video", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-ms-wma"), "Windows Media", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/quicktime"), "QuickTime", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/x-ms-asf"), "Microsoft ASF", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/x-msvideo"), "Microsoft AVI", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/x-sgi-mov"), "SGI movie", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/3gpp"), "3GPP video", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/3gpp-encrypted"), "3GPP encrypted video", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/3gpp2"), "3GPP2 video", "video", false));

        s.add(new MimeTypeRule(new MimeType("audio/x-realaudio"), "RealAudio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("text/vnd.rn-realtext"), "RealText", "text", false));
        s.add(new MimeTypeRule(new MimeType("audio/vnd.rn-realaudio"), "RealAudio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-realaudio"), "RealAudio plug-in", "audio", false));
        s.add(new MimeTypeRule(new MimeType("image/vnd.rn-realpix"), "RealPix", "image", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realmedia"), "RealMedia", "video", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realmedia-vbr"), "RealMedia VBR", "video", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realmedia-secure"), "secure RealMedia", "video", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realaudio-secure"), "secure RealAudio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-realaudio-secure"), "secure RealAudio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("video/vnd.rn-realvideo-secure"), "secure RealVideo", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/vnd.rn-realvideo"), "RealVideo", "video", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realsystem-rmj"), "RealSystem media", "video", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realsystem-rmx"), "RealSystem secure media", "video", false));
        s.add(new MimeTypeRule(new MimeType("audio/rn-mpeg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/x-shockwave-flash"), "Macromedia Shockwave", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/x-director"), "Macromedia Shockwave", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/x-authorware-bin"), "Macromedia Authorware binary", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/x-authorware-map"), "Macromedia Authorware shocked file", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/x-authorware-seg"), "Macromedia Authorware shocked packet", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/futuresplash"), "Macromedia FutureSplash", "multimedia", false));

        s.add(new MimeTypeRule(new MimeType("application/zip"), "ZIP", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-lzh"), "LZH archive", "archive", false));

        s.add(new MimeTypeRule(new MimeType("image/gif"), "Graphics Interchange Format", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/png"), "Portable Network Graphics", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/jpeg"), "JPEG", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/bmp"), "Microsoft BMP", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/tiff"), "Tagged Image File Format", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/x-freehand"), "Macromedia Freehand", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/x-cmu-raster"), "CMU Raster", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/x-rgb"), "RGB image", "image", false));

        s.add(new MimeTypeRule(new MimeType("text/css"), "cascading style sheet", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/html"), "HTML", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/plain"), "plain text", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/richtext"), "rich text", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/tab-separated-values"), "tab separated values", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/xml"), "XML", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/xsl"), "XSL", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/x-sgml"), "SGML", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/x-vcard"), "vCard", "text", false));

        s.add(new MimeTypeRule(new MimeType("application/mac-binhex40"), "Macintosh BinHex", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-stuffit"), "Macintosh Stuffit archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/macwriteii"), "MacWrite Document", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/applefile"), "Macintosh File", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/mac-compactpro"), "Macintosh Compact Pro", "archive", false));

        s.add(new MimeTypeRule(new MimeType("application/x-bzip2"), "block compressed", "compressed", false));
        s.add(new MimeTypeRule(new MimeType("application/x-shar"), "shell archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-gtar"), "gzipped tar archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-gzip"), "gzip compressed", "compressed", false));
        s.add(new MimeTypeRule(new MimeType("application/x-tar"), "4.3BSD tar archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-ustar"), "POSIX tar archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-cpio"), "old cpio archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-bcpio"), "POSIX cpio archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-sv4crc"), "System V cpio with CRC", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-compress"), "UNIX compressed", "compressed", false));
        s.add(new MimeTypeRule(new MimeType("application/x-sv4cpio"), "System V cpio", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-sh"), "UNIX shell script", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-csh"), "UNIX csh script", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-tcl"), "Tcl script", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-javascript"), "JavaScript", "executable", false));

        s.add(new MimeTypeRule(new MimeType("application/x-excel"), "Microsoft Excel", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/mspowerpoint"), "Microsoft Powerpoint", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/msword"), "Microsoft Word", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/wordperfect5.1"), "Word Perfect", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/rtf"), "Rich Text Format", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/pdf"), "Adobe Acrobat", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/postscript"), "Postscript", "document", false));

        settings.setBlockedMimeTypes(s);

        getBlacklist().updateToCurrentCategories(settings);

        setWebFilterSettings(settings);
    }

    @Override
    protected void postInit(String[] args)
    {
        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    Query q = s.createQuery
                        ("from WebFilterSettings hbs where hbs.tid = :tid");
                    q.setParameter("tid", getTid());
                    settings = (WebFilterSettings)q.uniqueResult();

                    getBlacklist().updateToCurrentCategories(settings);

                    boolean found = false;
                    for (BlacklistCategory bc : settings.getBlacklistCategories()) {
                        if (BlacklistCategory.UNCATEGORIZED.equals(bc.getName())) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        BlacklistCategory bc = new BlacklistCategory(BlacklistCategory.UNCATEGORIZED, "Uncategorized", "Uncategorized");
                        settings.addBlacklistCategory(bc);
                    }

                    return true;
                }

                public Object getResult() { return null; }
            };
        getNodeContext().runTransaction(tw);

        if (logger.isDebugEnabled()) {
            logger.debug("IN POSTINIT SET BLACKLIST " + settings);
        }
        getBlacklist().configure(settings);
        getBlacklist().open();
        reconfigure();

        deployWebAppIfRequired(logger);
    }

    @Override
    protected void postDestroy()
    {
        unDeployWebAppIfRequired(logger);
        getBlacklist().close();
    }

    // package protected methods ----------------------------------------------

    void log(WebFilterEvent se)
    {
        eventLogger.log(se);
    }

    void log(WebFilterEvent se, String host, int port)
    {
        eventLogger.log(se);
        if (443 == port) {
            RequestLine rl = se.getRequestLine();
            final HttpRequestEvent e = new HttpRequestEvent(rl, host, 0);
            TransactionWork tw = new TransactionWork()
                {
                    public boolean doWork(Session s)
                    {
                        s.save(e);
                        return true;
                    }
                };
            getNodeContext().runTransaction(tw);
        }
    }

    String generateNonce(WebFilterBlockDetails details)
    {
        return replacementGenerator.generateNonce(details);
    }

    Token[] generateResponse(String nonce, TCPSession session, String uri,
                             Header header, boolean persistent)
    {
        return replacementGenerator.generateResponse(nonce, session, uri,
                                                     header, persistent);
    }

    Token[] generateResponse(String nonce, TCPSession session,
                             boolean persistent)
    {
        return replacementGenerator.generateResponse(nonce, session,
                                                     persistent);
    }

    void incrementScanCount()
    {
        scanBlinger.increment();
    }

    void incrementBlockCount()
    {
        blockBlinger.increment();
    }

    void incrementPassCount()
    {
        passBlinger.increment();
    }

    void incrementPassLogCount()
    {
        passLogBlinger.increment();
    }

    // private methods --------------------------------------------------------

    private static synchronized void deployWebAppIfRequired(Logger logger)
    {
        if (0 != deployCount++) {
            return;
        }

        LocalUvmContext mctx = LocalUvmContextFactory.context();
        LocalAppServerManager asm = mctx.appServerManager();

        Valve v = new OutsideValve()
            {
                protected boolean isInsecureAccessAllowed()
                {
                    return true;
                }

                /* Unified way to determine which parameter to check */
                protected boolean isOutsideAccessAllowed()
                {
                    return false;
                }
            };

        if (null != asm.loadInsecureApp("/webfilter", "webfilter", v)) {
            logger.debug("Deployed WebFilter WebApp");
        } else {
            logger.error("Unable to deploy WebFilter WebApp");
        }
    }

    private static synchronized void unDeployWebAppIfRequired(Logger logger) {
        if (0 != --deployCount) {
            return;
        }

        LocalUvmContext mctx = LocalUvmContextFactory.context();
        LocalAppServerManager asm = mctx.appServerManager();

        if (asm.unloadWebApp("/webfilter")) {
            logger.debug("Unloaded WebFilter WebApp");
        } else {
            logger.warn("Unable to unload WebFilter WebApp");
        }
    }

    private void updateRules(final Set rules, final List added,
                             final List<Long> deleted, final List modified) {
        TransactionWork tw = new TransactionWork() {
                public boolean doWork(Session s) {
                    listUtil.updateCachedItems(rules, added, deleted, modified);

                    settings = (WebFilterSettings)s.merge(settings);

                    return true;
                }

                public Object getResult() {
                    return null;
                }
            };
        getNodeContext().runTransaction(tw);

        getBlacklist().configure(settings);
    }

    private void updateCategories(final Set categories, final List added,
                                  final List<Long> deleted, final List modified) {
        TransactionWork tw = new TransactionWork() {
                public boolean doWork(Session s) {
                    listUtil.updateCachedItems(categories, categoryHandler, added, deleted, modified);

                    settings = (WebFilterSettings)s.merge(settings);

                    return true;
                }

                public Object getResult() {
                    return null;
                }
            };
        getNodeContext().runTransaction(tw);

        getBlacklist().configure(settings);
    }

    private static class BlacklistCategoryHandler implements PartialListUtil.Handler<BlacklistCategory>
    {
        public Long getId(BlacklistCategory rule)
        {
            return rule.getId();
        }

        public void update(BlacklistCategory current, BlacklistCategory newRule)
        {
            current.update(newRule);
        }
    }
}

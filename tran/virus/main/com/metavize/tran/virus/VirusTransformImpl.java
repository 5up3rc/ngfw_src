/*
 * Copyright (c) 2003, 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.virus;

import static com.metavize.tran.util.Ascii.CRLF;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.argon.SessionMatcher;
import com.metavize.mvvm.logging.EventLogger;
import com.metavize.mvvm.logging.EventManager;
import com.metavize.mvvm.logging.SimpleEventFilter;
import com.metavize.mvvm.tapi.AbstractTransform;
import com.metavize.mvvm.tapi.Affinity;
import com.metavize.mvvm.tapi.Fitting;
import com.metavize.mvvm.tapi.IPSessionDesc;
import com.metavize.mvvm.tapi.PipeSpec;
import com.metavize.mvvm.tapi.PipelineFoundry;
import com.metavize.mvvm.tapi.Protocol;
import com.metavize.mvvm.tapi.SoloPipeSpec;
import com.metavize.mvvm.tapi.Subscription;
import com.metavize.mvvm.tran.MimeType;
import com.metavize.mvvm.tran.MimeTypeRule;
import com.metavize.mvvm.tran.StringRule;
import com.metavize.mvvm.tran.Transform;
import com.metavize.mvvm.tran.TransformContext;
import com.metavize.mvvm.util.TransactionWork;
import com.metavize.tran.mail.papi.smtp.SMTPNotifyAction;
import com.metavize.tran.token.TokenAdaptor;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;

public abstract class VirusTransformImpl extends AbstractTransform
    implements VirusTransform
{

    //Programatic defaults for the contents
    //of the "message" emails generated as a result
    //of a virus being found (and "notify" being
    //enabled.
    private static final String OUT_MOD_SUB_TEMPLATE =
      "[VIRUS] $MIMEMessage:SUBJECT$";

    // OLD
    // private static final String OUT_MOD_BODY_TEMPLATE =
    // "The attached message from $MIMEMessage:FROM$ was found to contain\r\n" +
    // "the virus \"$VirusReport:VIRUS_NAME$\".  The infected portion of the attached email was removed\r\n" +
    // "by Metavize EdgeGuard.\r\n";

    private static final String OUT_MOD_BODY_TEMPLATE =
        "The attached message from $MIMEMessage:FROM$\r\n" +
        "was found to contain the virus \"$VirusReport:VIRUS_NAME$\".\r\n"+
        "The infected portion of the message was removed by Metavize EdgeGuard.\r\n";
    private static final String OUT_MOD_BODY_SMTP_TEMPLATE =
        "The attached message from $MIMEMessage:FROM$ ($SMTPTransaction:FROM$)\r\n" +
        "was found to contain the virus \"$VirusReport:VIRUS_NAME$\".\r\n"+
        "The infected portion of the message was removed by Metavize EdgeGuard.\r\n";

    private static final String IN_MOD_SUB_TEMPLATE = OUT_MOD_SUB_TEMPLATE;
    private static final String IN_MOD_BODY_TEMPLATE = OUT_MOD_BODY_TEMPLATE;
    private static final String IN_MOD_BODY_SMTP_TEMPLATE = OUT_MOD_BODY_SMTP_TEMPLATE;

    private static final String OUT_NOTIFY_SUB_TEMPLATE =
      "[VIRUS NOTIFICATION] re: $MIMEMessage:SUBJECT$";

    private static final String OUT_NOTIFY_BODY_TEMPLATE =
        "On $MIMEHeader:DATE$ a message from $MIMEMessage:FROM$ ($SMTPTransaction:FROM$)" + CRLF +
        "was received by $SMTPTransaction:TO$.  The message was found" + CRLF +
        "to contain the virus \"$VirusReport:VIRUS_NAME$\"." + CRLF +
        "The infected portion of the message was removed by Metavize EdgeGuard";

    private static final String IN_NOTIFY_SUB_TEMPLATE = OUT_NOTIFY_SUB_TEMPLATE;
    private static final String IN_NOTIFY_BODY_TEMPLATE = OUT_NOTIFY_BODY_TEMPLATE;

    private static final PipelineFoundry FOUNDRY = MvvmContextFactory.context()
        .pipelineFoundry();

    private static final int FTP = 0;
    private static final int HTTP = 1;
    private static final int SMTP = 2;
    private static final int POP = 3;

    private final VirusScanner scanner;
    private final EventLogger<VirusEvent> eventLogger;
    private final PipeSpec[] pipeSpecs;

    private final Logger logger = Logger.getLogger(VirusTransformImpl.class);

    private VirusSettings settings;

    private static final SessionMatcher VIRUS_SESSION_MATCHER = new SessionMatcher() {
            /* Kill all sessions on ports 20, 21 and 80 */
            public boolean isMatch(com.metavize.mvvm.argon.IPSessionDesc session)
            {
                /* Don't kill any UDP Sessions */
                if (session.protocol() == com.metavize.mvvm.argon.IPSessionDesc.IPPROTO_UDP) {
                    return false;
                }

                int clientPort = session.clientPort();
                int serverPort = session.serverPort();

                /* FTP responds on port 20, server is on 21, HTTP server is on 80 */
                if (clientPort == 20 || serverPort == 21 || serverPort == 80 || serverPort == 20) {
                    return true;
                }

                /* email SMTP (25) / POP3 (110) / IMAP (143) */
                if (serverPort == 25 || serverPort == 110 || serverPort == 143) {
                    return true;
                }

                return false;
            }
        };

    // constructors -----------------------------------------------------------

    public VirusTransformImpl(VirusScanner scanner)
    {
        this.scanner = scanner;
        this.pipeSpecs = initialPipeSpecs();

        TransformContext tctx = getTransformContext();
        eventLogger = new EventLogger<VirusEvent>(tctx);

        String vendor = scanner.getVendorName();

        SimpleEventFilter ef = new VirusAllFilter(vendor);
        eventLogger.addSimpleEventFilter(ef);

        ef = new VirusInfectedFilter(vendor);
        eventLogger.addSimpleEventFilter(ef);

        ef = new VirusHttpFilter(vendor);
        eventLogger.addSimpleEventFilter(ef);

        ef = new VirusLogFilter(vendor);
        eventLogger.addSimpleEventFilter(ef);

        ef = new VirusMailFilter(vendor);
        eventLogger.addSimpleEventFilter(ef);

        ef = new VirusSmtpFilter(vendor);
        eventLogger.addSimpleEventFilter(ef);
    }

    // VirusTransform methods -------------------------------------------------

    public void setVirusSettings(final VirusSettings settings)
    {
        //TEMP hack - bscott
        ensureTemplateSettings(settings);

        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    s.saveOrUpdate(settings);
                    VirusTransformImpl.this.settings = settings;

                    virusReconfigure();

                    return true;
                }

                public Object getResult() { return null; }
            };
        getTransformContext().runTransaction(tw);
    }

    public VirusSettings getVirusSettings()
    {
        return settings;
    }

    public EventManager<VirusEvent> getEventManager()
    {
        return eventLogger;
    }

    abstract protected int getStrength();

    // Transform methods ------------------------------------------------------

    private PipeSpec[] initialPipeSpecs()
    {
        int strength = getStrength();
        PipeSpec[] result = new PipeSpec[] {
            new SoloPipeSpec("virus-ftp", this,
                             new TokenAdaptor(this, new VirusFtpFactory(this)),
                             Fitting.FTP_TOKENS, Affinity.SERVER, strength),
            new SoloPipeSpec("virus-http", this,
                             new TokenAdaptor(this, new VirusHttpFactory(this)),
                             Fitting.HTTP_TOKENS, Affinity.SERVER, strength),
            new SoloPipeSpec("virus-smtp", this,
                             new TokenAdaptor(this, new VirusSmtpFactory(this)),
                             Fitting.SMTP_TOKENS, Affinity.CLIENT, strength),
            new SoloPipeSpec("virus-pop", this,
                             new TokenAdaptor(this, new VirusPopFactory(this)),
                             Fitting.POP_TOKENS, Affinity.SERVER, strength),
            new SoloPipeSpec("virus-imap", this,
                             new TokenAdaptor(this, new VirusImapFactory(this)),
                             Fitting.IMAP_TOKENS, Affinity.SERVER, strength)
        };
        return result;
    }

    private void virusReconfigure()
    {
        // FTP
        Set subscriptions = new HashSet();
        {
            Subscription subscription = new Subscription(Protocol.TCP);
            subscriptions.add(subscription);
        }

        pipeSpecs[FTP].setSubscriptions(subscriptions);

        // HTTP
        subscriptions = new HashSet();
        if (settings.getHttpInbound().getScan()) {
            // Incoming settings for outbound sessions
            Subscription subscription = new Subscription
                (Protocol.TCP, false, true);
            subscriptions.add(subscription);
        }

        if (settings.getHttpOutbound().getScan()) {
            // Outgoing settings for inbound sessions
            Subscription subscription = new Subscription
                (Protocol.TCP, true, false);
            subscriptions.add(subscription);
        }

        pipeSpecs[HTTP].setSubscriptions(subscriptions);

        // SMTP
        subscriptions = new HashSet();
        {
            Subscription subscription = new Subscription(Protocol.TCP);
            subscriptions.add(subscription);
        }

        pipeSpecs[SMTP].setSubscriptions(subscriptions);

        // POP
        subscriptions = new HashSet();
        {
            Subscription subscription = new Subscription(Protocol.TCP);
            subscriptions.add(subscription);
        }

        pipeSpecs[POP].setSubscriptions(subscriptions);
    }

    // AbstractTransform methods ----------------------------------------------

    @Override
    protected PipeSpec[] getPipeSpecs()
    {
        return pipeSpecs;
    }

    /**
     * The settings for the IMAP/POP/SMTP
     * templates have been added to the
     * Config objects, yet not in the database
     * (9/05).  This method makes sure that
     * they are set to the programatic
     * default.
     *
     * Once we move these to the database,
     * this method is obsolete.
     */
    private void ensureTemplateSettings(VirusSettings vs) {
      vs.getIMAPInbound().setSubjectWrapperTemplate(IN_MOD_SUB_TEMPLATE);
      vs.getIMAPOutbound().setSubjectWrapperTemplate(OUT_MOD_SUB_TEMPLATE);
      vs.getIMAPInbound().setBodyWrapperTemplate(IN_MOD_BODY_TEMPLATE);
      vs.getIMAPOutbound().setBodyWrapperTemplate(OUT_MOD_BODY_TEMPLATE);

      vs.getPOPInbound().setSubjectWrapperTemplate(IN_MOD_SUB_TEMPLATE);
      vs.getPOPOutbound().setSubjectWrapperTemplate(OUT_MOD_SUB_TEMPLATE);
      vs.getPOPInbound().setBodyWrapperTemplate(IN_MOD_BODY_TEMPLATE);
      vs.getPOPOutbound().setBodyWrapperTemplate(OUT_MOD_BODY_TEMPLATE);

      vs.getSMTPInbound().setSubjectWrapperTemplate(IN_MOD_SUB_TEMPLATE);
      vs.getSMTPOutbound().setSubjectWrapperTemplate(OUT_MOD_SUB_TEMPLATE);
      vs.getSMTPInbound().setBodyWrapperTemplate(IN_MOD_BODY_SMTP_TEMPLATE);
      vs.getSMTPOutbound().setBodyWrapperTemplate(OUT_MOD_BODY_SMTP_TEMPLATE);

      vs.getSMTPInbound().setNotifySubjectTemplate(IN_NOTIFY_SUB_TEMPLATE);
      vs.getSMTPOutbound().setNotifySubjectTemplate(OUT_NOTIFY_SUB_TEMPLATE);
      vs.getSMTPInbound().setNotifyBodyTemplate(IN_NOTIFY_BODY_TEMPLATE);
      vs.getSMTPOutbound().setNotifyBodyTemplate(OUT_NOTIFY_BODY_TEMPLATE);
    }

    protected void initializeSettings()
    {
        VirusSettings vs = new VirusSettings(getTid());
        vs.setHttpInbound(new VirusConfig(true, true, "Scan incoming HTTP files on outbound sessions"));
        vs.setHttpOutbound(new VirusConfig(false, true, "Scan outgoing HTTP files on inbound sessions" ));
        vs.setFtpInbound(new VirusConfig(true, true, "Scan incoming FTP files on inbound and outbound sessions" ));
        vs.setFtpOutbound(new VirusConfig(false, true, "Scan outgoing FTP files on inbound and outbound sessions" ));


        vs.setSMTPInbound(
          new VirusSMTPConfig(true,
            SMTPVirusMessageAction.REMOVE,
            SMTPNotifyAction.NEITHER,
            "Scan incoming SMTP e-mail",
            IN_MOD_SUB_TEMPLATE,
            IN_MOD_BODY_SMTP_TEMPLATE,
            IN_NOTIFY_SUB_TEMPLATE,
            IN_NOTIFY_BODY_TEMPLATE));

        vs.setSMTPOutbound(
          new VirusSMTPConfig(false,
            SMTPVirusMessageAction.PASS,
            SMTPNotifyAction.NEITHER,
            "Scan outgoing SMTP e-mail",
            OUT_MOD_SUB_TEMPLATE,
            OUT_MOD_BODY_SMTP_TEMPLATE,
            OUT_NOTIFY_SUB_TEMPLATE,
            OUT_NOTIFY_BODY_TEMPLATE));


        vs.setPOPInbound(
          new VirusPOPConfig(true,
            VirusMessageAction.REMOVE,
            "Scan incoming POP e-mail",
            IN_MOD_SUB_TEMPLATE,
            IN_MOD_BODY_TEMPLATE));

        vs.setPOPOutbound(
          new VirusPOPConfig(false,
            VirusMessageAction.PASS,
            "Scan outgoing POP e-mail",
            OUT_MOD_SUB_TEMPLATE,
            OUT_MOD_BODY_TEMPLATE));


        vs.setIMAPInbound(
          new VirusIMAPConfig(true,
            VirusMessageAction.REMOVE,
            "Scan incoming IMAP e-mail",
            IN_MOD_SUB_TEMPLATE,
            IN_MOD_BODY_TEMPLATE));

        vs.setIMAPOutbound(
          new VirusIMAPConfig(false,
            VirusMessageAction.PASS,
            "Scan outgoing IMAP e-mail",
            OUT_MOD_SUB_TEMPLATE,
            OUT_MOD_BODY_TEMPLATE));


        List s = new ArrayList();
        s.add(new MimeTypeRule(new MimeType("application/x-javascript"), "JavaScript", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-shockwave-flash"), "Shockwave Flash", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-director"), "Macromedia Shockwave", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/futuresplash"), "Macromedia FutureSplash", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/x-java-applet"), "Java Applet", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/rtf"), "Rich Text Format", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/pdf"), "Adobe Acrobat", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/postscript"), "Postscript", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/*"), "applications", "misc", false));
        s.add(new MimeTypeRule(new MimeType("message/*"), "messages", "misc", true));
        s.add(new MimeTypeRule(new MimeType("image/*"), "images", "image", false));
        s.add(new MimeTypeRule(new MimeType("video/*"), "video", "video", false));
        s.add(new MimeTypeRule(new MimeType("text/*"), "text", "text", false));
        s.add(new MimeTypeRule(new MimeType("audio/*"), "audio", "audio", false));

        vs.setHttpMimeTypes(s);

        s = new ArrayList();
        /* XXX Need a description here */
        s.add(new StringRule("exe", "executable", "download" , true));
        s.add(new StringRule("com", "executable", "download", true));
        s.add(new StringRule("ocx", "executable", "ActiveX", true));
        s.add(new StringRule("dll", "executable", "ActiveX", true));
        s.add(new StringRule("cab", "executable", "ActiveX", true));
        s.add(new StringRule("bin", "executable", "download", true));
        s.add(new StringRule("bat", "executable", "download", true));
        s.add(new StringRule("pif", "executable", "download" , true));
        s.add(new StringRule("scr", "executable", "download" , true));
        s.add(new StringRule("cpl", "executable", "download" , true));
        s.add(new StringRule("zip", "archive", "download" , true));
        s.add(new StringRule("eml", "archive", "download" , true));
        s.add(new StringRule("hqx", "archive", "download", true));
        s.add(new StringRule("rar", "archive", "download" , true));
        s.add(new StringRule("arj", "archive", "download" , true));
        s.add(new StringRule("ace", "archive", "download" , true));
        s.add(new StringRule("gz", "archive", "download" , true));
        s.add(new StringRule("tar", "archive", "download" , true));
        s.add(new StringRule("jpg", "image", "download", false));
        s.add(new StringRule("png", "image", "download", false ));
        s.add(new StringRule("gif", "image", "download", false));
        s.add(new StringRule("jar", "java", "download", false));
        s.add(new StringRule("class", "java", "download", false));
        s.add(new StringRule("mp3", "audio", "download", false));
        s.add(new StringRule("wav", "audio", "download", false));
        s.add(new StringRule("wmf", "audio", "download", false));
        s.add(new StringRule("mov", "video", "download", false));
        s.add(new StringRule("mpg", "video", "download", false));
        s.add(new StringRule("avi", "video", "download", false));
        s.add(new StringRule("swf", "flash", "download", false));
        s.add(new StringRule("mp3", "audio", "stream", false));
        s.add(new StringRule("wav", "audio", "stream", false));
        s.add(new StringRule("wmf", "audio", "stream", false));
        s.add(new StringRule("mov", "video", "stream", false));
        s.add(new StringRule("mpg", "video", "stream", false));
        s.add(new StringRule("avi", "video", "stream", false));
        vs.setExtensions(s);

        //TEMP hack - bscott
        ensureTemplateSettings(vs);

        setVirusSettings(vs);
    }

    protected void preInit(String args[])
    {
        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    Query q = s.createQuery
                        ("from VirusSettings vs where vs.tid = :tid");
                    q.setParameter("tid", getTid());
                    settings = (VirusSettings)q.uniqueResult();

                    ensureTemplateSettings(settings);
                    return true;
                }

                public Object getResult() { return null; }
            };
        getTransformContext().runTransaction(tw);
    }

    protected void preStart()
    {
        virusReconfigure();
        eventLogger.start();
    }

    protected void postStart()
    {
        shutdownMatchingSessions();
    }

    protected void postStop()
    {
        eventLogger.stop();
    }

    // package protected methods ----------------------------------------------

    VirusScanner getScanner()
    {
        return scanner;
    }

    int getTricklePercent()
    {
        return settings.getTricklePercent();
    }

    List getExtensions()
    {
        return settings.getExtensions();
    }

    List getHttpMimeTypes()
    {
        return settings.getHttpMimeTypes();
    }

    boolean getFtpDisableResume()
    {
        return settings.getFtpDisableResume();
    }

    void log(VirusEvent evt)
    {
        eventLogger.log(evt);
    }

    // XXX soon to be deprecated ----------------------------------------------

    public Object getSettings()
    {
        return getVirusSettings();
    }

    public void setSettings(Object settings)
    {
        setVirusSettings((VirusSettings)settings);
    }

    public void reconfigure()
    {
        shutdownMatchingSessions();
    }

    protected SessionMatcher sessionMatcher()
    {
        return VIRUS_SESSION_MATCHER;
    }

    /**
     * Increment the counter for messages scanned
     */
    public void incrementScanCounter() {
      incrementCount(Transform.GENERIC_0_COUNTER);
    }
    /**
     * Increment the counter for blocked (SMTP only).
     */
    public void incrementBlockCounter() {
      incrementCount(Transform.GENERIC_1_COUNTER);
    }
    /**
     * Increment the counter for messages passed
     */
    public void incrementPassCounter() {
      incrementCount(Transform.GENERIC_2_COUNTER);
    }
    /**
     * Increment the counter for messages where we
     * removed a virus
     */
    public void incrementRemoveCounter() {
      incrementCount(Transform.GENERIC_3_COUNTER);
    }
}

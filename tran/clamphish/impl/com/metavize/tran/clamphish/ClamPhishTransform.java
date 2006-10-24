/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
p * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.metavize.tran.clamphish;

import com.metavize.mvvm.tapi.Affinity;
import com.metavize.mvvm.tapi.Fitting;
import com.metavize.mvvm.tapi.PipeSpec;
import com.metavize.mvvm.tapi.SoloPipeSpec;
import com.metavize.tran.spam.SpamImpl;
import com.metavize.tran.token.TokenAdaptor;
import static com.metavize.tran.util.Ascii.CRLF;

public class ClamPhishTransform extends SpamImpl
{

    private static final String OUT_MOD_SUB_TEMPLATE =
      "[PHISH] $MIMEMessage:SUBJECT$";
    private static final String OUT_MOD_BODY_TEMPLATE =
        "The attached message from $MIMEMessage:FROM$\r\n" +
        "was determined by Untangle Networks EdgeGuard to be PHISH (a fraudulent email\r\n" +
        "intended to steal information).  The kind of PHISH that was found was\r\n" +
        "$SPAMReport:FULL$";
  
    private static final String IN_MOD_SUB_TEMPLATE = OUT_MOD_SUB_TEMPLATE;
    private static final String IN_MOD_BODY_TEMPLATE = OUT_MOD_BODY_TEMPLATE;

    private static final String OUT_MOD_BODY_SMTP_TEMPLATE =
        "The attached message from $MIMEMessage:FROM$ ($SMTPTransaction:FROM$)\r\n" +
        "was determined by Untangle Networks EdgeGuard to be PHISH (a fraudulent email\r\n" +
        "intended to steal information).  The kind of PHISH that was found was\r\n" +
        "$SPAMReport:FULL$";

    private static final String IN_MOD_BODY_SMTP_TEMPLATE = OUT_MOD_BODY_SMTP_TEMPLATE;

    private static final String PHISH_HEADER_NAME = "X-Phish-Flag";

    private static final String OUT_NOTIFY_SUB_TEMPLATE =
      "[PHISH NOTIFICATION] re: $MIMEMessage:SUBJECT$";
  
    private static final String OUT_NOTIFY_BODY_TEMPLATE =
        "On $MIMEHeader:DATE$ a message from $MIMEMessage:FROM$ ($SMTPTransaction:FROM$)\r\n" +
        "was received by $SMTPTransaction:TO$.  The message was determined\r\n" +
        "by Untangle Networks EdgeGuard to be PHISH (a fraudulent email intended\r\n" +
        "to steal information).  The kind of PHISH that was found was\r\n" +
        "$SPAMReport:FULL$";
  
    private static final String IN_NOTIFY_SUB_TEMPLATE = OUT_NOTIFY_SUB_TEMPLATE;
    private static final String IN_NOTIFY_BODY_TEMPLATE = OUT_NOTIFY_BODY_TEMPLATE;    

    // We want to make sure that phish is before spam,
    // before virus in the pipeline (towards the client for smtp,
    // server for pop/imap).
    private final PipeSpec[] pipeSpecs = new PipeSpec[] {
        new SoloPipeSpec("phish-smtp", this, new TokenAdaptor(this, new PhishSmtpFactory(this)), Fitting.SMTP_TOKENS, Affinity.CLIENT, 12),
        new SoloPipeSpec("phish-pop", this, new TokenAdaptor(this, new PhishPopFactory(this)), Fitting.POP_TOKENS, Affinity.SERVER, 12),
        new SoloPipeSpec("phish-imap", this, new TokenAdaptor(this, new PhishImapFactory(this)), Fitting.IMAP_TOKENS, Affinity.SERVER, 12)
    };

    public ClamPhishTransform()
    {
        super(new ClamPhishScanner());
    }

    @Override
    protected PipeSpec[] getPipeSpecs()
    {
        return pipeSpecs;
    }

    @Override
    public String getDefaultSubjectWrapperTemplate(boolean inbound) {
      return inbound?IN_MOD_SUB_TEMPLATE:OUT_MOD_SUB_TEMPLATE;
    }
    
    @Override
    public String getDefaultBodyWrapperTemplate(boolean inbound) {
      return inbound?IN_MOD_BODY_TEMPLATE:OUT_MOD_BODY_TEMPLATE;
    }

    @Override
    public String getDefaultSMTPSubjectWrapperTemplate(boolean inbound) {
      return getDefaultSubjectWrapperTemplate(inbound);
    }

    @Override
    public String getDefaultSMTPBodyWrapperTemplate(boolean inbound) {
      return inbound?IN_MOD_BODY_SMTP_TEMPLATE:OUT_MOD_BODY_SMTP_TEMPLATE;
    }

    @Override
    public String getDefaultIndicatorHeaderName() {
      return PHISH_HEADER_NAME;
    }

    @Override
    public String getDefaultNotifySubjectTemplate(boolean inbound) {
      return inbound?IN_NOTIFY_SUB_TEMPLATE:OUT_NOTIFY_SUB_TEMPLATE;
    }

    @Override
    public String getDefaultNotifyBodyTemplate(boolean inbound) {
      return inbound?IN_NOTIFY_BODY_TEMPLATE:OUT_NOTIFY_BODY_TEMPLATE;
    }     

}

/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.spam;

import org.apache.log4j.Logger;
import java.net.InetAddress;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.File;
import com.metavize.tran.mime.LCString;
import com.metavize.tran.mime.MIMEUtil;
import com.metavize.tran.mime.MIMEMessage;
import com.metavize.tran.mime.MIMEOutputStream;
import com.metavize.tran.mime.HeaderParseException;
import com.metavize.tran.util.TempFileFactory;
import com.metavize.tran.mime.EmailAddress;
import static com.metavize.tran.util.Ascii.CRLF;
import com.metavize.tran.mail.papi.MessageInfo;
import com.metavize.tran.mail.papi.smtp.SmtpTransaction;
import com.metavize.tran.mail.papi.smtp.sapi.BufferingSessionHandler;
import com.metavize.tran.mail.papi.quarantine.QuarantineTransformView;
import com.metavize.tran.mail.papi.quarantine.MailSummary;
import com.metavize.tran.mail.papi.safelist.SafelistTransformView;
import com.metavize.mvvm.tapi.TCPSession;
import com.metavize.mvvm.MvvmContextFactory;
import java.util.List;


/**
 * Protocol Handler which is called-back as scanable messages
 * are encountered.
 */
public class SmtpSessionHandler
  extends BufferingSessionHandler {

  private final Logger m_logger = Logger.getLogger(SmtpSessionHandler.class);
  private final TempFileFactory m_fileFactory;
  private static final Logger m_eventLogger = MvvmContextFactory
    .context().eventLogger();

  private final SpamImpl m_spamImpl;
  private final SpamSMTPConfig m_config;
  private final QuarantineTransformView m_quarantine;
  private final SafelistTransformView m_safelist;

  public SmtpSessionHandler(TCPSession session,
    long maxClientWait,
    long maxSvrWait,
    SpamImpl impl,
    SpamSMTPConfig config,
    QuarantineTransformView quarantine,
    SafelistTransformView safelist) {

    super(config.getMsgSizeLimit(), maxClientWait, maxSvrWait, false);

    m_spamImpl = impl;
    m_quarantine = quarantine;
    m_safelist = safelist;
    m_config = config;
    m_fileFactory = new TempFileFactory(MvvmContextFactory.context().
      pipelineFoundry().getPipeline(session.id()));
  }


  /**
   * Method for subclasses (i.e. clamphish) to
   * set the
   * {@link com.metavize.tran.mail.papi.quarantine.MailSummary#getQuarantineCategory category}
   * for a Quarantine submission.
   */
  protected String getQuarantineCategory() {
    return "SPAM";
  }

  /**
   * Method for subclasses (i.e. clamphish) to
   * set the
   * {@link com.metavize.tran.mail.papi.quarantine.MailSummary#getQuarantineDetail detail}
   * for a Quarantine submission.
   */
  protected String getQuarantineDetail(SpamReport report) {
    //TODO bscott Do something real here
    return "" + report.getScore();
  }


  @Override
  public BPMEvaluationResult blockPassOrModify(MIMEMessage msg,
    SmtpTransaction tx,
    MessageInfo msgInfo) {
    m_logger.debug("[handleMessageCanBlock]");

    //I'm incrementing the count, even if the message is too big
    //or cannot be converted to file
    m_spamImpl.incrementScanCounter();

    //Scan the message
    File f = messageToFile(msg);
    if(f == null) {
      m_logger.error("Error writing to file.  Unable to scan.  Assume pass");
      m_spamImpl.incrementPassCounter();
      return PASS_MESSAGE;
    }

    if(f.length() > getGiveupSz()) {
      m_logger.debug("Message larger than " + getGiveupSz() + ".  Don't bother to scan");
      m_spamImpl.incrementPassCounter();
      return PASS_MESSAGE;
    }

    if(m_safelist.isSafelisted(tx.getFrom(),
      msg.getMMHeaders().getFrom(),
      tx.getRecipients(false))) {
      m_logger.debug("Message sender safelisted");
      m_spamImpl.incrementPassCounter();
      return PASS_MESSAGE;
    }

    SMTPSpamMessageAction action = m_config.getMsgAction();

    SpamReport report = scanFile(f);

    //Handle error case
    if(report == null) {
      m_logger.error("Error scanning message.  Assume pass");
      m_spamImpl.incrementPassCounter();
      return PASS_MESSAGE;
    }

    if(report.isSpam()) {//BEGIN SPAM
      m_logger.debug("Spam found");

      //Perform notification (if we should)
      if(m_config.getNotifyMessageGenerator().sendNotification(
        MvvmContextFactory.context().mailSender(),
        m_config.getNotifyAction(),
        msg,
        tx,
        tx, report)) {
        m_logger.debug("Notification handled without error");
      }
      else {
        m_logger.warn("Error sending notification");
      }

      if(action == SMTPSpamMessageAction.PASS) {
        m_logger.debug("Although SPAM detected, pass message as-per policy");
        markHeaders(msg, report);
        postSpamEvent(msgInfo, report, SMTPSpamMessageAction.PASS);
        m_spamImpl.incrementPassCounter();
        return PASS_MESSAGE;
      }
      else if(action == SMTPSpamMessageAction.MARK) {
        m_logger.debug("Marking message as-per policy");
        postSpamEvent(msgInfo, report, SMTPSpamMessageAction.MARK);
        markHeaders(msg, report);
        m_spamImpl.incrementMarkCounter();
        MIMEMessage wrappedMsg = m_config.getMessageGenerator().wrap(msg, tx, report);
        return new BPMEvaluationResult(wrappedMsg);
      }
      else if(action == SMTPSpamMessageAction.QUARANTINE) {
        m_logger.debug("Attempt to quarantine mail as-per policy");
        if(quarantineMail(msg, tx, report, f)) {
          m_spamImpl.incrementQuarantineCount();
          postSpamEvent(msgInfo, report, SMTPSpamMessageAction.QUARANTINE);
          return BLOCK_MESSAGE;
        }
        else {
          m_logger.debug("Quarantine failed.  Fall back to mark");
          m_spamImpl.incrementMarkCounter();
          postSpamEvent(msgInfo, report, SMTPSpamMessageAction.MARK);
          markHeaders(msg, report);
          MIMEMessage wrappedMsg = m_config.getMessageGenerator().wrap(msg, tx, report);
          return new BPMEvaluationResult(wrappedMsg);          
        }
      }
      else {//BLOCK
        m_logger.debug("Blocking SPAM message as-per policy");
        postSpamEvent(msgInfo, report, SMTPSpamMessageAction.BLOCK);
        m_spamImpl.incrementBlockCounter();
        return BLOCK_MESSAGE;
      }
    }//ENDOF SPAM
    else {//BEGIN HAM
      markHeaders(msg, report);
      postSpamEvent(msgInfo, report, SMTPSpamMessageAction.PASS);
      m_logger.debug("Not spam");
      m_spamImpl.incrementPassCounter();
      return PASS_MESSAGE;
    }//ENDOF HAM
  }


  @Override
  public BlockOrPassResult blockOrPass(MIMEMessage msg,
    SmtpTransaction tx,
    MessageInfo msgInfo) {

    m_logger.debug("[handleMessageCanNotBlock]");

    m_spamImpl.incrementScanCounter();

    //Scan the message
    File f = messageToFile(msg);
    if(f == null) {
      m_logger.error("Error writing to file.  Unable to scan.  Assume pass");
      m_spamImpl.incrementPassCounter();
      return BlockOrPassResult.PASS;
    }

    if(f.length() > getGiveupSz()) {
      m_logger.debug("Message larger than " + getGiveupSz() + ".  Don't bother to scan");
      m_spamImpl.incrementPassCounter();
      return BlockOrPassResult.PASS;
    }

    if(m_safelist.isSafelisted(tx.getFrom(),
      msg.getMMHeaders().getFrom(),
      tx.getRecipients(false))) {
      m_logger.debug("Message sender safelisted");
      m_spamImpl.incrementPassCounter();
      return BlockOrPassResult.PASS;
    }    

    SpamReport report = scanFile(f);

    //Handle error case
    if(report == null) {
      m_logger.error("Error scanning message.  Assume pass");
      postSpamEvent(msgInfo, report, SMTPSpamMessageAction.PASS);
      m_spamImpl.incrementPassCounter();
      return BlockOrPassResult.PASS;
    }

    SMTPSpamMessageAction action = m_config.getMsgAction();
    
    //Check for the impossible-to-satisfy action of "REMOVE"
    if(action == SMTPSpamMessageAction.MARK) {
      //Change action now, as it'll make the event logs
      //more accurate
      m_logger.debug("Implicitly converting policy from \"MARK\"" +
        " to \"PASS\" as we have already begun to trickle");
      action = SMTPSpamMessageAction.PASS;
    }


    if(report.isSpam()) {
      m_logger.debug("Spam");

      if(action == SMTPSpamMessageAction.PASS) {
        m_logger.debug("Although SPAM detected, pass message as-per policy");
        postSpamEvent(msgInfo, report, SMTPSpamMessageAction.PASS);
        m_spamImpl.incrementPassCounter();
        return BlockOrPassResult.PASS;
      }
      else if(action == SMTPSpamMessageAction.MARK) {
        m_logger.debug("Cannot mark at this time.  Simply pass");
        postSpamEvent(msgInfo, report, SMTPSpamMessageAction.PASS);
        m_spamImpl.incrementPassCounter();
        return BlockOrPassResult.PASS;
      }
      else if(action == SMTPSpamMessageAction.QUARANTINE) {
        m_logger.debug("Attempt to quarantine mail as-per policy");
        if(quarantineMail(msg, tx, report, f)) {
          m_logger.debug("Mail quarantined");
          postSpamEvent(msgInfo, report, SMTPSpamMessageAction.QUARANTINE);
          m_spamImpl.incrementQuarantineCount();
          return BlockOrPassResult.BLOCK;
        }
        else {
          m_logger.debug("Quarantine failed.  Fall back to pass");
          postSpamEvent(msgInfo, report, SMTPSpamMessageAction.PASS);
          m_spamImpl.incrementPassCounter();
          return BlockOrPassResult.PASS;
        }
      }
      else {//BLOCK
        m_logger.debug("Blocking SPAM message as-per policy");
        postSpamEvent(msgInfo, report, SMTPSpamMessageAction.BLOCK);
        m_spamImpl.incrementBlockCounter();
        return BlockOrPassResult.BLOCK;
      }
    }
    else {
      m_logger.debug("Not Spam");
      postSpamEvent(msgInfo, report, SMTPSpamMessageAction.PASS);
      m_spamImpl.incrementPassCounter();
      return BlockOrPassResult.PASS;
    }
  }

  private void markHeaders(MIMEMessage msg,
    SpamReport report) {
    try {
      msg.getMMHeaders().removeHeaderFields(new LCString(m_config.getHeaderName()));
      msg.getMMHeaders().addHeaderField(m_config.getHeaderName(),
        m_config.getHeaderValue(report.isSpam()));
    }
    catch(HeaderParseException shouldNotHappen) {
      m_logger.error(shouldNotHappen);
    }  
  }

  /**
   * ...name says it all...
   */
  private void postSpamEvent(MessageInfo msgInfo,
    SpamReport report,
    SMTPSpamMessageAction action) {
    
    SpamSmtpEvent spamEvent = new SpamSmtpEvent(
      msgInfo,
      report.getScore(),
      report.isSpam(),
      action,
      m_spamImpl.getScanner().getVendorName());
    m_eventLogger.info(spamEvent);    
  }

  /**
   * Wrapper that handles exceptions, and returns
   * null if there is a problem
   */
  private File messageToFile(MIMEMessage msg) {
  
    //Build the "fake" received header for SpamAssassin
    InetAddress clientAddr = getSession().getClientAddress();
    StringBuilder sb = new StringBuilder();
    sb.append("Received: ");
    sb.append("from ").append(getHELOEHLOName()).
      append(" (").append(clientAddr.getHostName()).
        append(" [").append(clientAddr.getHostAddress()).append("])").append(CRLF);
    sb.append("\tby mv-edgeguard; ").append(MIMEUtil.getRFC822Date());

    
    File ret = null;
    FileOutputStream fOut = null;
    try {
      ret = m_fileFactory.createFile("spamc_mv");
      fOut = new FileOutputStream(ret);
      BufferedOutputStream bOut = new BufferedOutputStream(fOut);
      MIMEOutputStream mimeOut = new MIMEOutputStream(bOut);
      mimeOut.writeLine(sb.toString());
      msg.writeTo(mimeOut);
      mimeOut.flush();
      bOut.flush();
      fOut.flush();
      fOut.close();
/*
      File copy = new File("TEMP_SPAM" + System.currentTimeMillis());
      FileOutputStream copyOut = new FileOutputStream(copy);
      byte[] buf = new byte[1024];
      FileInputStream copyIn = new FileInputStream(ret);
      int read = copyIn.read(buf);
      while(read > 0) {
        copyOut.write(buf, 0, read);
        read = copyIn.read(buf);
      }
      copyOut.flush();
      copyOut.close();
      copyIn.close();
*/      
      return ret;
    }
    catch(Exception ex) {
      try {fOut.close();}catch(Exception ignore){}
      try {ret.delete();}catch(Exception ignore){}
      m_logger.error("Exception writing MIME Message to file", ex);
      return null;
    }
  }

  /**
   * Wrapper method around the real scanner, which
   * swallows exceptions and simply returns null
   */
  private SpamReport scanFile(File f) {
    //Attempt scan
    try {
      SpamReport ret = m_spamImpl.getScanner().scanFile(f, m_config.getStrength()/10.0f);
      if(ret == null) {
        m_logger.error("Received ERROR SpamReport");
        return null;
      }
      return ret;
    }
    catch(Exception ex) {
      m_logger.error("Exception scanning message", ex);
      return null;
    }
  }

  private boolean quarantineMail(MIMEMessage msg,
    SmtpTransaction tx,
    SpamReport report,
    File file) {
    
    List<EmailAddress> addrList =
      tx.getRecipients(true);
    
    EmailAddress[] addresses =
      (EmailAddress[]) addrList.toArray(new EmailAddress[addrList.size()]);

    return m_quarantine.quarantineMail(file,
      new MailSummary(
        msg.getMMHeaders().getFrom()==null?
          (tx.getFrom()==null?
            "<>":
            tx.getFrom().getAddress()):
          msg.getMMHeaders().getFrom().getAddress(),
        msg.getMMHeaders().getSubject(),
        getQuarantineCategory(),
        getQuarantineDetail(report)),
      addresses);
  }
}

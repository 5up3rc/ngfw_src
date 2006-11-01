/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.mail.impl.imap;

import com.untangle.mvvm.tapi.TCPSession;
import com.untangle.tran.token.Parser;
import com.untangle.tran.token.Unparser;
import org.apache.log4j.Logger;
import com.untangle.tran.mail.impl.AbstractMailCasing;


/**
 * 'name says it all...
 */
class ImapCasing
  extends AbstractMailCasing {

  private final Logger m_logger =
    Logger.getLogger(ImapCasing.class);  

  private static final boolean TRACE = false;    

  private final ImapParser m_parser;
  private final ImapUnparser m_unparser;

  
  private final ImapSessionMonitor m_sessionMonitor;
  
  ImapCasing(TCPSession session,
    boolean clientSide) {

    super(session, clientSide, "imap", TRACE);

    //This sillyness is to work around some issues
    //with classloaders and logging
    try {
      new com.untangle.tran.mail.papi.imap.CompleteImapMIMEToken(null, null);
    }
    catch(Exception ignore){}

    m_logger.debug("Created");
    m_sessionMonitor = new ImapSessionMonitor();
    m_parser = clientSide? new ImapClientParser(session, this): new ImapServerParser(session, this);
    m_unparser = clientSide? new ImapClientUnparser(session, this): new ImapServerUnparser(session, this);
  }

  /**
   * Get the SessionMonitor for this Casing, which
   * performs read-only examination of the IMAP
   * conversation looking for username, as well
   * as commands like STARTTLS which require
   * passthru
   */
  ImapSessionMonitor getSessionMonitor() {
    return m_sessionMonitor;
  }

  public Parser parser() {
    return m_parser;
  }

  public Unparser unparser() {
    return m_unparser;
  }
}

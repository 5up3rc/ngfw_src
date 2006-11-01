/**
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

import java.nio.ByteBuffer;

import com.untangle.mvvm.tapi.TCPSession;
import com.untangle.mvvm.tapi.event.TCPStreamer;
import com.untangle.tran.token.Chunk;
import com.untangle.tran.token.Token;
import com.untangle.tran.token.UnparseResult;
import org.apache.log4j.Logger;

/**
 * ...name says it all...
 */
class ImapServerUnparser
  extends ImapUnparser {

  private final Logger m_logger =
    Logger.getLogger(ImapServerUnparser.class);

  ImapServerUnparser(TCPSession session,
    ImapCasing parent) {
    super(session, parent, false);
    m_logger.debug("Created");
  }


  @Override
  protected UnparseResult doUnparse(Token token) {

    ByteBuffer buf = ((Chunk)token).getBytes();

    if(!isPassthru()) {
      if(getImapCasing().getSessionMonitor().bytesFromClient(buf.duplicate())) {
        if(!isPassthru()) {
          m_logger.warn("Declaring passthru on advice of SessionMonitor, yet " +
            "should have already been declared by other half of casing");
        }
        declarePassthru();
      }
    }
    return new UnparseResult(buf);
  }

}
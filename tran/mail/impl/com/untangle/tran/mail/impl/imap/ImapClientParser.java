/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.mail.impl.imap;

import java.nio.ByteBuffer;
import com.untangle.tran.mail.papi.imap.IMAPTokenizer;
import com.untangle.tran.mail.papi.imap.ImapChunk;
import com.untangle.mvvm.tapi.TCPSession;
import com.untangle.tran.token.PassThruToken;
import com.untangle.tran.token.Token;
import com.untangle.tran.token.Chunk;
import com.untangle.tran.token.ParseResult;
import org.apache.log4j.Logger;
import java.util.ArrayList;

/**
 * 'name says it all...
 */
class ImapClientParser
  extends ImapParser {
  
  private final Logger m_logger =
    Logger.getLogger(ImapClientParser.class);

  private final IMAPTokenizer m_tokenizer;

  private int m_toSkip = 0;   

  ImapClientParser(TCPSession session,
    ImapCasing parent) {
    
    super(session, parent, true);
    lineBuffering(false);

    m_tokenizer = new IMAPTokenizer();    
    
    m_logger.debug("Created");
  }

  @Override
  protected ParseResult doParse(ByteBuffer buf) {

    //Create copy of buffer for the case of
    //the SessionMonitor telling us to punt
    ByteBuffer dupOfWholeBuffer = buf.duplicate();
    
    //Make sure to align the buffer on whole token boundaries
    ByteBuffer dupToScan = tokenAlignBuffer(buf);
    
    //Advance original buffer to the limit
    //of the duplicate to scan.  This may
    //not be the full extend of "buf", and any
    //remaining bytes will be converted to the
    //next read buffer
    buf.position(dupToScan.limit());

    //Get SessionMonitor to inspect bytes
    if(getImapCasing().getSessionMonitor().bytesFromClient(dupToScan.duplicate())) {
      m_logger.debug("Declare passthru as-per advice of SessionMonitor");
      declarePassthru();
      ArrayList<Token> toks = new ArrayList<Token>();
      toks.add(PassThruToken.PASSTHRU);
      toks.add(new Chunk(dupOfWholeBuffer));
      return new ParseResult(toks, null);
    }

    //If we're here, then the SessionMonitor does not
    //believe we should punt.  Pass along the bytes
    //aligned on the token boundary, and return the rest
    //in the read buffer
    buf = compactIfNotEmpty(buf, m_tokenizer.getLongestWord());

    m_logger.debug("Returning ImapChunk of length: " +
      dupToScan.remaining() + " and " +
      (buf==null?"no":Integer.toString(buf.position())) +
      " bytes for next read");
    return new ParseResult(new ImapChunk(dupToScan), buf);
  }


  /**
   * Little method which creates a new view of the
   * ByteBuffer, making sure that there are no dangling
   * token suspects.  This is done by advancing the
   * tokenizer until "NEED_MORE_DATA" is returned, possibly
   * leaving some bytes for later examination.  Note that
   * literals are skipped, and this skipping is
   * "remembered" by the tokenizer.
   *
   * The returned buffer's position will be
   * the position of <code>buf</code>, at the 
   * time this method was called, yet its limit
   * may be less than buf.  The difference in length is
   * the amount to be put back into the read buffer.
   */
  private ByteBuffer tokenAlignBuffer(ByteBuffer buf) {
  
    ByteBuffer dup = buf.duplicate();
    
    while(dup.hasRemaining()) {
      IMAPTokenizer.IMAPNextResult result = m_tokenizer.next(dup);
      if(result == IMAPTokenizer.IMAPNextResult.EXCEEDED_LONGEST_WORD) {
        dup.position(buf.position());
        return dup;
      }
      if(result == IMAPTokenizer.IMAPNextResult.NEED_MORE_DATA) {
        //This may be a misalignment, so assign
        //the limit of the duplicate to its current
        //position and rewind.
        dup.limit(dup.position());
        dup.position(buf.position());
        return dup;
      }      
      //Therefore, the return must have been "HAVE_TOKEN"
      if(m_tokenizer.getTokenType() == IMAPTokenizer.IMAPTT.LITERAL) {
        m_tokenizer.skipCurrentLiteral();
      }
    }
    //If we're here, the buffer was perfectly token-aligned.  No
    //need to adjust the limit
    dup.position(buf.position());
    return dup;
  }  
  
}

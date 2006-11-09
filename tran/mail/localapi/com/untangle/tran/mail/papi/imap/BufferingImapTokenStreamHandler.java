/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.mail.papi.imap;

import com.untangle.tran.mail.papi.ContinuedMIMEToken;
import com.untangle.tran.mail.papi.MIMEAccumulator;
import com.untangle.tran.mail.papi.MessageInfo;
import com.untangle.tran.mail.papi.MessageTransmissionTimeoutStrategy;
import com.untangle.tran.token.TokenResult;
import com.untangle.tran.token.Token;
import com.untangle.tran.mime.MIMEMessage;
import org.apache.log4j.Logger;

/**
 * Handler which listens on an IMAP Token Stream, and calls
 * its {@link #handleMessage handleMessage} method when
 * an entire mail is received.  Takes care of trickling
 * and such.
 */
public abstract class BufferingImapTokenStreamHandler
  extends ImapTokenStreamHandler {

  /**
   * Type used as the return from the {@link #handleMessage handleMessage method}.
   * There are only two factory methods for obtaining results (pass/replace).
   */
  public static final class HandleMailResult {
  
    private final MIMEMessage m_msg;
    private static final HandleMailResult PASS_RESULT =
      new HandleMailResult(null);
    
    private HandleMailResult(MIMEMessage msg) {
      m_msg = msg;
    }
    
    private boolean replacedMail() {
      return m_msg != null;
    }
    private MIMEMessage getReplacedMail() {
      return m_msg;
    }
    /**
     * Create a HandleMailResult indicating that the Handler
     * wishes the MIMEMessage replaced
     *
     * @param msg the new message
     *
     * @return the result
     */ 
    public static HandleMailResult forReplaceMessage(MIMEMessage msg) {
      return new HandleMailResult(msg);
    }

    /**
     * Create a HandleMailResult indicating that the handler
     * did not replace the message.
     *
     * @return the result
     */ 
    public static HandleMailResult forPassMessage() {
      return new HandleMailResult(null);
    }
  }
  
  private final Logger m_logger =
    Logger.getLogger(BufferingImapTokenStreamHandler.class);

  private final int m_maxSize;
  private final long m_maxClientWait;
  private final long m_maxServerWait;

  //Transient data members, if we're buffering
  private MIMEAccumulator m_accumulator;
  private int m_totalMessageOctets;
  private MessageInfo m_msgInfo;

  /**
   * Construct a new BufferingImapTokenStreamHandler without
   * a limit on message size.
   *
   * @param maxClientWait the max time a client can wait for data
   *        before we give-up and begin trickling
   * @param maxServerWait the max time a server can wait for data
   *        before we give-up and begin trickling
   */
  public BufferingImapTokenStreamHandler(long maxClientWait,
    long maxServerWait) {
    this(maxClientWait, maxServerWait, Integer.MAX_VALUE);
  }

  /**
   * Construct a new BufferingImapTokenStreamHandler
   *
   * @param maxClientWait the max time a client can wait for data
   *        before we give-up and begin trickling
   * @param maxServerWait the max time a server can wait for data
   *        before we give-up and begin trickling
   * @param maxSz the maximum size of the message before buffering
   *        is abandoned and trickling begins.
   *   
   */
  public BufferingImapTokenStreamHandler(long maxClientWait,
    long maxServerWait,
    int maxSz) {
    m_maxClientWait = maxClientWait;
    m_maxServerWait = maxServerWait;
    m_maxSize = maxSz;
  }

  /**
   * Method to be overidden by subclasses.  Provides a chance
   * to pass/replace the message
   *
   * @param msg the message
   * @param msgInfo the message info
   *
   * @return the result
   */
  public abstract HandleMailResult handleMessage(MIMEMessage msg,
    MessageInfo msgInfo);



  @Override
  public final boolean handleClientFin() {
    if(m_accumulator != null) {
      m_logger.warn("client FIN arrived while buffering mail");
      m_accumulator.dispose();
      clearBufferedMessage();
    }
    else {
      m_logger.debug("[handleClientFin]");
    }
    return true;
  }
  
  @Override
  public final boolean handleServerFin() {
    if(m_accumulator != null) {
      m_logger.warn("server FIN arrived while buffering mail");
      m_accumulator.dispose();
      clearBufferedMessage();
    }
    else {  
      m_logger.debug("[handleServerFin]");
    }
    return true;
  }
  
  @Override
  public final TokenResult handleChunkFromServer(ImapChunk token) {
    m_logger.debug("[handleChunkFromServer]");
    return new TokenResult(new Token[] { token }, null);
  }
  
  @Override  
  public final TokenResult handleBeginMIMEFromServer(BeginImapMIMEToken token) {
    m_logger.debug("[handleBeginMIMEFromServer]");

    //Assign data members from the BeginMIMEToken
    m_accumulator = token.getMIMEAccumulator();
    m_totalMessageOctets = token.getMessageLength();
    m_msgInfo = token.getMessageInfo();

    //Check if it is already too big
    if(m_accumulator.fileSize() > m_maxSize) {
      m_logger.debug("BeginMIMEToken already contains more bytes than " +
        "this transform wishes to scan.  Pass it along");
      clearBufferedMessage();
      return new TokenResult(new Token[] { token }, null);
    }
    return TokenResult.NONE;
  }
  
  @Override  
  public final TokenResult handleContinuedMIMEFromServer(ContinuedMIMEToken token) {
    m_logger.debug("[handleContinuedMIMEFromServer]");

    //==== Previous giveup case ===
    if(m_accumulator == null) {
      m_logger.debug("Received ContinuedMIMEToken yet accumulator is null.  We must " +
        "have given up.  Pass along continued token");
      return new TokenResult(new Token[] { token }, null);
    }

    //Since there are three cases with the same result (we punt),
    //they have been broken off for separate logging
    //but the action takes place once
    boolean punt = false;

    //==== Too big case ===
    if((m_accumulator.fileSize() + token.length()) > m_maxSize) {
      m_logger.debug("Message too large (larger than \"" + m_maxSize + "\" bytes.  Abandon" +
        " buffering");
      punt = true;
    }

    //======== Timeout Case ====
    if(isTimingOut()) {
      m_logger.debug("Risking timeout.  Enter trickle.");
      punt = true;
    }
    
    //Attempt to append to the file
    if(!m_accumulator.appendChunkToFile(token.getMIMEChunk())) {
      m_logger.error("Error attempting to place MIMEChunk in file.  Pass along " +
        "buffered message thus far");
      punt = true;        
    }

    if(punt) {
      Token[] ret = new Token[2];
      ret[0] = new BeginImapMIMEToken(m_accumulator, m_msgInfo, m_totalMessageOctets);
      ret[1] = token;
      clearBufferedMessage();
      return new TokenResult(ret, null);        
    }

    //Check if this was the last (and we should evaluate)
    if(token.isLast()) {
      m_logger.debug("Received the last MIME chunk.  Attempt to parse and evaluate");
      MIMEMessage mimeMsg = m_accumulator.parseBody();
      if(mimeMsg == null) {
        m_logger.error("Error parsing MIMEMessage.  Pass along the bytes we have thus far");
        Token[] ret = new Token[2];
        ret[0] = new BeginImapMIMEToken(m_accumulator, m_msgInfo, m_totalMessageOctets);
        ret[1] = new ContinuedMIMEToken(m_accumulator.createChunk(null, true));
        clearBufferedMessage();
        return new TokenResult(ret, null);         
      }
      m_accumulator.closeInput();
      TokenResult ret = callHandleMessage(mimeMsg, m_msgInfo);
      clearBufferedMessage();
      return ret;
    }
    else {
      return TokenResult.NONE;
    }
  }
  
  @Override  
  public final TokenResult handleCompleteMIMEFromServer(CompleteImapMIMEToken token) {
  
    m_logger.debug("[handleCompleteMIMEFromServer]");
    clearBufferedMessage();//REdundant, and perhaps an error if it did anything...?!?...
    
    //We cannot check if it is too big, but the transform subclass should do this anyway.
    return callHandleMessage(token.getMessage(),
      token.getMessageInfo());
  }

  private boolean isTimingOut() {
    return MessageTransmissionTimeoutStrategy.inTimeoutDanger(
      Math.max(m_maxClientWait, m_maxServerWait),
      Math.max(getStream().getLastClientTimestamp(), getStream().getLastServerTimestamp()));
  }

  /**
   * Sets three data members to null
   */
  private void clearBufferedMessage() {
    m_accumulator = null;
    m_totalMessageOctets = 0;
    m_msgInfo = null;
  }

  private TokenResult callHandleMessage(MIMEMessage msg,
    MessageInfo info) {

    try {
      HandleMailResult result = handleMessage(msg, info);
      if(result.replacedMail()) {
        m_logger.debug("handleMessage replaced MIME");
        msg = result.getReplacedMail();
      }
      else {
        m_logger.debug("handleMessage passed message w/o change");
      }      
    }
    catch(Throwable t) {
      m_logger.error("Exception handling IMAP mail", t);
    }
    

    return new TokenResult(new Token[] {
      new CompleteImapMIMEToken(msg, info)}, null);
  }
  
  @Override  
  public final void handleFinalized() {
    m_logger.debug("[handleFinalized]");
    if(m_accumulator != null) {
      m_logger.warn("handleFinalized arrived while buffering mail");
      m_accumulator.dispose();
      clearBufferedMessage();
    }    
  }
  
}


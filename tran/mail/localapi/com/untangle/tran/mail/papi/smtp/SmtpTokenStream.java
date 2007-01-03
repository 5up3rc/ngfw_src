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

package com.untangle.tran.mail.papi.smtp;


import com.untangle.tran.token.*;
import com.untangle.mvvm.tapi.TCPSession;
import org.apache.log4j.Logger;
import com.untangle.mvvm.tapi.Pipeline;
import com.untangle.mvvm.MvvmContextFactory;
import java.util.*;
import java.net.InetAddress;
import com.untangle.tran.mail.papi.BeginMIMEToken;
import com.untangle.tran.mail.papi.ContinuedMIMEToken;
import com.untangle.tran.mail.papi.CompleteMIMEToken;




/**
 * Class representing a stream of Smtp-centric tokens.
 * In other casings, this is sometimes called
 * the "StateMachine".
 * <br>
 * Unlike the StateMachine pattern, users do not subclass this
 * Object.  Instead, Transforms can monitor/manipulate
 * the Smtp stream by subclassing
 * {@link com.untangle.tran.mail.papi.smtp.SmtpTokenStreamHandler SmtpTokenStreamHandler}
 * and passing such an instance to the constructor or
 * {@link #setHandler setHandler}.
 */
public class SmtpTokenStream
  extends AbstractTokenHandler {

  private final Logger m_logger = Logger.getLogger(SmtpTokenStream.class);
  private final NOOPHandler NOOP_HANDLER = new NOOPHandler();

  private SmtpTokenStreamHandler m_handler;
  private boolean m_passthru = false;
  private final Pipeline m_pipeline;
  private boolean m_clientTokensEnabled = true;
  private List<Token> m_queuedClientTokens = new ArrayList<Token>();
  private long m_clientTimestamp;
  private long m_serverTimestamp;

  /**
   * Note that withouth a handler, everything just passes through
   */
  public SmtpTokenStream(TCPSession session) {
    this(session, null);
  }
  public SmtpTokenStream(TCPSession session, SmtpTokenStreamHandler handler) {
    super(session);
    setHandler(handler);
    m_pipeline = MvvmContextFactory.context().pipelineFoundry().getPipeline(session.id());
    updateTimestamps(true, true);
  }  

  /**
   * Get the Handler associated with this Stream
   */
  public final SmtpTokenStreamHandler getHandler() {
    return m_handler;
  }

  /**
   * Set the Handler associated with this Stream
   */  
  public final void setHandler(SmtpTokenStreamHandler handler) {
    m_handler = handler==null?
      NOOP_HANDLER:
      handler;
    m_handler.setSmtpTokenStream(this);
  }

  /**
   * Get the absolute time (based on the local clock) of when the client last
   * sent or was-sent a unit of data.
   */
  public long getLastClientTimestamp() {
    return m_clientTimestamp;
  }

  /**
   * Get the absolute time (based on the local clock) of when the server last
   * was sent or sent a unit of data.
   */
  public long getLastServerTimestamp() {
    return m_serverTimestamp;
  }

  /**
   * Get the client IP address
   */
  public InetAddress getClientAddress() {
    return getSession().clientAddr();
  }  

  /**
   * Re-enable the flow of Client tokens.  If this method is called
   * while Client Tokens are not {@link #disableClientTokens disabled},
   * this has no effect.
   */
  protected void enableClientTokens() {
    if(!m_clientTokensEnabled) {
      m_logger.debug("Re-enabling Client Tokens");
      m_clientTokensEnabled = true;
      //TODO signal casing
    }
    else {
      m_logger.debug("Redundant call to enable Client Tokens");
    }
  }
  /**
   * Disable the flow of client tokens.  No more calls to the Handler
   * will be made with Client Tokens until the {@link #enableClientTokens enable method}
   * is called.
   */
  protected void disableClientTokens() {
    if(m_clientTokensEnabled) {
      m_logger.debug("Disabling Client Tokens");
      m_clientTokensEnabled = false;
      //TODO signal casing
    }
    else {
      m_logger.debug("Redundant call to disable Client Tokens");
    }
  }

  public void handleFinalized() {
    m_handler.handleFinalized();    
  }

  //FROM Client
  public final TokenResult handleClientToken(Token token)
    throws TokenException {

    updateTimestamps(true, false);
    
    TokenResultBuilder trb = new TokenResultBuilder(m_pipeline);

    //First add the token, to preserve ordering if we have
    //a queue (and while draining someone changes the enablement
    //flag)
    m_queuedClientTokens.add(token);
    
    if(!m_clientTokensEnabled) {
      m_logger.debug("[handleClientToken] Queuing Token \"" +
        token.getClass().getName() + "\" (" +
        m_queuedClientTokens.size() + " tokens queued)");
    }
    else {
      //Important - the enablement of client tokens
      //could change as this loop is running.
      while(m_queuedClientTokens.size() > 0 && m_clientTokensEnabled) {
        if(m_queuedClientTokens.size() > 1) {
          m_logger.debug("[handleClientToken] Draining Queued Token \"" +
            m_queuedClientTokens.get(0).getClass().getName() + "\" (" + 
            m_queuedClientTokens.size() + " tokens remain)");
        }
        handleClientTokenImpl(m_queuedClientTokens.remove(0), trb);
      }
    }
    if(m_queuedClientTokens.size() > 0) {
      m_logger.debug("[handleClientToken] returning with (" +
        m_queuedClientTokens.size() + " queued tokens)");
    }
    updateTimestamps(trb.hasDataForClient(), trb.hasDataForServer());
    return trb.getTokenResult();
  }  



  public final TokenResult handleServerToken(Token token)
    throws TokenException {

    updateTimestamps(false, true);
    
    TokenResultBuilder trb = new TokenResultBuilder(m_pipeline);

    while(m_queuedClientTokens.size() > 0 && m_clientTokensEnabled) {
      m_logger.debug("[handleServerToken] Draining Queued Client Token \"" +
        m_queuedClientTokens.get(0).getClass().getName() + "\" (" + 
        m_queuedClientTokens.size() + " tokens remain)");
      handleClientTokenImpl(m_queuedClientTokens.remove(0), trb);
    }    
    handleServerTokenImpl(token, trb);

    //Important - the enablement of client tokens
    //could change as this loop is running.
    while(m_queuedClientTokens.size() > 0 && m_clientTokensEnabled) {
      m_logger.debug("[handleServerToken] Draining Queued Token \"" +
        m_queuedClientTokens.get(0).getClass().getName() + "\" (" + 
        m_queuedClientTokens.size() + " tokens remain)");
      handleClientTokenImpl(m_queuedClientTokens.remove(0), trb);
    }
    if(m_queuedClientTokens.size() > 0) {
      m_logger.debug("[handleServerToken] returning with (" +
        m_queuedClientTokens.size() + " queued tokens)");
    }    
    updateTimestamps(trb.hasDataForClient(), trb.hasDataForServer());    
    return trb.getTokenResult();
  }

  public final void handleClientFin()
    throws TokenException {
    if(m_handler.handleClientFIN()) {
      m_logger.debug("Passing along client FIN");    
      getSession().shutdownServer();
    }
    else {
      m_logger.debug("Supress client FIN");
    }     
  }

  public final void handleServerFin()
    throws TokenException {
    if(m_handler.handleServerFIN()) {
      m_logger.debug("Passing along server FIN");
      getSession().shutdownClient();
    }
    else {
      m_logger.debug("Supress server FIN");    
    }   
  }

  
  //FROM Client
  private final void handleClientTokenImpl(Token token,
    TokenResultBuilder trb)
    throws TokenException {

    //Check for passthrough
    if(m_passthru) {
      m_logger.debug("(In passthru, client token) passing token of type " + token.getClass().getName());
      trb.addTokenForServer(token);
      return;
    }

    if(token instanceof SASLExchangeToken) {
      m_logger.debug("Received SASL token");
      trb.addTokenForServer(token);
      return;
    }

    if(token instanceof AUTHCommand) {
      m_logger.debug("Received AUTHCommand");
      trb.addTokenForServer(token);
      return;
    }    

    //Passthru
    if(token instanceof PassThruToken) {
      m_logger.debug("(client token) Entering Passthru");
      m_passthru = true;
      trb.addTokenForServer(token);
      m_handler.passthru(trb);
      return;
    }
    else if(token instanceof MAILCommand) {
      m_handler.handleMAILCommand(trb, (MAILCommand) token);
      return;
    }
    else if(token instanceof RCPTCommand) {
      m_handler.handleRCPTCommand(trb, (RCPTCommand) token);
      return;     
    }
    else if(token instanceof Command) {
      m_handler.handleCommand(trb, (Command) token);
      return;     
    }
    else if(token instanceof BeginMIMEToken) {
      m_handler.handleBeginMIME(trb, (BeginMIMEToken) token);
      return;   
    }
    else if(token instanceof ContinuedMIMEToken) {
      m_handler.handleContinuedMIME(trb, (ContinuedMIMEToken) token);
      return;     
    }
    else if(token instanceof CompleteMIMEToken) {
      m_handler.handleCompleteMIME(trb, (CompleteMIMEToken) token);
      return;    
    }    
    else if(token instanceof Chunk) {
      m_handler.handleChunkForServer(trb, (Chunk) token);
      return;     
    }
    m_logger.error("(client token) Unexpected Token of type \"" +
      token.getClass().getName() + "\".  Pass it along");
    trb.addTokenForServer(token);
  }  

  
  //FROM Server
  private final void handleServerTokenImpl(Token token,
    TokenResultBuilder trb)
    throws TokenException {
//    m_logger.debug("[handleServerTokenImpl] Called with token type \"" +
//      token.getClass().getName() + "\"");

    if(m_passthru) {
      m_logger.debug("(In passthru, server token) passing token of type " +
        token.getClass().getName());
      trb.addTokenForClient(token);
      return;
    }

    if(token instanceof SASLExchangeToken) {
      m_logger.debug("Received SASL token");
      trb.addTokenForClient(token);
      return;
    }    
    
    //Passthru
    if(token instanceof PassThruToken) {
      m_logger.debug("(server token) Entering Passthru");
      m_passthru = true;
      trb.addTokenForClient(token);
      m_handler.passthru(trb);
      return;
    }
    else if(token instanceof Response) {
      m_handler.handleResponse(trb, (Response) token);
      return;  
    }
    else if(token instanceof Chunk) {
      m_handler.handleChunkForClient(trb, (Chunk) token);
      return;
    }
    m_logger.error("Unexpected Token of type \"" +
      token.getClass().getName() + "\".  Pass it along");
    trb.addTokenForClient(token);
  }


  private final void updateTimestamps(boolean client,
    boolean server) {
    long now = System.currentTimeMillis();
    if(client) {
      m_clientTimestamp = now;
    }
    if(server) {
      m_serverTimestamp = now;
    }
  }  


  //============== Inner Class ======================
  
  /**
   * As its name implies, does nothing except pass stuff through
   */
  private class NOOPHandler
    extends SmtpTokenStreamHandler {

    @Override
    public void passthru(TokenResultBuilder resultBuilder) {
      updateTimestamps(true, true);
      //Nothing to do
    }

    @Override
    public void handleCommand(TokenResultBuilder resultBuilder,
      Command cmd) {
      updateTimestamps(true, true);
      resultBuilder.addTokenForServer(cmd);
    }

    @Override
    public void handleMAILCommand(TokenResultBuilder resultBuilder,
      MAILCommand cmd) {
      updateTimestamps(true, true);
      resultBuilder.addTokenForServer(cmd);
    }

    @Override
    public void handleRCPTCommand(TokenResultBuilder resultBuilder,
      RCPTCommand cmd) {
      updateTimestamps(true, true);
      resultBuilder.addTokenForServer(cmd);
    }

    @Override
    public void handleBeginMIME(TokenResultBuilder resultBuilder,
      BeginMIMEToken token) {
      updateTimestamps(true, true);
      resultBuilder.addTokenForServer(token);
    }

    @Override
    public void handleContinuedMIME(TokenResultBuilder resultBuilder,
      ContinuedMIMEToken token) {
      updateTimestamps(true, true);
      resultBuilder.addTokenForServer(token);
    }

    @Override
    public void handleResponse(TokenResultBuilder resultBuilder,
      Response resp) {
      updateTimestamps(true, true);
      resultBuilder.addTokenForClient(resp);
    }

    @Override
    public void handleChunkForClient(TokenResultBuilder resultBuilder,
      Chunk chunk) {
      updateTimestamps(true, true);
      resultBuilder.addTokenForClient(chunk);
    }

    @Override
    public void handleChunkForServer(TokenResultBuilder resultBuilder,
      Chunk chunk) {
      updateTimestamps(true, true);
      resultBuilder.addTokenForServer(chunk);
    }

    @Override
    public void handleCompleteMIME(TokenResultBuilder resultBuilder,
      CompleteMIMEToken token) {
      updateTimestamps(true, true);
      resultBuilder.addTokenForServer(token);
    }

    @Override
    public boolean handleServerFIN() {
      updateTimestamps(false, true);
      return true;
    }

    @Override
    public boolean handleClientFIN() {
      updateTimestamps(true, false);
      return true;
    }
    @Override
    public void handleFinalized() {}
  }//ENDOF NOOPHandler Class Definition      
  
}//ENDOF SmtpTokenSteram Class Definition
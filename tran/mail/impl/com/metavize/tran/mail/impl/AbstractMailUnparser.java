/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.mail.impl;

import java.nio.ByteBuffer;
import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.tapi.Pipeline;
import java.nio.ByteBuffer;
import com.metavize.mvvm.tapi.event.TCPStreamer;
import com.metavize.tran.token.AbstractUnparser;
import com.metavize.tran.token.UnparseResult;
import com.metavize.tran.token.Token;
import org.apache.log4j.Logger;
import com.metavize.mvvm.tapi.TCPSession;
import com.metavize.tran.token.PassThruToken;

/**
 * Base class for the SmtpClient/ServerUnparser
 */
public abstract class AbstractMailUnparser
  extends AbstractUnparser {

  private final Pipeline m_pipeline;
  private final AbstractMailCasing m_parentCasing;
  private final Logger m_logger =
    Logger.getLogger(AbstractMailUnparser.class);
  private final boolean m_trace;
  private boolean m_passthru = false;
  private final String m_protocol;

  protected AbstractMailUnparser(TCPSession session,
    AbstractMailCasing parent,
    boolean clientSide,
    String protocolName) {
    
    super(session, clientSide);
    m_protocol = protocolName;
    m_trace = parent.isTrace();
    m_parentCasing = parent;
    m_pipeline = MvvmContextFactory.context().
      pipelineFoundry().getPipeline(session.id());
  }

  public AbstractMailCasing getParentCasing() {
    return m_parentCasing;
  }
  public Pipeline getPipeline() {
    return m_pipeline;
  }

  /**
   * Is the casing currently in passthru mode
   */
  protected boolean isPassthru() {
    return m_passthru;
  }

  /**
   * Called by the unparser to declare that
   * we are now in passthru mode.  This is called
   * either because of a parsing error by the caller,
   * or the reciept of a passthru token.
   * 
   */
  protected void declarePassthru() {
    m_passthru = true;
    m_parentCasing.passthru();
  }

  /**
   * Called by the casing to declare that this
   * instance should now be in passthru mode.
   */
  protected final void passthru() {
    m_passthru = true;
  }

  public UnparseResult unparse(Token token) {
    if(token instanceof PassThruToken) {
      m_logger.debug("Received PassThruToken");
      declarePassthru();//Inform the parser of this state
      return UnparseResult.NONE;
    }  
    if(m_trace) {
      UnparseResult ret = doUnparse(token);
      if(ret != null) {
        if(ret.isStreamer()) {
          ret = new UnparseResult(
            getParentCasing().wrapUnparseStreamerForTrace(ret.getTcpStreamer()));
        }
        else {
          ByteBuffer[] bufs = ret.result();
          if(bufs != null) {
            for(ByteBuffer buf : bufs) {
              if(buf != null) {
                getParentCasing().traceUnparse(buf);
              }
            }
          }
        }
      }
      return ret;
    }
    return doUnparse(token);
  }

  /**
   * Delegated "unparse" method.  The superclass performs
   * some housekeeping before calling this method.
   * <br><br>
   * Note that subclasses should <b>not</b> worry about
   * tracing, or receiving Passthru tokens
   */
  protected abstract UnparseResult doUnparse(Token token);

  public final TCPStreamer endSession() {
    m_logger.debug("(" +
      m_protocol + ")(" +
      (isClientSide()?"client":"server") + ") End Session");
    getParentCasing().endSession(isClientSide());
    return null;
  }


  @Override
  public void handleFinalized() {
    m_logger.debug("(" +
      m_protocol + ")(" +
      (isClientSide()?"client":"server") + ") handleFinalized");
  }  

}  
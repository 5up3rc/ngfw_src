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

package com.metavize.tran.mail.papi.smtp.sapi;

import com.metavize.tran.mail.papi.smtp.*;
import com.metavize.tran.mail.papi.*;
import com.metavize.tran.mime.*;

import org.apache.log4j.Logger;
import java.util.*;
import com.metavize.tran.token.Token;
import java.nio.*;
import java.nio.channels.*;
import java.io.*;

/**
 * Subclass of SessionHandler which yet-again simplifies
 * consumption of an SMTP stream.  This class was created
 * for Transforms wishing only to see "a whole mail".
 * <br><br>
 * This class <i>buffers</i> mails, meaning it does not
 * pass each MIME chunk to the server.  Instead, it attempts
 * to collect them into a file and present them to
 * the {@link #blockPassOrModify blockPassOrModify} method
 * for evaluation.
 * <br><br>
 * There are two cases when the {@link #blockPassOrModify blockPassOrModify}
 * method will not be called for a given mail (except for when
 * a given transaction is aborted, but the subclass does not even
 * see such aborts).  The first is if the subclass is only interested
 * in mails {@link #getGiveupSz below a certain size}.  This size is declared
 * by implementing the {@link #getGiveupSz getGiveupSz()} method.
 * <br><br>
 * The second case which prevents {@link #blockPassOrModify blockPassOrModify()} from being
 * called is when this handler has begun to <i>trickle</i>.  Trickling is
 * the state in which the BufferingSessionHandler passes MIME chunks
 * to the server as they arrive.  Tricking is initiated after the
 * BufferingSessionHandler determines either the client or server is
 * in danger of timing-out.  The timeout time is set by the subclass
 * via the {@link #getMaxClientWait getMaxClientWait()} and
 * {@link #getMaxServerWait getMaxServerWait()} methods.
 * <br><br>
 * When timeout occurs, the BufferingSessionHandler can enter one of
 * two states.  It can <i>giveup-then-trickle</i>, meaning no evaluation
 * will take place.  Alternatly, it can enter <i>buffer-and-trickle</i>
 * meaning it will continue to buffer while bytes are sent to the
 * server.  Which state is entered after timeout is determined by
 * the subclass' return of the {@link #isBufferAndTrickle isBufferAndTrickle()}
 * method.
 * <br><br>
 * If <i>buffer-and-trickle</i> is selected, the method
 * {@link #blockOrPass blockOrPass()} will be invoked once the whole
 * mail is observed.  Note that modification of the MIME message
 * is forbidden in the {@link #blockOrPass blockOrPass()} callback,
 * as it is too late to modify the message.
 */
public abstract class BufferingSessionHandler
  extends SessionHandler {

  private static final String RESP_TXT_354 = "Start mail input; end with <CRLF>.<CRLF>";


  //================================
  // Public Inner Classes
  //================================
  
  /**
   * Actions the subclass can take when
   * message modification is not an action.
   * The enum is easier than remembering if true
   * means "block" or "don't block"
   */
  public enum BlockOrPassResult {
    BLOCK,
    PASS
  };

  
  /**
   * <b>B</b>lock, <b>P</b>ass, or <b>M</b>odify Evaluation result.
   */
  public static final class BPMEvaluationResult {
    private MIMEMessage m_newMsg;
    private final boolean m_block;
    private BPMEvaluationResult(boolean block) {
      m_block = block;
    }
    /**
     * Constrctor used to create a result
     * indicating that the message has been
     * modified.  This implicitly is not
     * a block.
     */
    public BPMEvaluationResult(MIMEMessage newMsg) {
      m_block = false;
      m_newMsg = newMsg;
    }
    public boolean isBlock() {
      return m_block;
    }
    public boolean messageModified() {
      return m_newMsg != null;
    }
    public MIMEMessage getMessage() {
      return m_newMsg;
    }
  }
  /**
   * Result from {@link #blockPassOrModify blockPassOrModify} indicating block message
   */
  public static BPMEvaluationResult BLOCK_MESSAGE = new BPMEvaluationResult(true);
  /**
   * Result from {@link #blockPassOrModify blockPassOrModify} indicating pass message
   */  
  public static BPMEvaluationResult PASS_MESSAGE = new BPMEvaluationResult(false);

  private final Logger m_logger = Logger.getLogger(BufferingSessionHandler.class);


  //==================================
  // Abstract Methods
  //==================================

  /**
   * Get the size over-which this class should no
   * longer buffer.  After this point, the
   * Buffering is abandoned and the
   * <i>giveup-then-trickle</i> state is entered.
   */
  public abstract int getGiveupSz();

  /**
   * The maximum time (in relative milliseconds)
   * that the client can wait for a response
   * to DATA transmission.
   */
  public abstract long getMaxClientWait();

  /**
   * The maximum time that the server can wait
   * for a subsequent ("DATA") command.
   */
  public abstract long getMaxServerWait();

  /**
   * If true, this handler will continue to buffer even after 
   * trickling has begun (<i>buffer-and-trickle</i> mode).
   * The MIMEMessage can no longer be modified
   * (i.e. it will not be passed downstream) but
   * {@link #blockOrPass blockOrPass()} will still be called
   * once the complete message has been seen.
   */
  public abstract boolean isBufferAndTrickle();

  /**
   * Callback once an entire mail has been buffered.  Subclasses
   * can choose one of the three permitted outcomes (Block, pass, modify).
   *
   * @param msg the MIMEMessage
   * @param tx the transaction
   * @param msgInfo the MessageInfo (for creating reporting events).
   */
  public abstract BPMEvaluationResult blockPassOrModify(MIMEMessage msg,
    SmtpTransaction tx,
    MessageInfo msgInfo);

  /**
   * Callback once a complete message has been buffered,
   * after the BufferingSessionHandler already entered
   * <i>buffer-and-trickle</i> mode.  The message
   * cannot be modified, but it can still be blocked (and
   * any report events can be sent).
   * <br><br>
   * Note that this method is called <i>just before</i>
   * the last chunk of the message is passed-along to
   * the server (if it were afterwards, there would be
   * no ability to block!).
   *
   *
   * @param msg the MIMEMessage
   * @param tx the transaction
   * @param msgInfo the MessageInfo (for creating reporting events).
   */
  public abstract BlockOrPassResult blockOrPass(MIMEMessage msg,
    SmtpTransaction tx,
    MessageInfo msgInfo);


    
  //================================
  // SessionHandler methods
  //================================
    
  @Override
  public final void handleCommand(Command command,
    Session.SmtpCommandActions actions) {

    m_logger.debug("[handleCommand] with command of type \"" +
      command.getType() + "\"");

    actions.sendCommandToServer(command, new PassthruResponseCompletion());
  }

  @Override
  public final void handleOpeningResponse(Response resp,
    Session.SmtpResponseActions actions) {
    m_logger.debug("[handleOpeningResponse]");

    actions.sendResponseToClient(resp);
  }

  @Override
  public final TransactionHandler createTxHandler(SmtpTransaction tx) {
    return new BufferingTransactionHandler(tx);
  }

  @Override
  public boolean handleServerFIN(TransactionHandler currentTX) {
    return true;
  }

  @Override
  public boolean handleClientFIN(TransactionHandler currentTX) {
    return true;
  }  


  //==========================
  // Helpers
  //==========================
  
  /**
   * Determines, based on timestamps, if
   * trickling should begin
   */
  private boolean shouldBeginTrickle() {

    return MessageTransmissionTimeoutStrategy.inTimeoutDanger(
      Math.min(getMaxClientWait(), getMaxServerWait()),
      Math.min(
        getSession().getLastClientTimestamp(),
        getSession().getLastServerTimestamp()));
  }


  //===================== Inner Class ====================

  /**
   * Possible states for the Transactions
   */
  private enum BufTxState {
    INIT,
    GATHER_ENVELOPE,
    BUFFERING_MAIL,
    PASSTHRU_DATA_SENT,
    T_B_DATA_SENT,
    BUFFERED_DATA_SENT,
    BUFFERED_TRANSMITTED_WAIT_REPLY,
    PASSTHRU_PENDING_NACK,
    DRAIN_MAIL,
    DRAIN_MAIL_WAIT_REPLY,
    T_B_READING_MAIL,
    DONE
  };


  //===================== Inner Class ====================
  
  /**
   * Log of the transaction (not to be confused with database TX logs).
   * Simply a sequential dialog of what took place, so if there is
   * some problem we can send this log to the real log file.
   */
  private class TxLog extends ArrayList<String> {

  
    void receivedToken(Token token) {
      add("----Received Token " + token.getClass().getName() + "--------");
    }
    void receivedResponse(Response resp) {
      add("----Received Response " + resp.getCode() + "--------");
    }
    void recordStateChange(BufTxState old, BufTxState newState) {
      add("----Change State " + old + "->" + newState + "-----------");
    }
    void dumpToError(Logger logger) {
      logger.error("=======BEGIN Transaction Log=============");
      for(String s : this) {
        logger.error(s);
      }
      logger.error("=======ENDOF Transaction Log=============");
    }
    void dumpToDebug(Logger logger) {
      logger.debug("=======BEGIN Transaction Log=============");
      for(String s : this) {
        logger.debug(s);
      }
      logger.debug("=======ENDOF Transaction Log=============");
    }

    //TODO bscott this is temp, just for debugging
    @Override
    public boolean add(String s) {
      m_logger.debug("[TX-LOG] " + s);
      return super.add(s);
    }   
  
  }

  //===================== Inner Class ====================

  /**
   * The main workhorse of this class.  
   */
  private class BufferingTransactionHandler
    extends TransactionHandler {

    private TxLog m_txLog = new TxLog();
    private BufTxState m_state = BufTxState.INIT;

    private MIMEAccumulator m_accumulator;
    private MessageInfo m_messageInfo;
    private MIMEMessage m_msg;

    private int m_dataResp = -1;//Special case for when we get a negative
                                //response to our sending of a "real" DATA
                                //command to the server.  In this special
                                //case, we need to queue what the server said
                                //to the DATA command and instead send it
                                //as a result of the client's <CRLF>.<CRLF>
  
    BufferingTransactionHandler(SmtpTransaction tx) {
      super(tx);
      m_txLog.add("---- Initial state " + m_state + " (" +
        new Date() + ") -------");
    }


    //TODO bscott Nuke the file if we were accumulating MIME.  This means
    //     we need to handle the "client" and "server" close stuff

    @Override
    public void handleRSETCommand(Command command,
      Session.SmtpCommandActions actions) {
      
      m_txLog.receivedToken(command);

      //Look for the state we understand.  Note I included "INIT"
      //but that should be impossible by definition
      if(m_state == BufTxState.GATHER_ENVELOPE ||
        m_state == BufTxState.INIT) {
        m_txLog.add("Aborting at client request");
        actions.transactionEnded(this);
        getTransaction().reset();
        actions.sendCommandToServer(command, new PassthruResponseCompletion());
        changeState(BufTxState.DONE);
        finalReport();
        if(m_accumulator != null) {
          m_accumulator.dispose();
          m_accumulator = null;
        }
      }
      else {//State/command misalignment
        m_txLog.add("Impossible command now");
        m_txLog.dumpToError(m_logger);
        actions.transactionEnded(this);
        actions.sendCommandToServer(command, new PassthruResponseCompletion());
        changeState(BufTxState.DONE);
      }
    }
    
    @Override
    public void handleCommand(Command command,
      Session.SmtpCommandActions actions) {
      m_txLog.receivedToken(command);

      if(m_state == BufTxState.GATHER_ENVELOPE ||
        m_state == BufTxState.INIT) {
        if(command.getType() == Command.CommandType.DATA) {
          //Don't passthru
          m_txLog.add("Enqueue synthetic 354 for client");
          actions.appendSyntheticResponse(new FixedSyntheticResponse(354, RESP_TXT_354));
          changeState(BufTxState.BUFFERING_MAIL);
        }
        else {
          m_txLog.add("Passthru to client");
          actions.sendCommandToServer(command, new PassthruResponseCompletion());
        }
      }
      else {
        m_txLog.add("Impossible command now");
        m_txLog.dumpToError(m_logger);
        actions.sendCommandToServer(command, new PassthruResponseCompletion());
        actions.transactionEnded(this);
        changeState(BufTxState.DONE);
      }
    }

    private void handleMAILOrRCPTCommand(Command command,
      Session.SmtpCommandActions actions,
      ResponseCompletion compl) {
      
      m_txLog.receivedToken(command);

      if(m_state == BufTxState.GATHER_ENVELOPE ||
        m_state == BufTxState.INIT) {
        if(m_state == BufTxState.INIT) {
          changeState(BufTxState.GATHER_ENVELOPE);
        }
        m_txLog.add("Pass " + command.getType() + " command to server, register callback " +
          " to modify envelope at response");
        actions.sendCommandToServer(command, compl);
      }
      else {
        m_txLog.add("Impossible command now");
        m_txLog.dumpToError(m_logger);
        actions.sendCommandToServer(command, new PassthruResponseCompletion());
        actions.transactionEnded(this);
        changeState(BufTxState.DONE);
      }    
    }
    
    @Override
    public void handleMAILCommand(MAILCommand command,
      Session.SmtpCommandActions actions) {
      getTransaction().fromRequest(command.getAddress());
      handleMAILOrRCPTCommand(command, actions, new MAILContinuation(command.getAddress()));
    }

    @Override
    public void handleRCPTCommand(RCPTCommand command,
      Session.SmtpCommandActions actions) {
      getTransaction().toRequest(command.getAddress());
      handleMAILOrRCPTCommand(command, actions, new RCPTContinuation(command.getAddress()));
    }    
    

    
    @Override
    public void handleBeginMIME(BeginMIMEToken token,
      Session.SmtpCommandActions actions) {
      
      m_txLog.receivedToken(token);

      m_accumulator = token.getMIMEAccumulator();
      m_messageInfo = token.getMessageInfo();

      handleMIMEChunk(true,
        false,
        null,
        actions);
    }
    
    @Override    
    public void handleContinuedMIME(ContinuedMIMEToken token,
      Session.SmtpCommandActions actions) {
      m_txLog.receivedToken(token);

      //TODO bscott ***DEBUG***
      token.getMIMEChunk().superDebugMe(m_logger, "[handleContinuedMIME()]");

      handleMIMEChunk(false,
        token.isLast(),
        token,
        actions);
    }
    
    @Override
    public void handleCompleteMIME(CompleteMIMEToken token,
      Session.SmtpCommandActions actions) {
      
      m_txLog.receivedToken(token);

      m_msg = token.getMessage();
      m_messageInfo = token.getMessageInfo();
      //TODO bscott Should we close the file of accumulated MIME?  It is really
      //     an error to have the file at all
      m_accumulator = null;
      m_messageInfo = null;
      handleMIMEChunk(true, true, null, actions);
      
    }

    
    private void handleMIMEChunk(boolean isFirst,
      boolean isLast,
      ContinuedMIMEToken continuedToken,/*Odd semantics - may be null*/
      Session.SmtpCommandActions actions) {
      

      switch(m_state) {
        case INIT:
        case GATHER_ENVELOPE:
        case BUFFERED_DATA_SENT:
        case BUFFERED_TRANSMITTED_WAIT_REPLY:
        case PASSTHRU_DATA_SENT:
        case DRAIN_MAIL_WAIT_REPLY:
        case T_B_DATA_SENT:        
        case DONE:
          //TODO bscott handle this case better.  Dump anything we have first and
          //     declare passthru
          m_txLog.add("Impossible command now");
          m_txLog.dumpToError(m_logger);
          appendChunk(continuedToken);
 //         if(isLast) {
 //           actions.sendContinuedMIMEToServer(new ContinuedMIMEToken(false));
 //         }
 //         else {
 //           actions.sendFinalMIMEToServer(new ContinuedMIMEToken(true), new PassthruResponseCompletion());
 //         }
          changeState(BufTxState.DONE);
          break;                
        case BUFFERING_MAIL:
          //------Page 21--------
          if(isLast) {
            //We have the complete message.
            m_txLog.add("Have whole message.  Evaluate");
            appendChunk(continuedToken);
            if(evaluateMessage(true)) {//BEGIN Not Blocking
              m_txLog.add("Message passed evaluation");
              //We're passing the message (or there was a silent parser error)

              //Disable client tokens while we "catch-up" by issuing the DATA
              //command
              actions.disableClientTokens();

              m_txLog.add("Send synthetic DATA to server, continue when reply arrives");
              actions.sendCommandToServer(new Command(Command.CommandType.DATA),
                new BufferedDATARequestContinuation());

              changeState(BufTxState.BUFFERED_DATA_SENT);//TODO bscott Useless state.
                                                        //It only ever gets callback and does some reporting
            }//ENDOF Not Blocking
            else {//BEGIN Blocking Message
              //TODO bscott is it safe to nuke the accumulator and/or message
              //     here, or do we wait for the callback?
              //We're blocking the message
              m_txLog.add("Message failed evaluation (we're going to block it)");
              //Send a fake 250 to client
              m_txLog.add("Enqueue synthetic 250 to client");
              actions.appendSyntheticResponse(new FixedSyntheticResponse(250, "OK"));

              //Send REST to server (ignore result)
              m_txLog.add("Send synthetic RSET to server (ignoring the response)");
              actions.sendCommandToServer(
                new Command(Command.CommandType.RSET),
                new NoopResponseCompletion());

              changeState(BufTxState.DONE);
              actions.transactionEnded(this);
              getTransaction().reset();
              finalReport();
            }//ENDOF Blocking Message
          }
          else {
            m_txLog.add("Not last MIME chunk, append to the file");
            appendChunk(continuedToken);
            //We go down one of three branches
            //from here.  We begin passthru if the
            //mail is too large or if we've timed out.
            //
            //We Trickle&Buffer if we've timed out
            //but the subclass wants to keep going.
            //
            //We simply record the new chunk if nothing
            //else is to be done.
            boolean tooBig = m_accumulator.fileSize() > getGiveupSz();
            boolean timedOut = shouldBeginTrickle();
  
            if(tooBig || (timedOut && !isBufferAndTrickle())) {
              //Passthru DATA Sent
              if(tooBig) {
                m_txLog.add("Mail too big for scanning.  Begin trickle");
              }
              else {
                m_txLog.add("Mail timed-out w/o needing to buffer (trickle, not buffer-and-trickle)");
              }
              //Disable tokens from client until we get the disposition to the DATA command
              actions.disableClientTokens();
              //Send the DATA command to the server
              m_txLog.add("Send synthetic DATA to server, continue when response arrives");
              //Use the contnuation shared by this and the T_B_READING_MAIL state.  The
              //continuations are 99% the same, except they differ in their
              //next state upon success
              actions.sendCommandToServer(new Command(Command.CommandType.DATA),
                new MsgIncompleteDATARequestContinuation(BufTxState.DRAIN_MAIL));
              changeState(BufTxState.PASSTHRU_DATA_SENT);
            }
            else if(timedOut && isBufferAndTrickle()) {
              //T&B DATA Sent
              m_txLog.add("Mail timed out.  Begin trickle and buffer");
              //Disable client until we can hear back from the "DATA" command
              actions.disableClientTokens();
              //Send the DATA command to the server and set-up the callback
              m_txLog.add("Send synthetic DATA to server, continue when response arrives");
              actions.sendCommandToServer(new Command(Command.CommandType.DATA),
                new MsgIncompleteDATARequestContinuation(BufTxState.T_B_READING_MAIL));
              changeState(BufTxState.T_B_DATA_SENT);
            }
            else {
              //The chunk is already recorded.  Nothing to do.  No delta state
            }
          }//ENDOF Not Last Token

          break;
        case DRAIN_MAIL:
          //Page 25
          //TODO bscott how can I tell this won't be null?
          m_txLog.add("Pass this chunk on to the server");
          if(isLast) {
            //Make sure we're no longer the active transaction
            actions.transactionEnded(BufferingTransactionHandler.this);

            //Install the simple callback handler (doesn't do much)
            actions.sendFinalMIMEToServer(continuedToken, new MailTransmissionContinuation());
            
            changeState(BufTxState.DRAIN_MAIL_WAIT_REPLY);//TODO bscott a stupid state (not needed)            
          }
          else {
            //Nothing interesting to do.  No change in state,
            //and the chunk has already been passed along.
            actions.sendContinuedMIMEToServer(continuedToken);
          }
          break;
        case PASSTHRU_PENDING_NACK:
          //Page 27
          //Let the token fall on the floor, as it'll never be accepted by
          //the server.  We're just combing through the junk from the client
          //getting to the point where we can NACK
          if(isLast) {
            //Make sure we're no longer the active transaction
            actions.transactionEnded(BufferingTransactionHandler.this);
            //Transaction Failed
            getTransaction().failed();
            //Pass along same error (whatever it was) to client
            m_txLog.add("End of queued NACK.  Send " + m_dataResp + " to client");
            actions.appendSyntheticResponse(new FixedSyntheticResponse(m_dataResp, ""));
            //We're done
            changeState(BufTxState.DONE);
            finalReport();
          }
          else {
            //Nothing interesting to do.
          }
          break;
        case T_B_READING_MAIL:
          //Page 29
          //Write it to the file regardless
          appendChunk(continuedToken);
          m_txLog.add("Trickle and buffer.  Whole message obtained.  Evaluate");
          if(isLast) {
            if(evaluateMessage(false)) {
              m_txLog.add("Evaluation passed");
              actions.sendFinalMIMEToServer(continuedToken,
                new MailTransmissionContinuation());
            }
            else {
              //Block, the hard way...
              m_txLog.add("Evaluation failed.  Send a \"fake\" 250 to client then shutdown server");
              actions.appendSyntheticResponse(new FixedSyntheticResponse(250, "OK"));
              actions.transactionEnded(this);
              //Put a Response handler here, in case the goofy server
              //sends an ACK to the FIN (which Exim seems to do!?!)
              actions.sendFINToServer(new NoopResponseCompletion());
              getSession().setSessionHandler(
                new ShuttingDownSessionHandler(1000*60));//TODO bscott a real timeout value
            }
          }
          else {
            actions.sendContinuedMIMEToServer(continuedToken);
          }
          break;
        default:
          m_txLog.add("Error - Unknown State " + m_state);
          changeState(BufTxState.DONE);
          actions.transactionEnded(this);
          appendChunk(continuedToken);
          if(isLast) {
            actions.sendContinuedMIMEToServer(continuedToken);
          }
          else {
            actions.sendFinalMIMEToServer(continuedToken, new PassthruResponseCompletion());
          }
      }
    }


    //If null, just ignore
    private void appendChunk(ContinuedMIMEToken continuedToken) {
      if(m_accumulator == null) {
        m_logger.error("Received ContinuedMIMEToken without a MIMEAccumulator set");
      }
      if(continuedToken == null) {
        return;
      }
      if(!m_accumulator.appendChunkToFile(continuedToken.getMIMEChunk())) {
        m_logger.error("Error appending MIME Chunk");
        //TODO bscott If there is a write-error, should we dispose?
        m_accumulator.dispose();
        m_accumulator = null;
      }
      
    }    


    private void changeState(BufTxState newState) {
      m_txLog.recordStateChange(m_state, newState);
      m_state = newState;
    }
    /**
     * Helper which prints the tx log to debug
     * as a final report of what took place
     */
    private void finalReport() {
      //TODO Send TxLog to debug and add final state
//      m_txLog.add("Final transaction state: " + getTransaction().getState());
//      m_txLog.dumpToDebug(m_logger);
    }

    /**
     * Returns true if we should pass.  If there is a parsing
     * error, this also returns true.  If the message
     * was changed, it'll just be picked-up implicitly.
     */
    private boolean evaluateMessage(boolean canModify) {
      if(m_msg == null) {
        m_msg = m_accumulator.parseBody();
        m_accumulator.closeInput();
        if(m_msg == null) {
          m_txLog.add("Parse error on MIME.  Assume it passed scanning");
          return true;
        }
      }
      if(canModify) {
        BPMEvaluationResult result = blockPassOrModify(m_msg,
          getTransaction(),
          m_messageInfo);
        if(result.messageModified()) {
          m_txLog.add("Evaluation modified MIME message");
          m_msg = result.getMessage();
          return true;
        }
        else {
          return !result.isBlock();
        }
      }
      else {
        return blockOrPass(m_msg, getTransaction(), m_messageInfo) == BlockOrPassResult.PASS;
      }
    }  
  
  
  
    
    //==========================
    // Inner-Inner-Classes
    //===========================

    //****************** Inner-Inner Class Separator ******************
    /**
     * Callback when we have buffered a mail, decided to let
     * it pass, then sent a DATA to the server.
     */
    private class BufferedDATARequestContinuation
      extends PassthruResponseCompletion {

      public void handleResponse(Response resp,
        Session.SmtpResponseActions actions) {
        
        m_txLog.receivedResponse(resp);
        m_txLog.add("Response to DATA command was " + resp.getCode());

        //Save this in a variable, so we can pass it along
        //later (if not positive)
        m_dataResp = resp.getCode();
        
        actions.enableClientTokens();
        actions.transactionEnded(BufferingTransactionHandler.this);

        if(resp.getCode() < 400) {
          //Don't forward to client
          
          //Pass either complete MIME or begin/end (if there
          //was a parse error)
          if(m_msg == null) {
            m_txLog.add("Passing along an unparsable MIME message in two tokens");
            actions.sendBeginMIMEToServer(new BeginMIMEToken(m_accumulator, m_messageInfo));
            actions.sendFinalMIMEToServer(
              new ContinuedMIMEToken(m_accumulator.createChunk(null, true)),
              new MailTransmissionContinuation());
            m_accumulator = null;
          }
          else {
            m_txLog.add("Passing along parsed MIME in one token");
            if(m_accumulator != null) {
              m_accumulator.closeInput();
              m_accumulator = null;
            }
            actions.sentWholeMIMEToServer(new CompleteMIMEToken(m_msg, m_messageInfo),
              new MailTransmissionContinuation());
          }
          

          //Change state to BUFFERED_TRANSMITTED_WAIT_REPLY
          changeState(BufTxState.BUFFERED_TRANSMITTED_WAIT_REPLY);
        }
        else {
          //Discard message
          if(m_accumulator != null) {
            m_accumulator.closeInput();
          }
          if(m_msg != null) {
            m_msg.dispose();
          }
          else {
            m_accumulator.dispose();
          }
          m_accumulator = null;
          m_msg = null;
          //Transaction Failed
          getTransaction().failed();

          //Pass along same error (whatever it was) to client
          actions.sendResponseToClient(resp);

          //We're done
          changeState(BufTxState.DONE);
          finalReport();
        }
      }
    }    
  
    //****************** Inner-Inner Class Separator ******************
    /**
     * Callback when a DATA request completes.  This is used
     * for the "PASSTHRU_DATA_SENT" and "T_B_DATA_SENT"
     * states, which are issuing the DATA command in advance
     * of having the complete message.
     *
     * Both of those states share the same negative
     * next state, but differ in their positive next state.
     */
    private class MsgIncompleteDATARequestContinuation
      extends PassthruResponseCompletion {

      private BufTxState m_nextState;
      
      MsgIncompleteDATARequestContinuation(BufTxState nextStateIfPositive) {
        m_nextState = nextStateIfPositive;
      }
  
      public void handleResponse(Response resp,
        Session.SmtpResponseActions actions) {
        
        m_txLog.receivedResponse(resp);
        m_txLog.add("Response to DATA command was " + resp.getCode());

        //Save this in a variable, so we can pass it along
        //later (if not positive)
        m_dataResp = resp.getCode();
        
        actions.enableClientTokens();
        
        if(resp.getCode() < 400) {
          m_txLog.add("Begin trickle with BeginMIMEToken");
          actions.sendBeginMIMEToServer(new BeginMIMEToken(m_accumulator, m_messageInfo));
          changeState(m_nextState);
        }
        else {
          //Discard message
          if(m_accumulator != null) {
            m_accumulator.closeInput();
          }
          if(m_msg != null) {
            m_msg.dispose();
          }
          else {
            m_accumulator.dispose();
          }
          m_accumulator = null;
          m_msg = null;
          actions.sendCommandToServer(new Command(Command.CommandType.RSET),
            new NoopResponseCompletion());
          changeState(BufTxState.PASSTHRU_PENDING_NACK);
        }
      }
    }

  
    //****************** Inner-Inner Class Separator ******************
    
    /**
     * Callback for the server's response to the mail transmission
     * (the thing ending in <CRLF>.<CRLF>).
     */
    private class MailTransmissionContinuation
      extends PassthruResponseCompletion {

      public void handleResponse(Response resp,
        Session.SmtpResponseActions actions) {
        
        m_txLog.receivedResponse(resp);
        m_txLog.add("Response to mail transmission command was " + resp.getCode());

        if(resp.getCode() < 300) {
          getTransaction().commit();
        }
        else {
          getTransaction().failed();
        }
        changeState(BufTxState.DONE);
        actions.transactionEnded(BufferingTransactionHandler.this);
        finalReport();
        super.handleResponse(resp, actions);
      }
    }    
    
  
  
    //****************** Inner-Inner Class Separator ******************
      
    private abstract class ContinuationWithAddress
      extends PassthruResponseCompletion {
  
      private final EmailAddress m_addr;
  
      ContinuationWithAddress(EmailAddress addr) {
        m_addr = addr;
      }
  
      protected EmailAddress getAddress() {
        return m_addr;
      }
      
      public void handleResponse(Response resp,
        Session.SmtpResponseActions actions) {

        super.handleResponse(resp, actions);
      }
    }
  
  
    //****************** Inner-Inner Class Separator ******************
  
    private class MAILContinuation
      extends ContinuationWithAddress {
  
      MAILContinuation(EmailAddress addr) {
        super(addr);
      }
      
      public void handleResponse(Response resp,
        Session.SmtpResponseActions actions) {
        m_txLog.receivedResponse(resp);
        m_txLog.add("Response to MAIL for address \"" +
          getAddress() + "\" was " + resp.getCode());
        getTransaction().fromResponse(getAddress(), (resp.getCode() < 300));
        super.handleResponse(resp, actions);
      }
    }
  
  
    //****************** Inner-Inner Class Separator ******************
      
    private class RCPTContinuation
      extends ContinuationWithAddress {
  
      RCPTContinuation(EmailAddress addr) {
        super(addr);
      }
      
      public void handleResponse(Response resp,
        Session.SmtpResponseActions actions) {
        m_txLog.receivedResponse(resp);
        m_txLog.add("Response to RCPT for address \"" +
          getAddress() + "\" was " + resp.getCode());
        getTransaction().toResponse(getAddress(), (resp.getCode() < 300));
        super.handleResponse(resp, actions);
      }
    }
  
  }//ENDOF BufferingTransactionHandler Class Definition

}//ENDOF BufferingSessionHandler Class Definition
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

import com.metavize.tran.mail.papi.smtp.Command;
import com.metavize.tran.mail.papi.smtp.Response;
import com.metavize.tran.mail.papi.smtp.SmtpTransaction;


/**
 * Root callback interface for Object wishing to
 * participate in an Smtp Session.  A SessionHandler
 * is passed to the constructor of a
 * {@link com.metavize.tran.mail.papi.smtp.sapi.Session Session}.
 * <br>
 * Once registered with a Session, the SessionHandler will be
 * called back to handle various transitions within an Smtp
 * Session.
 * <br>
 * This interface and the associated interfaces instances must
 * obey (such as TransactionHandlers) work from the following
 * model.
 * <br>
 * Client Commands (as well as various forms of MIME Messages}
 * are received by the {@link com.metavize.tran.mail.papi.smtp.sapi.Session parent Session}.
 * These are then passed to either instances of this interface, or
 * to {@link com.metavize.tran.mail.papi.smtp.sapi.TransactionHandler TransactionHandlers}.
 * The Session maintains Transaction boundaries, calling the
 * {@link #createTxHandler factory method} to create TransactionHandlers are
 * Transactions are entered.
 * <br>
 * Each Command/MIME Bit are then passed to a callback on the Session/TransactionHandler
 * along with an Object representing the available Actions (@see Session).  Handlers
 * then can either pass-along the Command/MIME Bit or perform some protocol manipulation.
 * When a Command is passed-along to the server, the Handler must also provide an Object
 * to be notified when the response arrives from the server.  This is a
 * {@link com.metavize.tran.mail.papi.smtp.sapi.ResponseCompletion ResponseCompletion}.
 * For every command issued to the server there should be one outstanding
 * ResponseCompletion.  The Session maintains ordering, such that any pipelining
 * performed by the client is "hidden" from implementers of this interface.
 * <br>
 * For the purposes of protocol manipulation, we will define
 * three terms:
 * <ul>
 *   <li>
 *     <i>Synthetic Request</i>.  This is a request issued to the
 *        server by the Handler itself.  It did not originate from
 *        the client.
 *   </li>
 *   <li>
 *     <i>Synthetic Response</i>.  This is a response issued by the handler
 *        as a result of a client request.  The server never "sees" the
 *        request, and instead the Handler acts-as the server.
 *   </li>
 *   <li>
 *     <i>Buffering</i>.  This form of manipulation takes data from the
 *        client yet does not pass it along to the server.  
 *   </li>  
 * </ul>
 * 
 */
public interface SessionHandler {


  /**
   * Handle a "normal" Command from the client.  This
   * includes things like "HELO" or "HELP" issued
   * outside the boundaries of a transaction.
   * <br>
   * Note that it may be confusing, but a client
   * can issue a "RSET" when not within a transaction.
   * This is a "safety" thing, for servers which
   * reuse Mail sessions (and want the session in
   * a known state before begining a new Transaction).
   *
   * @param command the client command
   * @param actions the available actions
   */
  public void handleCommand(Command command,
    Session.SmtpCommandActions actions);

  /**
   * Edge-case handler.  When an SMTP Session is created,
   * the server is the first actor to send data.  However,
   * the SessionHandler did not "see" any request corresponding
   * to this response and could not have installed
   * a ResponseCompletion.  Instead, this method is used
   * to handle this "misaligned" response.
   *
   * @param resp the response
   * @param actions the available actions.
   */
  public void handleOpeningResponse(Response resp,
    Session.SmtpResponseActions actions);

  /**
   * Create a new TransactionHandler.  This method is called
   * as the Session crosses a Transaction boundary.  Note that
   * the previous Transaction may still be incomplete (waiting
   * for final server disposition) when pipelining (either legal
   * or otherwise) is employed by the client.
   *
   * @param tx the Transaction to be associated with the Handler
   * @return a new TransactionHandler
   */
  public TransactionHandler createTxHandler(SmtpTransaction tx);

}
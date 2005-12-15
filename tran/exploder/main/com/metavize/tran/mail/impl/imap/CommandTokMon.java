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

package com.metavize.tran.mail.impl.imap;
import org.apache.log4j.Logger;
import com.metavize.tran.mail.papi.imap.IMAPTokenizer;
import java.nio.ByteBuffer;

/**
 * Abstract class which looks for a given
 * command (issued from the client on a
 * tagged line), then maintains state of that
 * command.  This class assumes that client-issued
 * commands are on tagged request lines,
 * and that the command itself is the second token.
 * <br><br>
 * Note that this class does not
 * prevent nested commands (and does not detect them).
 */
abstract class CommandTokMon extends TokMon {

  /**
   * Enum of the different Command states
   */
  enum CommandState {
    /**
     * The command has not been issued by the client,
     * or has already been issued and a tagged response
     * from server observed (and completed)
     */
    NOT_ACTIVE,
    /**
     * The command has been issued by the client, yet
     * the tagged response (OK/BAD) has not been issued
     * by the server
     */
    OPEN,
    /**
     * The tagged response is currently being sent by the server.
     */
    CLOSING
  };

  protected static final byte[] OK_BYTES = "ok".getBytes();
  protected static final byte[] NO_BYTES = "no".getBytes();
  protected static final byte[] BAD_BYTES = "bad".getBytes();

  private final Logger m_logger =
    Logger.getLogger(CommandTokMon.class);

  private CommandState m_cmdState = CommandState.NOT_ACTIVE;
  private byte[] m_cmdTag;

  CommandTokMon(ImapSessionMonitor sesMon) {
    super(sesMon);
  }
  
  CommandTokMon(ImapSessionMonitor sesMon,
    TokMon state) {
    super(sesMon, state);
    if(state != null && (state instanceof CommandTokMon)) {
      CommandTokMon other = (CommandTokMon) state;
      m_cmdTag = other.m_cmdTag;
      m_cmdState = other.m_cmdState;
    }
  }

  /**
   * Get the current state of the issued command.  Note that this
   * property is valid <b>as</b> client/server tokens are
   * passed to the handleClient/ServerToken methods.
   *
   * @return the CommandState.
   */
  protected CommandState getCommandState() {
    return m_cmdState;
  }

  @Override
  protected void previewTokenFromClient(IMAPTokenizer tokenizer,
    ByteBuffer buf) {
    super.previewTokenFromClient(tokenizer, buf);

    if(m_cmdState == CommandState.NOT_ACTIVE) {
      //Check for a candidate line
      if(getClientReqType() != ClientReqType.TAGGED ||
        tokenizer.isTokenEOL()) {
        return;
      }

      if(getClientRequestTokenCount() == 1) {
        m_cmdTag = new byte[tokenizer.getTokenLength()];
        for(int i = 0; i<m_cmdTag.length; i++) {
          m_cmdTag[i] = buf.get(tokenizer.getTokenStart() + i);
        }
        return;
      }
      else if(getClientRequestTokenCount() == 2) {
        if(testCommand(tokenizer, buf)) {
          m_cmdState = CommandState.OPEN;
          return;
        }
      }
      m_cmdTag = null;
      m_cmdState = CommandState.NOT_ACTIVE;
    }
  }

  @Override
  protected void previewTokenFromServer(IMAPTokenizer tokenizer,
    ByteBuffer buf) {
    super.previewTokenFromServer(tokenizer, buf);

    if(m_cmdState == CommandState.OPEN) {

      //Look for the start of the tagged response line
      if(!tokenizer.isTokenEOL() && getServerRespType() == ServerRespType.TAGGED) {
        if(m_cmdTag == null) {
          //TODO bscott an error
          m_cmdState = CommandState.NOT_ACTIVE;
          return;
        }
        if(tokenizer.compareWordAgainst(buf, m_cmdTag, false)) {
          m_cmdState = CommandState.CLOSING;
        }
      }
    }
    else if(m_cmdState == CommandState.CLOSING) {
      //Look for a new line start (i.e. not the "EOL", but
      //the first token of a line).  This is when we transition
      //back to NOT_ACTIVE
      if(!tokenizer.isTokenEOL() && getServerResponseTokenCount() == 1) {
        m_cmdState = CommandState.NOT_ACTIVE;
      }
    }
    
  }

  /**
   * Method for base classes to test the current Command (the
   * second token on a TAGGED line).
   *
   * @return true if the CommandTokMon should consider this
   * command a "hit" and track it.
   */
  protected abstract boolean testCommand(IMAPTokenizer tokenizer,
    ByteBuffer buf);
    

}

/*
 * Copyright (c) 2004, 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.mail.papi.smtp;

import java.nio.ByteBuffer;

import com.metavize.tran.token.Token;

/**
 * Opaque chunk of data, used to pass SASL information
 * between casings, being ignored by Transforms.
 */
public class SASLExchangeToken
  implements Token {

  private final ByteBuffer m_buf;

  public SASLExchangeToken(ByteBuffer data) {
    m_buf = data;
  }

  /**
    * Returns a duplicate of the internal ByteBuffer, allowing
    * the caller to modify the returned ByteBuffer without concern
    * for any downstream token handlers.
    */
  public ByteBuffer getBytes() {
    return m_buf.slice();
  }

    public int getEstimatedSize()
    {
        return m_buf.remaining();
    }
}

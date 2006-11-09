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

import com.untangle.tran.token.Chunk;
import java.nio.ByteBuffer;


/**
 * A chunk (ByteBuffer) as part of the IMAP protocol
 * handling.
 */
public class ImapChunk
  extends Chunk {

  public ImapChunk(ByteBuffer data) {
    super(data==null?
      data:(data.position()>0?data.slice():data));
  }  
}
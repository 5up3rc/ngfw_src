/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.mail.papi.imap;

import java.nio.ByteBuffer;


/**
 * A chunk of MIME (bytes) for a message which encountered
 * parsing errors.  Receivers of this type of chunk
 * should differentiate it from other protocol elements
 * they may wish to parse.
 */
public class UnparsableMIMEChunk
  extends ImapChunk {
  
  public UnparsableMIMEChunk(ByteBuffer data) {
    super(data);
  }   
}
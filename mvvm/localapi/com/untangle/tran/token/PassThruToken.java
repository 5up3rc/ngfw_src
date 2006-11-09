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

package com.untangle.tran.token;

import java.nio.ByteBuffer;

/**
 * Token representing the directive that 
 * downstream TokenHandlers should enter
 * Passthru mode (letting through all 
 * bytes unmolested).
 */
public class PassThruToken 
  extends MetadataToken {

  public static final PassThruToken PASSTHRU = new PassThruToken();
  
  private PassThruToken() {}
}
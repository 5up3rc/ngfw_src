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

package com.metavize.tran.mail.impl.quarantine.store;
import org.apache.log4j.Logger;

import java.io.*;


/**
 * Name of a file, relative to some other root.
 * <br><br>
 * Typed just to keep track of when we're dealing
 * with a relative path
 */
class RelativeFileName {

  /**
   * The relative path
   */
  final String relativePath;

  RelativeFileName(String path) {
    this.relativePath = path;
  }

  @Override
  public boolean equals(Object obj) {
    return relativePath.equals(obj);
  }

  @Override
  public int hashCode() {
    return relativePath.hashCode();
  }
}
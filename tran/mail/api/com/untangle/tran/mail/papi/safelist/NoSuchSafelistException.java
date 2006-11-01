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

package com.untangle.tran.mail.papi.safelist;
import java.io.Serializable;

/**
 * ...name says it all...
 */
public class NoSuchSafelistException
  extends Exception
  implements Serializable {

  private final String m_emailAddress;

  public NoSuchSafelistException(String address) {
    super("No safelist for address \"" + address + "\"");
    m_emailAddress = address;
  }

  public String getAddress() {
    return m_emailAddress;
  }
  
}
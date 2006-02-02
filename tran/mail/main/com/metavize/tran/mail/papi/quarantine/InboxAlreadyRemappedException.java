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

package com.metavize.tran.mail.papi.quarantine;
import java.io.Serializable;

/**
 * ...name says it all...
 */
public class InboxAlreadyRemappedException
  extends Exception
  implements Serializable {

  private final String m_alreadyMappedTo;
  private final String m_toRemap;

  public InboxAlreadyRemappedException(String toRemap,
    String alreadyMappedTo) {
    super(toRemap + " already remapped to " + alreadyMappedTo);
    m_toRemap = toRemap;
    m_alreadyMappedTo = alreadyMappedTo;
  }

  public String getAccountToRemap() {
    return m_toRemap;
  }

  public String getAlreadyMappedTo() {
    return m_alreadyMappedTo;
  }
  
  
}
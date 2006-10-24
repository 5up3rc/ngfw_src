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
package com.metavize.tran.mail.web.euv.tags;

import javax.servlet.jsp.PageContext;
import sun.misc.BASE64Encoder;



/**
 * Really dumb tag which just outputs the
 * contents of the current Safelist entry while
 * iterating through a collection of 'em
 * <br><br>
 * Works with SafelistListTag
 * 
 */
public final class SafelistEntryTag 
  extends SingleValueTag {

  private static final String ENTRY_KEY = "metavize.safelist.entry";

  private boolean m_encoded = false;

  public boolean isEncoded() {
    return m_encoded;
  }
  public void setEncoded(boolean encoded) {
    m_encoded = encoded;
  }

  @Override
  protected String getValue() {
    String ret = (String) pageContext.getAttribute(ENTRY_KEY, PageContext.PAGE_SCOPE);
    if(isEncoded()) {
      ret = base64Encode(ret);
    }
    return ret;
  }  

  public static void setCurrent(PageContext pageContext, String entry) {
    pageContext.setAttribute(ENTRY_KEY, entry, PageContext.PAGE_SCOPE);
  }

  private String base64Encode(String s) {
    if(s == null) {
      return null;
    }
    try {
      return new BASE64Encoder().encode(s.getBytes());
    }
    catch(Exception ex) {
      return null;
    }
  }   
}

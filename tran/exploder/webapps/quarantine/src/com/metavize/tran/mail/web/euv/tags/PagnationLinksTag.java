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
package com.metavize.tran.mail.web.euv.tags;

import com.metavize.tran.mail.papi.quarantine.InboxRecordCursor;
import com.metavize.tran.mail.web.euv.Constants;
import com.metavize.tran.mail.web.euv.Util;
import java.net.URLEncoder;


/**
 * Constructs the "next/prev" query string.  Does <b>not</b>
 * check if there <i>should</i> be "prev/next" links.
 * <br><br>
 * Values for LinkType property are either "prev" or "next"
 * 
 */
public final class PagnationLinksTag
  extends SingleValueTag {

  private String m_linkType;


  public String getLinkType() {
    return m_linkType;
  }
  public void setLinkType(String t) {
    m_linkType = t;
  }

  

//InboxRecordCursor getCurrentIndex(ServletRequest request) {  
  
  
  @Override
  protected String getValue() {
    InboxRecordCursor cursor =
      InboxIndexTag.getCurrentIndex(pageContext.getRequest());
    if(cursor == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append(Constants.INBOX_MAINTENENCE_CTL).append('?');
    addKVP(Constants.ACTION_RP, Constants.VIEW_INBOX_RV, sb);
    sb.append('&');     
    addKVP(Constants.AUTH_TOKEN_RP, CurrentAuthTokenTag.getCurrent(pageContext.getRequest()), sb);
    sb.append('&');    
    addKVP(Constants.SORT_BY_RP, Util.sortByToString(cursor.getSortedBy()), sb);
    sb.append('&');
    addKVP(Constants.SORT_ASCEND_RP, "" + cursor.isAscending(), sb);
    sb.append('&');
    if(getLinkType().equalsIgnoreCase("prev")) {
      addKVP(Constants.FIRST_RECORD_RP, "" + cursor.getPrevStartingAt(Constants.RECORDS_PER_PAGE), sb);
    }
    else {
      addKVP(Constants.FIRST_RECORD_RP, "" + cursor.getNextStartingAt(), sb);
    }
    return sb.toString();
  }

  private static void addKVP(String key, String value, StringBuilder sb) {
    sb.append(key).append('=').append(/*URLEncoder.encode(*/value/*)*/);
  }

}

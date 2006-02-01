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
package com.metavize.tran.mail.web.euv;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import com.metavize.tran.mail.papi.quarantine.QuarantineUserView;
import com.metavize.tran.mail.papi.quarantine.InboxIndex;
import com.metavize.tran.mail.papi.quarantine.QuarantineUserActionFailedException;
import com.metavize.tran.mail.papi.quarantine.NoSuchInboxException;
import com.metavize.tran.mail.papi.quarantine.InboxRecordComparator;
import com.metavize.tran.mail.papi.quarantine.InboxRecordCursor;

import com.metavize.tran.mail.papi.safelist.SafelistEndUserView;

import com.metavize.tran.mail.web.euv.tags.MessagesSetTag;
import com.metavize.tran.mail.web.euv.tags.InboxIndexTag;
import com.metavize.tran.mail.web.euv.tags.HasSafelistTag;

import com.metavize.tran.util.Pair;


/**
 * Controler used for inbox maintenence (purge/rescue/view).
 */
public class InboxMaintenenceControler
  extends MaintenenceControlerBase {

  protected void serviceImpl(HttpServletRequest req,
    HttpServletResponse resp,
    String account,
    QuarantineUserView quarantine,
    SafelistEndUserView safelist)
    throws ServletException, IOException {

    try {

      //Now, figure out what they wanted to do.  Either
      //purge, rescue, or simply to see the index
      String action = req.getParameter(Constants.ACTION_RP);
      log("[InboxMaintenenceControler] Action " + action);
      if(action == null) {
        action = Constants.VIEW_INBOX_RV;
      }

      //Defaults for the view of the user's list.
      InboxRecordComparator.SortBy sortBy =
        Util.stringToSortBy(req.getParameter(Constants.SORT_BY_RP),
          InboxRecordComparator.SortBy.INTERN_DATE);
      boolean ascending = Util.readBooleanParam(req, Constants.SORT_ASCEND_RP, true);
      int startingAt = Util.readIntParam(req, Constants.FIRST_RECORD_RP, 0);

//      System.out.println("*** DEBUG ***" +
//        "SortBy: " + sortBy + ", Ascending: " + ascending + ", startingAt: " + startingAt);

      //No matter what action we take, the outcome
      //is an Index
      InboxIndex index = null;

      if(action.equals(Constants.PURGE_RV) ||
        action.equals(Constants.RESCUE_RV)) {

        String[] mids = req.getParameterValues(Constants.MAIL_ID_RP);
        log("[InboxMaintenenceControler]" + (mids==null?"0":mids.length) + " Mail IDs");

        if(action.equals(Constants.PURGE_RV) &&
          mids!= null && mids.length > 0) {
          log("[InboxMaintenenceControler] Purge request for account \"" + account + "\"");
          index = quarantine.purge(account, mids);
          MessagesSetTag.addInfoMessage(req,
            mids.length + " message" + (mids.length>1?"s":"") + " deleted");
        }
        else if(action.equals(Constants.RESCUE_RV) &&
          mids!= null && mids.length > 0) {
          log("[InboxMaintenenceControler] Rescue request for account \"" + account + "\"");
          index = quarantine.rescue(account, mids);
          MessagesSetTag.addInfoMessage(req,
            mids.length + " message" + (mids.length>1?"s":"") + " released");        
        }        
      }
      
      if(action.equals(Constants.SAFELIST_ADD_RV)) {
        String[] targetAddresses = req.getParameterValues(Constants.SAFELIST_TARGET_ADDR_RP);
        if(targetAddresses != null && targetAddresses.length > 0) {
          Pair<String[], InboxIndex> result = addToSafelist(
            req, resp, account, quarantine, safelist, targetAddresses);
          index = result.b;
        }
      }

      if(index == null) {
        index = quarantine.getInboxIndex(account);
      }

      //Set the flag for whether the user does/does not
      //have a Safelist
      HasSafelistTag.setCurrent(req, safelist.hasOrCanHaveSafelist(account));

      InboxIndexTag.setCurrentIndex(req,
        InboxRecordCursor.get(
          index.getAllRecords(),
          sortBy,
          ascending,
          startingAt,
          Constants.RECORDS_PER_PAGE));

      req.getRequestDispatcher(Constants.INBOX_VIEW).forward(req, resp);
      
    }
    catch(NoSuchInboxException ex) {
      //Odd case.  Someone had a valid auth token, yet
      //there is no inbox.  Likely, it was deleted.  Don't
      //give them an error - simply forward them to the display
      //page w/ no messages
      InboxIndexTag.setCurrentIndex(req, null);
      req.getRequestDispatcher(Constants.INBOX_VIEW).forward(req, resp);
    }
//    catch(QuarantineUserActionFailedException ex) {
//    }
    catch(Exception ex) {
      log("[InboxMaintenenceControler] Exception servicing request", ex);
      req.getRequestDispatcher(Constants.SERVER_UNAVAILABLE_ERRO_VIEW).forward(req, resp);
      return;    
    }

  }
} 

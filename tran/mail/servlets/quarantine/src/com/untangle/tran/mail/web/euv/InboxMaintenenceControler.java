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
package com.untangle.tran.mail.web.euv;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import com.untangle.tran.mail.papi.quarantine.QuarantineUserView;
import com.untangle.tran.mail.papi.quarantine.InboxIndex;
import com.untangle.tran.mail.papi.quarantine.QuarantineUserActionFailedException;
import com.untangle.tran.mail.papi.quarantine.NoSuchInboxException;
import com.untangle.tran.mail.papi.quarantine.InboxRecordComparator;
import com.untangle.tran.mail.papi.quarantine.InboxRecordCursor;

import com.untangle.tran.mail.papi.safelist.SafelistEndUserView;

import com.untangle.tran.mail.web.euv.tags.MessagesSetTag;
import com.untangle.tran.mail.web.euv.tags.InboxIndexTag;
import com.untangle.tran.mail.web.euv.tags.HasSafelistTag;
import com.untangle.tran.mail.web.euv.tags.PagnationPropertiesTag;

import com.untangle.tran.util.Pair;

/**
 * Controler used for inbox maintenence (purge/rescue/refresh/view).
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
      //purge, rescue, refresh, or simply to see the index
      //Note that refresh or simply to see the index,
      //we fall through and take the basic action (not purge and rescue)

      String action = req.getParameter(Constants.ACTION_RP);
      log("[InboxMaintenenceControler] Action " + action);
      if(action == null) {
        action = Constants.VIEW_INBOX_RV;
      }

      //Defaults for the view of the user's list.
      InboxRecordComparator.SortBy sortBy =
        Util.stringToSortBy(req.getParameter(Constants.SORT_BY_RP),
          InboxRecordComparator.SortBy.INTERN_DATE);
      boolean ascending = Util.readBooleanParam(req, Constants.SORT_ASCEND_RP, false);
      int startingAt = Util.readIntParam(req, Constants.FIRST_RECORD_RP, 0);
      int rowsPerPage = Util.readIntParam(req, "rowsPerPage", Constants.RECORDS_PER_PAGE);

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
          rowsPerPage));

      PagnationPropertiesTag.setCurrentRowsPerPAge(req, "" + rowsPerPage);

      req.getRequestDispatcher(Constants.INBOX_VIEW).forward(req, resp);
    } catch(NoSuchInboxException ex) {
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

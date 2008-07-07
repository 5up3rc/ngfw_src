/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.untangle.node.mail.web.euv;

import java.io.IOException;
import java.util.HashSet;
import java.security.Principal;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.LocalUvmContext;
import com.untangle.uvm.addrbook.RemoteAddressBook;
import com.untangle.uvm.addrbook.UserEntry;
import com.untangle.node.mail.papi.quarantine.BadTokenException;
import com.untangle.node.mail.papi.quarantine.InboxIndex;
import com.untangle.node.mail.papi.quarantine.InboxRecord;
import com.untangle.node.mail.papi.quarantine.InboxRecordComparator;
import com.untangle.node.mail.papi.quarantine.InboxRecordCursor;
import com.untangle.node.mail.papi.quarantine.NoSuchInboxException;
import com.untangle.node.mail.papi.quarantine.QuarantineUserActionFailedException;
import com.untangle.node.mail.papi.quarantine.QuarantineUserView;
import com.untangle.node.mail.papi.safelist.NoSuchSafelistException;
import com.untangle.node.mail.papi.safelist.SafelistActionFailedException;
import com.untangle.node.mail.papi.safelist.SafelistEndUserView;
import com.untangle.node.mail.web.euv.tags.CurrentAuthTokenTag;
import com.untangle.node.mail.web.euv.tags.CurrentEmailAddressTag;
import com.untangle.node.mail.web.euv.tags.HasSafelistTag;
import com.untangle.node.mail.web.euv.tags.InboxIndexTag;
import com.untangle.node.mail.web.euv.tags.IsReceivesRemapsTag;
import com.untangle.node.mail.web.euv.tags.IsRemappedTag;
import com.untangle.node.mail.web.euv.tags.MaxDaysIdleInboxTag;
import com.untangle.node.mail.web.euv.tags.MaxDaysToInternTag;
import com.untangle.node.mail.web.euv.tags.MessagesSetTag;
import com.untangle.node.mail.web.euv.tags.ReceivingRemapsListTag;
import com.untangle.node.mail.web.euv.tags.RemappedToTag;
import com.untangle.node.mail.web.euv.tags.SafelistListTag;
import com.untangle.node.mail.web.euv.tags.PagnationPropertiesTag;
import com.untangle.node.util.Pair;

/**
 * Controler used for inbox maintenence (purge/rescue/refresh/view).
 */
public class InboxMaintenenceControler extends HttpServlet
{
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        String authTkn = req.getParameter(Constants.AUTH_TOKEN_RP);
        if(authTkn == null) {
            log("[MaintenenceControlerBase] Auth token null");
            req.getRequestDispatcher(Constants.REQ_DIGEST_VIEW).forward(req, resp);
            return;
        }

        //Get the QuarantineUserView reference.  If we cannot,
        //the user is SOL
        SafelistEndUserView safelist =
            QuarantineEnduserServlet.instance().getSafelist();
        if(safelist == null) {
            log("[MaintenenceControlerBase] Safelist Hosed");
            req.getRequestDispatcher(Constants.SERVER_UNAVAILABLE_ERRO_VIEW).forward(req, resp);
            return;
        }
        QuarantineUserView quarantine =
            QuarantineEnduserServlet.instance().getQuarantine();
        if(quarantine == null) {
            log("[MaintenenceControlerBase] Quarantine Hosed");
            req.getRequestDispatcher(Constants.SERVER_UNAVAILABLE_ERRO_VIEW).forward(req, resp);
            return;
        }
        String maxDaysToIntern =
            QuarantineEnduserServlet.instance().getMaxDaysToIntern();
        if(maxDaysToIntern == null) {
            log("[MaintenenceControlerBase] Quarantine Settings (days to intern) Hosed");
            req.getRequestDispatcher(Constants.SERVER_UNAVAILABLE_ERRO_VIEW).forward(req, resp);
            return;
        }
        String maxDaysIdleInbox =
            QuarantineEnduserServlet.instance().getMaxDaysIdleInbox();
        if(maxDaysIdleInbox == null) {
            log("[MaintenenceControlerBase] Quarantine Settings (days inbox idle) Hosed");
            req.getRequestDispatcher(Constants.SERVER_UNAVAILABLE_ERRO_VIEW).forward(req, resp);
            return;
        }

        String account = null;
        try {
            //Attempt to decrypt their token
            if (authTkn.equals("PU")) {
                Principal pl = req.getUserPrincipal();
                if (null != pl) {
                    LocalUvmContext mctx = LocalUvmContextFactory.context();
                    RemoteAddressBook ab = mctx.appAddressBook();
                    String user = mctx.portalManager().getUid(pl);
                    if (user != null) {
                        UserEntry ue = ab.getEntry(user);
                        if (null != ue) {
                            account = ue.getEmail();
                        }
                    }
                }

                if (null == account ||
                    false == account.contains("@") ||
                    true == account.equalsIgnoreCase("user@localhost")) {
                    log("[MaintenenceControlerBase] (quarantine access through portal) email address is invalid: " + account);
                    req.getRequestDispatcher(Constants.INVALID_PORTAL_EMAIL).forward(req, resp);
                    return;
                }
            } else {
                account = quarantine.getAccountFromToken(authTkn);
            }
            String remappedTo = quarantine.getMappedTo(account);
            IsRemappedTag.setCurrent(req, remappedTo!= null);
            if(remappedTo != null) {
                RemappedToTag.setCurrent(req, remappedTo);
            }

            String[] inboundRemappings = quarantine.getMappedFrom(account);
            if(inboundRemappings != null && inboundRemappings.length > 0) {
                IsReceivesRemapsTag.setCurrent(req, true);
                ReceivingRemapsListTag.setCurrentList(req, inboundRemappings);
            }
            else {
                IsReceivesRemapsTag.setCurrent(req, false);
            }
        }
        catch(BadTokenException ex) {
            //Put a message in the request
            MessagesSetTag.setErrorMessages(req, "Unable to determine your email address from your previous request");
            req.getRequestDispatcher(Constants.REQ_DIGEST_VIEW).forward(req, resp);
            return;
        }
        catch(Exception ex) {
            log("[MaintenenceControlerBase] Exception servicing request", ex);
            req.getRequestDispatcher(Constants.SERVER_UNAVAILABLE_ERRO_VIEW).forward(req, resp);
            return;
        }
        CurrentEmailAddressTag.setCurrent(req, account);
        CurrentAuthTokenTag.setCurrent(req, authTkn);
        MaxDaysToInternTag.setMaxDays(req, maxDaysToIntern);
        MaxDaysIdleInboxTag.setMaxDays(req, maxDaysIdleInbox);

        /* This is just plain wrong. */
        req.setAttribute( "cdata_start", "<![CDATA[" );
        req.setAttribute( "cdata_end", "]]>" );
        /* End of the wrongness */


        /* Setup the cobranding settings. */
        LocalUvmContext uvm = LocalUvmContextFactory.context();
        req.setAttribute( "bs", uvm.brandingManager().getBaseSettings());
        /* setup the skinning settings */
        req.setAttribute( "ss", uvm.skinManager().getSkinSettings());
        serviceImpl(req, resp, account, quarantine, safelist);
    }

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

            //Defaults for the view of the user's list.
            InboxRecordComparator.SortBy sortBy =
                Util.stringToSortBy(req.getParameter(Constants.SORT_BY_RP),
                                    InboxRecordComparator.SortBy.INTERN_DATE);
            boolean ascending = Util.readBooleanParam(req, Constants.SORT_ASCEND_RP, false);
            int startingAt = Util.readIntParam(req, Constants.FIRST_RECORD_RP, 0);
            int rowsPerPage = Util.readIntParam(req, Constants.ROWS_PER_PAGE_RP, Constants.RECORDS_PER_PAGE);


            //No matter what action we take, the outcome is an Index
            InboxIndex index = quarantine.getInboxIndex(account);

            //Set the flag for whether the user does/does not have a Safelist
            HasSafelistTag.setCurrent(req, safelist.hasOrCanHaveSafelist(account));

            SafelistListTag.setCurrentList(req, safelist.getSafelistContents(account));

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

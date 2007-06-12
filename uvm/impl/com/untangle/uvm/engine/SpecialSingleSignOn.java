/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.uvm.engine;

import java.io.IOException;

import java.security.Principal;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;

import org.apache.catalina.Valve;
import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.authenticator.SingleSignOn;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;

import com.untangle.uvm.UvmLocalContext;
import com.untangle.uvm.portal.BasePortalManager;
import com.untangle.uvm.portal.BasePortalLogin;
import com.untangle.uvm.security.UvmPrincipal;


class SpecialSingleSignOn extends SingleSignOn
{
    /* This is a set of all of the context paths that use UvmPrincipal */
    /* Dirty hack designed to ignore sessions that are in this context path */
    private final Set<String> uvmContextSet;

    private BasePortalManager pmgr;

    SpecialSingleSignOn(UvmLocalContext uvmContext, String ... contextPathArray )
    {
        Set<String> contextSet = new HashSet<String>();
        
        for ( String contextPath : contextPathArray ) contextSet.add( contextPath );
        
        this.uvmContextSet =  Collections.unmodifiableSet( contextSet );

        pmgr = (BasePortalManager) uvmContext.portalManager();
    }

    /**
     * Perform single-sign-on support processing for this request.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    @Override
    public void invoke(Request request, Response response)
        throws IOException, ServletException {
        String contextPath = request.getContextPath();
        if ( uvmContextSet.contains(contextPath)) {
            /* Ignore single sign on for this context path */
            if ( containerLog.isDebugEnabled()) {
                containerLog.debug( "The path: [" + contextPath + "] is ignored by single sign on" );
            }
            
            Valve next = getNext();
            if ( next != null ) next.invoke(request,response);
            return;
        } else {
            if ( containerLog.isDebugEnabled()) {
                containerLog.debug( "The path: [" + contextPath + "] uses sign on" );
            }
        }
        super.invoke( request, response );

        Principal principal = request.getUserPrincipal();
        if (principal != null && principal instanceof BasePortalLogin) {
            if (containerLog.isDebugEnabled())
                containerLog.debug( "Checking liveness for " + principal.getName());
            boolean live = pmgr.isLive(principal);
            if (!live) {
                request.setAuthType(null);
                request.setUserPrincipal(null);
                String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
                if (containerLog.isDebugEnabled())
                    containerLog.debug( "Not live, clearing sso " + ssoId);
                if (ssoId != null)
                    deregister(ssoId);
            }
        }
    }

    @Override
    protected void register(String ssoId, Principal principal, String authType,
                            String username, String password)
    {
        /* Never register these sessions, they are bunk */
        if (principal instanceof UvmPrincipal ) {
            if ( containerLog.isDebugEnabled()) {
                containerLog.debug( "Ignoring uvm principal" );
            }

            return;
        }
        
        super.register(ssoId,principal,authType,username,password);
    }
}

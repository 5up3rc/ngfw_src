/*
 * Copyright (c) 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.ids;

import java.util.List;

import com.metavize.mvvm.tapi.*;
import com.metavize.mvvm.tapi.event.*;

public class IDSSessionInfo {

    private     List<IDSRuleSignature>  c2sSignatures;
    private     List<IDSRuleSignature>  s2cSignatures;
    private     IPSession               session;
    private     IPDataEvent             event;
    private     String                  uriPath;
    private     boolean                 isServer;

    //ContentOption variables
    public int start;
    public int end;
    public int indexOfLastMatch;

    public IDSSessionInfo(IPSession session) {
        this.session = session;
    }

    //public void setContentOptionStart(int val) { start = val; }
    //public int getContentOptionStart(int val) { return start; }
    //public void setContentOptionEnd(int val) { end = val; }
    //public int getContentOptionEnd(int val) { return end; }

    public void setUriPath(String path) {
        uriPath = path;
    }

    public String  getUriPath() {
        return uriPath;
    }
    /**Do i need to set sessionion data? I dont think so..
     * Check later.
     */
    public IPSession getSession() {
        return session;
    }

    public void setC2SSignatures(List<IDSRuleSignature> signatures) {
        this.c2sSignatures = signatures;
    }

    public void setS2CSignatures(List<IDSRuleSignature> signatures) {
        this.s2cSignatures = signatures;
    }
    public void setEvent(IPDataEvent event) {
        this.event = event;
    }

    public IPDataEvent getEvent() {
        return event;
    }

    public void setFlow(boolean isServer) {
        this.isServer = isServer;
    }

    public boolean isServer() {
        return isServer;
    }

    // First match wins. XX
    public boolean processC2SSignatures() {
        for(IDSRuleSignature sig : c2sSignatures)
            if (sig.execute(this))
                return true;
        return false;
    }

    // First match wins. XX
    public boolean processS2CSignatures() {
        for(IDSRuleSignature sig : s2cSignatures)
            if (sig.execute(this))
                return true;
        return false;
    }

    // For debugging/loggin
    public int numC2SSignatures() {return c2sSignatures.size();}
    public int numS2CSignatures() {return s2cSignatures.size();}

    public void blockSession() {
        if(session instanceof TCPSession) {
            ((TCPSession)session).resetClient();
            ((TCPSession)session).resetServer();
        }
        else if(session instanceof UDPSession) {
            ((UDPSession)session).expireClient();
            ((UDPSession)session).expireServer();
        }
        session.release();
    }



    /**Debug methods*/
    public boolean testSignature(int num) {
        return c2sSignatures.get(num).execute(this);
    }

    public IDSRuleSignature getSignature(int num) {
        return c2sSignatures.get(num);
    }
    // */
}

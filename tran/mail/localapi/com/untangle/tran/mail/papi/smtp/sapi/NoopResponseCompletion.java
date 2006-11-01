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

package com.untangle.tran.mail.papi.smtp.sapi;

import com.untangle.tran.mail.papi.smtp.Response;

/**
 * Convienence implementation of ResponseCompletion
 * which does nothing
 * 
 */
public class NoopResponseCompletion
  implements ResponseCompletion {


  public void handleResponse(Response resp,
    Session.SmtpResponseActions actions) {
    //Nothing to do...
  }

}
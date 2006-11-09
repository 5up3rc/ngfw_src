/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.mail.impl.quarantine.store;

import com.untangle.tran.mail.papi.quarantine.InboxRecord;
import com.untangle.tran.mail.papi.quarantine.MailSummary;
import java.io.Serializable;

/**
 * Private implementation of an Inbox record
 */
public final class InboxRecordImpl
  extends InboxRecord
  implements Serializable {

  public InboxRecordImpl() {}

  public InboxRecordImpl(String mailID,
    long addedOn,
    MailSummary summary,
    String[] recipients) {
    
    super(mailID, addedOn, summary, recipients);
    
  }
}

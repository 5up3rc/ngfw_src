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

package com.untangle.tran.mime;

/**
 * Enum of recipient types (TO, CC).  Note that "Bcc" is part of this
 * enumeration, but will never be returned from a MIMEMessage.  It
 * has been added to assist in SMTP-type stuff.
 */
public enum RcptType {
  TO,
  CC,
  BCC
}
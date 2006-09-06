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
package com.metavize.tran.mail.papi.smtp;


/**
 * Exception thrown whena response line
 * is illegal (not starting with "NNN"
 */
public class NotAnSMTPResponseLineException
  extends Exception {
  
  public NotAnSMTPResponseLineException() {
    super();
  }
  public NotAnSMTPResponseLineException(Exception ex) {
    super(ex);
  }  
  public NotAnSMTPResponseLineException(String msg) {
    super(msg);
  } 
  public NotAnSMTPResponseLineException(String msg, Exception ex) {
    super(msg, ex);
  } 
  
}
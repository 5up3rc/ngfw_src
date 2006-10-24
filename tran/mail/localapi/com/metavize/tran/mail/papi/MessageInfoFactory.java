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
package com.metavize.tran.mail.papi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.metavize.tran.mime.*;
import com.metavize.mvvm.tran.PipelineEndpoints;
import org.apache.log4j.Logger;

public class MessageInfoFactory
{
    private MessageInfoFactory() { }

    /**
     * Helper MessageInfo factory method which constructs a MessageInfo from
     * the contents of a MIME message.  Moved here because xdoclet doesn't let
     * it be in MessageInfo.java.
     */
    public static MessageInfo fromMIMEMessage(MIMEMessageHeaders headers,
      PipelineEndpoints pe,
      int port) {

      MessageInfo ret = new MessageInfo(pe, port, headers.getSubject());

      //Drain all TO and CC
      List<EmailAddressWithRcptType> allRcpts = headers.getAllRecipients();
      for(EmailAddressWithRcptType eawrt : allRcpts) {
        if(!eawrt.address.isNullAddress()) {
          ret.addAddress(
            ((eawrt.type == RcptType.TO)?AddressKind.TO:AddressKind.CC),
            eawrt.address.getAddress(),
            eawrt.address.getPersonal());
        }
      }

      //Drain FROM
      EmailAddress from = headers.getFrom();
      if(from != null &&
        !from.isNullAddress()) {
        ret.addAddress(
            AddressKind.FROM,
            from.getAddress(),
            from.getPersonal());
      }
      return ret;
    }
}

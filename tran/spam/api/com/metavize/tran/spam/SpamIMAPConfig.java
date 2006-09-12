/*
 * Copyright (c) 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.spam;

import java.io.Serializable;
import java.util.*;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.metavize.tran.mail.papi.WrappedMessageGenerator;
import org.hibernate.annotations.Type;

/**
 * Spam control: Definition of spam control settings (either
 * direction)
 *
 * @author <a href="mailto:jdi@metavize.com">John Irwin</a>
 * @version 1.0
 */
@Entity
@Table(name="tr_spam_imap_config", schema="settings")
public class SpamIMAPConfig extends SpamProtoConfig
 {
     private static final long serialVersionUID = 7520156745253589127L;

     /* settings */
     private SpamMessageAction zMsgAction = SpamMessageAction.MARK;

     // constructor ------------------------------------------------------------

     public SpamIMAPConfig() {}

     public SpamIMAPConfig(boolean bScan,
                           SpamMessageAction zMsgAction,
                           int strength,
                           String zNotes,
                           String subjectTemplate,
                           String bodyTemplate,
                           String headerName,
                           String isSpamHeaderValue,
                           String isHamHeaderValue)
     {
         super(bScan,
               strength,
               zNotes,
               subjectTemplate,
               bodyTemplate,
               headerName,
               isSpamHeaderValue,
               isHamHeaderValue);
         this.zMsgAction = zMsgAction;
     }

     // business methods ------------------------------------------------------

     /*
       public String render(String site, String category)
       {
       String message = BLOCK_TEMPLATE.replace("@HEADER@", header);
       message = message.replace("@SITE@", site);
       message = message.replace("@CATEGORY@", category);
       message = message.replace("@CONTACT@", contact);

       return message;
       }
     */

     // accessors --------------------------------------------------------------

     /**
      * messageAction: a string specifying a response if a message
      * contains spam (defaults to MARK) one of MARK or PASS
      *
      * @return the action to take if a message is judged to be spam.
      * @hibernate.property
      */
     @Column(name="msg_action", nullable=false)
     @Type(type="com.metavize.tran.spam.SpamMessageActionUserType")
     public SpamMessageAction getMsgAction()
     {
         return zMsgAction;
     }

     public void setMsgAction(SpamMessageAction zMsgAction)
     {
         // Guard XXX
         this.zMsgAction = zMsgAction;
         return;
     }

     /* for GUI */
     @Transient
     public String[] getMsgActionEnumeration()
     {
         SpamMessageAction[] azMsgAction = SpamMessageAction.getValues();
         String[] azStr = new String[azMsgAction.length];

         for (int i = 0; i < azMsgAction.length; i++)
             azStr[i] = azMsgAction[i].toString();

         return azStr;
     }
}

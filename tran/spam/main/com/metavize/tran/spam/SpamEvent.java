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

package com.metavize.tran.spam;

import java.util.Iterator;

import com.metavize.mvvm.logging.LogEvent;
import com.metavize.mvvm.logging.SyslogBuilder;
import com.metavize.mvvm.logging.SyslogPriority;
import com.metavize.mvvm.tran.PipelineEndpoints;
import com.metavize.tran.mail.papi.AddressKind;
import com.metavize.tran.mail.papi.MessageInfo;
import com.metavize.tran.mail.papi.MessageInfoAddr;

public abstract class SpamEvent extends LogEvent
{
    // action types
    public static final int PASSED = 0; // pass or clean message
    public static final int MARKED = 1;
    public static final int BLOCKED = 2;
    public static final int QUARANTINED = 3;

    // constructors -----------------------------------------------------------

    public SpamEvent() { }

    // abstract methods -------------------------------------------------------

    public abstract String getType();
    public abstract boolean isSpam();
    public abstract float getScore();
    public abstract int getActionType();
    public abstract String getActionName();
    public abstract MessageInfo getMessageInfo();
    public abstract String getVendorName();

    // public methods ---------------------------------------------------------

    public String getSender()
    {
        return get(AddressKind.FROM);
    }

    public String getReceiver()
    {
        return get(AddressKind.TO);
    }

    public String getSubject()
    {
        return null == getMessageInfo() ? "" : getMessageInfo().getSubject();
    }

    public String getClientAddr()
    {
        if (null == getMessageInfo()) {
            return "";
        } else {
            PipelineEndpoints pe = getMessageInfo().getPipelineEndpoints();
            return null == pe ? "" : pe.getCClientAddr().getHostAddress();
        }
    }

    public int getClientPort()
    {
        if (null == getMessageInfo()) {
            return -1;
        } else {
            PipelineEndpoints pe = getMessageInfo().getPipelineEndpoints();
            return null == pe ? -1 : pe.getCClientPort();
        }
    }

    public String getServerAddr()
    {
        if (null == getMessageInfo()) {
            return "";
        } else {
            PipelineEndpoints pe = getMessageInfo().getPipelineEndpoints();
            return null == pe ? "" : pe.getSServerAddr().getHostAddress();
        }
    }

    public int getServerPort()
    {
        if (null == getMessageInfo()) {
            return -1;
        } else {
            PipelineEndpoints pe = getMessageInfo().getPipelineEndpoints();
            return null == pe ? -1 : pe.getSServerPort();
        }
    }

    public String getDirectionName()
    {
        if (null == getMessageInfo()) {
            return null;
        } else {
            PipelineEndpoints pe = getMessageInfo().getPipelineEndpoints();
            return null == pe ? null : pe.getDirectionName();
        }
    }

    // Syslog methods ---------------------------------------------------------

    public void appendSyslog(SyslogBuilder sb)
    {
        PipelineEndpoints pe = getMessageInfo().getPipelineEndpoints();
        if (null != pe) {
            pe.appendSyslog(sb);
        }

        sb.startSection("info");
        sb.addField("vendor", getVendorName());
        sb.addField("score", getScore());
        sb.addField("spam", isSpam());
        sb.addField("action", getActionName());
        sb.addField("sender", getSender());
        sb.addField("receiver", getReceiver());
        sb.addField("subject", getSubject());
    }

    public String getSyslogId()
    {
        return getType();
    }

    public SyslogPriority getSyslogPriority()
    {
        switch(getActionType())
        {
            case PASSED:
                // NOTICE = spam but passed
                // INFORMATIONAL = statistics or normal operation
                return true == isSpam() ? SyslogPriority.NOTICE : SyslogPriority.INFORMATIONAL;

            default:
            case MARKED:
            case BLOCKED:
            case QUARANTINED:
                return SyslogPriority.WARNING; // traffic altered
        }
    }

    // internal methods ---------------------------------------------------------

    protected String get(AddressKind kind)
    {
        MessageInfo messageInfo = getMessageInfo();

        if (null == messageInfo) {
            return "";
        } else {
            for (Iterator i = messageInfo.getAddresses().iterator(); i.hasNext(); ) {
                MessageInfoAddr mi = (MessageInfoAddr)i.next();

                if (mi.getKind() == kind && mi.getPosition() == 1) {
                    String addr = mi.getAddr();
                    if (addr == null)
                        return "";
                    else
                        return addr;
                }
            }

            return "";
        }
    }
}

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

package com.untangle.mvvm.logging;

import com.untangle.mvvm.tran.IPaddr;
import java.net.InetAddress;
import java.util.Date;

public interface SyslogBuilder
{
    void startSection(String s);

    void addField(String key, String value);
    void addField(String key, boolean value);
    void addField(String key, int value);
    void addField(String key, long value);
    void addField(String key, double value);
    void addField(String key, InetAddress addr);
    void addField(String key, IPaddr addr);
    void addField(String key, Date date);
}

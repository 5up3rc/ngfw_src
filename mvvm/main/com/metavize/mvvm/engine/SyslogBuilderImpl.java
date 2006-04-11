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

package com.metavize.mvvm.engine;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Date;
import java.util.Formatter;

import com.metavize.mvvm.logging.LogEvent;
import com.metavize.mvvm.logging.SyslogBuilder;
import com.metavize.tran.util.AsciiCharBuffer;
import org.apache.log4j.Logger;

public class SyslogBuilderImpl implements SyslogBuilder
{
    private static final int PACKET_SIZE = 1024;
    private static final int MAX_VALUE_SIZE = 256;
    private static final String DATE_FORMAT = "%1$tb %1$2te %1$tH:%1$tM:%1$tS";

    private final byte[] buf = new byte[PACKET_SIZE];
    private final AsciiCharBuffer sb = AsciiCharBuffer.wrap(buf);
    private final Formatter dateFormatter = new Formatter(sb);

    private final Logger logger = Logger.getLogger(getClass());

    private boolean first = true;
    private boolean inSection = false;

    // public methods ---------------------------------------------------------

    public void startSection(String s)
    {
        sb.append(" # ");
        sb.append(s);
        sb.append(": ");
        first = true;
    }

    public void addField(String key, String value)
    {
        if (null == value)
            value = "";
        int s = key.length() + (first ? 0 : 2) + 1;
        if (sb.remaining() <= s) {
            logger.error("could not fit field key: '" + key
                         + "' value: '" + value + "'");
            return;
        }

        if (!first) {
            sb.append(", ");
        } else {
            first = false;
        }

        sb.append(key);
        sb.append("=");

        int i = Math.min(value.length(), MAX_VALUE_SIZE);

        if (sb.remaining() < i) {
            logger.warn("value too long, truncating");
            i = sb.remaining();
        }

        sb.append(value, 0, i);
    }

    public void addField(String key, boolean value)
    {
        addField(key, Boolean.toString(value));
    }

    public void addField(String key, int value)
    {
        addField(key, Integer.toString(value));
    }

    public void addField(String key, long value)
    {
        addField(key, Long.toString(value));
    }

    public void addField(String key, double value)
    {
        addField(key, Double.toString(value));
    }

    public void addField(String key, InetAddress ia)
    {
        addField(key, ia.getHostAddress());
    }

    public void addField(String key, Date d)
    {
        addField(key, d.toString());
    }

    DatagramPacket makePacket(LogEvent e, int facility, String host,
                              String tag)
    {
        sb.clear();

        int v = 8 * facility + e.getSyslogPriority().getPriorityValue();
        sb.append("<");
        sb.append(Integer.toString(v));
        sb.append(">");

        // 'TIMESTAMP'
        dateFormatter.format(DATE_FORMAT, e.getTimeStamp());

        sb.append(' ');

        // 'HOSTNAME'
        sb.append(host); // XXX use legit hostname

        sb.append(' ');

        // 'TAG[pid]: '
        sb.append(tag);

        // CONTENT
        sb.append(e.getSyslogId());

        e.appendSyslog(this);

        sb.append(" #");

        return new DatagramPacket(buf, 0, sb.position());
    }
}

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

package com.metavize.mvvm.tran;

import java.io.Serializable;
import java.util.Date;

/**
 * <code>TransformStats</code> records vital statistics for a running
 * transform.  It is contained within each Transform.  All statistics
 * are since the last start.
 *
 * @author <a href="mailto:jdi@untangle.com"></a>
 * @version 1.0
 */
public class TransformStats implements Serializable
{
    private static final long serialVersionUID = 5196595597614966041L;

    protected long c2tBytes = 0;
    protected long t2sBytes = 0;
    protected long s2tBytes = 0;
    protected long t2cBytes = 0;

    protected long c2tChunks = 0;
    protected long t2sChunks = 0;
    protected long s2tChunks = 0;
    protected long t2cChunks = 0;

    protected int udpSessionCount = 0;
    protected int tcpSessionCount = 0;
    protected int udpSessionTotal = 0;
    protected int tcpSessionTotal = 0;

    protected int udpSessionRequestTotal = 0;
    protected int tcpSessionRequestTotal = 0;

    protected Date startDate;
    protected Date lastConfigureDate;
    protected Date lastActivityDate;

    // XXX temporary hack, remove someday !!!
    private long[] counters = new long[16];

    public TransformStats() {
        long now = System.currentTimeMillis();
        startDate = new Date(now);
        lastConfigureDate = new Date(now);
        lastActivityDate = new Date(now);
    }

    /**
     * <code>tcpSessionCount</code> gives the count of live TCP sessions for this transform
     *
     * @return an <code>int</code> giving the number of live TCP sessions
     */
    public int tcpSessionCount() {
        return tcpSessionCount;
    }

    /**
     * <code>udpSessionCount</code> gives the count of live UDP sessions for this transform
     *
     * @return an <code>int</code> giving the number of live UDP sessions
     */
    public int udpSessionCount() {
        return udpSessionCount;
    }

    /**
     * <code>tcpSessionTotal</code> gives the count of all TCP sessions since the transform
     * was started
     *
     * @return an <code>int</code> giving the total number of TCP sessions created since start
     */
    public int tcpSessionTotal() {
        return tcpSessionTotal;
    }

    /**
     * <code>udpSessionTotal</code> gives the count of all UDP sessions since the transform
     * was started
     *
     * @return an <code>int</code> giving the total number of UDP sessions created since start
     */
    public int udpSessionTotal() {
        return udpSessionTotal;
    }

    /**
     * <code>tcpSessionRequestTotal</code> gives the count of all TCP session
     * requests since the transform was started
     *
     * @return an <code>int</code> giving the total number of new TCP sessions requested since start
     */
    public int tcpSessionRequestTotal() {
        return tcpSessionRequestTotal;
    }

    /**
     * <code>udpSessionRequestTotal</code> gives the count of all UDP session
     * requests since the transform was started
     *
     * @return an <code>int</code> giving the total number of new UDP sessions requested since start
     */
    public int udpSessionRequestTotal() {
        return udpSessionRequestTotal;
    }


    /**
     * <code>c2tBytes</code> gives the count of bytes transferred from the client to the transform.
     * This may not be the same as <code>t2sBytes</code> if the transform is changing the data.
     *
     * @return a <code>long</code> giving the number of bytes transferred from the client to the transform.
     */
    public long c2tBytes() {
        return c2tBytes;
    }

    public void c2tBytes(long c2tBytes) {
        this.c2tBytes = c2tBytes;
    }

    /**
     * <code>t2sBytes</code> gives the count of bytes transferred from the transform to the server.
     * This may not be the same as <code>c2tBytes</code> if the transform is changing the data.
     *
     * @return a <code>long</code> giving the number of bytes transferred from the transform to the server.
     */
    public long t2sBytes() {
        return t2sBytes;
    }

    public void t2sBytes(long t2sBytes) {
        this.t2sBytes = t2sBytes;
    }

    /**
     * <code>s2tBytes</code> gives the count of bytes transferred from the server to the transform.
     * This may not be the same as <code>t2cBytes</code> if the transform is changing the data.
     *
     * @return a <code>long</code> giving the number of bytes transferred from the server to the transform.
     */
    public long s2tBytes() {
        return s2tBytes;
    }

    public void s2tBytes(long s2tBytes) {
        this.s2tBytes = s2tBytes;
    }

    /**
     * <code>t2cBytes</code> gives the count of bytes transferred from the transform to the client.
     * This may not be the same as <code>s2tBytes</code> if the transform is changing the data.
     *
     * @return a <code>long</code> giving the number of bytes transferred from the transform to the client.
     */
    public long t2cBytes() {
        return t2cBytes;
    }

    public void t2cBytes(long t2cBytes) {
        this.t2cBytes = t2cBytes;
    }

    // Chunks for tcp, packets for udp (icmp)
    public long c2tChunks() {
        return c2tChunks;
    }

    public void c2tChunks(long c2tChunks) {
        this.c2tChunks = c2tChunks;
    }

    public long t2sChunks() {
        return t2sChunks;
    }

    public void t2sChunks(long t2sChunks) {
        this.t2sChunks = t2sChunks;
    }

    public long s2tChunks() {
        return s2tChunks;
    }

    public void s2tChunks(long s2tChunks) {
        this.s2tChunks = s2tChunks;
    }

    public long t2cChunks() {
        return t2cChunks;
    }

    public void t2cChunks(long t2cChunks) {
        this.t2cChunks = t2cChunks;
    }


    /**
     * <code>startDate</code> gives the time of the transform's start (the last time
     * the transform entered the start state).
     *
     * @return a <code>Date</code> giving the time of the tranform's start
     */
    public Date startDate() {
        return startDate;
    }

    /**
     * <code>lastConfigureDate</code> gives the time of the transform's last configuration change
     * (either the last start time or the last time reconfigure() was called).
     *
     * @return a <code>Date</code> giving the time of the tranform's last configuration change
     */
    public Date lastConfigureDate() {
        return lastConfigureDate;
    }

    /**
     * <code>lastActivityDate</code> gives the time of the last activity on any session
     * of the transform.
     * Activity is any bytes, creation, or close/shutdown.
     *
     * @return a <code>Date</code> giving the time of the last activity on this session
     */
    public Date lastActivityDate() {
        return lastActivityDate;
    }


    public long getCount(int i)
    {
        synchronized (counters) {
            return counters[i];
        }
    }

    public long incrementCount(int i)
    {
        return incrementCount(i, 1);
    }

    public long incrementCount(int i, long delta)
    {
        synchronized (counters) {
            return counters[i] += delta;
        }
    }
}

/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id: RBLClient.java 8515 2007-01-03 00:13:24Z amread $
 */

package com.untangle.tran.spam;

import java.io.IOException;
import java.lang.SecurityException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import com.untangle.mvvm.tapi.TCPNewSessionRequest;
import com.untangle.mvvm.tran.PipelineEndpoints;
import org.apache.log4j.Logger;

public final class RBLClient implements Runnable {
    private final Logger logger = Logger.getLogger(getClass());

    private RBLClientContext cContext;

    private Thread cThread;
    private String dbgName; // thread name and socket host

    public RBLClient(RBLClientContext cContext) {
        this.cContext = cContext;
    }

    public void setThread(Thread cThread) {
        this.cThread = cThread;
        dbgName = new StringBuilder("<").append(cThread.getName()).append(">").append(cContext.getHostname()).append("/").append(cContext.getIPAddr()).toString();
        return;
    }

    public RBLClientContext getClientContext() {
        return cContext;
    }

    public void startScan() {
        //logger.debug("start, thread: " + cThread + ", this: " + this);
        cThread.start(); // execute run() now
        return;
    }

    public void checkProgress(long timeout) {
        //logger.debug("check, thread: " + cThread + ", this: " + this);
        if (false == cThread.isAlive()) {
            logger.debug(dbgName + ", is not alive; not waiting");
            return;
        }

        try {
            synchronized (this) {
                long startTime = System.currentTimeMillis();
                this.wait(timeout); // wait for run() to finish/timeout

                // retry when no result yet and time remains before timeout
                long elapsedTime = System.currentTimeMillis() - startTime;
                while (null == cContext.getResult() && elapsedTime < timeout) {
                    this.wait(timeout - elapsedTime);
                    elapsedTime = System.currentTimeMillis() - startTime;
                }
            }
        } catch (InterruptedException e) {
            logger.warn(dbgName + ", DNSBL check interrupted", e);
        } catch (Exception e) {
            logger.warn(dbgName + ", DNSBL check failed", e);
        }

        if (null == cContext.getResult()) {
            logger.warn(dbgName + ", DNSBL check timer expired");
            stopScan();
        }

        return;
    }

    public void stopScan() {
        //logger.debug("stop, thread: " + cThread + ", this: " + this);
        if (false == cThread.isAlive()) {
            logger.debug(dbgName + ", is not alive; no need to stop");
            return;
        }

        cThread.interrupt(); // stop run() now
        return;
    }

    public String toString() {
        return dbgName;
    }

    // run() performs minimal work so if interrupted, it exits "immediately"
    // -> e.g., no need to implement stop flag
    public void run() {
        Boolean isBlacklisted = Boolean.FALSE;

        try {
            InetAddress hostIPAddr = InetAddress.getByName(cContext.getInvertedIPAddr() + "." + cContext.getHostname());
            if (null != hostIPAddr) {
                logger.debug(dbgName + ", received confirmation that IP is on blacklist");
                isBlacklisted = Boolean.TRUE;
            } else {
                // assume ipAddr is not on this blacklist
                logger.debug(dbgName + ", could not confirm that IP is on blacklist; assuming IP is not on blacklist");
            }
        } catch(UnknownHostException e) {
            // ipAddr is not on this blacklist
            logger.debug(dbgName + ", received confirmation that IP is not on blacklist");
            // note that on interrupt,
            // IOException is thrown as UnknownHostException and
            // UnknownHostException will catch both so
            // we will not be able to differentiate between them
        } catch(IOException e) {
            // assume ipAddr is not on this blacklist
            logger.warn(dbgName + ", DNSBL checker i/o exception: ", e);
        } catch(SecurityException e) {
            // assume ipAddr is not on this blacklist
            logger.warn(dbgName + ", not allowed to query host: ", e);
        } catch(Exception e) {
            // assume ipAddr is not on this blacklist
            logger.warn(dbgName + ", DNSBL checker failed: ", e);
        } finally {
            cContext.setResult(isBlacklisted);

            synchronized(this) {
                this.notifyAll(); // notify waiting thread and finish run()
            }

            return;
        }
    }
}

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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.metavize.mvvm.DownloadComplete;
import com.metavize.mvvm.DownloadProgress;
import com.metavize.mvvm.InstallComplete;
import com.metavize.mvvm.InstallProgress;
import com.metavize.mvvm.InstallTimeout;
import org.apache.log4j.Logger;

class AptLogTail implements Runnable
{
    private static final String APT_LOG
        = System.getProperty("bunnicula.log.dir") + "/apt.log";
    private static final long TIMEOUT = 500000;

    private static final Pattern FETCH_PATTERN;
    private static final Pattern DOWNLOAD_PATTERN;

    private static final Logger logger = Logger.getLogger(AptLogTail.class);

    static {
        FETCH_PATTERN = Pattern.compile("'(http://.*)' (.*\\.deb) ([0-9]+) ([0-9a-z]+)");
        DOWNLOAD_PATTERN = Pattern.compile("( *[0-9]+)K[ .]+([0-9]+)% *([0-9]+\\.[0-9]+) *MB/s");
    }

    private final long key;

    private final RandomAccessFile raf;

    private final List<InstallProgress> events
        = new LinkedList<InstallProgress>();

    private volatile boolean live = true;

    private StringBuilder builder = new StringBuilder(80);
    private long lastActivity = -1;

    // constructor ------------------------------------------------------------

    AptLogTail(long key)
    {
        logger.debug("new AptLogTail: " + key);

        this.key = key;

        File f = new File(APT_LOG);
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException exn) {
                throw new RuntimeException("could not create: " + APT_LOG);
            }
        }

        try {
            logger.debug("creating RandomAccessFile: " + f);
            raf = new RandomAccessFile(f, "r");
        } catch (FileNotFoundException exn) {
            throw new RuntimeException("should never happen");
        }

        logger.debug("AptLogTail constructed");
    }

    // package protected methods ----------------------------------------------

    List<InstallProgress> getEvents()
    {
        logger.debug("getting events");
        List<InstallProgress> l = new LinkedList<InstallProgress>();

        synchronized (events) {
            l.addAll(events);
        }

        logger.debug("returning events: " + l);
        return l;
    }

    long getKey()
    {
        return key;
    }

    boolean isDead()
    {
        synchronized (events) {
            return !live && events.size() == 0;
        }
    }

    // Runnable methods -------------------------------------------------------

    public void run()
    {
        try {
            doIt();
        } finally {
            try {
                raf.close();
            } catch (IOException exn) {
                logger.warn("could not close: " + APT_LOG);
            }
        }
    }

    public void doIt()
    {
        // find `start key'
        logger.debug("finding start key: \"start " + key + "\"");
        for (String line = readLine(); !line.equals("start " + key); line = readLine());

        // 'uri' package size hash
        List<PackageInfo> downloadQueue = new LinkedList<PackageInfo>();
        while (true) {
            String line = readLine();

            Matcher m = FETCH_PATTERN.matcher(line);
            if (line.equals("END PACKAGE LIST")) {
                logger.debug("found: END PACKAGE LIST");
                break;
            } else if (m.matches()) {
                PackageInfo pi = new PackageInfo(m.group(1), m.group(2),
                                                 new Integer(m.group(3)),
                                                 m.group(4));
                logger.debug("adding package: " + pi);
                downloadQueue.add(pi);
            } else {
                logger.debug("does not match FETCH_PATTERN: " + line);
            }
        }

        for (PackageInfo pi : downloadQueue) {
            logger.debug("downloading: " + pi);
            while (true) {
                String line = readLine();
                Matcher m = DOWNLOAD_PATTERN.matcher(line);
                if (line.startsWith("DOWNLOAD SUCCEEDED: ")) {
                    logger.debug("download succeeded");
                    synchronized (events) {
                        events.add(new DownloadComplete(true));
                    }
                    break;
                } else if (line.startsWith("DOWNLOAD FAILED: " )) {
                    logger.debug("download failed");
                    synchronized (events) {
                        events.add(new DownloadComplete(false));
                    }
                    break;
                } else if (m.matches()) {
                    int bytesDownloaded = Integer.parseInt(m.group(1)) * 100;
                    int percent = Integer.parseInt(m.group(2));
                    float speed = Float.parseFloat(m.group(3));

                    // enqueue event
                    DownloadProgress dpe = new DownloadProgress
                        (pi.file, bytesDownloaded, pi.size, speed);
                    logger.debug("Adding event: " + dpe);

                    synchronized (events) {
                        events.add(dpe);
                    }
                } else {
                    logger.debug("ignoring line: " + line);
                }
            }
        }

        logger.debug("installation complete");
        synchronized (events) {
            events.add(new InstallComplete(true));
        }
        live = false;
    }

    private String readLine()
    {
        try {
            while (true) {
                long t = System.currentTimeMillis();
                if (0 > lastActivity) {
                    lastActivity = t;
                }
                int c = raf.read();
                if (0 > c) {
                    try {
                        if (TIMEOUT < t - lastActivity) {
                            // just end the thread adding TimeoutEvent
                            logger.warn("AptLogTail timing out: "
                                        + (t - lastActivity));
                            synchronized (events) {
                                events.add(new InstallTimeout(t));
                            }
                            throw new RuntimeException("timing out: "
                                                       + (t - lastActivity));
                        } else {
                            Thread.currentThread().sleep(100);
                        }
                    } catch (InterruptedException exn) { }
                } else if ('\n' == c) {
                    lastActivity = t;
                    String s = builder.toString().trim();
                    builder.delete(0, builder.length());
                    return s;
                } else {
                    lastActivity = t;
                    builder.append((char)c);
                }
            }
        } catch (IOException exn) {
            throw new RuntimeException("could not read apt-log", exn);
        }
    }

    private static class PackageInfo
    {
        final String url;
        final String file;
        final int size;
        final String hash;

        // constructors -------------------------------------------------------

        PackageInfo(String url, String file, int size, String hash)
        {
            this.url = url;
            this.file = file;
            this.size = size;
            this.hash = hash;
        }

        // Object methods -----------------------------------------------------

        public String toString()
        {
            return "PackageInfo url: " + url + " file: " + file
                + " size: " + size + " hash: " + hash;
        }
    }

    // main -------------------------------------------------------------------

    public static void main(String args[]) {
        new AptLogTail(1).run();
    }
}

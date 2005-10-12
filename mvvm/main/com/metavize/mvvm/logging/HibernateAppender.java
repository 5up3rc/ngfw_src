/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.logging;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.MvvmLocalContext;
import com.metavize.mvvm.security.Tid;
import com.metavize.mvvm.tran.TransformContext;
import com.metavize.mvvm.util.TransactionWork;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.hibernate.Session;

public class HibernateAppender extends AppenderSkeleton
{
    private static final int QUEUE_SIZE = 10000;
    private static final int BATCH_SIZE = 1000;
    private static final int SLEEP_TIME = 15000;

    private static final Object WRITE_LOCK = new Object();

    private static final Logger logger = Logger
        .getLogger(HibernateAppender.class);

    private final Map<Tid, LogWorker> loggers;

    // constructors -----------------------------------------------------------

    public HibernateAppender()
    {
        this.loggers = new ConcurrentHashMap<Tid, LogWorker>();
    }

    // Appender methods -------------------------------------------------------

    protected void append(LoggingEvent event)
    {
        TransformContext tctx = MvvmContextFactory.context().transformManager().threadContext();

        Tid tid = null == tctx ? new Tid(0L) : tctx.getTid();

        Object msg = event.getMessage();
        if (!(msg instanceof LogEvent)) {
            logger.warn("not logEvent: " + msg);
            return;
        }

        LogEvent le = (LogEvent)msg;
        le.setTimeStamp(new Date(event.timeStamp));

        LogWorker worker = loggers.get(tid);
        if (null == worker) {
            worker = createLogger(tid);
        }
        worker.append(le);
    }

    public boolean requiresLayout()
    {
        return false;
    }

    public void close()
    {
        shutdownLoggers();
    }

    // private classes --------------------------------------------------------

    private class LogWorker implements Runnable
    {
        private final Tid tid;
        private final BlockingQueue<LogEvent> queue;
        private final MvvmLocalContext mctx;

        private Thread thread;

        private volatile boolean shutdown = false;

        // constructor --------------------------------------------------------

        LogWorker(Tid tid)
        {
            this.tid = tid;
            this.queue = new ArrayBlockingQueue<LogEvent>(QUEUE_SIZE);

            if (0 == tid.getId()) {
                mctx = MvvmContextFactory.context();
            } else {
                mctx = null;
            }
        }

        // public methods -----------------------------------------------------

        public void append(LogEvent le)
        {
            if (shutdown) {
                logger.warn("logger is shut down: " + tid);
            } else {
                if (!queue.offer(le)) {
                    logger.warn("dropped log event: " + le);
                }
            }
        }

        public void stop()
        {
            shutdown = true;
            Thread t = thread;
            if (null != t) {
                t.interrupt();
            }
            thread = null;
        }

        // Runnable methods ---------------------------------------------------

        public void run()
        {
            thread = Thread.currentThread();

            List<LogEvent> l = new ArrayList<LogEvent>(BATCH_SIZE);

            while (!shutdown && (null != mctx || null != getTctx())) {
                try {
                    drainTo(l);
                    if (!shutdown) {
                        synchronized (WRITE_LOCK) {
                            persist(l);
                        }
                    }
                } catch (Exception exn) {
                    logger.warn("danger, will robinson", exn); // never die
                }

                l.clear();
            }

            if (!shutdown) {
                loggers.remove(tid);
            }
        }

        // Private methods ----------------------------------------------------

        private TransformContext getTctx()
        {
            return MvvmContextFactory.context().transformManager()
                .transformContext(tid);
        }

        private void drainTo(List<LogEvent> l)
        {
            long maxTime = System.currentTimeMillis() + SLEEP_TIME;

            while (null != thread && l.size() < BATCH_SIZE) {
                long time = System.currentTimeMillis();

                if (maxTime <= time) { break; }

                try {
                    LogEvent le = (LogEvent)queue.poll(maxTime - time,
                                                       TimeUnit.MILLISECONDS);
                    if (null == le) {
                        break;
                    } else {
                        l.add(le);
                    }
                } catch (InterruptedException exn) { continue; }

                queue.drainTo(l, BATCH_SIZE - l.size());
            }
        }

        private void persist(final List<LogEvent> l)
        {
            logger.debug(tid + " persisting: " + l.size());

            TransactionWork tw = new TransactionWork()
                {
                    public boolean doWork(Session s)
                    {
                        for (LogEvent logEvent : l) {
                            s.save(logEvent);
                        }
                        return true;
                    }

                    public Object getResult() { return null; }
                };

            if (null != mctx) {
                mctx.runTransaction(tw);
            } else {
                TransformContext tctx = getTctx();
                if (null == tctx) {
                    logger.warn("transform context no longer exists");
                    return;
                } else {
                    tctx.runTransaction(tw);
                }
            }
        }
    }

    private synchronized LogWorker createLogger(Tid tid)
    {
        LogWorker worker = loggers.get(tid);

        if (null == worker) {
            worker = new LogWorker(tid);
            loggers.put(tid, worker);

            new Thread(worker).start();
        }

        return worker;
    }

    private void shutdownLoggers()
    {
        for (LogWorker worker : loggers.values()) {
            worker.stop();
        }
    }
}

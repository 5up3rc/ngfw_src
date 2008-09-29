/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.uvm.engine;

import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.argon.ArgonAgent;
import com.untangle.uvm.message.BlingBlinger;
import com.untangle.uvm.message.Counters;
import com.untangle.uvm.message.LoadCounter;
import com.untangle.uvm.message.LocalMessageManager;
import com.untangle.uvm.node.Node;
import com.untangle.uvm.node.NodeContext;
import com.untangle.uvm.node.NodeDesc;
import com.untangle.uvm.util.I18nUtil;
import com.untangle.uvm.util.MetaEnv;
import com.untangle.uvm.util.SessionUtil;
import com.untangle.uvm.vnet.IPNewSessionRequest;
import com.untangle.uvm.vnet.IPSession;
import com.untangle.uvm.vnet.IPSessionDesc;
import com.untangle.uvm.vnet.MPipeException;
import com.untangle.uvm.vnet.SessionStats;
import com.untangle.uvm.vnet.TCPNewSessionRequest;
import com.untangle.uvm.vnet.TCPSession;
import com.untangle.uvm.vnet.UDPNewSessionRequest;
import com.untangle.uvm.vnet.UDPSession;
import com.untangle.uvm.vnet.event.IPDataResult;
import com.untangle.uvm.vnet.event.IPSessionEvent;
import com.untangle.uvm.vnet.event.SessionEventListener;
import com.untangle.uvm.vnet.event.TCPChunkEvent;
import com.untangle.uvm.vnet.event.TCPNewSessionRequestEvent;
import com.untangle.uvm.vnet.event.TCPSessionEvent;
import com.untangle.uvm.vnet.event.UDPErrorEvent;
import com.untangle.uvm.vnet.event.UDPNewSessionRequestEvent;
import com.untangle.uvm.vnet.event.UDPPacketEvent;
import com.untangle.uvm.vnet.event.UDPSessionEvent;
import gnu.trove.TIntArrayList;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;

/**
 * One dispatcher per MPipe.  This where all the new session logic
 * lives, and the event dispatching.
 *
 * @author <a href="mailto:jdi@untangle.com">John Irwin</a>
 * @version 1.0
 */
class Dispatcher implements com.untangle.uvm.argon.NewSessionEventListener
{

    // This should be a config param XXX
    // Note we only send a heartbeat once we haven't communicated with
    // mPipe in that long.  any command or new session (incoming
    // command) resets the timer.
    public static final int DEFAULT_HEARTBEAT_INTERVAL = 30000;
    public static final int DEFAULT_CHECKUP_RESPONSE_TIMEOUT = 10000;

    // 8 seconds is the longest interval we wait between each attempt
    // to try to connect to MPipe.
    public static final int MAX_CONNECT_BACKOFF_TIME = 8000;

    private static final int COMMAND_DISPATCHER_POLL_TIME = 3000;

    // Set to true to disable use of the session and newSession thread
    // pools and run everything in the command/session dispatchers.
    public static final boolean HANDLE_ALL_INLINE = true;

    static final String SESSION_ID_MDC_KEY = "SessionID";

    private Logger logger;

    private final MPipeImpl mPipe;
    private final Node node;
    private final NodeContext nodeContext;
    private final NodeManagerImpl nodeManager;

    private final LoadCounter udpLiveSessionCounter;
    private final LoadCounter tcpLiveSessionCounter;
    private final BlingBlinger udpTotalSessionCounter;
    private final BlingBlinger tcpTotalSessionCounter;
    private final BlingBlinger udpTotalSessionRequestCounter;
    private final BlingBlinger tcpTotalSessionRequestCounter;

    /**
     * <code>mainThread</code> is the master thread started by
     * <code>start</code>.  It handles connecting to mPipe, monitoring
     * of the command/session master threads for mPipe death, and
     * reconnection.
     */
    private volatile Thread mainThread;

    /**
     * <code>comThread</code> is the command master thread started by
     * the mainThread.  It handles reading from the command socket and
     * heartbeat.
     */
    private volatile Thread comThread;

    /**
     * <code>sessThread</code> is the session master thread started by
     * the mainThread.  It handles every session (which isn't in
     * double-endpoint mode).
     */
    private volatile Thread sessThread;

    private SessionEventListener sessionEventListener;

    private boolean singleThreadedSessions;

    private Logger sessionEventLogger;


    /**
     * We need a single global <code>releasedHandler</code> for all
     * sessions that have been release(); ed after session request
     * time.  We use the default abstract event handler implmentation
     * to become a transparent proxy.
     */
    private SessionEventListener releasedHandler;

    // All sessions with active timers.  There aren't many of these,
    // so we don't worry about keeping them in a priority queue.  Map
    // from SessionImpl to Date
    private Map timers;

    // The session with the nearest timeout
    private IPSessionImpl soonestTimeredSession = null;
    private long soonestTimerTime; // Ignored if soonestTimeredSession is null

    /**
     * SessionHandler & friends let us know to update the selection
     * keys for these session here.
     */
    // private LinkedQueue dirtySessions;

    /**
     * We keep a queue of ready sessions so that we can have short
     * decoupling.  This is a queue of SessionHandler objects.  Only
     * used when HANDLE_ALL_INLINE is true, since it doesn't make
     * sense in the pooled case.
     */
    // private LinkedQueue readySessions;


    /**
     * Where new sessions are passed between command thread and new
     * session handler threads.  Uses itself for locking.
     */
    // private PooledExecutor newSessionPool;

    // private PooledExecutor sessionPool;

    /**
     * The set of active sessions (both TCP and UDP), kept as weak
     * references to SessionState object (both TCP/UDP)
     */
    private ConcurrentHashMap liveSessions;

    /**
     * This is the command selector for the node. We handle the
     * command socket.
     */
    private Selector comSelector = null;

    /**
     * This is the session selector for the node. We handle all
     * sockets for sessions in the normal mode. (Sessions in
     * double-endpoint-mode are handled by themselves).
     */
    private Selector sesSelector = null;

    private String threadNameBase; // Convenience

    // Gives the thread pool thread unique names
    private int sessThreadNum = 1;
    private int newsThreadNum = 1;

    /**
     * We hold this lock while calling select(), so that only one
     * thread is selecting at a time.
     */
    // private Object selectLock = new Object();

    private boolean lastSessionReadFailed = false;
    private long lastSessionReadTime;

    // This one is for the command socket.
    private boolean lastCommandReadFailed = false;
    private long lastCommandReadTime;

    // A dispatcher is created MPipe.start() when user decides this
    // dispatcher should begin handling a newly connected MPipe.
    //
    // Note that order of initialization is important in here, since
    // the "inner" classes access stuff from us.
    Dispatcher(MPipeImpl mPipe) {
        logger = Logger.getLogger(Dispatcher.class.getName());
        this.mPipe = mPipe;
        this.node = mPipe.node();
        this.nodeContext = mPipe.node().getNodeContext();
        this.nodeManager = UvmContextImpl.getInstance().nodeManager();
        lastSessionReadTime = lastCommandReadTime = MetaEnv.currentTimeMillis();
        sessionEventListener = null;
        timers = new HashMap();
        NodeDesc td = node.getNodeDesc();
        String tidn = td.getTid().getName();
        // dirtySessions = new LinkedQueue(); // FIFO
        // readySessions = new LinkedQueue(); // FIFO

        threadNameBase = td.getName() + "(";
        // Find out if we're the inside or outside if a Casing node.  This
        // is super ugly. XXX
        /*
          if (tt instanceof CasingNode) {
          CasingNode ct = (CasingNode)tt;
          if (ct.getMPipe(true) == mPipe)
          threadNameBase += "i";
          else
          threadNameBase += "o";
          }
        */
        threadNameBase += tidn + ") ";

        //singleThreadedSessions = td.isSingleThreadedSessions();
        sessionEventLogger = mPipe.sessionEventLogger();
        releasedHandler = new ReleasedEventHandler(node);

        // mainThread = ThreadPool.pool().allocate(this, threadNameBase + "disp");
        // mainThread = new Thread(this, threadNameBase + "mainDisp");
        liveSessions = new ConcurrentHashMap();

        LocalMessageManager lmm = LocalUvmContextFactory.context()
            .localMessageManager();
        Counters c = lmm.getCounters(node.getTid());
        udpLiveSessionCounter = c.makeLoadCounter("udpLiveSessionCounter",
                                                  "UDP Sessions");
        tcpLiveSessionCounter = c.makeLoadCounter("tcpLiveSessionCounter",
                                                  "TCP Sessions");
        udpTotalSessionCounter = c.addMetric("udpTotalSessionCounter",
                                             I18nUtil.marktr("UDP Sessions"), null, false);
        tcpTotalSessionCounter = c.addMetric("tcpTotalSessionCounter",
                                        I18nUtil.marktr("TCP Sessions"), null, false);
        udpTotalSessionRequestCounter = c
            .addMetric("udpTotalSessionRequestCounter", I18nUtil.marktr("UDP Session Requests"),
                       null, false);
        tcpTotalSessionRequestCounter = c
            .addMetric("tcpTotalSessionRequestCounter", I18nUtil.marktr("TCP Session Requests"),
                       null, false);
    }

    /*
      public void finalize()
      {
      if (comrbuf != null) {
      BufferPool.pool().release(comrbuf);
      comrbuf = null;
      }
      }
    */


    // Called by the new session handler thread.
    void addSession(TCPSession sess)
        throws InterruptedException
    {
        tcpLiveSessionCounter.increment();
        tcpTotalSessionCounter.increment();
        // liveSessions.add(new WeakReference(ss));
        liveSessions.put(sess, sess);
    }

    // Called by the new session handler thread.
    void addSession(UDPSession sess) throws InterruptedException
    {
        udpLiveSessionCounter.increment();
        udpTotalSessionCounter.increment();
        // liveSessions.add(new WeakReference(ss));
        liveSessions.put(sess, sess);
    }

    // Called by IPSessionImpl at closeFinal (raze) time.
    void removeSession(IPSessionImpl sess)
    {
        liveSessions.remove(sess);
        ArgonAgent agent = ((MPipeImpl)mPipe).getArgonAgent();
        if (agent == null) {
            logger.warn("attempt to remove session " + sess.id() + " when already destroyed");
        } else {
            agent.removeSession(sess.pSession);
        }

        if (sess instanceof UDPSession) {
            udpLiveSessionCounter.decrement();
        } else if (sess instanceof TCPSession) {
            tcpLiveSessionCounter.decrement();
        }
    }

    public com.untangle.uvm.argon.TCPSession newSession(com.untangle.uvm.argon.TCPNewSessionRequest request)
    {
        try {
            nodeManager.registerThreadContext(nodeContext);
            MDC.put(SESSION_ID_MDC_KEY, "NT" + request.id());
            return newSessionInternal(request);
        } finally {
            nodeManager.deregisterThreadContext();
            MDC.remove(SESSION_ID_MDC_KEY);
        }
    }

    public com.untangle.uvm.argon.UDPSession newSession(com.untangle.uvm.argon.UDPNewSessionRequest request)
    {
        try {
            nodeManager.registerThreadContext(nodeContext);
            MDC.put(SESSION_ID_MDC_KEY, "NU" + request.id());
            return newSessionInternal(request);
        } finally {
            nodeManager.deregisterThreadContext();
            MDC.remove(SESSION_ID_MDC_KEY);
        }
    }


    // Here's the callback that Argon calls to notify of a new TCP session:
    public com.untangle.uvm.argon.TCPSession newSessionInternal(com.untangle.uvm.argon.TCPNewSessionRequest request)
    {
        int sessionId = -1;

        try {
            long firstRequestHandleTime = 0, dispatchRequestTime = 0,
                requestHandledTime = 0, madeSessionTime = 0,
                dispatchNewTime = 0, newHandledTime = 0, finishNewTime = 0;
            if (RWSessionStats.DoDetailedTimes)
                firstRequestHandleTime = MetaEnv.currentTimeMillis();

            NodeDesc td = node.getNodeDesc();
            sessionId = request.id();

            TCPNewSessionRequestImpl treq = new TCPNewSessionRequestImpl(this, request);
            if (RWSessionStats.DoDetailedTimes)
                dispatchRequestTime = MetaEnv.currentTimeMillis();
            tcpTotalSessionRequestCounter.increment();

            // Give the request event to the user, to give them a
            // chance to reject the session.
            logger.debug("sending TCP new session request event");
            TCPNewSessionRequestEvent revent = new TCPNewSessionRequestEvent(mPipe, treq);
            dispatchTCPNewSessionRequest(revent);

            if (RWSessionStats.DoDetailedTimes)
                requestHandledTime = MetaEnv.currentTimeMillis();

            // Check the session only if it was not rejected.
            switch (treq.state()) {
            case com.untangle.uvm.argon.IPNewSessionRequest.REJECTED:
            case com.untangle.uvm.argon.IPNewSessionRequest.REJECTED_SILENT:
                if (treq.needsFinalization()) {
                    logger.debug("rejecting (with finalization)");
                } else {
                    logger.debug("rejecting");
                    return null;
                }

                /* XX Otherwise fall through and create a "fake" session that
                 * exists just to modify the session or to get the raze() call
                 * from Argon when the session is razed. */
                break;
            case com.untangle.uvm.argon.IPNewSessionRequest.RELEASED:
                boolean needsFinalization = treq.needsFinalization();
                boolean modified = treq.modified();
                if (needsFinalization)
                    logger.debug("releasing (with finalization)");
                else if (modified)
                    logger.debug("releasing (with modification)");
                else
                    logger.debug("releasing");
                if (!needsFinalization && !modified)
                    // Then we don't need to create a session at all.
                    return null;

                /* XX Otherwise fall through and create a "fake" session that
                 * exists just to modify the session or to get the raze() call
                 * from Argon when the session is razed. */
                break;
            case com.untangle.uvm.argon.IPNewSessionRequest.REQUESTED:
            case com.untangle.uvm.argon.IPNewSessionRequest.ENDPOINTED:
            default:
                break;
            }

            // Create the session, client and server channels
            com.untangle.uvm.argon.TCPSession pSession =
                new com.untangle.uvm.argon.TCPSessionImpl(request);
            TCPSessionImpl session = new TCPSessionImpl(this, pSession, request.pipelineEndpoints(),
                                                        td.getTcpClientReadBufferSize(),
                                                        td.getTcpServerReadBufferSize());
            session.attach(treq.attachment());
            registerPipelineListener(pSession, session);
            if (RWSessionStats.DoDetailedTimes)
                madeSessionTime = MetaEnv.currentTimeMillis();

            // Send the new session event.  Maybe this should be done on the session handler
            // thread instead?  XX
            if (logger.isInfoEnabled())
                logger.info("New TCP session " +
                            session.clientAddr().getHostAddress() + ":" + session.clientPort() + " -> " +
                            session.serverAddr().getHostAddress() + ":" + session.serverPort());
            if (RWSessionStats.DoDetailedTimes)
                dispatchNewTime = MetaEnv.currentTimeMillis();
            if (treq.state() == com.untangle.uvm.argon.IPNewSessionRequest.RELEASED) {
                session.release(treq.needsFinalization());
            } else {
                TCPSessionEvent tevent = new TCPSessionEvent(mPipe, session);
                dispatchTCPNewSession(tevent);
            }
            if (RWSessionStats.DoDetailedTimes)
                newHandledTime = MetaEnv.currentTimeMillis();

            // Finally it to our set of owned sessions.
            addSession(session);

            if (RWSessionStats.DoDetailedTimes) {
                finishNewTime = MetaEnv.currentTimeMillis();

                long[] times = session.stats().times();
                times[SessionStats.NEW_SESSION_RECEIVED] = firstRequestHandleTime;
                times[SessionStats.DISPATCH_REQUEST] = dispatchRequestTime;
                times[SessionStats.REQUEST_HANDLED] = requestHandledTime;
                times[SessionStats.MADE_SESSION] = madeSessionTime;
                times[SessionStats.DISPATCH_NEW] = dispatchNewTime;
                times[SessionStats.NEW_HANDLED] = newHandledTime;
                times[SessionStats.FINISH_NEW] = finishNewTime;
            }
            return pSession;
        } catch (MPipeException x) {
            String message = "MPipeException building TCP session  " + sessionId;
            logger.error(message,  x);
            // This "kills" the session:
            return null;
        } catch (Exception x) {
            String message = "" + x.getClass().getName() + " building TCP session " + sessionId;
            logger.error(message, x);
            // This "kills" the session:
            return null;
        }
    }

    public com.untangle.uvm.argon.UDPSession newSessionInternal(com.untangle.uvm.argon.UDPNewSessionRequest request)
    {
        int sessionId = -1;

        try {
            long firstRequestHandleTime = 0, dispatchRequestTime = 0,
                requestHandledTime = 0, madeSessionTime = 0,
                dispatchNewTime = 0, newHandledTime = 0, finishNewTime = 0;
            if (RWSessionStats.DoDetailedTimes)
                firstRequestHandleTime = MetaEnv.currentTimeMillis();

            NodeDesc td = node.getNodeDesc();
            sessionId = request.id();

            UDPNewSessionRequestImpl ureq = new UDPNewSessionRequestImpl(this, request);
            if (RWSessionStats.DoDetailedTimes)
                dispatchRequestTime = MetaEnv.currentTimeMillis();
            udpTotalSessionRequestCounter.increment();

            // Give the request event to the user, to give them a chance to reject the session.
            logger.debug("sending UDP new session request event");
            UDPNewSessionRequestEvent revent = new UDPNewSessionRequestEvent(mPipe, ureq);
            dispatchUDPNewSessionRequest(revent);

            if (RWSessionStats.DoDetailedTimes)
                requestHandledTime = MetaEnv.currentTimeMillis();

            // Check the session only if it was not rejected.
            switch (ureq.state()) {
            case com.untangle.uvm.argon.IPNewSessionRequest.REJECTED:
            case com.untangle.uvm.argon.IPNewSessionRequest.REJECTED_SILENT:
                if (ureq.needsFinalization()) {
                    logger.debug("rejecting (with finalization)");
                } else {
                    logger.debug("rejecting");
                    return null;
                }

                /* XX Otherwise fall through and create a "fake" session that
                 * exists just to modify the session or to get the raze() call
                 * from Argon when the session is razed. */
                break;
            case com.untangle.uvm.argon.IPNewSessionRequest.RELEASED:
                boolean needsFinalization = ureq.needsFinalization();
                boolean modified = ureq.modified();
                if (needsFinalization)
                    logger.debug("releasing (with finalization)");
                else if (modified)
                    logger.debug("releasing (with modification)");
                else
                    logger.debug("releasing");
                if (!needsFinalization && !modified)
                    // Then we don't need to create a session at all.
                    return null;

                /* XX Otherwise fall through and create a "fake" session that
                 * exists just to modify the session or to get the raze() call
                 * from Argon when the session is razed. */
                break;
            case com.untangle.uvm.argon.IPNewSessionRequest.REQUESTED:
            case com.untangle.uvm.argon.IPNewSessionRequest.ENDPOINTED:
            default:
                break;
            }

            // Create the session, client and server channels
            com.untangle.uvm.argon.UDPSession pSession =
                new com.untangle.uvm.argon.UDPSessionImpl(request);
            UDPSessionImpl session = new UDPSessionImpl(this, pSession, request.pipelineEndpoints(),
                                                        td.getUdpMaxPacketSize(),
                                                        td.getUdpMaxPacketSize());
            session.attach(ureq.attachment());
            registerPipelineListener(pSession, session);
            if (RWSessionStats.DoDetailedTimes)
                madeSessionTime = MetaEnv.currentTimeMillis();

            // Send the new session event.  Maybe this should be done on the session handler
            // thread instead?  XX
            if (logger.isInfoEnabled())
                logger.info("New UDP session " +
                            session.clientAddr().getHostAddress() + ":" + session.clientPort() + " -> " +
                            session.serverAddr().getHostAddress() + ":" + session.serverPort());
            if (RWSessionStats.DoDetailedTimes)
                dispatchNewTime = MetaEnv.currentTimeMillis();
            if (ureq.state() == com.untangle.uvm.argon.IPNewSessionRequest.RELEASED) {
                session.release(ureq.needsFinalization());
            } else {
                UDPSessionEvent tevent = new UDPSessionEvent(mPipe, session);
                dispatchUDPNewSession(tevent);
            }
            if (RWSessionStats.DoDetailedTimes)
                newHandledTime = MetaEnv.currentTimeMillis();

            // Finally add it to our set of owned sessions.
            addSession(session);

            if (RWSessionStats.DoDetailedTimes) {
                finishNewTime = MetaEnv.currentTimeMillis();

                long[] times = session.stats().times();
                times[SessionStats.NEW_SESSION_RECEIVED] = firstRequestHandleTime;
                times[SessionStats.DISPATCH_REQUEST] = dispatchRequestTime;
                times[SessionStats.REQUEST_HANDLED] = requestHandledTime;
                times[SessionStats.MADE_SESSION] = madeSessionTime;
                times[SessionStats.DISPATCH_NEW] = dispatchNewTime;
                times[SessionStats.NEW_HANDLED] = newHandledTime;
                times[SessionStats.FINISH_NEW] = finishNewTime;
            }
            return pSession;
        } catch (MPipeException x) {
            String message = "MPipeException building UDP session  " + sessionId;
            logger.error(message, x);
            // This "kills" the session:
            return null;
        } catch (Exception x) {
            String message = "" + x.getClass().getName() + " building UDP session " + sessionId;
            logger.error(message, x);
            // This "kills" the session:
            return null;
        }
    }

    void registerPipelineListener(com.untangle.uvm.argon.IPSession pSession, IPSessionImpl session)
    {
        pSession.registerListener(session);
    }

    /**
     * Describe <code>start</code> method here.
     *
     * By the time we're called, the load request has been sent, but the response has not been read.
     *
     * @exception MPipeException if an error occurs
     */
    void start() throws MPipeException
    {
        // if (mainThread != null && !mainThread.isAlive())
        // mainThread.start();
    }

    /**
     * Called from MPipeImpl.  Stop is only called to disconnect us
     * from a live MPipe.  Once stopped we cannot be restarted.  This
     * function is idempotent for safety.
     *
     * When used in drain mode, this function will not return until
     * all sessions have naturally finished.  When not in drain mode,
     * all existing sessions are forcibly terminated (by closing
     * connection to MPipe == close server & client sockets outside of
     * VP).  In both modes, when this function returns we guarantee:
     * No sessions are alive.  No DEM threads are alive.  No session
     * threads, new session threads, or any of the three main threads
     * are alive.
     *
     * @param drainMode a <code>boolean</code> true if we should
     * switch into drain-then-exit mode, false if we should
     * immediately exit.
     */
    void destroy(boolean drainMode) throws InterruptedException
    {
        logger.info("destroy called");
        if (mainThread != null) {
            Thread oldMain = mainThread;
            mainThread = null;
            oldMain.interrupt();

            if (drainMode)
                logger.error("Drain mode not yet supported");

            // The main thread exitting handles all the cleanup.  We just wait for it to finish.
            try {
                oldMain.join(10000); // Need constant XXX
            } catch (InterruptedException x) {
                // Can't really happen
                logger.error("Dispatcher.destroy() interrupted waiting for mainThread to die");
                // Resend it back out
                Thread.currentThread().interrupt();
            }

            if (oldMain.isAlive())
                logger.error("Dispatcher didn't die");
        }
    }

    // Note no synchronization here, since we only call this after the new session threads
    // have stopped.
    private void killExistingSessions() {
        boolean annc = false;
        // Wrap it all up and add it to our session collection
        for (Iterator iter = liveSessions.keySet().iterator(); iter.hasNext();) {
            if (!annc) {
                logger.info("Closing all active sessions:");
                annc = true;
            }
            // WeakReference ssRef = (WeakReference) iter.next();
            // IPSessionState ss = (IPSessionState) ssRef.get();
            IPSessionImpl sess = (IPSessionImpl) iter.next();
            logger.info("   closing " + sess.id());
            // Right? XXX
            sess.closeFinal();
            iter.remove();
        }
    }

    /*
      void cancelTimer(IPSessionImpl session) {
      assert session != null;
      synchronized(timers) {
      timers.remove(session);
      if (session == soonestTimeredSession) {
      setSoonestTimer();
      }
      }
      }

      void scheduleTimer(IPSessionImpl session, long delay)
      {
      assert session != null;
      synchronized(timers) {
      long timerTime = MetaEnv.currentTimeMillis() + delay;
      timers.put(session, new Date(timerTime));
      if (soonestTimeredSession == null || timerTime < soonestTimerTime) {
      soonestTimeredSession = session;
      soonestTimerTime = timerTime;
      } else if (session == soonestTimeredSession) {
      // We may have set the timer so that we're not soonest anymore.
      setSoonestTimer();
      }
      }
      }

      // Must always be called with timers lock held.
      private void setSoonestTimer() {
      IPSessionImpl minSession = null;
      long minTime = Long.MAX_VALUE;
      for (Iterator iter = timers.keySet().iterator(); iter.hasNext();) {
      IPSessionImpl sess = (IPSessionImpl) iter.next();
      Date sesstd = (Date) timers.get(sess);
      long sesstt = sesstd.getTime();
      if (sesstt < minTime) {
      minSession = sess;
      minTime = sesstt;
      }
      }
      soonestTimeredSession = minSession;
      soonestTimerTime = minTime;
      }
    */


    MPipeImpl mPipe()
    {
        return mPipe;
    }

    // Called whenever a session socket read occurs.
    void lastSessionNumRead(long numRead)
    {
        if (numRead > 0) {
            lastSessionReadFailed = false;
            lastSessionReadTime = MetaEnv.currentTimeMillis();
        } else {
            lastSessionReadFailed = true;
        }
    }

    boolean lastReadFailed()
    {
        // Note that we do *not* consider session reads a failure.  This is a result
        // of how we currently handle session shutdown.
        return (lastCommandReadFailed);
    }

    // Called whenever a command socket read occurs.
    void lastCommandNumRead(int numRead)
    {
        if (numRead > 0) {
            lastCommandReadFailed = false;
            lastCommandReadTime = MetaEnv.currentTimeMillis();
        } else {
            lastCommandReadFailed = true;
        }
    }

    void setSessionEventListener(SessionEventListener listener)
    {
        sessionEventListener = listener;
    }


    //

    int[] liveSessionIds()
    {
        TIntArrayList idlist = new TIntArrayList(liveSessions.size());
        for (Iterator i = liveSessions.keySet().iterator(); i.hasNext(); ) {
            // WeakReference wr = (WeakReference)i.next();
            // SessionState s = (SessionState)wr.get();
            IPSession sess = (IPSession)i.next();
            idlist.add(sess.id());
        }

        return idlist.toNativeArray();
    }

    private static final IPSessionDesc[] SESSION_DESC_ARRAY_PROTO
        = new IPSessionDesc[0];

    IPSessionDesc[] liveSessionDescs()
    {
        List l = new ArrayList(liveSessions.size());
        for (Iterator i = liveSessions.keySet().iterator(); i.hasNext(); ) {
            IPSessionImpl sess = (IPSessionImpl)i.next();
            l.add(sess.makeDesc());
        }

        return (IPSessionDesc[])l.toArray(SESSION_DESC_ARRAY_PROTO);
    }

    // This one is used to dump to our own log, including internal
    // session state.
    void dumpSessions()
    {
        System.out.println("Live session dump for " + node.getNodeDesc().getName());
        System.out.println("ID\t\tDir\tC State\tC Addr\tC Port\tS State\tS Addr\tS Port\t" +
                           "Created\tLast Activity\tC->T Bs\tT->S Bs\tS->T Bs\tT->C Bs\t" +
                           "c2sDir\ts2cDir\tcio\tsio\tcro\tsro\tcreq\tsreq");
        for (Iterator i = liveSessions.keySet().iterator(); i.hasNext(); ) {
            IPSessionImpl sd = (IPSessionImpl)i.next();
            SessionStats stats = sd.stats();
            if (sd instanceof UDPSession)
                System.out.print("U");
            else if (sd instanceof TCPSession)
                System.out.print("T");
            System.out.print(sd.id());
            System.out.print("\t");
            System.out.print(SessionUtil.prettyState(sd.clientState()));
            System.out.print("\t");
            System.out.print(sd.clientAddr());
            System.out.print("\t");
            System.out.print(sd.clientPort());
            System.out.print("\t");
            System.out.print(SessionUtil.prettyState(sd.serverState()));
            System.out.print("\t");
            System.out.print(sd.serverAddr());
            System.out.print("\t");
            System.out.print(sd.serverPort());
            System.out.print("\t");
            System.out.print(stats.creationDate());
            System.out.print("\t");
            System.out.print(stats.lastActivityDate());
            System.out.print("\t");
            System.out.print(stats.c2tBytes());
            System.out.print("\t");
            System.out.print(stats.t2sBytes());
            System.out.print("\t");
            System.out.print(stats.s2tBytes());
            System.out.print("\t");
            System.out.print(stats.t2cBytes());
            System.out.print("\t");
            /*
              if (ss instanceof IPSessionState) {
              IPSessionState iss = (IPSessionState)ss;
              System.out.print(iss.c2sWriting() ? "write" : "read");
              System.out.print("\t");
              System.out.print(iss.s2cWriting() ? "write" : "read");
              System.out.print("\t");
              System.out.print(iss.clientKey == null ? "unreg" : Integer.toString(iss.clientKey.interestOps()));
              System.out.print("\t");
              System.out.print(iss.serverKey == null ? "unreg" : Integer.toString(iss.serverKey.interestOps()));
              System.out.print("\t");
              System.out.print(iss.clientKey == null ? "unreg" : Integer.toString(iss.clientKey.readyOps()));
              System.out.print("\t");
              System.out.print(iss.serverKey == null ? "unreg" : Integer.toString(iss.serverKey.readyOps()));
              System.out.print("\t");
              String creq = " ";
              if (iss.clientCloseRequested)
              creq = "close";
              else if (iss.s2cShutdownRequested)
              creq = "shut";
              System.out.print(creq);
              System.out.print("\t");
              String sreq = " ";
              if (iss.serverCloseRequested)
              sreq = "close";
              else if (iss.c2sShutdownRequested)
              sreq = "shut";
              System.out.println(sreq);
              }
            */
        }
    }

    //////////////////////////////////////////////////////////////////

    void dispatchTCPNewSessionRequest(TCPNewSessionRequestEvent event)
        throws MPipeException
    {
        elog(Level.WARN, "TCPNewSessionRequest", event.sessionRequest().id());
        if (sessionEventListener == null)
            releasedHandler.handleTCPNewSessionRequest(event);
        else
            sessionEventListener.handleTCPNewSessionRequest(event);
    }

    void dispatchUDPNewSessionRequest(UDPNewSessionRequestEvent event)
        throws MPipeException
    {
        elog(Level.WARN, "UDPNewSessionRequest", event.sessionRequest().id());
        if (sessionEventListener == null)
            releasedHandler.handleUDPNewSessionRequest(event);
        else
            sessionEventListener.handleUDPNewSessionRequest(event);
    }

    void dispatchTCPNewSession(TCPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.WARN, "TCPNewSession", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTCPNewSession(event);
        else
            sessionEventListener.handleTCPNewSession(event);
    }

    void dispatchUDPNewSession(UDPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.WARN, "UDPNewSession", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPNewSession(event);
        else
            sessionEventListener.handleUDPNewSession(event);
    }

    IPDataResult dispatchTCPClientChunk(TCPChunkEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPClientChunk", session.id(), event.chunk().remaining());
        if (sessionEventListener == null || session.released())
            return releasedHandler.handleTCPClientChunk(event);
        else
            return sessionEventListener.handleTCPClientChunk(event);
    }

    IPDataResult dispatchTCPServerChunk(TCPChunkEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPServerChunk", session.id(), event.chunk().remaining());
        if (sessionEventListener == null || session.released())
            return releasedHandler.handleTCPServerChunk(event);
        else
            return sessionEventListener.handleTCPServerChunk(event);
    }

    IPDataResult dispatchTCPClientWritable(TCPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPClientWritable", session.id());
        if (sessionEventListener == null || session.released())
            return releasedHandler.handleTCPClientWritable(event);
        else
            return sessionEventListener.handleTCPClientWritable(event);
    }

    IPDataResult dispatchTCPServerWritable(TCPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "TCPServerWritable", session.id());
        if (sessionEventListener == null || session.released())
            return releasedHandler.handleTCPServerWritable(event);
        else
            return sessionEventListener.handleTCPServerWritable(event);
    }

    void dispatchUDPClientPacket(UDPPacketEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPClientPacket", session.id(), event.packet().remaining());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPClientPacket(event);
        else
            sessionEventListener.handleUDPClientPacket(event);
    }

    void dispatchUDPServerPacket(UDPPacketEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPServerPacket", session.id(), event.packet().remaining());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPServerPacket(event);
        else
            sessionEventListener.handleUDPServerPacket(event);
    }

    void dispatchUDPClientError(UDPErrorEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPClientError", session.id(), event.packet().remaining());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPClientError(event);
        else
            sessionEventListener.handleUDPClientError(event);
    }

    void dispatchUDPServerError(UDPErrorEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPServerError", session.id(), event.packet().remaining());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPServerError(event);
        else
            sessionEventListener.handleUDPServerError(event);
    }

    IPDataResult dispatchTCPClientDataEnd(TCPChunkEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "TCPClientDataEnd", session.id());
        if (sessionEventListener == null || session.released())
            return releasedHandler.handleTCPClientDataEnd(event);
        else
            return sessionEventListener.handleTCPClientDataEnd(event);
    }

    void dispatchTCPClientFIN(TCPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "TCPClientFIN", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTCPClientFIN(event);
        else
            sessionEventListener.handleTCPClientFIN(event);
    }

    IPDataResult dispatchTCPServerDataEnd(TCPChunkEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "TCPServerDataEnd", session.id());
        if (sessionEventListener == null || session.released())
            return releasedHandler.handleTCPServerDataEnd(event);
        else
            return sessionEventListener.handleTCPServerDataEnd(event);
    }

    void dispatchTCPServerFIN(TCPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "TCPServerFIN", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTCPServerFIN(event);
        else
            sessionEventListener.handleTCPServerFIN(event);
    }

    void dispatchTCPClientRST(TCPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "TCPClientRST", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTCPClientRST(event);
        else
            sessionEventListener.handleTCPClientRST(event);
    }

    void dispatchTCPServerRST(TCPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "TCPServerRST", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTCPServerRST(event);
        else
            sessionEventListener.handleTCPServerRST(event);
    }

    void dispatchTCPFinalized(TCPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "TCPFinalized", session.id());
        if (sessionEventListener == null || (session.released() && !session.needsFinalization()))
            releasedHandler.handleTCPFinalized(event);
        else
            sessionEventListener.handleTCPFinalized(event);
    }

    void dispatchTCPComplete(TCPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "TCPComplete", session.id());
        if (sessionEventListener == null || (session.released() && !session.needsFinalization()))
            releasedHandler.handleTCPComplete(event);
        else
            sessionEventListener.handleTCPComplete(event);
    }

    void dispatchUDPClientExpired(UDPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "UDPClientExpired", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPClientExpired(event);
        else
            sessionEventListener.handleUDPClientExpired(event);
    }

    void dispatchUDPServerExpired(UDPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "UDPServerExpired", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPServerExpired(event);
        else
            sessionEventListener.handleUDPServerExpired(event);
    }

    void dispatchUDPClientWritable(UDPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPClientWritable", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPClientWritable(event);
        else
            sessionEventListener.handleUDPClientWritable(event);
    }

    void dispatchUDPServerWritable(UDPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.DEBUG, "UDPServerWritable", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleUDPServerWritable(event);
        else
            sessionEventListener.handleUDPServerWritable(event);
    }

    void dispatchUDPFinalized(UDPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "UDPFinalized", session.id());
        if (sessionEventListener == null || (session.released() && !session.needsFinalization()))
            releasedHandler.handleUDPFinalized(event);
        else
            sessionEventListener.handleUDPFinalized(event);
    }

    void dispatchUDPComplete(UDPSessionEvent event)
        throws MPipeException
    {
        IPSessionImpl session = (IPSessionImpl) event.session();
        elog(Level.INFO, "UDPComplete", session.id());
        if (sessionEventListener == null || (session.released() && !session.needsFinalization()))
            releasedHandler.handleUDPComplete(event);
        else
            sessionEventListener.handleUDPComplete(event);
    }

    void dispatchTimer(IPSessionEvent event)
    {
        IPSessionImpl session = (IPSessionImpl) event.ipsession();
        elog(Level.WARN, "Timer", session.id());
        if (sessionEventListener == null || session.released())
            releasedHandler.handleTimer(event);
        else
            sessionEventListener.handleTimer(event);
    }

    private void elog(Level level, String eventName, int sessionId)
    {
        if (sessionEventLogger.isEnabledFor(level)) {
            StringBuilder message = new StringBuilder("EV[");
            message.append(sessionId);
            message.append(",");
            message.append(eventName);
            message.append("] T: ");
            message.append(Thread.currentThread().getName());

            sessionEventLogger.log(level, message.toString());
        }
    }

    private void elog(Level level, String eventName, int sessionId, long dataSize)
    {
        if (sessionEventLogger.isEnabledFor(level)) {
            StringBuilder message = new StringBuilder("EV[");
            message.append(sessionId);
            message.append(",");
            message.append(eventName);
            message.append("] S: ");
            message.append(dataSize);
            message.append(" T: ");
            message.append(Thread.currentThread().getName());

            sessionEventLogger.log(level, message.toString());
        }
    }
}

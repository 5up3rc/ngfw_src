/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.mvvm.engine;

import static com.untangle.mvvm.engine.Dispatcher.SESSION_ID_MDC_KEY;

import java.net.InetAddress;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.untangle.jvector.Crumb;
import com.untangle.jvector.DataCrumb;
import com.untangle.jvector.IncomingSocketQueue;
import com.untangle.jvector.OutgoingSocketQueue;
import com.untangle.mvvm.argon.PipelineListener;
import com.untangle.mvvm.tapi.*;
import com.untangle.mvvm.tapi.event.IPStreamer;
import com.untangle.mvvm.tran.PipelineEndpoints;
import com.untangle.mvvm.tran.Transform;
import com.untangle.mvvm.tran.TransformContext;
import com.untangle.mvvm.tran.TransformState;
import com.untangle.mvvm.util.MetaEnv;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.helpers.AbsoluteTimeDateFormat;

abstract class IPSessionImpl extends SessionImpl implements IPSession, PipelineListener {

    protected boolean released = false;
    protected boolean needsFinalization = true;
    protected boolean isInbound;

    protected Dispatcher dispatcher;

    protected PipelineEndpoints pipelineEndpoints;

    protected List<Crumb>[] crumbs2write = new ArrayList[] { null, null };

    protected IPStreamer[] streamer = null;

    protected Logger logger;

    protected RWSessionStats stats;

    private Logger timesLogger = null;

    private final TransformManagerImpl transformManager;

    protected IPSessionImpl(Dispatcher disp, com.untangle.mvvm.argon.IPSession pSession, boolean isInbound,
                            PipelineEndpoints pe)
    {
        super(disp.mPipe(), pSession);
        this.dispatcher = disp;
        this.isInbound = isInbound;
        this.stats = new RWSessionStats();
        this.pipelineEndpoints = pe;
        if (RWSessionStats.DoDetailedTimes)
            timesLogger = Logger.getLogger("com.untangle.mvvm.tapi.SessionTimes");
        transformManager = MvvmContextImpl.getInstance().transformManager();
        logger = disp.mPipe().sessionLogger();
    }

    public boolean isInbound()
    {
        return isInbound;
    }

    public boolean isOutbound()
    {
        return !isInbound;
    }

    public short protocol()
    {
        return ((com.untangle.mvvm.argon.IPSession)pSession).protocol();
    }

    public InetAddress clientAddr()
    {
        return ((com.untangle.mvvm.argon.IPSession)pSession).clientAddr();
    }

    public InetAddress serverAddr()
    {
        return ((com.untangle.mvvm.argon.IPSession)pSession).serverAddr();
    }

    public int clientPort()
    {
        return ((com.untangle.mvvm.argon.IPSession)pSession).clientPort();
    }

    public int serverPort()
    {
        return ((com.untangle.mvvm.argon.IPSession)pSession).serverPort();
    }

    public SessionStats stats()
    {
        return stats;
    }

    public PipelineEndpoints pipelineEndpoints()
    {
        return pipelineEndpoints;
    }

    public void release()
    {
        // 8/8/05 jdi -- default changed to true -- finalization is almost always important
        // when it is defined.
        release(true);
    }

    // This one is for releasing once the session has been alive.
    public void release(boolean needsFinalization)
    {
        cancelTimer();

        // We could theoretically detach the attachment here (maybe only when !needsFinalization) XXX

        /** Someday...
        try {
            Mnp req = RequestUtil.createReleaseNewSession();
            ReleaseNewSessionType rl = req.getReleaseNewSession();
            rl.setSessId(id);
            xenon.requestNoReply(req);
        } catch (XenonException x) {
            // Not expected, just log
            xenon.sessionLogger().warn("Exception releasing new session", x);
        }
        */
        released = true;
        this.needsFinalization = needsFinalization;
        // Do more eventually (closing sockets.) XX
    }

    public byte clientIntf()
    {
        return ((com.untangle.mvvm.argon.IPSession)pSession).clientIntf();
    }

    public byte serverIntf()
    {
        return ((com.untangle.mvvm.argon.IPSession)pSession).serverIntf();
    }

    public boolean released()
    {
        return released;
    }

    public void scheduleTimer(long delay)
    {
        if (delay < 0)
            throw new IllegalArgumentException("Delay must be non-negative");
        // xenon.scheduleTimer(this, delay);
    }

    public void cancelTimer()
    {
        // xenon.cancelTimer(this);
    }

    protected Crumb getNextCrumb2Send(int side) {
        List<Crumb> crumbs = crumbs2write[side];
        assert crumbs != null;
        Crumb result = crumbs.get(0);
        assert result != null;
        // The following no longer applies since data can be null for ICMP packets: (5/05  jdi)
        // assert result.remaining() > 0 : "Cannot send zero length buffer";
        int len = crumbs.size() - 1;
        if (len == 0) {
            // Check if we sent em all, and if so remove the array.
            crumbs2write[side] = null;
        } else {
            crumbs.remove(0);
        }
        return result;
    }

    protected void addCrumb(int side, Crumb buf)
    {
        if (buf == null
            // The following no longer applies since data can be null for ICMP packets: (5/05  jdi)
            // assert result.remaining() > 0 : "Cannot send zero length buffer";
            //  buf.remaining() == 0
            )
            // Skip it.
            return;
        OutgoingSocketQueue out;
        if (side == CLIENT)
            out = ((com.untangle.mvvm.argon.Session)pSession).clientOutgoingSocketQueue();
        else
            out = ((com.untangle.mvvm.argon.Session)pSession).serverOutgoingSocketQueue();
        if (out == null || out.isClosed()) {
            String sideName = side == CLIENT ? "client" : "server";
            warn("Ignoring crumb for dead " + sideName + " outgoing socket queue");
            return;
        }

        List<Crumb> crumbs = crumbs2write[side];

        if (crumbs == null) {
            crumbs = new ArrayList<Crumb>();
            crumbs2write[side] = crumbs;
        }
        crumbs.add(buf);
    }

    protected int sendCrumb(Crumb crumb, OutgoingSocketQueue out)
    {
        int size = 0;
        if (crumb instanceof DataCrumb)
            size = ((DataCrumb)crumb).limit();
        boolean success = out.write(crumb);
        if (logger.isDebugEnabled()) {
            debug("writing " + crumb.type() + " crumb to " + out + ", size: " + size);
        }
        assert success;
        return size;
    }

    public void complete()
    {
        Transform xform = mPipe().transform();
        if (xform.getRunState() != TransformState.RUNNING) {
            String message = "killing: complete(in) for transform in state " + xform.getRunState();
            warn(message);
            // killSession(message);
            return;
        }
        TransformContext tctx = xform.getTransformContext();
        ClassLoader classLoader = tctx.getClassLoader();
        Thread ct = Thread.currentThread();
        ClassLoader oldCl = ct.getContextClassLoader();

        // entering TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ct.setContextClassLoader(classLoader);
        try {
            transformManager.registerThreadContext(tctx);
            MDC.put(SESSION_ID_MDC_KEY, idForMDC());

            sendCompleteEvent();
        } catch (MPipeException x) {
            String message = "MPipeException while completing";
            error(message, x);
            // killSession(message);
        } catch (Exception x) {
            String message = "" + x.getClass().getName() + " while completing";
            // error(message, x);
            killSession(message);
        } catch (OutOfMemoryError x) {
            Main.fatalError("SessionHandler", x);
        } finally {
            transformManager.deregisterThreadContext();
            MDC.remove(SESSION_ID_MDC_KEY);
            ct.setContextClassLoader(oldCl);
            // left TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        }
    }

    public void raze()
    {
        Transform xform = mPipe().transform();
        if (xform.getRunState() != TransformState.RUNNING) {
            String message = "killing: raze for transform in state " + xform.getRunState();
            warn(message);
            // No need to kill the session, it's already dead.
            // killSession(message);
            return;
        }

        TransformContext tctx = xform.getTransformContext();
        ClassLoader classLoader = tctx.getClassLoader();
        Thread ct = Thread.currentThread();
        ClassLoader oldCl = ct.getContextClassLoader();

        // entering TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ct.setContextClassLoader(classLoader);
        try {
            transformManager.registerThreadContext(tctx);
            MDC.put(SESSION_ID_MDC_KEY, idForMDC());
            if (released) {
                debug("raze released");
            } else {
                if (logger.isDebugEnabled()) {
                    IncomingSocketQueue ourcin = ((com.untangle.mvvm.argon.Session)pSession).clientIncomingSocketQueue();
                    IncomingSocketQueue oursin = ((com.untangle.mvvm.argon.Session)pSession).serverIncomingSocketQueue();
                    OutgoingSocketQueue ourcout = ((com.untangle.mvvm.argon.Session)pSession).clientOutgoingSocketQueue();
                    OutgoingSocketQueue oursout = ((com.untangle.mvvm.argon.Session)pSession).serverOutgoingSocketQueue();
                    debug("raze ourcin: " + ourcin +
                          ", ourcout: " + ourcout + ", ourcsin: " + oursin + ", oursout: " + oursout +
                          "  /  crumbs[CLIENT]: " + crumbs2write[CLIENT] + ", crumbs[SERVER]: " + crumbs2write[SERVER]);
                }
            }
            closeFinal();
        } catch (Exception x) {
            String message = "" + x.getClass().getName() + " in raze";
            error(message, x);
        } catch (OutOfMemoryError x) {
            Main.fatalError("SessionHandler", x);
        } finally {
            transformManager.deregisterThreadContext();
            MDC.remove(SESSION_ID_MDC_KEY);
            ct.setContextClassLoader(oldCl);
            // left TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        }
    }

    public void clientEvent(IncomingSocketQueue in)
    {
        Transform xform = mPipe().transform();
        if (xform.getRunState() != TransformState.RUNNING) {
            String message = "killing: clientEvent(in) for transform in state " + xform.getRunState();
            warn(message);
            killSession(message);
            return;
        }
        TransformContext tctx = xform.getTransformContext();
        ClassLoader classLoader = tctx.getClassLoader();
        Thread ct = Thread.currentThread();
        ClassLoader oldCl = ct.getContextClassLoader();

        // entering TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ct.setContextClassLoader(classLoader);
        try {
            transformManager.registerThreadContext(tctx);
            readEvent(CLIENT, in);
        } finally {
            transformManager.deregisterThreadContext();
            ct.setContextClassLoader(oldCl);
            // left TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        }
    }

    public void serverEvent(IncomingSocketQueue in)
    {
        Transform xform = mPipe().transform();
        if (xform.getRunState() != TransformState.RUNNING) {
            String message = "killing: serverEvent(in) for transform in state " + xform.getRunState();
            warn(message);
            killSession(message);
            return;
        }
        TransformContext tctx = xform.getTransformContext();
        ClassLoader classLoader = tctx.getClassLoader();
        Thread ct = Thread.currentThread();
        ClassLoader oldCl = ct.getContextClassLoader();

        // entering TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ct.setContextClassLoader(classLoader);
        try {
            transformManager.registerThreadContext(tctx);
            readEvent(SERVER, in);
        } finally {
            transformManager.deregisterThreadContext();
            ct.setContextClassLoader(oldCl);
            // left TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        }
    }

    public void clientEvent(OutgoingSocketQueue out)
    {
        Transform xform = mPipe().transform();
        if (xform.getRunState() != TransformState.RUNNING) {
            String message = "killing: clientEvent(out) for transform in state " + xform.getRunState();
            warn(message);
            killSession(message);
            return;
        }
        TransformContext tctx = xform.getTransformContext();
        ClassLoader classLoader = tctx.getClassLoader();
        Thread ct = Thread.currentThread();
        ClassLoader oldCl = ct.getContextClassLoader();

        // entering TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ct.setContextClassLoader(classLoader);
        try {
            transformManager.registerThreadContext(tctx);
            writeEvent(CLIENT, out);
        } finally {
            transformManager.deregisterThreadContext();
            ct.setContextClassLoader(oldCl);
            // left TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        }
    }

    public void serverEvent(OutgoingSocketQueue out)
    {
        Transform xform = mPipe().transform();
        if (xform.getRunState() != TransformState.RUNNING) {
            String message = "killing: serverEvent(out) for transform in state " + xform.getRunState();
            warn(message);
            killSession(message);
            return;
        }
        TransformContext tctx = xform.getTransformContext();
        ClassLoader classLoader = tctx.getClassLoader();
        Thread ct = Thread.currentThread();
        ClassLoader oldCl = ct.getContextClassLoader();

        // entering TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ct.setContextClassLoader(classLoader);
        try {
            transformManager.registerThreadContext(tctx);
            writeEvent(SERVER, out);
        } finally {
            transformManager.deregisterThreadContext();
            ct.setContextClassLoader(oldCl);
            // left TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        }
    }

    /** The write side of the client has been closed from underneath the transform,
     * this is the same as an EPIPE, but is delivered as an event */
    public void clientOutputResetEvent(OutgoingSocketQueue out)
    {
        Transform xform = mPipe().transform();
        if (xform.getRunState() != TransformState.RUNNING) {
            String message = "killing: output reset(client) for transform in state " + xform.getRunState();
            warn(message);
            // killSession(message);
            return;
        }
        TransformContext tctx = xform.getTransformContext();
        ClassLoader classLoader = tctx.getClassLoader();
        Thread ct = Thread.currentThread();
        ClassLoader oldCl = ct.getContextClassLoader();

        // entering TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ct.setContextClassLoader(classLoader);
        try {
            transformManager.registerThreadContext(tctx);
            MDC.put(SESSION_ID_MDC_KEY, idForMDC());
            
            IncomingSocketQueue in = ((com.untangle.mvvm.argon.Session)pSession).clientIncomingSocketQueue();
            if (in != null)
                in.reset();
            sideDieing(CLIENT);
        } catch (MPipeException x) {
            String message = "MPipeException while output resetting";
            error(message, x);
            // killSession(message);
        } catch (Exception x) {
            String message = "" + x.getClass().getName() + " while output resetting";
            // error(message, x);
            killSession(message);
        } catch (OutOfMemoryError x) {
            Main.fatalError("SessionHandler", x);
        } finally {
            transformManager.deregisterThreadContext();
            MDC.remove(SESSION_ID_MDC_KEY);
            ct.setContextClassLoader(oldCl);
            // left TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        }
    }

    /** The write side of the server has been closed from underneath the transform,
     * this is the same as an EPIPE, but is delivered as an event */
    public void serverOutputResetEvent(OutgoingSocketQueue out)
    {
        Transform xform = mPipe().transform();
        if (xform.getRunState() != TransformState.RUNNING) {
            String message = "killing: output reset(server) for transform in state " + xform.getRunState();
            warn(message);
            // killSession(message);
            return;
        }
        TransformContext tctx = xform.getTransformContext();
        ClassLoader classLoader = tctx.getClassLoader();
        Thread ct = Thread.currentThread();
        ClassLoader oldCl = ct.getContextClassLoader();

        // entering TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ct.setContextClassLoader(classLoader);
        try {
            transformManager.registerThreadContext(tctx);
            MDC.put(SESSION_ID_MDC_KEY, idForMDC());
            
            IncomingSocketQueue in = ((com.untangle.mvvm.argon.Session)pSession).serverIncomingSocketQueue();
            if (in != null)
                in.reset();
            sideDieing(SERVER);
        } catch (MPipeException x) {
            String message = "MPipeException while output resetting";
            error(message, x);
            // killSession(message);
        } catch (Exception x) {
            String message = "" + x.getClass().getName() + " while output resetting";
            // error(message, x);
            killSession(message);
        } catch (OutOfMemoryError x) {
            Main.fatalError("SessionHandler", x);
        } finally {
            transformManager.deregisterThreadContext();
            MDC.remove(SESSION_ID_MDC_KEY);
            ct.setContextClassLoader(oldCl);
            // left TransformClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        }
    }


    /**
     * This one sets up the socket queues for streaming to begin.
     *
     */
    private void setupForStreaming()
    {
        IncomingSocketQueue cin = ((com.untangle.mvvm.argon.Session)pSession).clientIncomingSocketQueue();
        IncomingSocketQueue sin = ((com.untangle.mvvm.argon.Session)pSession).serverIncomingSocketQueue();
        OutgoingSocketQueue cout = ((com.untangle.mvvm.argon.Session)pSession).clientOutgoingSocketQueue();
        OutgoingSocketQueue sout = ((com.untangle.mvvm.argon.Session)pSession).serverOutgoingSocketQueue();
        assert (streamer != null);

        if (cin != null)
            cin.disable();
        if (sin != null)
            sin.disable();
        if (streamer[CLIENT] != null) {
            if (cout != null)
                cout.enable();
            if (sout != null)
                sout.disable();
        } else {
            if (sout != null)
                sout.enable();
            if (cout != null)
                cout.disable();
        }

        if (logger.isDebugEnabled()) {
            debug("entering streaming mode c: " + streamer[CLIENT] + ", s: " + streamer[SERVER]);
        }
    }

    /**
     * This one sets up the socket queues for normal operation; used when streaming ends.
     *
     */
    private void setupForNormal()
    {
        IncomingSocketQueue cin = ((com.untangle.mvvm.argon.Session)pSession).clientIncomingSocketQueue();
        IncomingSocketQueue sin = ((com.untangle.mvvm.argon.Session)pSession).serverIncomingSocketQueue();
        OutgoingSocketQueue cout = ((com.untangle.mvvm.argon.Session)pSession).clientOutgoingSocketQueue();
        OutgoingSocketQueue sout = ((com.untangle.mvvm.argon.Session)pSession).serverOutgoingSocketQueue();
        assert (streamer == null);

        // We take care not to change the state unless it's really changing, as changing the
        // state calls notifymvpoll() every time.
        if (sout != null && !sout.isEnabled())
            sout.enable();
        if (sout == null || (sout.isEmpty() && crumbs2write[SERVER] == null)) {
            if (cin != null && !cin.isEnabled())
                cin.enable();
        } else {
            if (cin != null && cin.isEnabled())
                cin.disable();
        }
        if (cout != null && !cout.isEnabled())
            cout.enable();
        if (cout == null || (cout.isEmpty() && crumbs2write[CLIENT] == null)) {
            if (sin != null && !sin.isEnabled())
                sin.enable();
        } else {
            if (sin != null && sin.isEnabled())
                sin.disable();
        }
    }

    /**
     * Returns true if we did something.
     *
     * @param side an <code>int</code> value
     * @param out an <code>OutgoingSocketQueue</code> value
     * @return a <code>boolean</code> value
     */
    private boolean doWrite(int side, OutgoingSocketQueue out)
        throws MPipeException
    {
        boolean didSomething = false;
        if (out != null && out.isEmpty()) {
            if (crumbs2write[side] != null) {
                // Do this first, before checking streamer, so we drain out any remaining buffer.
                tryWrite(side, out, true);
                didSomething = true;
            } else if (streamer != null) {
                IPStreamer s = streamer[side];
                if (s != null) {
                    // It's the right one.
                    addStreamBuf(side, s);
                    if (crumbs2write[side] != null) {
                        tryWrite(side, out, true);
                        didSomething = true;
                    }
                }
            }
        }
        return didSomething;
    }

    //==================================================
    // 8/2/05 - wrs.  Added "MDC" stuff to cause
    // the session ID to be accessible from the
    // log appender
    //
    //===================================================

    // This is the main write hook called by the Vectoring machine
    public void writeEvent(int side, OutgoingSocketQueue out)
    {
        String sideName = side == CLIENT ? "client" : "server";
        MDC.put(SESSION_ID_MDC_KEY, idForMDC());
        try {
            assert out != null;
            if (!out.isEmpty()) {
                warn("writeEvent to non empty outgoing queue on: " + sideName);
                return;
            }

            IncomingSocketQueue ourin;
            OutgoingSocketQueue ourout, otherout;
            if (side == CLIENT) {
                ourin = ((com.untangle.mvvm.argon.Session)pSession).serverIncomingSocketQueue();
                ourout = ((com.untangle.mvvm.argon.Session)pSession).clientOutgoingSocketQueue();
                otherout = ((com.untangle.mvvm.argon.Session)pSession).serverOutgoingSocketQueue();
            } else {
                ourin = ((com.untangle.mvvm.argon.Session)pSession).clientIncomingSocketQueue();
                ourout = ((com.untangle.mvvm.argon.Session)pSession).serverOutgoingSocketQueue();
                otherout = ((com.untangle.mvvm.argon.Session)pSession).clientOutgoingSocketQueue();
            }
            assert out == ourout;

            if (logger.isDebugEnabled()) {
                debug("write(" + sideName + ") out: " + out +
                      "   /  crumbs, write-queue  " +  crumbs2write[side] + ", " + out.numEvents() +
                      "(" + out.numBytes() + " bytes)" + "   opp-read-queue: " +
                      (ourin == null ? null : ourin.numEvents()));
            }

            if (!doWrite(side, ourout)) {
                sendWritableEvent(side);
                // We have to try more writing here in case we added stuff.
                doWrite(side, ourout);
                doWrite(1 - side, otherout);
            }
            if (streamer == null)
                setupForNormal();
        } catch (MPipeException x) {
            String message = "MPipeException while writing to " + sideName;
            error(message, x);
            killSession(message);
        } catch (Exception x) {
            String message = "" + x.getClass().getName() + " while writing to " + sideName;
            error(message, x);
            killSession(message);
        } catch (OutOfMemoryError x) {
            Main.fatalError("SessionHandler", x);
        } finally {
          MDC.remove(SESSION_ID_MDC_KEY);
        }

    }


    public void readEvent(int side, IncomingSocketQueue in)
    {
        String sideName = side == CLIENT ? "client" : "server";
        MDC.put(SESSION_ID_MDC_KEY, idForMDC());
        try {
            assert in != null;

            // need to check if input contains RST (for TCP) or EXPIRE (for UDP)
            // independent of the write buffers.
            if (isSideDieing(side, in)) {
                sideDieing(side);
                return;
            }

            if (!in.isEnabled()) {
                warn("ignoring readEvent called for disabled side " + side);
                return;
            }
            IncomingSocketQueue ourin, otherin;
            OutgoingSocketQueue ourout, otherout;
            OutgoingSocketQueue cout = ((com.untangle.mvvm.argon.Session)pSession).clientOutgoingSocketQueue();
            OutgoingSocketQueue sout = ((com.untangle.mvvm.argon.Session)pSession).serverOutgoingSocketQueue();
            if (side == CLIENT) {
                ourin = ((com.untangle.mvvm.argon.Session)pSession).clientIncomingSocketQueue();
                otherin = ((com.untangle.mvvm.argon.Session)pSession).serverIncomingSocketQueue();
                ourout = sout;
                otherout = cout;
            } else {
                ourin = ((com.untangle.mvvm.argon.Session)pSession).serverIncomingSocketQueue();
                otherin = ((com.untangle.mvvm.argon.Session)pSession).clientIncomingSocketQueue();
                ourout = cout;
                otherout = sout;
            }
            assert in == ourin;

            if (logger.isDebugEnabled()) {
                debug("read(" + sideName + ") in: " + in +
                      "   /  opp-write-crumbs: " + crumbs2write[1 - side] + ", opp-write-queue: " +
                      (ourout == null ? null : ourout.numEvents()));
            }

            assert streamer == null : "readEvent when streaming";;

            if (ourout == null || (crumbs2write[1 - side] == null && ourout.isEmpty())) {
                tryRead(side, in, true);
                doWrite(side, otherout);
                doWrite(1 - side, ourout);
                if (streamer != null) {
                    // We do this after the writes so that we try to write out first.
                    setupForStreaming();
                    return;
                }
            } else {
                error("Illegal State: read(" + sideName + ") in: " + in +
                      "   /  opp-write-crumbs: " + crumbs2write[1 - side] + ", opp-write-queue: " +
                      (ourout == null ? null : ourout.numEvents()));
            }
            setupForNormal();
        } catch (MPipeException x) {
            String message = "MPipeException while reading from " + sideName;
            error(message, x);
            killSession(message);
        } catch (Exception x) {
            String message = "" + x.getClass().getName() + " while reading from " + sideName;
            error(message, x);
            killSession(message);
        } catch (OutOfMemoryError x) {
            Main.fatalError("SessionHandler", x);
        } finally {
          MDC.remove(SESSION_ID_MDC_KEY);
        }
    }

    // Callback called on finalize
    protected void closeFinal()
    {
        cancelTimer();
        if (RWSessionStats.DoDetailedTimes) {
            long[] times = stats().times();
            times[SessionStats.FINAL_CLOSE] = MetaEnv.currentTimeMillis();
            if (timesLogger.isInfoEnabled())
                reportTimes(times);
        }

        dispatcher.removeSession(this);
    }

    boolean needsFinalization()
    {
        return needsFinalization;
    }

    protected void error(String message)
    {
        logger.error(message.toString());
    }

    protected void error(String message, Exception x)
    {
        logger.error(message.toString(), x);
    }

    protected void warn(String message)
    {
        logger.warn(message.toString());
    }

    protected void warn(String message, Exception x)
    {
        logger.warn(message.toString(), x);
    }

    protected void info(String message)
    {
        if (logger.isInfoEnabled()) {
            logger.info(message.toString());
        }
    }

    protected void debug(String message)
    {
        if (logger.isDebugEnabled()) {
            logger.debug(message.toString());
        }
    }

    protected void debug(String message, Exception x)
    {
        if (logger.isDebugEnabled()) {
            logger.debug(message.toString(), x);
        }
    }

    /**
     * <code>isSideDieing</code> returns true if the incoming socket queue
     * contains an event that will cause the end of the session (at least on
     * that side). These are RST for TCP and EXPIRE for UDP.
     *
     * @param in an <code>IncomingSocketQueue</code> value
     * @return a <code>boolean</code> value
     */
    abstract protected boolean isSideDieing(int side, IncomingSocketQueue in);

    abstract protected void sideDieing(int side) throws MPipeException;

    abstract void killSession(String message);

    public abstract IPSessionDesc makeDesc();

    abstract void sendWritableEvent(int side) throws MPipeException;

    abstract void sendCompleteEvent() throws MPipeException;

    abstract void tryWrite(int side, OutgoingSocketQueue out, boolean warnIfUnable)
        throws MPipeException;

    abstract void addStreamBuf(int side, IPStreamer streamer)
        throws MPipeException;

    abstract void tryRead(int side, IncomingSocketQueue in, boolean warnIfUnable)
        throws MPipeException;

    abstract String idForMDC();

    private static DateFormat formatter = new AbsoluteTimeDateFormat();

    private void reportTimes(long[] times) {
        StringBuffer result = new StringBuffer("times for ");
        result.append(id());
        result.append("\n");

        for (int i = SessionStats.MIN_TIME_INDEX; i < SessionStats.MAX_TIME_INDEX; i++) {
            if (times[i] == 0)
                continue;
            String name = SessionStats.TimeNames[i];
            int len = name.length();
            int pad = 30 - len;
            result.append(name);
            result.append(": ");
            for (int j = 0; j < pad; j++)
                result.append(' ');
            formatter.format(new Date(times[i]), result, null);
            result.append("\n");
        }
        timesLogger.info(result.toString());
    }


    // Don't need equal or hashcode since we can only have one of these objects per
    // session (so the memory address is ok for equals/hashcode).
}

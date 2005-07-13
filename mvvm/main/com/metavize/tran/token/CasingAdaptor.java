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

package com.metavize.tran.token;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.tapi.AbstractEventHandler;
import com.metavize.mvvm.tapi.IPSessionDesc;
import com.metavize.mvvm.tapi.MPipeException;
import com.metavize.mvvm.tapi.Pipeline;
import com.metavize.mvvm.tapi.PipelineFoundry;
import com.metavize.mvvm.tapi.Session;
import com.metavize.mvvm.tapi.TCPSession;
import com.metavize.mvvm.tapi.event.IPDataResult;
import com.metavize.mvvm.tapi.event.IPSessionEvent;
import com.metavize.mvvm.tapi.event.TCPChunkEvent;
import com.metavize.mvvm.tapi.event.TCPChunkResult;
import com.metavize.mvvm.tapi.event.TCPSessionEvent;
import com.metavize.mvvm.tapi.event.TCPStreamer;
import org.apache.log4j.Logger;

public class CasingAdaptor extends AbstractEventHandler
{
    private final CasingFactory casingFactory;
    private final boolean clientSide;

    private final Map casings = new ConcurrentHashMap();

    private final PipelineFoundry pipeFoundry = MvvmContextFactory.context()
        .pipelineFoundry();
    private final Logger logger = Logger.getLogger(CasingAdaptor.class);

    public CasingAdaptor(CasingFactory casingFactory, boolean clientSide)
    {
        this.casingFactory = casingFactory;
        this.clientSide = clientSide;
    }

    // SessionEventListener methods -------------------------------------------

    @Override
    public void handleTCPNewSession(TCPSessionEvent e)
    {
        logger.debug("new session");
        TCPSession session = e.session();

        Casing casing = casingFactory.casing(session, clientSide);
        Pipeline pipeline = pipeFoundry.getPipeline(session.id());
        logger.debug("setting: " + pipeline + " for: " + session.id());
        addCasing(session, casing, pipeline);

        Parser parser = casing.parser();
        Unparser unparser = casing.unparser();

        if (clientSide) {
            session.serverReadLimit(8);
        } else {
            session.clientReadLimit(8);
        }
    }

    @Override
    public IPDataResult handleTCPClientChunk(TCPChunkEvent e)
    {
        logger.debug("handling client chunk, session: " + e.session().id());
        boolean inbound = e.session().direction() == IPSessionDesc.INBOUND;

        logger.debug("client direction: " + e.session().direction());
        logger.debug("client inbound: " + inbound);

        if (clientSide) {
            return parse(e, false, false);
        } else {
            return unparse(e, false);
        }
    }

    @Override
    public IPDataResult handleTCPServerChunk(TCPChunkEvent e)
    {
        logger.debug("handling server chunk, session: " + e.session().id());
        boolean inbound = e.session().direction() == IPSessionDesc.INBOUND;

        logger.debug("server direction: " + e.session().direction());
        logger.debug("server inbound: " + inbound);

        if (clientSide) {
            return unparse(e, true);
        } else {
            return parse(e, true, false);
        }
    }

    @Override
    public IPDataResult handleTCPClientDataEnd(TCPChunkEvent e)
    {
        logger.debug("handling client chunk, session: " + e.session().id());
        boolean inbound = e.session().direction() == IPSessionDesc.INBOUND;

        logger.debug("client direction: " + e.session().direction());
        logger.debug("client inbound: " + inbound);

        if (clientSide) {
            return parse(e, false, true);
        } else {
            if (e.chunk().hasRemaining()) {
                logger.warn("should not happen: unparse TCPClientDataEnd");
            }
            return null;
        }
    }

    @Override
    public IPDataResult handleTCPServerDataEnd(TCPChunkEvent e)
    {
        logger.debug("handling server chunk, session: " + e.session().id());
        boolean inbound = e.session().direction() == IPSessionDesc.INBOUND;

        logger.debug("server direction: " + e.session().direction());
        logger.debug("server inbound: " + inbound);

        if (clientSide) {
            if (e.chunk().hasRemaining()) {
                logger.warn("should not happen: unparse TCPClientDataEnd");
            }
            return null;
        } else {
            return parse(e, true, true);
        }
    }

    @Override
    public void handleTCPClientFIN(TCPSessionEvent e)
    {
        TokenStreamer ts = null;

        TCPSession s = (TCPSession)e.ipsession();
        Casing c = getCasing(s);

        if (clientSide) {
            ts = c.parser().endSession();
        } else {
            ts = c.unparser().endSession();
        }

        if (null != ts) {
            s.beginServerStream(ts);
        } else {
            s.shutdownServer();
        }
    }

    @Override
    public void handleTCPServerFIN(TCPSessionEvent e)
    {
        TokenStreamer ts = null;

        TCPSession s = (TCPSession)e.ipsession();
        Casing c = getCasing(s);

        if (clientSide) {
            ts = c.unparser().endSession();
        } else {
            ts = c.parser().endSession();
        }

        if (null != ts) {
            s.beginClientStream(ts);
        } else {
            s.shutdownClient();
        }
    }

    @Override
    public void handleTCPFinalized(TCPSessionEvent e) throws MPipeException
    {
        logger.debug("finalizing " + e.session().id());
        removeCasingDesc(e.session());
    }

    @Override
    public void handleTimer(IPSessionEvent e)
    {
        TCPSession s = (TCPSession)e.ipsession();

        Parser p = getCasing(s).parser();
        p.handleTimer();
        // XXX unparser doesnt get one, does it need it?
    }

    // CasingDesc utils -------------------------------------------------------

    private static class CasingDesc
    {
        final Casing casing;
        final Pipeline pipeline;

        CasingDesc(Casing casing, Pipeline pipeline)
        {
            this.casing = casing;
            this.pipeline = pipeline;
        }
    }

    private void addCasing(Session session, Casing casing, Pipeline pipeline)
    {
        casings.put(session, new CasingDesc(casing, pipeline));
    }

    private CasingDesc getCasingDesc(Session session)
    {
        CasingDesc casingDesc = (CasingDesc)casings.get(session);
        return casingDesc;
    }

    private Casing getCasing(Session session)
    {
        CasingDesc casingDesc = (CasingDesc)casings.get(session);
        return casingDesc.casing;
    }

    private Pipeline getPipeline(Session session)
    {
        CasingDesc casingDesc = (CasingDesc)casings.get(session);
        return casingDesc.pipeline;
    }

    private void removeCasingDesc(Session session)
    {
        casings.remove(session);
    }

    // private methods --------------------------------------------------------

    private IPDataResult unparse(TCPChunkEvent e, boolean s2c)
    {
        ByteBuffer b = e.chunk();

        assert b.remaining() <= 8;

        if (b.remaining() < 8) {
            // read limit 2
            b.compact();
            b.limit(8);
            logger.debug("unparse returning buffer, for more: " + b);
            return new TCPChunkResult(null, null, b);
        }

        TCPSession s = e.session();
        CasingDesc casingDesc = getCasingDesc(s);
        Casing casing = casingDesc.casing;
        Pipeline pipeline = casingDesc.pipeline;

        Long key = new Long(b.getLong());
        Token tok = (Token)pipeline.detach(key);
        logger.debug("RETRIEVED object: " + tok + " with key: " + key
                     + " on pipeline: " + pipeline);

        b.limit(8);

        assert !b.hasRemaining();

        UnparseResult ur;
        try {
            Unparser u = casing.unparser();
            ur = u.unparse(tok);
        } catch (Exception exn) { /* not just UnparseException */
            logger.error("internal error, closing connection", exn);
            if (s2c) {
                // XXX We don't have a good handle on this
                s.resetClient();
                s.resetServer();
            } else {
                // XXX We don't have a good handle on this
                s.shutdownServer();
                s.resetClient();
            }
            logger.debug("returning DO_NOT_PASS");
            return IPDataResult.DO_NOT_PASS;
        }

        if (ur.isStreamer()) {
            TCPStreamer ts = ur.getTcpStreamer();
            if (s2c) {
                s.beginClientStream(ts);
            } else {
                s.beginServerStream(ts);
            }
            return new TCPChunkResult(null, null, null);
        } else {
            if (s2c) {
                logger.debug("unparse result to client");
                ByteBuffer[] r = ur.result();
                for (int i = 0; null != null && i < r.length; i++) {
                    logger.debug("  to client: " + r[i]);
                }
                return new TCPChunkResult(r, null, null);
            } else {
                logger.debug("unparse result to server");
                ByteBuffer[] r = ur.result();
                for (int i = 0; null != r && i < r.length; i++) {
                    logger.debug("  to server: " + r[i]);
                }
                return new TCPChunkResult(null, r, null);
            }
        }
    }

    private IPDataResult parse(TCPChunkEvent e, boolean s2c, boolean last)
    {
        TCPSession s = e.session();
        CasingDesc casingDesc = getCasingDesc(s);
        Casing casing = casingDesc.casing;
        Pipeline pipeline = casingDesc.pipeline;

        ParseResult pr;
        try {
            Parser p = casing.parser();
            if (last) {
                pr = p.parseEnd(e.chunk());
            } else {
                pr = p.parse(e.chunk());
            }
        } catch (Exception exn) { /* not just the ParseException */
            logger.error("closing connection", exn);
            if (s2c) {
                // XXX We don't have a good handle on this
                s.resetClient();
                s.resetServer();
            } else {
                // XXX We don't have a good handle on this
                s.shutdownServer();
                s.resetClient();
            }
            logger.debug("unparse returning DO_NOT_PASS");
            return IPDataResult.DO_NOT_PASS;
        }

        if (pr.isStreamer()) {
            TokenStreamer ts = pr.getTokenStreamer();
            if (s2c) {
                s.beginClientStream(ts);
            } else {
                s.beginServerStream(ts);
            }
            return new TCPChunkResult(null, null, pr.getReadBuffer());
        } else {
            List<Token> results = pr.getResults();

            // XXX add magic:
            ByteBuffer bb = ByteBuffer.allocate(8 * results.size());

            // XXX add magic:
            for (Token t : results) {
                Long key = pipeline.attach(t);
                logger.debug("SAVED object: " + t + " with key: " + key
                             + " on pipeline: " + pipeline);
                bb.putLong(key);
            }
            bb.flip();

            ByteBuffer[] r = new ByteBuffer[] { bb };

            if (s2c) {
                logger.debug("parse result to server, read buffer: "
                             + pr.getReadBuffer() + "  to client: " + r[0]);
                return new TCPChunkResult(r, null, pr.getReadBuffer());
            } else {
                logger.debug("parse result to client, read buffer: "
                             + pr.getReadBuffer() + "  to server: " + r[0]);
                return new TCPChunkResult(null, r, pr.getReadBuffer());
            }
        }
    }
}

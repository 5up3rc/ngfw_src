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

package com.untangle.tran.token;

import static com.untangle.tran.token.CasingAdaptor.TOKEN_SIZE;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.untangle.mvvm.MvvmContextFactory;
import com.untangle.mvvm.tapi.AbstractEventHandler;
import com.untangle.mvvm.tapi.MPipeException;
import com.untangle.mvvm.tapi.Pipeline;
import com.untangle.mvvm.tapi.PipelineFoundry;
import com.untangle.mvvm.tapi.Session;
import com.untangle.mvvm.tapi.TCPSession;
import com.untangle.mvvm.tapi.event.IPDataResult;
import com.untangle.mvvm.tapi.event.IPSessionEvent;
import com.untangle.mvvm.tapi.event.TCPChunkEvent;
import com.untangle.mvvm.tapi.event.TCPChunkResult;
import com.untangle.mvvm.tapi.event.TCPNewSessionRequestEvent;
import com.untangle.mvvm.tapi.event.TCPSessionEvent;
import com.untangle.mvvm.tapi.event.TCPStreamer;
import com.untangle.mvvm.tapi.event.UDPNewSessionRequestEvent;
import com.untangle.mvvm.tapi.event.UDPPacketEvent;
import com.untangle.mvvm.tapi.event.UDPSessionEvent;
import com.untangle.mvvm.tran.MutateTStats;
import com.untangle.mvvm.tran.Transform;
import org.apache.log4j.Logger;

public class TokenAdaptor extends AbstractEventHandler
{
    private static final ByteBuffer[] BYTE_BUFFER_PROTO = new ByteBuffer[0];

    private final TokenHandlerFactory handlerFactory;
    private final Map handlers = new ConcurrentHashMap();

    private final PipelineFoundry pipeFoundry = MvvmContextFactory.context()
        .pipelineFoundry();
    private final Logger logger = Logger.getLogger(TokenAdaptor.class);

    public TokenAdaptor(Transform transform, TokenHandlerFactory thf)
    {
        super(transform);
        this.handlerFactory = thf;
    }

    @Override
    public void handleTCPNewSessionRequest(TCPNewSessionRequestEvent e)
        throws MPipeException
    {
        handlerFactory.handleNewSessionRequest(e.sessionRequest());
    }

    @Override
    public void handleTCPNewSession(TCPSessionEvent e)
        throws MPipeException
    {
        TCPSession s = e.session();
        TokenHandler h = handlerFactory.tokenHandler(s);
        Pipeline pipeline = pipeFoundry.getPipeline(s.id());
        addHandler(s, h, pipeline);
        logger.debug("new session, s: " + s + " h: " + h);

        s.clientReadBufferSize(TOKEN_SIZE);
        s.clientLineBuffering(false);
        s.serverReadBufferSize(TOKEN_SIZE);
        s.serverLineBuffering(false);
        // (read limits are automatically set to the buffer size)
    }

    @Override
    public IPDataResult handleTCPServerChunk(TCPChunkEvent e)
        throws MPipeException
    {
        HandlerDesc handlerDesc = getHandlerDesc(e.session());
        return handleToken(handlerDesc, e, true);
    }

    @Override
    public IPDataResult handleTCPClientChunk(TCPChunkEvent e)
        throws MPipeException
    {
        HandlerDesc handlerDesc = getHandlerDesc(e.session());
        return handleToken(handlerDesc, e, false);
    }

    @Override
    public void handleTCPClientFIN(TCPSessionEvent e)
        throws MPipeException
    {
        TCPSession session = (TCPSession)e.session();
        HandlerDesc handlerDesc = getHandlerDesc(session);

        try {
            handlerDesc.handler.handleClientFin();
        } catch (TokenException exn) {
            logger.warn("resetting connection", exn);
            session.resetClient();
            session.resetServer();
        }
    }

    @Override
    public void handleTCPServerFIN(TCPSessionEvent e)
        throws MPipeException
    {
        TCPSession session = (TCPSession)e.session();
        HandlerDesc handlerDesc = getHandlerDesc(session);

        try {
            handlerDesc.handler.handleServerFin();
        } catch (TokenException exn) {
            logger.warn("resetting connection", exn);
            session.resetClient();
            session.resetServer();
        }
    }

    @Override
    public void handleTCPFinalized(TCPSessionEvent e) throws MPipeException
    {
        TCPSession session = (TCPSession)e.session();
        HandlerDesc handlerDesc = getHandlerDesc(session);

        try {
            handlerDesc.handler.handleFinalized();
        } catch (TokenException exn) {
            logger.warn("resetting connection", exn);
            session.resetClient();
            session.resetServer();
        }

        super.handleTCPFinalized(e);
        removeHandler(e.session());
    }

    // UDP events -------------------------------------------------------------

    @Override
    public void handleUDPNewSessionRequest(UDPNewSessionRequestEvent e)
        throws MPipeException
    {
        throw new UnsupportedOperationException("UDP not supported");
    }

    @Override
    public void handleUDPNewSession(UDPSessionEvent e) throws MPipeException
    {
        throw new UnsupportedOperationException("UDP not supported");
    }

    @Override
    public void handleUDPClientPacket(UDPPacketEvent e)
        throws MPipeException
    {
        throw new UnsupportedOperationException("UDP not supported");
    }

    @Override
    public void handleUDPServerPacket(UDPPacketEvent e)
        throws MPipeException
    {
        throw new UnsupportedOperationException("UDP not supported");
    }

    @Override
    public void handleUDPClientExpired(UDPSessionEvent e) throws MPipeException
    {
        throw new UnsupportedOperationException("UDP not supported");
    }

    @Override
    public void handleUDPServerExpired(UDPSessionEvent e) throws MPipeException
    {
        throw new UnsupportedOperationException("UDP not supported");
    }

    @Override
    public void handleUDPFinalized(UDPSessionEvent e) throws MPipeException
    {
        throw new UnsupportedOperationException("UDP not supported");
    }

    @Override
    public void handleTimer(IPSessionEvent e)
    {
        TokenHandler th = getHandler(e.ipsession());
        try {
            th.handleTimer();
        } catch (TokenException exn) {
            logger.warn("exception in timer, no action taken", exn);
        }
    }

    // HandlerDesc utils ------------------------------------------------------

    private static class HandlerDesc
    {
        final TokenHandler handler;
        final Pipeline pipeline;

        HandlerDesc(TokenHandler handler, Pipeline pipeline)
        {
            this.handler = handler;
            this.pipeline = pipeline;
        }
    }

    private void addHandler(Session session, TokenHandler handler,
                            Pipeline pipeline)
    {
        handlers.put(session, new HandlerDesc(handler, pipeline));
    }

    private HandlerDesc getHandlerDesc(Session session)
    {
        HandlerDesc handlerDesc = (HandlerDesc)handlers.get(session);
        return handlerDesc;
    }

    private TokenHandler getHandler(Session session)
    {
        HandlerDesc handlerDesc = (HandlerDesc)handlers.get(session);
        return handlerDesc.handler;
    }

    private Pipeline getPipeline(Session session)
    {
        HandlerDesc handlerDesc = (HandlerDesc)handlers.get(session);
        return handlerDesc.pipeline;
    }

    private void removeHandler(Session session)
    {
        handlers.remove(session);
    }

    // private methods --------------------------------------------------------

    private IPDataResult handleToken(HandlerDesc handlerDesc, TCPChunkEvent e,
                                     boolean s2c)
    {
        TokenHandler handler = handlerDesc.handler;
        Pipeline pipeline = handlerDesc.pipeline;

        ByteBuffer b = e.chunk();

        if (b.remaining() < TOKEN_SIZE) {
            // read limit to token size
            b.compact();
            b.limit(TOKEN_SIZE);
            logger.debug("returning buffer, for more: " + b);
            return new TCPChunkResult(BYTE_BUFFER_PROTO, BYTE_BUFFER_PROTO, b);
        }

        Long key = new Long(b.getLong());

        Token token = (Token)pipeline.detach(key);
        if (logger.isDebugEnabled())
            logger.debug("RETRIEVED object " + token + " with key: " + key);

        TCPSession session = e.session();

        int d = s2c ? MutateTStats.SERVER_TO_CLIENT
            : MutateTStats.CLIENT_TO_SERVER;
        try {
            MutateTStats.rereadData(d, session,
                                    token.getEstimatedSize() - TOKEN_SIZE);
        } catch (Exception exn) {
            logger.warn("could not get estimated size", exn);
        }

        TokenResult tr;
        try {
            tr = doToken(session, s2c, pipeline, handler, token);
        } catch (TokenException exn) {
            logger.warn("resetting connection", exn);
            session.resetClient();
            session.resetServer();
            return IPDataResult.DO_NOT_PASS;
        }

        // XXX ugly:
        if (tr.isStreamer()) {
            if (tr.s2cStreamer() != null) {
                logger.debug("beginning client stream");
                TokenStreamer tokSt = tr.s2cStreamer();
                TokenStreamerWrapper wrapper
                    = new TokenStreamerWrapper(tokSt, session,
                                               MutateTStats.SERVER_TO_CLIENT);
                TCPStreamer ts = new TokenStreamerAdaptor(pipeline, wrapper);
                session.beginClientStream(ts);
            } else {
                logger.debug("beginning server stream");
                TokenStreamer tokSt = tr.c2sStreamer();
                TokenStreamerWrapper wrapper
                    = new TokenStreamerWrapper(tokSt, session,
                                               MutateTStats.CLIENT_TO_SERVER);
                TCPStreamer ts = new TokenStreamerAdaptor(pipeline, wrapper);
                session.beginServerStream(ts);
            }
            // just means nothing extra to send before beginning stream.
            return IPDataResult.SEND_NOTHING;
        } else {
            logger.debug("processing s2c tokens");
            ByteBuffer[] cr = processResults(tr.s2cTokens(), pipeline, session,
                                             true);
            logger.debug("processing c2s");
            ByteBuffer[] sr = processResults(tr.c2sTokens(), pipeline, session,
                                             false);

            if (logger.isDebugEnabled()) {
                logger.debug("returning results: ");
                for (int i = 0; null != cr && i < cr.length; i++) {
                    logger.debug("  to client: " + cr[i]);
                }
                for (int i = 0; null != sr && i < sr.length; i++) {
                    logger.debug("  to server: " + sr[i]);
                }
            }

            return new TCPChunkResult(cr, sr, null);
        }
    }

    public TokenResult doToken(TCPSession session, boolean s2c,
                               Pipeline pipeline, TokenHandler handler,
                               Token token)
        throws TokenException
    {
        if (token instanceof Release) {
            Release release = (Release)token;

            TokenResult utr = handler.releaseFlush();

            session.release();

            if (utr.isStreamer()) {
                if (s2c) {
                    TokenStreamer cStm = utr.c2sStreamer();
                    TokenStreamer sStm = new ReleaseTokenStreamer
                        (utr.s2cStreamer(), release);

                    return new TokenResult(sStm, cStm);
                } else {
                    TokenStreamer cStm = new ReleaseTokenStreamer
                        (utr.c2sStreamer(), release);
                    TokenStreamer sStm = utr.s2cStreamer();

                    return new TokenResult(sStm, cStm);
                }
            } else {
                if (s2c) {
                    Token[] cTok = utr.c2sTokens();

                    Token[] sTokOrig = utr.s2cTokens();
                    Token[] sTok = new Token[sTokOrig.length + 1];
                    System.arraycopy(sTokOrig, 0, sTok, 0, sTokOrig.length);
                    sTok[sTok.length - 1] = release;

                    return new TokenResult(sTok, cTok);
                } else {
                    Token[] cTokOrig = utr.c2sTokens();
                    Token[] cTok = new Token[cTokOrig.length + 1];
                    System.arraycopy(cTokOrig, 0, cTok, 0, cTokOrig.length);
                    cTok[cTok.length - 1] = release;
                    Token[] sTok = utr.s2cTokens();
                    return new TokenResult(sTok, cTok);
                }
            }
        } else {
            if (s2c) {
                return handler.handleServerToken(token);
            } else {
                return handler.handleClientToken(token);
            }
        }
    }

    private ByteBuffer[] processResults(Token[] results, Pipeline pipeline,
                                        Session session, boolean s2c)
    {
        // XXX factor out token writing
        ByteBuffer bb = ByteBuffer.allocate(TOKEN_SIZE * results.length);

        for (Token tok : results) {
            if (null == tok) { continue; }

            int d = s2c ? MutateTStats.SERVER_TO_CLIENT
                : MutateTStats.CLIENT_TO_SERVER;
            try {
                MutateTStats.rewroteData(d, session,
                                         tok.getEstimatedSize() - TOKEN_SIZE);
            } catch (Exception exn) {
                logger.warn("could not estimate size", exn);
            }

            Long key = pipeline.attach(tok);
            if (logger.isDebugEnabled())
                logger.debug("SAVED object " + tok + " with key: " + key);

            bb.putLong(key);
        }
        bb.flip();

        return 0 == bb.remaining() ? null : new ByteBuffer[] { bb };
    }
}


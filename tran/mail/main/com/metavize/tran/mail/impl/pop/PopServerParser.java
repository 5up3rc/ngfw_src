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

package com.metavize.tran.mail.impl.pop;

import static com.metavize.tran.util.Ascii.*;
import static com.metavize.tran.util.BufferUtil.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.tapi.Pipeline;
import com.metavize.mvvm.tapi.TCPSession;
import com.metavize.tran.mail.PopCasing;
import com.metavize.tran.mail.papi.AddressKind;
import com.metavize.tran.mail.papi.DoNotCareT;
import com.metavize.tran.mail.papi.DoNotCareChunkT;
import com.metavize.tran.mail.papi.MessageBoundaryScanner;
import com.metavize.tran.mail.papi.MessageInfo;
import com.metavize.tran.mail.papi.MessageInfoFactory;
import com.metavize.tran.mail.papi.MIMEMessageT;
import com.metavize.tran.mail.papi.MIMEMessageTrickleT;
import com.metavize.tran.mail.papi.pop.PopReply;
import com.metavize.tran.mail.papi.pop.PopReplyMore;
import com.metavize.tran.mime.HeaderParseException;
import com.metavize.tran.mime.InvalidHeaderDataException;
import com.metavize.tran.mime.LineTooLongException;
import com.metavize.tran.mime.MIMEMessageHeaders;
import com.metavize.tran.token.AbstractParser;
import com.metavize.tran.token.Chunk;
import com.metavize.tran.token.EndMarker;
import com.metavize.tran.token.ParseException;
import com.metavize.tran.token.ParseResult;
import com.metavize.tran.token.Token;
import com.metavize.tran.util.AsciiCharBuffer;
import org.apache.log4j.Logger;

public class PopServerParser extends AbstractParser
{
    private static final Logger logger = Logger.getLogger(PopServerParser.class);

    private static final File BUNNICULA_TMP = new File(System.getProperty("bunnicula.tmp.dir"));

    private static final int LINE_SZ = 1024;

    private enum State {
        REPLY,
        DATA,
        HDRDATA,
        SKIPDATA,
        DONOTCARE
    };

    private final Pipeline pipeline;
    private final PopCasing zCasing;
    private final MessageBoundaryScanner zMBScanner;

    private File zMsgFile;
    private FileChannel zWrChannel;

    private State state;
    private MIMEMessageT zMMessageT;
    private boolean bHdrDone;
    private boolean bBodyDone;

    // constructors -----------------------------------------------------------

    public PopServerParser(TCPSession session, PopCasing zCasing)
    {
        super(session, true);
        lineBuffering(false);

        pipeline = MvvmContextFactory.context().pipelineFoundry().getPipeline(session.id());
        this.zCasing = zCasing;
        zMBScanner = new MessageBoundaryScanner();

        state = State.REPLY;
        bHdrDone = false;
        bBodyDone = false;
    }

    // Parser methods ---------------------------------------------------------

    public ParseResult parse(ByteBuffer buf) throws ParseException
    {
        //logger.debug("parse(" + AsciiCharBuffer.wrap(buf) + "), " + buf);

        List<Token> zTokens = new LinkedList<Token>();
        boolean bDone = false;

        ByteBuffer dup;

        while (false == bDone) {
            switch (state) {
            case REPLY:
                int iReplyEnd = findCRLFEnd(buf);
                logger.debug(state + " state, " + buf + ", end at: " + iReplyEnd);
                if (1 < iReplyEnd) {
                    dup = buf.duplicate();

                    /* scan and consume reply */
                    PopReply reply;
                    try {
                        reply = PopReply.parse(buf, iReplyEnd);
                        zTokens.add(reply);
                    } catch (ParseException exn) {
                        /* long reply may break before CRLF sequence
                         * so if PopReply fails,
                         * we assume long reply spans multiple buffers
                         */
                        zTokens.add(new PopReplyMore(dup));
                        logger.debug("reply (more): " + dup + ", " + exn);

                        buf = null; /* buf has been consumed */
                        bDone = true;
                        break;
                    }

                    boolean bRetrReply;
                    if (true == reply.isMsgData() &&
                        false == zCasing.getIncomingMsgHdr()) {
                        // we got +OK w/ octet count and client didn't send TOP
                        // (so client sent RETR)
                        // - msg must start next
                        bRetrReply = true;
                        //logger.debug("retr message reply (octets): " + reply);
                    } else if (true == zCasing.getIncomingMsg()) {
                        if (true == reply.isSimpleOK()) {
                            // we got +OK w/o octet count after client sent RETR
                            // - assume that msg starts next
                            bRetrReply = true;
                            reply.setMsgData(true);
                            //logger.debug("retr message reply (no octets): " + reply);
                        } else {
                            // we didn't get +OK after client sent RETR (-ERR)
                            // - no msg will follow
                            bRetrReply = false;
                            reply.setMsgData(false);
                            logger.warn("retr message reply (no octets): " + reply);
                        }
                    } else {
                        bRetrReply = false;
                        reply.setMsgData(false);
                    }

                    zCasing.setIncomingMsg(false);

                    boolean bTopReply;
                    if (false == bRetrReply &&
                        true == zCasing.getIncomingMsgHdr()) {
                        if (true == reply.isOK()) {
                            // we got +OK (w/ or w/o octet count) and
                            // client sent TOP
                            // - assume that msg hdr starts next
                            // (we do not scan msg frag because we cannot know
                            //  if frag contains full msg)
                            bTopReply = true;
                            reply.setMsgHdrData(true);
                            //logger.debug("top message reply: " + reply);
                        } else {
                            // we didn't get +OK after client sent TOP (-ERR)
                            // - no msg hdr will follow
                            bTopReply = false;
                            reply.setMsgHdrData(false);
                            logger.warn("top message reply: " + reply);
                        }
                    } else {
                        bTopReply = false;
                        reply.setMsgHdrData(false);
                    }

                    zCasing.setIncomingMsgHdr(false);

                    if (true == bRetrReply ||
                        true == bTopReply) {
                        try {
                            zMsgFile = pipeline.mktemp("popsp");
                            zWrChannel = new FileOutputStream(zMsgFile).getChannel();
                            //logger.debug("message file: " + zMsgFile);
                        } catch (IOException exn) {
                            zWrChannel = null;
                            zMsgFile = null;
                            throw new ParseException("cannot create message file: " + exn + "; releasing session");
                        }

                        if (true == bRetrReply) {
                            logger.debug("retr message reply: " + buf);
                            logger.debug("entering DATA state");
                            state = State.DATA;
                        } else { /* must be (true == bTopReply) */
                            logger.debug("top message reply: " + buf);
                            logger.debug("entering HDRDATA state");
                            state = State.HDRDATA;
                        }

                        zMMessageT = new MIMEMessageT(zMsgFile);

                        if (false == buf.hasRemaining()) {
                            logger.debug("buf is empty");

                            buf = null; /* buf has been consumed */
                            bDone = true;
                        }
                        /* else if we have more data
                         * (e.g., buf also contains msg frag),
                         * then parse remaining data in DATA or HDRDATA stage
                         */
                    } else {
                        //logger.debug("reply: " + reply + ", " + buf);
                        logger.debug("reply: " + buf);

                        if (false == buf.hasRemaining()) {
                            logger.debug("reply buf is empty");

                            buf = null; /* buf has been consumed */
                            bDone = true;
                        } else {
                            /* else if we have more data */
                            logger.debug("compact reply buf: " + buf);

                            dup.clear();
                            dup.put(buf);
                            dup.flip();

                            buf = dup;
                        }
                    }
                } else {
                    logger.debug("buf does not contain CRLF");

                    if (buf.limit() == buf.capacity()) {
                        /* casing adapter will handle full buf for us */
                        throw new ParseException("server read buf is full and does not contain CRLF; traffic cannot be POP; releasing session: " + buf);
                    }
                    /* else buf is already "compact" */

                    /* wait for more data */
                    bDone = true;
                }

                break;

            case DATA:
                logger.debug(state + " state, " + buf);

                if (true == buf.hasRemaining()) {
                    dup = buf.duplicate();

                    if (false == bHdrDone) {
                        /* casing temporarily buffers and writes header */
                        logger.debug("message header: " + buf);

                        ByteBuffer writeDup = buf.duplicate();

//                        try {
                            /* scan and "consume" msg frag */
                            bHdrDone = zMBScanner.processHeaders(buf, LINE_SZ);
//                        } catch (LineTooLongException exn) {
//                            logger.warn("cannot process message header: " + exn);
//                            zWrChannel = closeChannel(zWrChannel);
//                            handleException(zTokens, dup);
//                            zMsgFile = null;
//                            buf = null; /* buf has been consumed */
//                            bDone = true;
//                            break;
//                        }

                        logger.debug("message header is complete: " + bHdrDone + ", " + buf);

                        /* writeDup position is already set */
                        writeDup.limit(buf.position());
                        writeFile(writeDup);

                        if (true == bHdrDone) {
                            zWrChannel = closeChannel(zWrChannel);

                            try {
                                MIMEMessageHeaders zMMHeader = MIMEMessageHeaders.parseMMHeaders(zMMessageT.getInputStream(), zMMessageT.getFileMIMESource());

                                MessageInfo zMsgInfo = MessageInfoFactory.fromMIMEMessage(zMMHeader, session.pipelineEndpoints(), session.serverPort());
                                zMsgInfo.addAddress(AddressKind.USER, zCasing.getUser(), null);

                                zMMessageT.setMIMEMessageHeader(zMMHeader);
                                zMMessageT.setMessageInfo(zMsgInfo);

                                zTokens.add(zMMessageT);
                            } catch (IOException exn) {
                                logger.warn("cannot parse message header: " + exn);
                                handleException(zTokens, dup);
                                zMsgFile = null;
                                buf = null; /* buf has been consumed */
                                bDone = true;
                                break;
                            } catch (InvalidHeaderDataException exn) {
                                logger.warn("cannot parse message header: " + exn);
                                handlePHException(zTokens);
                                /* if any data remains, we'll process later */
                                /* fall through */
                            } catch (HeaderParseException exn) {
                                logger.warn("cannot parse message header: " + exn);
                                handlePHException(zTokens);
                                /* if any data remains, we'll process later */
                                /* fall through */
                            }

                            zMsgFile = null;

                            /* if we have more data, we have body to process */
                        } else {
                            /* we don't have enough data; we need more data */
                            bDone = true;
                        }
                    } else {
                        /* transform writes body */
                        logger.debug("message body: " + buf);

                        /* scan and "copy" msg frag */
                        ByteBuffer chunkDup = ByteBuffer.allocate(buf.remaining());
                        bBodyDone = zMBScanner.processBody(buf, chunkDup);

                        /* flip to set limit; rewind doesn't set limit */
                        chunkDup.flip();
                        zTokens.add(new Chunk(chunkDup));

                        if (true == bBodyDone) {
                            logger.debug("got message end: " + buf);

                            /* note that we've excluded EOD from last chunk
                             * (we add EOD later)
                             */
                            zTokens.add(EndMarker.MARKER);

                            reset();
                        }

                        bDone = true; /* we may need more data */
                    }

                    if (false == buf.hasRemaining()) {
                        logger.debug("data buf is empty");

                        buf = null; /* buf has been consumed */
                        bDone = true;
                    } else {
                        logger.debug("compact data buf: " + buf);

                        dup.clear();
                        dup.put(buf);
                        dup.flip();

                        buf = dup;

                        if (buf.limit() == buf.capacity()) {
                            /* we can only report problem and
                             * let session terminate
                             * (we cannot trickle what we have collected and
                             *  then throw ParseException to release session)
                             */
                            logger.error("buf is full but MessageBoundaryScanner did not process (e.g., consume) any data in buf: " + buf);
                        }
                    }
                } else {
                    logger.debug("no (more) data");

                    buf = null; /* buf has been consumed */
                    bDone = true;
                }

                break;

            case HDRDATA:
                logger.debug(state + " state, " + buf);

                ByteBuffer writeDup = buf.duplicate();
                ByteBuffer hdrDataDup = ByteBuffer.allocate(buf.remaining());
                bBodyDone = zMBScanner.processBody(buf, hdrDataDup);

                /* we are initiating trickle
                 * so we must dump data that we've processed so far to file
                 * - none of transforms will write data to file
                 * - hdrDataDup contains data that we've processed
                 *
                 * we keep dumping until we find "body is done" indicator
                 * - only after we have indicator
                 *   do we trickle what we've dumped
                 */

                /* writeDup position is already set */
                writeDup.limit(hdrDataDup.position());
                writeFile(writeDup);

                if (true == bBodyDone) {
                    logger.debug("got message end: " + buf);

                    /* ready to trickle msg frag now */
                    zTokens.add(new MIMEMessageTrickleT(zMMessageT));
                    /* note that we've excluded EOD from msg frag
                     * (we add EOD later)
                     */
                    zTokens.add(EndMarker.MARKER);

                    zWrChannel = closeChannel(zWrChannel);

                    reset();
                }

                bDone = true; /* we may need more data */

                if (false == buf.hasRemaining()) {
                    logger.debug(state + " buf is empty");

                    buf = null; /* buf has been consumed */
                } else {
                    logger.debug("compact " + state + " buf: " + buf);

                    dup = buf.duplicate();
                    dup.clear();
                    dup.put(buf);
                    dup.flip();

                    buf = dup;
                }
                break;

            case SKIPDATA:
                logger.debug(state + " state, " + buf);

                ByteBuffer skipDataDup = ByteBuffer.allocate(buf.remaining());
                bBodyDone = zMBScanner.processBody(buf, skipDataDup);

                /* flip to set limit; rewind doesn't set limit */
                skipDataDup.flip();
                zTokens.add(new Chunk(skipDataDup));

                if (true == bBodyDone) {
                    logger.debug("got message end: " + buf);

                    /* note that we've excluded EOD from last chunk
                     * (we add EOD later)
                     */
                    zTokens.add(EndMarker.MARKER);

                    reset();
                }

                bDone = true; /* we may need more data */

                if (false == buf.hasRemaining()) {
                    logger.debug(state + " buf is empty");

                    buf = null; /* buf has been consumed */
                } else {
                    logger.debug("compact " + state + " buf: " + buf);

                    dup = buf.duplicate();
                    dup.clear();
                    dup.put(buf);
                    dup.flip();

                    buf = dup;
                }
                break;

            case DONOTCARE:
                /* once we enter DONOTCARE stage, we do not exit */
                logger.debug(state + " state, " + buf);

                zTokens.add(new DoNotCareChunkT(buf));

                buf = null; /* buf has been consumed */
                bDone = true;
                break;

            default:
                throw new IllegalStateException("unknown state: " + state);
            }
        }

        if (null != buf) {
            buf.position(buf.limit());
            buf.limit(buf.capacity());

            //logger.debug("reset (compacted) buf to add more data: " + buf);
        }

        logger.debug("returning ParseResult(" + zTokens + ", " + buf + ")");

        return new ParseResult(zTokens, buf);
    }

    public ParseResult parseEnd(ByteBuffer buf) throws ParseException
    {
        if (true == buf.hasRemaining()) {
            logger.warn("data trapped in read buffer: " + AsciiCharBuffer.wrap(buf) + ", " + buf);
        }

        // XXX do something?

        return new ParseResult();
    }

    // private methods --------------------------------------------------------

    private int findCRLFEnd(ByteBuffer zBuf)
    {
        /* returns 1 (if no CRLF) or greater (if CRLF found)
         * - findCrLf returns -1 if buffer contains no CRLF pair
         * - findCrLf returns absolute index of end of CRLF pair in buffer
         */
        return findCrLf(zBuf) + (1 + 1);
    }

    private void writeFile(ByteBuffer zBuf)
    {
        try {
            for (; true == zBuf.hasRemaining(); ) {
                zWrChannel.write(zBuf);
            }
        } catch (IOException exn) {
            zWrChannel = closeChannel(zWrChannel);
            zMsgFile = null;
            logger.warn("cannot write to message file: ", exn);
        }

        return;
    }

    private FileChannel closeChannel(FileChannel zChannel)
    {
        if (null == zChannel) {
            return null;
        }

        logger.debug("close message channel file");

        try {
            zChannel.force(true);
            zChannel.close();
        } catch (IOException exn) {
            logger.warn("cannot close message file: ", exn);
        }

        return null;
    }

    private void handleException(List<Token> zTokens, ByteBuffer zBuf) throws ParseException
    {
        logger.debug("parsed reply (exception): " + zBuf);

        state = State.DONOTCARE;
        logger.debug("entering " + state + " state");

        /* state machine will pull up and
         * pass through message file (whatever we have collected) and
         * do-not-care tokens (and stay in DONOTCARE stage)
         */

        if (0 < zMsgFile.length()) {
            /* if we have data, we should have already dumped it to file */
            zTokens.add(new MIMEMessageTrickleT(new MIMEMessageT(zMsgFile)));
        }

        ByteBuffer zDup = ByteBuffer.allocate(zBuf.remaining());
        zDup.put(zBuf);
        zDup.rewind();
        zTokens.add(new DoNotCareT(zDup));

        return;
    }

    private void handlePHException(List<Token> zTokens) throws ParseException
    {
        logger.debug("parsed reply (header parse exception)");

        state = State.SKIPDATA;
        logger.debug("entering " + state + " state");

        /* since header parse exception has occurred,
         * we have message file so
         * pass through message file
         *
         * if we have data, we should have already dumped it to file
         */
        zTokens.add(new MIMEMessageTrickleT(new MIMEMessageT(zMsgFile)));

        return;
    }

    private void reset()
    {
        state = State.REPLY;
        logger.debug("re-entering " + state + " state");

        bHdrDone = false;
        bBodyDone = false;

        zMBScanner.reset();
        zMMessageT = null;
        return;
    }
}

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

package com.untangle.tran.ftp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import com.untangle.mvvm.MvvmContextFactory;
import com.untangle.mvvm.tapi.Fitting;
import com.untangle.mvvm.tapi.Pipeline;
import com.untangle.mvvm.tapi.TCPSession;
import com.untangle.tran.token.AbstractParser;
import com.untangle.tran.token.Chunk;
import com.untangle.tran.token.EndMarker;
import com.untangle.tran.token.ParseException;
import com.untangle.tran.token.ParseResult;
import com.untangle.tran.token.Token;
import com.untangle.tran.token.TokenStreamer;
import com.untangle.tran.util.AsciiCharBuffer;
import org.apache.log4j.Logger;

public class FtpServerParser extends AbstractParser
{
    private static final char SP = ' ';
    private static final char HYPHEN = '-';
    private static final char CR = '\r';
    private static final char LF = '\n';

    private static final String CRLF = "\r\n";

    private final Fitting fitting;

    private final Logger logger = Logger.getLogger(FtpServerParser.class);

    FtpServerParser(TCPSession session)
    {
        super(session, false);
        lineBuffering(true);

        Pipeline p = MvvmContextFactory.context().pipelineFoundry()
            .getPipeline(session.id());
        fitting = p.getServerFitting(session.mPipe());
    }

    public ParseResult parse(ByteBuffer buf) throws ParseException
    {
        if (Fitting.FTP_CTL_STREAM == fitting) {
            return parseServerCtl(buf);
        } else if (Fitting.FTP_DATA_STREAM == fitting) {
            return parseServerData(buf);
        } else {
            throw new IllegalStateException("bad input fitting: " + fitting);
        }
    }

    public ParseResult parseEnd(ByteBuffer buf) throws ParseException
    {
        if (Fitting.FTP_DATA_STREAM == fitting) {
            List<Token> l = Arrays.asList(new Token[] { EndMarker.MARKER });
            return new ParseResult(l, null);
        } else {
            if (buf.hasRemaining()) {
                logger.warn("unread data in read buffer: " + buf.remaining());
            }
            return new ParseResult();
        }
    }

    public TokenStreamer endSession() { return null; }

    // private methods --------------------------------------------------------

    private ParseResult parseServerCtl(ByteBuffer buf) throws ParseException
    {
        ByteBuffer dup = buf.duplicate();

        if (completeLine(dup)) {
            int replyCode = replyCode(dup);

            if (-1 == replyCode) {
                throw new ParseException("expected reply code");
            }

            switch (dup.get()) {
            case SP: {
                String message = AsciiCharBuffer.wrap(buf).toString();

                FtpReply reply = new FtpReply(replyCode, message);
                List<Token> l = Arrays.asList(new Token[] { reply });

                return new ParseResult(l, null);
            }

            case HYPHEN: {
                int i = dup.limit() - 2;
                while (3 < --i && LF != dup.get(i));

                if (LF != dup.get(i++)) {
                    break;
                }

                ByteBuffer end = dup.duplicate();
                end.position(i);
                end.limit(end.limit() - 2);
                int endCode = replyCode(end);

                if (-1 == endCode || SP != end.get()) {
                    break;
                }

                String message = AsciiCharBuffer.wrap(buf).toString();

                FtpReply reply = new FtpReply(replyCode, message);
                List<Token> l = Arrays.asList(new Token[] { reply });
                return new ParseResult(l, null);
            }

            default:
                throw new ParseException("expected a space");
            }
        }

        // incomplete input
        if (buf.limit() + 80 > buf.capacity()) {
            ByteBuffer b = ByteBuffer.allocate(2 * buf.capacity());
            b.put(buf);
            buf = b;
        } else {
            buf.compact();
        }
        return new ParseResult(buf);
    }

    private ParseResult parseServerData(ByteBuffer buf) throws ParseException
    {
        Chunk c = new Chunk(buf.duplicate());
        List<Token> l = Arrays.asList(new Token[] { c });
        return new ParseResult(l, null);
    }

    private int replyCode(ByteBuffer buf)
    {
        int i = 0;

        byte c = buf.get();
        if (48 <= c && 57 >= c) {
            i = (c - 48) * 100;
        } else {
            return -1;
        }

        c = buf.get();
        if (48 <= c && 57 >= c) {
            i += (c - 48) * 10;
        } else {
            return -1;
        }

        c = buf.get();
        if (48 <= c && 57 >= c) {
            i += (c - 48);
        } else {
            return -1;
        }

        return i;
    }

    /**
     * Checks if the buffer contains a complete line.
     *
     * @param buf to check.
     * @return true if a complete line.
     */
    private boolean completeLine(ByteBuffer buf)
    {
        int l = buf.limit();
        return buf.remaining() >= 2 && buf.get(l - 2) == CR
            && buf.get(l - 1) == LF;
    }
}

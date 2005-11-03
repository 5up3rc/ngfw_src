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

package com.metavize.tran.virus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Iterator;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.tapi.TCPSession;
import com.metavize.mvvm.tran.MimeTypeRule;
import com.metavize.mvvm.tran.StringRule;
import com.metavize.mvvm.tran.Transform;
import com.metavize.tran.http.HttpMethod;
import com.metavize.tran.http.HttpStateMachine;
import com.metavize.tran.http.RequestLine;
import com.metavize.tran.http.StatusLine;
import com.metavize.tran.token.Chunk;
import com.metavize.tran.token.EndMarker;
import com.metavize.tran.token.FileChunkStreamer;
import com.metavize.tran.token.Header;
import com.metavize.tran.token.Token;
import com.metavize.tran.token.TokenException;
import com.metavize.tran.util.TempFileFactory;
import org.apache.log4j.Logger;

class VirusHttpHandler extends HttpStateMachine
{
    // make configurable
    private static final int TIMEOUT = 30000;
    private static final int SIZE_LIMIT = 256000;
    private static final int MAX_SCAN_LIMIT = 200000000;

    private static final String BLOCK_MESSAGE
        = "<HTML><HEAD>"
        + "<TITLE>403 Forbidden</TITLE>"
        + "</HEAD><BODY>"
        + "<center><b>%s AntiVirus Scanner</b></center>"
        + "<p>This site blocked because it contained a virus</p>"
        + "<p>Host: %s</p>"
        + "<p>URI: %s</p>"
        + "<p>Please contact your network administrator</p>"
        + "<HR>"
        + "<ADDRESS>Metavize EdgeGuard</ADDRESS>"
        + "</BODY></HTML>";

    private static final int SCAN_COUNTER  = Transform.GENERIC_0_COUNTER;
    private static final int BLOCK_COUNTER = Transform.GENERIC_1_COUNTER;
    private static final int PASS_COUNTER  = Transform.GENERIC_2_COUNTER;

    private static final Logger logger = Logger
        .getLogger(VirusHttpHandler.class);
    private static final Logger eventLogger = MvvmContextFactory
        .context().eventLogger();

    private final String vendor;
    private final VirusTransformImpl transform;

    private boolean scan;
    private long bufferingStart;
    private boolean buffering;
    private int outstanding;
    private int totalSize;
    private String extension;
    private String fileName;
    private FileChannel outFile;
    private FileChannel inFile;
    private File file;

    // constructors -----------------------------------------------------------

    VirusHttpHandler(TCPSession session, VirusTransformImpl transform)
    {
        super(session);

        this.transform = transform;
        this.vendor = transform.getScanner().getVendorName();
    }

    // HttpStateMachine methods -----------------------------------------------

    @Override
    protected RequestLine doRequestLine(RequestLine requestLine)
    {
        this.scan = false;
        String path = requestLine.getRequestUri().getPath();

        int i = path.lastIndexOf('.');
        extension = (0 <= i && path.length() - 1 > i)
            ? path.substring(i + 1) : null;

        releaseRequest();
        return requestLine;
    }

    @Override
    protected Header doRequestHeader(Header requestHeader)
    {
        logger.debug("got a request header");

        requestHeader.removeField("range");

        return requestHeader;
    }

    @Override
    protected Chunk doRequestBody(Chunk chunk)
    {
        return chunk;
    }

    @Override
    protected void doRequestBodyEnd() { }

    @Override
    protected StatusLine doStatusLine(StatusLine statusLine)
    {
        return statusLine;
    }

    @Override
    protected Header doResponseHeader(Header header)
    {
        logger.debug("doing response header");

        String reason = "";

        RequestLine rl = getResponseRequest();

        if (null == rl || HttpMethod.HEAD == rl.getMethod()) {
            logger.debug("CONTINUE or HEAD");
        } else if (matchesExtension(extension)) {
            logger.debug("matches extension");
            reason = extension;
            this.scan = true;
        } else {
            logger.debug("else...");
            String mimeType = header.getValue("content-type");
            logger.debug("content-type: " + mimeType);
            this.scan = matchesMimeType(mimeType);
            logger.debug("matches mime-type: " + scan);
            reason = mimeType;
        }

        if (scan) {
            buffering = true;
            bufferingStart = System.currentTimeMillis();
            outstanding = 0;
            totalSize = 0;
            setupFile(reason);
        } else {
            header.replaceField("accept-ranges", "none");
            releaseResponse();
        }

        return header;
    }

    @Override
    protected Chunk doResponseBody(Chunk chunk) throws TokenException
    {
        return scan ? bufferOrTrickle(chunk) : chunk;
    }

    @Override
    protected void doResponseBodyEnd()
    {
        if (scan) {
            try {
                outFile.close();
            } catch (IOException exn) {
                logger.warn("could not close channel", exn);
            }
            scanFile();
        }
    }

    // private methods --------------------------------------------------------

    private void scanFile()
    {
        VirusScannerResult result;
        try {
            logger.debug("Scanning the file: " + fileName);
            transform.incrementCount(SCAN_COUNTER);
            result = transform.getScanner().scanFile(fileName);
        } catch (Exception e) {
            // Should never happen
            logger.error("Virus scan failed: "+ e);
            result = VirusScannerResult.ERROR;
        }

        if (result == null) {
            // Should never happen
            logger.error("Virus scan failed: null");
            result = VirusScannerResult.ERROR;
        }

        eventLogger.info(new VirusHttpEvent(getResponseRequest(), result,  vendor));

        if (result.isClean()) {
            transform.incrementCount(PASS_COUNTER, 1);

            if (result.isVirusCleaned()) {
                logger.info("Cleaned infected file");
            } else {
                logger.info("Clean");
            }

            if (buffering) {
                releaseResponse();
            } else {
                preStream(new FileChunkStreamer(file, inFile, null, null, false));
            }

        } else {
            logger.info("Virus found, killing session");
            // Todo: Quarantine (for now, don't delete the file) XXX
            transform.incrementCount(BLOCK_COUNTER, 1);

            if (buffering) {
                blockResponse(blockMessage());
            } else {
                TCPSession s = getSession();
                s.shutdownClient();
                s.shutdownServer();
                s.release();
            }
        }
    }

    private Token[] blockMessage()
    {
        StatusLine sl = new StatusLine("HTTP/1.1", 403, "Forbidden");

        RequestLine rl = getResponseRequest();
        String uri = null != rl ? rl.getRequestUri().toString() : "";
        String host = getResponseHost();

        String message = String.format(BLOCK_MESSAGE, vendor, host, uri);

        Header h = new Header();
        h.addField("Content-Length", Integer.toString(message.length()));
        h.addField("Content-Type", "text/html");
        h.addField("Connection", isResponsePersistent() ? "Keep-Alive" : "Close");

        ByteBuffer buf = ByteBuffer.allocate(message.length());
        buf.put(message.getBytes());
        buf.flip();
        Chunk c = new Chunk(buf);

        return new Token[] { sl, h, c, EndMarker.MARKER };
    }

    private boolean matchesExtension(String extension)
    {
        if (null == extension) { return false; }

        for (Iterator i = transform.getExtensions().iterator();
             i.hasNext();) {
            StringRule sr = (StringRule)i.next();
            if (sr.isLive() && sr.getString().equalsIgnoreCase(extension)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesMimeType(String mimeType)
    {
        int longestMatch = 0;
        boolean isLive = false;
        String match = "";

        if (null == mimeType) {
            return false;
        }

        /*
         * XXX This is inefficient, but typically there are only a few
         * rules in this list.
         */
        for (Iterator i = transform.getHttpMimeTypes().iterator(); i.hasNext();) {

            MimeTypeRule mtr = (MimeTypeRule)i.next();
            String currentMt = mtr.getMimeType().getType();

            /* Skip all of the shorter or equal mimetypes */
            if (currentMt.length() <= longestMatch) {
                continue;
            }

            if (mtr.getMimeType().matches(mimeType)) {
                /* Exact match, break */
                if (currentMt.length() == mimeType.length()) {
                    isLive = mtr.isLive();
                    match = currentMt;
                    break;
                }

                /* This must be a wildcard match, don't include the
                 * '*' in the length of the match
                 */
                longestMatch = currentMt.length() - 1;
                isLive = mtr.isLive();
                match = currentMt;
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Mapped: " + mimeType + " to: '" + match
                         + "' scan: "+ isLive);
        }

        return isLive;
    }

    private void setupFile(String reason)
    {
        logger.info("VIRUS: Scanning because of: " + reason);
        try {
            TempFileFactory tff = new TempFileFactory(getPipeline());
            File fileBuf = tff.createFile("http-virus");

            this.fileName = fileBuf.getAbsolutePath();

            if (logger.isDebugEnabled()) {
                logger.debug("VIRUS: Using temporary file: " + this.fileName);
            }

            this.outFile = (new FileOutputStream(fileBuf)).getChannel();
            this.inFile = (new FileInputStream(fileBuf)).getChannel();
            this.file = fileBuf;
            this.scan = true;
        } catch (IOException e) {
            logger.warn("Unable to create temporary file: " + e);
            this.scan = false;
        }
    }

    private Chunk bufferOrTrickle(Chunk chunk) throws TokenException
    {
        ByteBuffer buf = chunk.getData();

        try {
            for (ByteBuffer bb = buf.duplicate(); bb.hasRemaining(); outFile.write(bb));
        } catch (IOException e) {
            logger.warn("Unable to write to buffer file: " + e);
            throw new TokenException(e);
        }

        outstanding += buf.remaining();
        totalSize += buf.remaining();

        if (buffering) {
            buffering = TIMEOUT > (System.currentTimeMillis() - bufferingStart)
                && SIZE_LIMIT > totalSize;
            if (buffering) {    /* remain in buffering mode */
                logger.debug("buffering");
                return chunk;
            } else {            /* switch to trickle mode */
                logger.debug("switching to trickling");
                try {
                    inFile.position(outstanding);
                } catch (IOException exn) {
                    logger.warn("could not change file pointer", exn);
                }
                outstanding = 0;
                releaseResponse();
                return chunk;
            }
        } else {                /* stay in trickle mode */
            logger.debug("trickling");
            if (MAX_SCAN_LIMIT < totalSize) {
                logger.debug("MAX_SCAN_LIMIT exceeded, not scanning");
                scan = false;
                FileChunkStreamer streamer = new FileChunkStreamer
                    (file, inFile, null, EndMarker.MARKER, false);
                preStream(streamer);

                return Chunk.EMPTY;
            } else {
                logger.debug("continuing to trickle: " + totalSize);
                Chunk c = trickle();
                return c;
            }
        }
    }

    private Chunk trickle() throws TokenException
    {
        logger.debug("handleTokenTrickle()");

        int tricklePercent = transform.getTricklePercent();
        int trickleLen = (outstanding * tricklePercent) / 100;
        ByteBuffer inbuf = ByteBuffer.allocate(trickleLen);

        inbuf.limit(trickleLen);

        try {
            for (; inbuf.hasRemaining(); inFile.read(inbuf));
        } catch (IOException e) {
            logger.warn("Unable to read from buffer file: " + e);
            throw new TokenException(e);
        }

        inbuf.flip();
        outstanding = 0;

        return new Chunk(inbuf);
    }

    private boolean isPersistent(Header header)
    {
        String con = header.getValue("connection");
        return null == con ? false : con.equalsIgnoreCase("keep-alive");
    }
}

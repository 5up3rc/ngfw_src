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

package com.untangle.node.virus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.untangle.uvm.tapi.Pipeline;
import com.untangle.uvm.tapi.TCPSession;
import com.untangle.uvm.tapi.event.TCPStreamer;
import com.untangle.uvm.node.Node;
import com.untangle.node.ftp.FtpCommand;
import com.untangle.node.ftp.FtpFunction;
import com.untangle.node.ftp.FtpReply;
import com.untangle.node.ftp.FtpStateMachine;
import com.untangle.node.token.Chunk;
import com.untangle.node.token.EndMarker;
import com.untangle.node.token.FileChunkStreamer;
import com.untangle.node.token.Token;
import com.untangle.node.token.TokenException;
import com.untangle.node.token.TokenResult;
import com.untangle.node.token.TokenStreamer;
import com.untangle.node.token.TokenStreamerAdaptor;
import com.untangle.node.util.TempFileFactory;
import org.apache.log4j.Logger;

class VirusFtpHandler extends FtpStateMachine
{
    /* XXX Should be from the same place as the HTTP constants */
    private static final int SCAN_COUNTER  = Node.GENERIC_0_COUNTER;
    private static final int BLOCK_COUNTER = Node.GENERIC_1_COUNTER;
    private static final int PASS_COUNTER  = Node.GENERIC_2_COUNTER;


    private final VirusNodeImpl node;
    private final boolean scanClient;
    private final boolean scanServer;

    private final Logger logger = Logger.getLogger(FtpStateMachine.class);

    private File file;
    private FileChannel inChannel;
    private FileChannel outChannel;
    private boolean c2s;
    private final TempFileFactory m_fileFactory;

    // constructors -----------------------------------------------------------

    VirusFtpHandler(TCPSession session, VirusNodeImpl node)
    {
        super(session);

        this.node = node;

        VirusSettings vs = node.getVirusSettings();

        if (!session.isInbound()) { // outgoing
            scanClient = vs.getFtpOutbound().getScan();
            scanServer = vs.getFtpInbound().getScan();
        } else {
            scanClient = vs.getFtpInbound().getScan();
            scanServer = vs.getFtpOutbound().getScan();
        }

        m_fileFactory = new TempFileFactory(getPipeline());
    }

    // FtpStateMachine methods ------------------------------------------------

    @Override
    protected TokenResult doClientData(Chunk c) throws TokenException
    {
        if (scanClient) {
            logger.debug("doServerData()");

            if (null == file) {
                logger.debug("creating file for client");
                createFile();
                c2s = true;
            }

            Chunk outChunk = trickle(c.getData());

            return new TokenResult(null, new Token[] { outChunk });
        } else {
            return new TokenResult(null, new Token[] { c });
        }
    }

    @Override
    protected TokenResult doServerData(Chunk c) throws TokenException
    {
        if (scanServer) {
            logger.debug("doServerData()");

            if (null == file) {
                logger.debug("creating file for server");
                createFile();
                c2s = false;
            }

            Chunk outChunk = trickle(c.getData());

            return new TokenResult(new Token[] { outChunk }, null);
        } else {
            return new TokenResult(new Token[] { c }, null);
        }
    }

    @Override
    protected void doClientDataEnd() throws TokenException
    {
        logger.debug("doClientDataEnd()");

        if (scanClient && c2s && null != file) {
            try {
                outChannel.close();
            } catch (IOException exn) {
                logger.warn("could not close out channel");
            }

            if (logger.isDebugEnabled()) {
                logger.debug("c2s file: " + file);
            }
            TCPStreamer ts = scan();
            if (null != ts) {
                getSession().beginServerStream(ts);
            }
            file = null;
        } else {
            getSession().shutdownServer();
        }
    }

    @Override
    protected void doServerDataEnd() throws TokenException
    {
        logger.debug("doServerDataEnd()");

        if (scanServer && !c2s && null != file) {
            try {
                outChannel.close();
            } catch (IOException exn) {
                logger.warn("could not close out channel", exn);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("!c2s file: " + file);
            }
            TCPStreamer ts = scan();
            if (null != ts) {
                getSession().beginClientStream(ts);
            }
            file = null;
        } else {
            getSession().shutdownClient();
        }
    }

    @Override
    protected TokenResult doCommand(FtpCommand command) throws TokenException
    {
        if (FtpFunction.REST == command.getFunction()
            && node.getFtpDisableResume()) {
            FtpReply reply = FtpReply.makeReply(502, "Command not implemented.");
            return new TokenResult(new Token[] { reply }, null);
        } else {
            return new TokenResult(null, new Token[] { command });
        }
    }

    // private methods --------------------------------------------------------

    private Chunk trickle(ByteBuffer b) throws TokenException
    {
        int l = b.remaining() * node.getTricklePercent() / 100;

        try {
            while (b.hasRemaining()) {
                outChannel.write(b);
            }

            b.clear().limit(l);

            while (b.hasRemaining()) {
                inChannel.read(b);
            }
        } catch (IOException exn) {
            throw new TokenException("could not trickle", exn);
        }

        b.flip();

        return new Chunk(b);
    }

    private TCPStreamer scan() throws TokenException
    {
        VirusScannerResult result;

        try {
            node.incrementCount(SCAN_COUNTER);
            result = node.getScanner().scanFile(file);
        } catch (Exception exn) {
            // Should never happen
            throw new TokenException("could not scan TokenException", exn);
        }

        /* XXX handle the case where result is null */

        node.log(new VirusLogEvent(getSession().pipelineEndpoints(), result, node.getScanner().getVendorName()));

        if (result.isClean()) {
            node.incrementCount(PASS_COUNTER);
            Pipeline p = getPipeline();
            TokenStreamer tokSt = new FileChunkStreamer
                (file, inChannel, null, EndMarker.MARKER, true);
            return new TokenStreamerAdaptor(p, tokSt);
        } else {
            node.incrementCount(BLOCK_COUNTER);
            // Todo: Quarantine (for now, don't delete the file) XXX
            TCPSession s = getSession();
            s.shutdownClient();
            s.shutdownServer();
            return null;
        }
    }

    private void createFile() throws TokenException
    {
        try {
            file = m_fileFactory.createFile("ftp-virus");

            FileInputStream fis = new FileInputStream(file);
            inChannel = fis.getChannel();

            FileOutputStream fos = new FileOutputStream(file);
            outChannel = fos.getChannel();

        } catch (IOException exn) {
            throw new TokenException("could not create tmp file");
        }
    }
}

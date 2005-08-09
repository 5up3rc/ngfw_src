/*
 * Copyright (c) 2003-2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id$
 */

package com.metavize.mvvm.tapi.impl;

import java.nio.ByteBuffer;

import com.metavize.jvector.Crumb;
import com.metavize.jvector.DataCrumb;
import com.metavize.jvector.IncomingSocketQueue;
import com.metavize.jvector.OutgoingSocketQueue;
import com.metavize.jvector.ResetCrumb;
import com.metavize.jvector.ShutdownCrumb;
import com.metavize.mvvm.tapi.*;
import com.metavize.mvvm.tapi.client.TCPSessionDescImpl;
import com.metavize.mvvm.tapi.event.*;
import com.metavize.mvvm.tran.MutateTStats;
import com.metavize.mvvm.util.MetaEnv;

class TCPSessionImpl extends IPSessionImpl implements TCPSession
{
    protected static final ByteBuffer SHUTDOWN_COOKIE_BUF = ByteBuffer.allocate(1);

    private static final ByteBuffer EMPTY_BUF = ByteBuffer.allocate(0);

    protected int[] readLimit;
    protected int[] readBufferSize;
    protected boolean[] lineBuffering = new boolean[] { false, false };
    protected ByteBuffer[] readBuf = new ByteBuffer[] { null, null };

    protected TCPSessionImpl(Dispatcher disp,
                             com.metavize.mvvm.argon.TCPSession pSession,
                             int clientReadBufferSize,
                             int serverReadBufferSize)
    {
        super(disp, pSession);

        if (clientReadBufferSize < 2 || clientReadBufferSize > TCP_MAX_CHUNK_SIZE)
            throw new IllegalArgumentException("Illegal maximum client read bufferSize: " + clientReadBufferSize);
        if (serverReadBufferSize < 2 || serverReadBufferSize > TCP_MAX_CHUNK_SIZE)
            throw new IllegalArgumentException("Illegal maximum server read bufferSize: " + serverReadBufferSize);
        this.readBufferSize = new int[] { clientReadBufferSize, serverReadBufferSize };
        this.readLimit = new int[] { clientReadBufferSize, serverReadBufferSize };
        logger = disp.mPipe().sessionLoggerTCP();
    }

    public int serverReadBufferSize() {
        return readBufferSize[SERVER];
    }
    public void serverReadBufferSize(int numBytes) {
        if (numBytes < 2 || numBytes > TCP_MAX_CHUNK_SIZE)
            throw new IllegalArgumentException("Illegal maximum read bufferSize: " + numBytes);
        readBufferSize[SERVER] = numBytes;
    }

    public int clientReadBufferSize() {
        return readBufferSize[CLIENT];
    }
    public void clientReadBufferSize(int numBytes) {
        if (numBytes < 2 || numBytes > TCP_MAX_CHUNK_SIZE)
            throw new IllegalArgumentException("Illegal maximum read bufferSize: " + numBytes);
        readBufferSize[CLIENT] = numBytes;
    }

    public int serverReadLimit() {
        return readLimit[SERVER];
    }
    public void serverReadLimit(int numBytes) {
        if (numBytes > readBufferSize[SERVER])
            numBytes = readBufferSize[SERVER];
        if (numBytes < 1 || numBytes > TCP_MAX_CHUNK_SIZE)
            throw new IllegalArgumentException("Illegal maximum read limit: " + numBytes);
        readLimit[SERVER] = numBytes;
    }

    public int clientReadLimit() {
        return readLimit[CLIENT];
    }
    public void clientReadLimit(int numBytes) {
        if (numBytes > readBufferSize[CLIENT])
            numBytes = readBufferSize[CLIENT];
        if (numBytes < 1 || numBytes > TCP_MAX_CHUNK_SIZE)
            throw new IllegalArgumentException("Illegal maximum read limit: " + numBytes);
        readLimit[CLIENT] = numBytes;
    }

    public void clientLineBuffering(boolean oneLine)
    {
        lineBuffering[CLIENT] = oneLine;
    }

    public void serverLineBuffering(boolean oneLine)
    {
        lineBuffering[SERVER] = oneLine;
    }

    public void release()
    {
        // Since we're releasing, make things as fast as possible.
        clientLineBuffering(false);
        serverLineBuffering(false);
        readBufferSize[CLIENT] = 8192; // XXX
        readBufferSize[SERVER] = 8192; // XXX
        readLimit[CLIENT] = readBufferSize[CLIENT];
        readLimit[SERVER] = readBufferSize[SERVER];
        super.release();
    }

    public IPSessionDesc makeDesc()
    {
        return new TCPSessionDescImpl(id(), new SessionStats(stats),
                                      clientState(), serverState(), clientIntf(), serverIntf(),
                                      clientAddr(), serverAddr(), clientPort(), serverPort());
    }

    public byte clientState()
    {
        if (((com.metavize.mvvm.argon.Session)pSession).clientIncomingSocketQueue() == null)
            if (((com.metavize.mvvm.argon.Session)pSession).clientOutgoingSocketQueue() == null)
                return IPSessionDesc.CLOSED;
            else
                return TCPSessionDesc.HALF_OPEN_OUTPUT;
        else
            if (((com.metavize.mvvm.argon.Session)pSession).clientOutgoingSocketQueue() == null)
                return TCPSessionDesc.HALF_OPEN_INPUT;
            else
                return IPSessionDesc.OPEN;
    }

    public byte serverState()
    {
        if (((com.metavize.mvvm.argon.Session)pSession).serverIncomingSocketQueue() == null)
            if (((com.metavize.mvvm.argon.Session)pSession).serverOutgoingSocketQueue() == null)
                return IPSessionDesc.CLOSED;
            else
                return TCPSessionDesc.HALF_OPEN_OUTPUT;
        else
            if (((com.metavize.mvvm.argon.Session)pSession).serverOutgoingSocketQueue() == null)
                return TCPSessionDesc.HALF_OPEN_INPUT;
            else
                return IPSessionDesc.OPEN;
    }

    public void shutdownServer()
    {
        shutdownSide(SERVER, ((com.metavize.mvvm.argon.Session)pSession).serverOutgoingSocketQueue(), false);
    }

    public void shutdownServer(boolean force)
    {
        shutdownSide(SERVER, ((com.metavize.mvvm.argon.Session)pSession).serverOutgoingSocketQueue(), force);
    }

    public void shutdownClient()
    {
        shutdownSide(CLIENT, ((com.metavize.mvvm.argon.Session)pSession).clientOutgoingSocketQueue(), false);
    }

    public void shutdownClient(boolean force)
    {
        shutdownSide(CLIENT, ((com.metavize.mvvm.argon.Session)pSession).clientOutgoingSocketQueue(), force);
    }

    private void shutdownSide(int side, OutgoingSocketQueue out, boolean force)
    {
        if (out != null) {
            if (crumbs2write[side] != null && !force) {
                // Indicate the need to shutdown
                addCrumb(side, ShutdownCrumb.getInstance(force));
                // we get handled later automatically by tryWrite()
                return;
            }
            Crumb crumb = ShutdownCrumb.getInstance(force);
            boolean success = out.write(crumb);
            assert success;
        }
    }

    public void resetServer()
    {
        // Go ahead and write out the reset.
        OutgoingSocketQueue oursout = ((com.metavize.mvvm.argon.Session)pSession).serverOutgoingSocketQueue();
        IncomingSocketQueue oursin  = ((com.metavize.mvvm.argon.Session)pSession).serverIncomingSocketQueue();
        if (oursout != null) {
            Crumb crumb = ResetCrumb.getInstance();
            boolean success = oursout.write(crumb);
            assert success;
        }

        // Reset the incoming socket queue
        if ( oursin != null )
            oursin.reset();

        // Will result in server's outgoing and incoming socket queue being set to null in pSession.
    }

    public void resetClient()
    {
        // Go ahead and write out the reset.
        OutgoingSocketQueue ourcout = ((com.metavize.mvvm.argon.Session)pSession).clientOutgoingSocketQueue();
        IncomingSocketQueue ourcin  = ((com.metavize.mvvm.argon.Session)pSession).clientIncomingSocketQueue();

        if (ourcout != null) {
            Crumb crumb = ResetCrumb.getInstance();
            boolean success = ourcout.write(crumb);
            assert success;
        }

        if ( ourcin != null )
            ourcin.reset();

        // Will result in client's outgoing and incoming socket queue being set to null in pSession.
    }

    public void beginClientStream(TCPStreamer streamer)
    {
        beginStream(CLIENT, streamer);
    }

    public void beginServerStream(TCPStreamer streamer)
    {
        beginStream(SERVER, streamer);
    }

    protected void beginStream(int side, TCPStreamer s)
    {
        if (streamer != null) {
            String message = "Already streaming";
            error(message);
            throw new IllegalArgumentException(message);
        }

        if (side == CLIENT)
            streamer = new TCPStreamer[] { s, null };
        else
            streamer = new TCPStreamer[] { null, s };
    }

    protected void endStream()
    {
        IPStreamer cs = streamer[CLIENT];
        IPStreamer ss = streamer[SERVER];

        if (cs != null) {
            if (cs.closeWhenDone())
                shutdownClient();
        } else if (ss != null) {
            if (ss.closeWhenDone())
                shutdownServer();
        }
        streamer = null;
    }


    protected boolean sideDieing(int side, IncomingSocketQueue in)
        throws MPipeException
    {
        if (in.containsReset()) {
            sendRSTEvent(side);
            return true;
        }
        return false;
    }

    void tryWrite(int side, OutgoingSocketQueue out, boolean warnIfUnable)
        throws MPipeException
    {
        String sideName = (side == CLIENT ? "client" : "server");
        assert out != null;
        if (out.isFull()) {
            if (warnIfUnable)
                warn("tryWrite to full outgoing queue");
            else
                debug("tryWrite to full outgoing queue");
        } else {
            // Old busted comment:
	    // We know it's a data crumb since there can be nothing else
	    // enqueued for TCP.  
            // New hotness comment:
            // It can be a shutdown crumb as well as a data crumb.
            Crumb crumb2send = getNextCrumb2Send(side);
            assert crumb2send != null;
            int numWritten = sendCrumb(crumb2send, out);
            if (RWSessionStats.DoDetailedTimes) {
                long[] times = stats.times();
                if (times[SessionStats.FIRST_BYTE_WROTE_TO_CLIENT + side] == 0)
                    times[SessionStats.FIRST_BYTE_WROTE_TO_CLIENT + side] = MetaEnv.currentTimeMillis();
            }
            mPipe.lastSessionWriteFailed(false);
            stats.wroteData(side, numWritten);
            MutateTStats.wroteData(side, this, numWritten);
            if (logger.isDebugEnabled())
                debug("wrote " + numWritten + " to " + sideName);
        }
    }

    void addStreamBuf(int side, IPStreamer ipStreamer)
        throws MPipeException
    {
        TCPStreamer streamer = (TCPStreamer)ipStreamer;

        String sideName = (side == CLIENT ? "client" : "server");

        ByteBuffer buf2send = streamer.nextChunk();
        if (buf2send == null) {
            debug("end of stream");
            endStream();
            return;
        }

        addBuf(side, buf2send);

        if (logger.isDebugEnabled())
            debug("streamed " + buf2send.remaining() + " to " + sideName);
    }

    private void addBufs(int side, ByteBuffer[] new2send)
    {
        if (new2send == null || new2send.length == 0)
            return;
        for (int i = 0; i < new2send.length; i++)
            addBuf(side, new2send[i]);
    }

    private void addBuf(int side, ByteBuffer buf2send)
    {
        byte[] array;
        int offset = buf2send.position();
	int limit = buf2send.remaining();
        if (limit <= 0) {
            if (logger.isInfoEnabled())
                info("ignoring empty send to " + (side == CLIENT ? "client" : "server") + ", pos: " +
                     buf2send.position() + ", rem: " + buf2send.remaining() + ", ao: " +
                     buf2send.arrayOffset());
            return;
        }
            
        if (buf2send.hasArray()) {
            array = buf2send.array();
            offset += buf2send.arrayOffset();
            limit += buf2send.arrayOffset();
        } else {
            warn("out-of-heap byte buffer, had to copy");
            array = new byte[buf2send.remaining()];
            buf2send.get(array);
            buf2send.position(offset);
            offset = 0;
        }
        DataCrumb crumb = new DataCrumb(array, offset, limit);
        addCrumb(side, crumb);
    }

    protected void sendWritableEvent(int side)
        throws MPipeException
    {
        TCPSessionEvent wevent = new TCPSessionEvent(mPipe, this);
        IPDataResult result = side == CLIENT ? dispatcher.dispatchTCPClientWritable(wevent)
            : dispatcher.dispatchTCPServerWritable(wevent);
        if (result == IPDataResult.SEND_NOTHING)
            // Optimization
            return;
        if (result.readBuffer() != null)
            // Not allowed
            warn("Ignoring readBuffer returned from writable event");
        addBufs(CLIENT, result.bufsToClient());
        addBufs(SERVER, result.bufsToServer());
    }

    protected void sendFINEvent(int side, ByteBuffer existingReadBuf)
        throws MPipeException
    {
        // First give the transform a chance to do something...
        IPDataResult result;
        ByteBuffer dataBuf = existingReadBuf != null ? existingReadBuf : EMPTY_BUF;
        TCPChunkEvent cevent = new TCPChunkEvent(mPipe, this, dataBuf);
        if (side == CLIENT)
            result = dispatcher.dispatchTCPClientDataEnd(cevent);
        else
            result = dispatcher.dispatchTCPServerDataEnd(cevent);

        if (result != null) {
            if (result.readBuffer() != null)
                // Not allowed
                warn("Ignoring readBuffer returned from FIN event");
            addBufs(CLIENT, result.bufsToClient());
            addBufs(SERVER, result.bufsToServer());
        }

        // Then run the FIN handler.  This will send the FIN along to the other side by default.
        TCPSessionEvent wevent = new TCPSessionEvent(mPipe, this);
        if (side == CLIENT)
            dispatcher.dispatchTCPClientFIN(wevent);
        else
            dispatcher.dispatchTCPServerFIN(wevent);
    }

    protected void sendRSTEvent(int side)
        throws MPipeException
    {
        TCPSessionEvent wevent = new TCPSessionEvent(mPipe, this);
        if (side == CLIENT)
            dispatcher.dispatchTCPClientRST(wevent);
        else
            dispatcher.dispatchTCPServerRST(wevent);
    }

    void tryRead(int side, IncomingSocketQueue in, boolean warnIfUnable)
        throws MPipeException
    {
        tryReadInt(side, in, warnIfUnable);
    }

    // Handles the actual reading from the client
    int tryReadInt(int side, IncomingSocketQueue in, boolean warnIfUnable)
        throws MPipeException
    {
        int numRead = 0;
        boolean gotLine = false;
        String sideName = (side == CLIENT ? "client" : "server");

        if (readBuf[side] == null) {
            // Defer the creation of the buffer until we are sure we need it.
        } else if (!readBuf[side].hasRemaining()) {
            // This isn't really supposed to happen XXX
            // We need to kill the session to keep it from spinning.
            error("read with full read buffer (" + readBuf[side].position() + "," +
                  readBuf[side].limit() + "," + readBuf[side].capacity() +
                  ", killing session");
            readBuf[side] = null;
            killSession("full read buffer on " + sideName);
            return numRead;
        }

        boolean lineMode = lineBuffering[side];
        // Currently we have no special handling for \r. XXX

        if (!lineMode || !gotLine) {
            assert in != null;
            if (in.isEmpty()) {
                if (warnIfUnable)
                    warn("tryRead from empty incoming queue");
                else
                    debug("tryRead from empty incoming queue");
                return numRead;
            }

            Crumb crumb = in.peek();
            if (RWSessionStats.DoDetailedTimes) {
                long[] times = stats.times();
                if (times[SessionStats.FIRST_BYTE_READ_FROM_CLIENT + side] == 0)
                    times[SessionStats.FIRST_BYTE_READ_FROM_CLIENT + side] = MetaEnv.currentTimeMillis();
            }
            switch (crumb.type()) {
            case Crumb.TYPE_SHUTDOWN:
                debug("read FIN");
                sendFINEvent(side, readBuf[side]);
                in.read();
                readBuf[side] = null;
                return numRead;
            case Crumb.TYPE_RESET:
            default:
                // Should never happen.
                debug("read crumb " + crumb.type());
                in.read();
                assert false;
                break;
            case Crumb.TYPE_DATA:
                break;
            }
            // Wrap a byte buffer around the data.
            DataCrumb dc = (DataCrumb)crumb;
            byte[] dcdata = dc.data();
            int dclimit = dc.limit();
            int dccap = dcdata.length;
            int dcoffset = dc.offset();
            int dcsize = dclimit - dcoffset;

            if (dcoffset >= dclimit) {
                warn("Zero length TCP crumb read");
                in.read();  // Consume the crumb
                return numRead;
            } else if (readBuf[side] == null &&
                       (dcoffset != 0 || dcsize > readLimit[side] || dccap < readLimit[side] || lineMode)) {
                if (logger.isDebugEnabled()) {
                    if (dcoffset != 0)
                        logger.debug("Creating readbuf because dcoffset = " + dcoffset);
                    else if (dcsize > readLimit[side])
                        logger.debug("Creating readbuf because dcsize = " + dcsize + " but readLimit = " +
                                     readLimit[side]);
                    else if (dccap < readLimit[side])
                        logger.debug("Creating readbuf because dccap = " + dccap + " but readLimit = " +
                                     readLimit[side]);
                    else if (lineMode)
                        logger.debug("Creating readbuf because lineMode");
                }
                readBuf[side] = ByteBuffer.allocate(readBufferSize[side]);
                readBuf[side].limit(readLimit[side]);
            }
            if (readBuf[side] != null) {
                logger.debug("putting into existing readbuf");
                // We have to put the crumb into the buffer, using the overflow if necessary.
                int s = dcsize;
                if (s > readBuf[side].remaining())
                    s = readBuf[side].remaining();
                int i = 0;
                if (lineMode) {
                    // Have to do the copy one char at a time.
                    while (i < s) {
                        byte c = dcdata[dcoffset + i++];
                        numRead++;
                        readBuf[side].put(c);
                        if (c == '\n')
                            break;
                    }
                } else {
                    readBuf[side].put(dcdata, dcoffset, s);
                    i = s;
                    numRead += s;
                }
                if (i < dcsize) {
                    // We have to adjust the crumb and leave it there as an overflow.
                    // 'i' now means 'eolPosition', or 'last char I want in there'
                    dc.offset(dcoffset + i);
                    if (logger.isDebugEnabled())
                        debug("Leaving " + (dcsize - i) + " bytes in the " + sideName +
                              " incoming queue");
                } else {
                    in.read();  // Consume the crumb
                    if (logger.isDebugEnabled())
                        debug("Removing incoming crumb for " + sideName);
                }
            } else {
                in.read();  // Consume the crumb
                logger.debug("using jvector buf as new readbuf");
                readBuf[side] = ByteBuffer.wrap(dcdata, 0, dcsize);
                readBuf[side].position(dcsize);
                readBuf[side].limit(readLimit[side]);
                numRead = dcsize;
            }
        }

        if (logger.isDebugEnabled())
            debug("read " + numRead + " size chunk from " + sideName);
        dispatcher.lastSessionNumRead(numRead);

        stats.readData(side, numRead);
        MutateTStats.readData(side, this, numRead);

        // We have received bytes.  Give them to the user.

        // We duplicate the buffer so that the event handler can mess up
        // the position/mark/limit as desired.
        ByteBuffer userBuf;
        /*
        if (readOnly())
            userBuf = readBuf[side].asReadOnlyBuffer();
        else
        */
        userBuf = readBuf[side].duplicate();
        userBuf.flip();
        IPDataResult result;
        TCPChunkEvent event = new TCPChunkEvent(mPipe, this, userBuf);

        if (side == CLIENT)
            result = dispatcher.dispatchTCPClientChunk(event);
        else
            result = dispatcher.dispatchTCPServerChunk(event);
        if (/* readOnly() || */ result == IPDataResult.PASS_THROUGH) {
            readBuf[side].flip();
            // Write it to the other side.
            addBuf(1 - side, readBuf[side]);
            readBuf[side] = null;
        } else if (result == TCPChunkResult.READ_MORE_NO_WRITE) {
            // Check for full here. XX

            // (re)Save the buffer for later.
        } else {
            addBufs(CLIENT, result.bufsToClient());
            addBufs(SERVER, result.bufsToServer());
            readBuf[side] = result.readBuffer();
        }
        // XXX also needs to work if readbuf same as before?

        return numRead;
    }


    StringBuffer logPrefix()
    {
      //8/2/05 - wrs.  Took out id.  Now part of MDC stuff from Log4J    
        StringBuffer logPrefix = new StringBuffer("");
//        logPrefix.append(id());
        logPrefix.append("(");
        logPrefix.append(Thread.currentThread().getName());
        logPrefix.append("): ");
        return logPrefix;
    }

    @Override
    protected void closeFinal()
    {
        try {
            TCPSessionEvent wevent = new TCPSessionEvent(mPipe, this);
            dispatcher.dispatchTCPFinalized(wevent);
        } catch (MPipeException x) {
            warn("MPipeException in Finalized", x);
        } catch (Exception x) {
            warn("Exception in Finalized", x);
        }

        readBuf[CLIENT] = null;
        readBuf[SERVER] = null;
        MutateTStats.removeTCPSession(mPipe);
        super.closeFinal();
    }

    protected void killSession(String reason)
    {
        // Sends a RST both directions and nukes the socket queues.
        pSession.killSession();
    }

    // Don't need equal or hashcode since we can only have one of these objects per
    // session (so the memory address is ok for equals/hashcode).
}







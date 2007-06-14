/*
 * $HeadURL:$
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

package com.untangle.node.protofilter;

import java.nio.*;
import java.util.ArrayList;
import java.util.regex.Pattern;

import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.tapi.*;
import com.untangle.uvm.tapi.event.*;
import com.untangle.uvm.node.Node;
import com.untangle.node.util.AsciiCharBuffer;
import org.apache.log4j.Logger;

public class EventHandler extends AbstractEventHandler
{
    private final Logger logger = Logger.getLogger(EventHandler.class);

    static final int SCAN_COUNTER   = Node.GENERIC_0_COUNTER;
    static final int DETECT_COUNTER = Node.GENERIC_1_COUNTER;
    static final int BLOCK_COUNTER  = Node.GENERIC_2_COUNTER;

    // These are all set at preStart() time by reconfigure()
    private ArrayList  _patternList;
    private int        _byteLimit;
    private int        _chunkLimit;
    private String     _unknownString;
    private boolean    _stripZeros;
    private ProtoFilterImpl node;

    private class SessionInfo {

        public AsciiCharBuffer serverBuffer;
        public AsciiCharBuffer clientBuffer;

        public int serverBufferSize;
        public int clientBufferSize;

        public int serverChunkCount;
        public int clientChunkCount;

        public String protocol;
        public boolean identified;

        private Pipeline pipeline;
    }

    EventHandler( ProtoFilterImpl node )
    {
        super(node);

        this.node = node;
    }

    public void handleTCPNewSession (TCPSessionEvent event)
    {
        TCPSession sess = event.session();

        SessionInfo sessInfo = new SessionInfo();
        // We now don't allocate memory until we need it.
        sessInfo.clientBuffer = null;
        sessInfo.serverBuffer = null;
        sessInfo.pipeline = UvmContextFactory.context().pipelineFoundry().getPipeline(sess.id());
        sess.attach(sessInfo);
    }

    public void handleUDPNewSession (UDPSessionEvent event)
    {
        UDPSession sess = event.session();

        SessionInfo sessInfo = new SessionInfo();
        // We now don't allocate memory until we need it.
        sessInfo.clientBuffer = null;
        sessInfo.serverBuffer = null;
        sessInfo.pipeline = UvmContextFactory.context().pipelineFoundry().getPipeline(sess.id());
        sess.attach(sessInfo);
    }

    public IPDataResult handleTCPClientChunk (TCPChunkEvent e)
    {
        _handleChunk(e, e.session(), false);
        return IPDataResult.PASS_THROUGH;
    }

    public IPDataResult handleTCPServerChunk (TCPChunkEvent e)
    {
        _handleChunk(e, e.session(), true);
        return IPDataResult.PASS_THROUGH;
    }

    public void handleUDPClientPacket (UDPPacketEvent e)
        throws MPipeException
    {
        UDPSession sess = e.session();
        ByteBuffer packet = e.packet().duplicate(); // Save position/limit for sending.
        _handleChunk(e, e.session(), false);
        sess.sendServerPacket(packet, e.header());
    }

    public void handleUDPServerPacket (UDPPacketEvent e)
        throws MPipeException
    {
        UDPSession sess = e.session();
        ByteBuffer packet = e.packet().duplicate(); // Save position/limit for sending.
        _handleChunk(e, e.session(), true);
        sess.sendClientPacket(packet, e.header());
    }

    public void patternList (ArrayList patternList)
    {
        _patternList = patternList;
    }

    public void chunkLimit (int chunkLimit)
    {
        _chunkLimit = chunkLimit;
    }

    public void byteLimit (int byteLimit)
    {
        _byteLimit  = byteLimit;
    }

    public void stripZeros (boolean stripZeros)
    {
        _stripZeros = stripZeros;
    }

    public void unknownString (String unknownString)
    {
        _unknownString  = unknownString;
    }


    private void _handleChunk (IPDataEvent event, IPSession sess, boolean server)
    {
        ByteBuffer chunk = event.data();
        SessionInfo sessInfo = (SessionInfo)sess.attachment();

        int chunkBytes = chunk.remaining();
        int bufferSize = server ? sessInfo.serverBufferSize: sessInfo.clientBufferSize;
        int bytesToWrite = chunkBytes > (this._byteLimit - bufferSize) ? this._byteLimit - bufferSize :
            chunkBytes;
        AsciiCharBuffer buf;
        int chunkCount;
        int written = 0;

        /**
         * grab the chunk
         */
        if (server) {
            buf = sessInfo.serverBuffer;
            chunkCount = sessInfo.serverChunkCount;
        } else {
            buf = sessInfo.clientBuffer;
            chunkCount = sessInfo.clientChunkCount;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Got #" + chunkCount + " chunk from " + (server ? "server" : "client") + " of size " +
                         chunkBytes + ", writing " + bytesToWrite + " of that");
        }

        if (buf == null) {
            // We thought about an optimization for the first chunk of
            // actually wrapping the chunk buffer itself. This would be
            // dangerous if anyone else in the pipeline held onto the
            // buffer for any reason (such as another protofilter). Too
            // scary for now.
            if (logger.isDebugEnabled()) {
                logger.debug("Creating new buffer of size " + bytesToWrite);
            }
            buf = AsciiCharBuffer.allocate(bytesToWrite, true);
        } else {
            buf = ensureRoomFor(buf, bytesToWrite);
        }
        ByteBuffer bbuf = buf.getWrappedBuffer();

        /**
         * copy the data into buf, possibly stripping zeros
         */
        for (int i = 0; i < bytesToWrite; i++) {
            byte b = chunk.get();
            if ((b != 0x00) || (!_stripZeros)) {
                bbuf.put(b);
                written++;
            }
        }
        bufferSize += written;
        chunkCount++;
        if (logger.isDebugEnabled()) {
            logger.debug("Wrote " + written + " bytes to buffer, now have " + bufferSize + " with capacity " +
                         buf.capacity());
        }

        /**
         * update the buffer metadata
         */
        if (server) {
            sessInfo.serverBuffer = buf;
            sessInfo.serverBufferSize = bufferSize;
            sessInfo.serverChunkCount = chunkCount;
        } else {
            sessInfo.clientBuffer = buf;
            sessInfo.clientBufferSize = bufferSize;
            sessInfo.clientChunkCount = chunkCount;
        }

        ProtoFilterPattern elem = _findMatch(sessInfo, sess, server);
        node.incrementCount( SCAN_COUNTER );
        if (elem != null) {
            sessInfo.protocol = elem.getProtocol();
            sessInfo.identified = true;
            String l4prot = "";
            if (sess instanceof TCPSession)
                l4prot = "TCP";
            if (sess instanceof UDPSession)
                l4prot = "UDP";

            if (logger.isInfoEnabled()) {
                if (!elem.isBlocked()) {
                    // No sense logging twice.
                    logger.info(" ----------------LOG: " + sessInfo.protocol + " traffic----------------");
                    logger.info( l4prot + ": " + sess.clientAddr().getHostAddress() + ":" + sess.clientPort() + " -> " +
                                 sess.serverAddr().getHostAddress() + ":" + sess.serverPort() + " matched " + sessInfo.protocol);
                    logger.info(" ----------------LOG: "+ sessInfo.protocol + " traffic----------------");
                }
            }

            node.incrementCount( DETECT_COUNTER );

            if(elem.getAlert()) {
                /* XXX Do alert here */
            }

            if (elem.isBlocked()) {
                node.incrementCount( BLOCK_COUNTER );

                if (logger.isInfoEnabled()) {
                    logger.info(" ----------------BLOCKED: " + sessInfo.protocol + " traffic----------------");
                    logger.info( l4prot + ": " + sess.clientAddr().getHostAddress() + ":" + sess.clientPort() + " -> " +
                                 sess.serverAddr().getHostAddress() + ":" + sess.serverPort() + " matched " + sessInfo.protocol);
                    logger.info(" ----------------BLOCKED: "+ sessInfo.protocol + " traffic----------------");
                }

                if (sess instanceof TCPSession) {
                    ((TCPSession)sess).resetClient();
                    ((TCPSession)sess).resetServer();
                }
                else if (sess instanceof UDPSession) {
                    ((UDPSession)sess).expireClient(); /* XXX correct? */
                    ((UDPSession)sess).expireServer(); /* XXX correct? */
                }

            }

            ProtoFilterLogEvent evt = new ProtoFilterLogEvent
                (sess.pipelineEndpoints(), sessInfo.protocol, elem.isBlocked());
            node.log(evt);

            // We release session immediately upon first match.
            sess.attach(null);
            sess.release();
        } else if (bufferSize >= this._byteLimit || (sessInfo.clientChunkCount+sessInfo.serverChunkCount) >= this._chunkLimit) {
            // Since we don't log this it isn't interesting
            // sessInfo.protocol = this._unknownString;
            // sessInfo.identified = true;
            if (logger.isDebugEnabled())
                logger.debug("Giving up after " + bufferSize + " bytes and " +
                             (sessInfo.clientChunkCount+sessInfo.serverChunkCount) + " chunks");
            sess.attach(null);
            sess.release();
        }
    }

    private ProtoFilterPattern _findMatch (SessionInfo sessInfo, IPSession sess, boolean server)
    {
        AsciiCharBuffer buffer = server ? sessInfo.serverBuffer : sessInfo.clientBuffer;
        AsciiCharBuffer toScan = buffer.asReadOnlyBuffer();
        toScan.flip();

        for (int i = 0; i < _patternList.size(); i++) {
            ProtoFilterPattern elem = (ProtoFilterPattern)_patternList.get(i);
            Pattern pat = PatternFactory.createRegExPattern(elem.getDefinition());
            if (pat.matcher(toScan).find())
                return elem; /* XXX - can match multiple patterns */
        }

        return null;
    }

    // Only works with non-direct ByteBuffers.  Ignores mark.
    private AsciiCharBuffer ensureRoomFor(AsciiCharBuffer abuf, int bytesToAdd) {
        if (abuf.remaining() < bytesToAdd) {
            ByteBuffer buf = abuf.getWrappedBuffer();
            int oldCapacity = buf.capacity();
            // Make the buffer twice as big, or as big as needed, whichever is less.
            int newCapacity = (oldCapacity + 1) * 2;
            if (oldCapacity + bytesToAdd > newCapacity)
                newCapacity = oldCapacity + bytesToAdd;
            if (logger.isDebugEnabled()) {
                logger.debug("Expanding buffer to size " + newCapacity);
            }
            byte[] oldBytes = buf.array();
            byte[] newBytes = new byte[newCapacity];
            System.arraycopy(oldBytes, 0, newBytes, 0, oldBytes.length);
            AsciiCharBuffer newBuf = AsciiCharBuffer.wrap(newBytes);
            newBuf.position(buf.position());
            return  newBuf;
        } else {
            return abuf;
        }
    }
}

/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module.  An independent
 * module is a module which is not derived from or based on this library.
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

package com.untangle.uvm.tapi.event;

import java.nio.ByteBuffer;


/**
 * IPDataResult is "mostly abstract" -- you probably want to be looking at
 * <code>TCPChunkResult</code> <code>UDPPacketResult</code>.
 *
 * @author <a href="mailto:jdi@slab.ninthwave.com">John Irwin</a>
 * @version 1.0
 */
public class IPDataResult
{
    // We don't use these publicly, instead use the static singletons above.
    private static final byte TYPE_NORMAL = 0;
    private static final byte TYPE_PASS_THROUGH = 1;

    public static final IPDataResult PASS_THROUGH = new IPDataResult(TYPE_PASS_THROUGH);


    /**
     * Describe constant <code>DO_NOT_PASS</code> here.  Also means "send nothing more"
     * when used with writable events.
     */
    public static final IPDataResult DO_NOT_PASS = new IPDataResult(null, null, null);
    public static final IPDataResult SEND_NOTHING = DO_NOT_PASS;


    // No access to this since we require using the immutable singletons for efficiency.
    private byte code = TYPE_NORMAL;

    // private IPStreamer clientStreamer = null;
    // private IPStreamer serverStreamer = null;

    private ByteBuffer[] bufsToServer = null;
    private ByteBuffer[] bufsToClient = null;
    private ByteBuffer readBuffer     = null;

    protected IPDataResult(byte code) {
        this.code = code;
    }

    /**
     * This is the main constructor for IPDataResults.  The ByteBuffers accepted
     * here are assumed to be positioned and ready for writing/reading.  For the two
     * write buffers, that means:
     *  system will write out each buffer from position to limit.
     * for the read buffer, that means:
     *  system will read into the buffer starting from position to a max of limit
     *   XXX -- this conflicts/overrides
     * any of the buffers may be null, for the write buffers, that means:
     *  system will write nothing in that direction.
     * for the read buffer that means
     *  user is done with the bytes in the buffer and the buffer may be freed (if a
     *  system buffer, user-allocated buffers are not automatically freed.
     *
     * Note that the readBuffer is always null for UDP.
     *
     * @param bufsToClient a <code>ByteBuffer[]</code> giving bytes to be written to the client
     * @param bufsToServer a <code>ByteBuffer[]</code> giving bytes to be written to the server
     * @param readBuffer a <code>ByteBuffer</code> giving the buffer to be further read into.
     */
    protected IPDataResult(ByteBuffer[] bufsToClient,
                           ByteBuffer[] bufsToServer,
                           ByteBuffer readBuffer) {
        this.bufsToClient = bufsToClient;
        this.bufsToServer = bufsToServer;
        this.readBuffer = readBuffer;
    }

    public ByteBuffer[] bufsToClient() {
        return bufsToClient;
    }

    public ByteBuffer[] bufsToServer() {
        return bufsToServer;
    }

    public ByteBuffer readBuffer() {
        return readBuffer;
    }

    /*
     * Streaming now handled in IPSession.
     *

     protected IPDataResult(IPStreamer clientStreamer, IPStreamer serverStreamer)
     {
     this.clientStreamer = clientStreamer;
     this.serverStreamer = serverStreamer;
     }

     public IPStreamer clientStreamer() {
     return clientStreamer;
     }

     public IPStreamer serverStreamer() {
     return serverStreamer;
     }

    */
}


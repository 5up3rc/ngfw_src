/**
 * $Id$
 */
package com.untangle.uvm.vnet.event;

import java.nio.ByteBuffer;

import com.untangle.uvm.vnet.PipelineConnector;
import com.untangle.uvm.vnet.NodeTCPSession;

/**
 * TCP data event -- chunk of bytes received.
 */
@SuppressWarnings("serial")
public class TCPChunkEvent extends TCPSessionEvent
{
    private ByteBuffer readBuffer;

    public TCPChunkEvent( PipelineConnector pipelineConnector, NodeTCPSession session, ByteBuffer readBuffer )
    {
        super(pipelineConnector, session);
        this.readBuffer = readBuffer;
    }

    public ByteBuffer chunk()
    {
        return readBuffer;
    }

    public ByteBuffer data()
    {
        return readBuffer;
    }
}

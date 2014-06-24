/**
 * $Id$
 */
package com.untangle.uvm.vnet.event;

import com.untangle.uvm.vnet.NodeSession;
import com.untangle.uvm.vnet.PipelineConnector;

/**
 * Base class for all IP live session events
 */
@SuppressWarnings("serial")
public class IPSessionEvent extends SessionEvent
{
    public IPSessionEvent(PipelineConnector pipelineConnector, NodeSession session)
    {
        super(pipelineConnector, session);
    }

    public NodeSession session()
    {
        return (NodeSession)getSource();
    }
}

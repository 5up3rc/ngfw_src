/*
 * $Id$
 */
package com.untangle.node.shield;

import java.net.InetAddress;
import java.util.Map;
import java.util.LinkedList;
import java.util.LinkedHashMap;

import org.apache.log4j.Logger;

import com.untangle.uvm.UvmContext;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.vnet.AbstractEventHandler;
import com.untangle.uvm.vnet.IPNewSessionRequest;
import com.untangle.uvm.vnet.Protocol;
import com.untangle.uvm.vnet.NodeSession;
import com.untangle.uvm.vnet.TCPNewSessionRequest;
import com.untangle.uvm.vnet.event.TCPNewSessionRequestEvent;
import com.untangle.uvm.vnet.event.TCPSessionEvent;
import com.untangle.uvm.vnet.event.UDPNewSessionRequestEvent;
import com.untangle.uvm.vnet.event.UDPSessionEvent;

class EventHandler extends AbstractEventHandler
{
    private StatsTableHashMap<InetAddress,HostStats> hostStatsTable = new StatsTableHashMap<InetAddress,HostStats>();

    private ShieldNodeImpl node;
    
    private final Logger logger = Logger.getLogger(EventHandler.class);

    private long lastLoggedWarningTime = System.currentTimeMillis();
    
    public EventHandler( ShieldNodeImpl node )
    {
        super( node );
        this.node = node;
    }

    public void handleTCPNewSessionRequest(TCPNewSessionRequestEvent event)
    {
        handleNewSessionRequest(event.sessionRequest(), Protocol.TCP);
    }

    public void handleUDPNewSessionRequest(UDPNewSessionRequestEvent event)
    {
        handleNewSessionRequest(event.sessionRequest(), Protocol.UDP);
    }

    private void handleNewSessionRequest(IPNewSessionRequest request, Protocol protocol)
    {
        if ( ! node.getSettings().isShieldEnabled() ) {
            request.release();
            return;
        }

        InetAddress clientAddr = request.getOrigClientAddr();
        HostStats stats;
        
        synchronized ( this.node ) {
            stats = hostStatsTable.get( clientAddr );
            if ( stats == null ) {
                stats = new HostStats();
                hostStatsTable.put( clientAddr, stats );
            }
        }

        LinkedList<ShieldRule> rules = node.getSettings().getRules();
        int multiplier = 1;
        if (rules != null ) {
            for (ShieldRule rule : rules) {
                if (rule.isMatch(request.getProtocol(),
                                 request.getClientIntf(), request.getServerIntf(),
                                 request.getOrigClientAddr(), request.getNewServerAddr(),
                                 request.getOrigClientPort(), request.getNewServerPort())) {
                    multiplier = rule.getMultiplier();
                    break;
                }
            }
        }


        //update stats
        stats.pulse(0);

        if ( multiplier > 0 && stats.load5 > ( node.getSettings().getRequestPerSecondLimit() * 5 * multiplier ) ) {
            if ( System.currentTimeMillis() - this.lastLoggedWarningTime > 10000 ) {
                this.lastLoggedWarningTime = System.currentTimeMillis();
                logger.info("Host " + clientAddr.getHostAddress() + " exceeded limit. 5-second load: " + String.format("%.2f",stats.load5) );
            }

            ShieldEvent evt = new ShieldEvent( request.sessionEvent(), true );
            node.logEvent( evt );
            
            if (protocol == Protocol.UDP) {
                request.rejectReturnUnreachable( IPNewSessionRequest.PORT_UNREACHABLE );
            } else {
                ((TCPNewSessionRequest)request).rejectReturnRst();
            }
        } else {
            if ( logger.isDebugEnabled() ) {
                logger.debug("Session allowed for " + clientAddr.getHostAddress() + "." + " 5-second-load: " + String.format("%.2f",stats.load5) );
            }

            stats.pulse(1);
            request.release();
        }
    }

    @SuppressWarnings("serial")
    private class StatsTableHashMap<K,V> extends LinkedHashMap<K,V>
    {
        @Override
        protected boolean removeEldestEntry( Map.Entry<K,V> eldest )
        {
            if ( size() > 10000 ) return true;
            return false;
        }

    }
}

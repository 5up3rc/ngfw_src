/*
 * $Id$
 */
package com.untangle.uvm;

import java.util.List;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashMap;
import java.net.InetAddress;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import org.apache.log4j.Logger;

import com.untangle.uvm.SessionMonitor;
import com.untangle.uvm.UvmState;
import com.untangle.uvm.UvmContext;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.SessionMonitorEntry;
import com.untangle.uvm.node.Node;
import com.untangle.uvm.node.NodeManager;
import com.untangle.uvm.node.SessionTuple;
import com.untangle.uvm.vnet.NodeSession;
import com.untangle.uvm.node.NodeSettings;
import com.untangle.uvm.network.InterfaceSettings;

/**
 * SessionMonitor is a utility class that provides some convenient functions
 * to monitor and view current sessions and state existing in the untangle-vm
 *
 * This is used by the UI to display state
 */
public class SessionMonitorImpl implements SessionMonitor
{
    private final Logger logger = Logger.getLogger(getClass());

    UvmContext uvmContext;
    
    public SessionMonitorImpl ()
    {
        uvmContext = UvmContextFactory.context();
    }

    public List<SessionMonitorEntry> getMergedBandwidthSessions(String interfaceIdStr, int nodeId)
    {
        /**
         * Find the the system interface name that matches this ID
         */
        try {
            int interfaceId = Integer.parseInt(interfaceIdStr);
            InterfaceSettings intf = uvmContext.networkManager().findInterfaceId( interfaceId );
            
            if (intf == null) {
                logger.warn( "Unabled to find interface " + interfaceId );
                return null;
            }
            
            String systemName = intf.getSystemDev();
            return _getMergedBandwidthSessions(systemName);

        } catch (Exception e) {
            logger.warn("Unable to retrieve sessions",e);
            return null;
        }
    }
    
    /**
     * documented in SessionMonitor.java
     */
    public List<SessionMonitorEntry> getMergedBandwidthSessions(String interfaceIdStr)
    {
        return getMergedBandwidthSessions(interfaceIdStr, 0);
    }

    /**
     * documented in SessionMonitor.java
     */
    public List<SessionMonitorEntry> getMergedBandwidthSessions()
    {
        return getMergedBandwidthSessions("0");
    }

    /**
     * documented in SessionMonitor.java
     */
    public List<SessionMonitorEntry> getMergedSessions()
    {
        return getMergedSessions(0);
    }
    
    /**
     * documented in SessionMonitor.java
     */
    public List<SessionMonitorEntry> getMergedSessions(long nodeId)
    {
        List<SessionMonitorEntry> sessions = this._getConntrackSessionMonitorEntrys();
        List<SessionGlobalState> netcapSessions = SessionTable.getInstance().getSessions();
        List<SessionTuple> nodeSessions = null;;

        Node node = null;
        if (nodeId > 0)
            node = UvmContextFactory.context().nodeManager().node(nodeId);
        if (node != null)
            nodeSessions = node.liveSessions();

        HashMap<Tuple,SessionGlobalState> map = new HashMap<Tuple,SessionGlobalState>();
        for (SessionGlobalState netcapSession : netcapSessions) {
            com.untangle.uvm.node.SessionTuple clientSide = netcapSession.netcapHook().getClientSide();
            Tuple clientTuple = new Tuple( clientSide.getProtocol(),
                                           clientSide.getClientAddr(),
                                           clientSide.getServerAddr(),
                                           clientSide.getClientPort(),
                                           clientSide.getServerPort());
            com.untangle.uvm.node.SessionTuple serverSide = netcapSession.netcapHook().getServerSide();
            Tuple serverTuple = new Tuple( serverSide.getProtocol(),
                                           serverSide.getClientAddr(),
                                           serverSide.getServerAddr(),
                                           serverSide.getClientPort(),
                                           serverSide.getServerPort());
            
            map.put( clientTuple, netcapSession );
            map.put( serverTuple, netcapSession );
        }
        
        for (Iterator<SessionMonitorEntry> i = sessions.iterator(); i.hasNext(); ) {  
            SessionMonitorEntry session = i.next();
            session.setPolicy("");             
            session.setClientIntf(Integer.valueOf(-1));
            session.setServerIntf(Integer.valueOf(-1));
            session.setPriority(session.getQosPriority()); 

            Tuple tuple = _makeTuple( session );
            SessionGlobalState netcapSession = map.get(tuple); // find corresponding netcap

            if ( netcapSession != null ) {
                try {
                    int priority = netcapSession.netcapSession().clientQosMark();
                    com.untangle.uvm.node.SessionTuple clientSide = netcapSession.netcapHook().getClientSide();
                    com.untangle.uvm.node.SessionTuple serverSide = netcapSession.netcapHook().getServerSide();

                    NetcapHook hook = netcapSession.netcapHook();
                    if (hook == null)
                        continue;
                        
                    Long policyId = hook.getPolicyId();
                    if (policyId == null)
                        session.setPolicy("");
                    else
                        session.setPolicy(policyId.toString()); 

                    session.setSessionId(netcapSession.id());
                    session.setBypassed(Boolean.FALSE);
                    session.setClientIntf(new Integer(clientSide.getClientIntf()));
                    session.setServerIntf(new Integer(serverSide.getServerIntf()));

                    /**
                     * The conntrack entry shows that this session has been redirect to the local host
                     * We need to overwrite that with the correct info
                     */
                    session.setPostNatClient(serverSide.getClientAddr());
                    session.setPostNatServer(serverSide.getServerAddr());
                    session.setPostNatClientPort(serverSide.getClientPort());
                    session.setPostNatServerPort(serverSide.getServerPort());

                    /**
                     * Only have one priority per session
                     * Assume both client and server are the same
                     */
                    if (priority != 0)
                        session.setPriority(priority);

                    session.setAttachments(netcapSession.getAttachments());
                } catch (Exception e) {
                    logger.warn("Exception while searching for session",e);
                }
            } else {
                /**
                 * If the session is not bypassed and is not in the UVM
                 * Then it is likely some expired session or some local session (blockpages)
                 * Remove it and dont show it to the user
                 */
                if ( !session.getBypassed() ) {
                    logger.debug("Ignoring session: " + session);
                    i.remove();
                }
            }

            /**
             * Ignore sessions to 192.0.2.42
             */
            if ( "192.0.2.42".equals( session.getPostNatServer().getHostAddress() ) ) {
                i.remove();
            }
        }

        /**
         * Update some additional fields
         */
        for (SessionMonitorEntry entry : sessions) {
            entry.setNatted(Boolean.FALSE);
            entry.setPortForwarded(Boolean.FALSE);

            if (! entry.getPreNatClient().equals(entry.getPostNatClient())) {
                entry.setNatted(Boolean.TRUE);
            }
            if (! entry.getPreNatServer().equals(entry.getPostNatServer())) {
                entry.setPortForwarded(Boolean.TRUE);
            }
        }

        /**
         * If a nodeId was specified remove all sessions not being touched by that nodeId
         */
        if ( nodeSessions != null ) {
            for (Iterator<SessionMonitorEntry> i = sessions.iterator(); i.hasNext(); ) {  
                SessionMonitorEntry entry = i.next();
                Long sessionId = entry.getSessionId();
                boolean found = false;
                for (SessionTuple tuple : nodeSessions) {
                    if ( sessionId != null && sessionId == tuple.getSessionId() ) {
                        found = true;
                        break;
                    }
                }
                if (!found)
                    i.remove();
            }
        }

        return sessions;
    }
    
    /**
     * Returns a fully merged list for the given interface
     * systemIntfName is the system interface (example: "eth0")
     * This takes 5 seconds to gather data before it returns
     */
    private List<SessionMonitorEntry> _getMergedBandwidthSessions(String systemIntfName)
    {
        List<SessionMonitorEntry> jnettopSessions = _getJnettopSessionMonitorEntrys(systemIntfName);
        List<SessionMonitorEntry> sessions = this.getMergedSessions();

        for (SessionMonitorEntry session : sessions) {

            session.setClientKBps(Float.valueOf(0.0f));
            session.setServerKBps(Float.valueOf(0.0f));
            session.setTotalKBps(Float.valueOf(0.0f));

            for (SessionMonitorEntry jnettopSession : jnettopSessions) {

                boolean match = false;

                /**
                 * Depending on whether the internal and external is source interface jnettop can see either pre or post NAT
                 * and whether jnettop sees a client or server packet first the order it can reverse client and server
                 * We have to check for all combinations for matches
                 * (Bug #8290)
                 */
                if (match || _matches(jnettopSession.getProtocol(),
                                      jnettopSession.getPreNatClient(),jnettopSession.getPreNatServer(),
                                      jnettopSession.getPreNatClientPort(),jnettopSession.getPreNatServerPort(),
                                      session.getProtocol(),
                                      session.getPreNatClient(),session.getPreNatServer(),
                                      session.getPreNatClientPort(),session.getPreNatServerPort()))
                    match = true;
                if (match || _matches(jnettopSession.getProtocol(),
                                      jnettopSession.getPreNatClient(),jnettopSession.getPreNatServer(),
                                      jnettopSession.getPreNatClientPort(),jnettopSession.getPreNatServerPort(),
                                      session.getProtocol(),
                                      session.getPreNatServer(),session.getPreNatClient(),
                                      session.getPreNatServerPort(),session.getPreNatClientPort()))
                    match = true;
                if (match || _matches(jnettopSession.getProtocol(),
                                      jnettopSession.getPreNatClient(),jnettopSession.getPreNatServer(),
                                      jnettopSession.getPreNatClientPort(),jnettopSession.getPreNatServerPort(),
                                      session.getProtocol(),
                                      session.getPostNatClient(),session.getPostNatServer(),
                                      session.getPostNatClientPort(),session.getPostNatServerPort()))
                    match = true;
                if (match || _matches(jnettopSession.getProtocol(),
                                      jnettopSession.getPreNatClient(),jnettopSession.getPreNatServer(),
                                      jnettopSession.getPreNatClientPort(),jnettopSession.getPreNatServerPort(),
                                      session.getProtocol(),
                                      session.getPostNatServer(),session.getPostNatClient(),
                                      session.getPostNatServerPort(),session.getPostNatClientPort()))
                    match = true;

                if ( match ) {
                    session.setClientKBps(jnettopSession.getClientKBps());
                    session.setServerKBps(jnettopSession.getServerKBps());
                    session.setTotalKBps(jnettopSession.getTotalKBps());
                    break; /* break to outer loop */
                }
            }
        }

        return sessions;
    }

    /**
     * This returns a list of sessions and bandwidth usages reported by jnettop over 5 seconds
     * This takes 5 seconds to gather data before it returns
     */
    @SuppressWarnings("unchecked") //JSON
    private List<SessionMonitorEntry> _getJnettopSessionMonitorEntrys(String systemIntfName)
    {
        String execStr = new String(System.getProperty("uvm.bin.dir") + "/" + "ut-jnettop" + " " + systemIntfName);

        try {
            String output = uvmContext.execManager().execOutput(execStr);
            List<SessionMonitorEntry> entryList = (List<SessionMonitorEntry>) ((UvmContextImpl)UvmContextFactory.context()).getSerializer().fromJSON(output);
            return entryList;
            
        } catch (org.jabsorb.serializer.UnmarshallException exc) {
            logger.error("Unable to read jnettop - invalid JSON",exc);
            return null;
        }
    }
    
    /**
     * This returns a list of descriptors for all sessions in the conntrack table
     */
    @SuppressWarnings("unchecked") //JSON
    private List<SessionMonitorEntry> _getConntrackSessionMonitorEntrys()
    {
        String execStr = new String(System.getProperty("uvm.bin.dir") + "/" + "ut-conntrack");

        try {
            String output = uvmContext.execManager().execOutput(execStr);
            List<SessionMonitorEntry> entryList = (List<SessionMonitorEntry>) ((UvmContextImpl)UvmContextFactory.context()).getSerializer().fromJSON(output);
            return entryList;
        } catch (org.jabsorb.serializer.UnmarshallException exc) {
            logger.error("Unable to read conntrack - invalid JSON",exc);
            return null;
        }
    }

    /**
     * Check if the entry matches the sessionDesc
     * This checks the 5-tuple (protocol, src, dst, src_port, dst_port)
     */
    private boolean _matches(com.untangle.uvm.node.SessionTuple sessionDesc, SessionMonitorEntry session)
    {
        switch (sessionDesc.getProtocol()) {
        case SessionTuple.PROTO_TCP:
            if (! "TCP".equals(session.getProtocol())) {
                return false;
            }
            break;
        case SessionTuple.PROTO_UDP:
            if (! "UDP".equals(session.getProtocol())) {
                return false;
            }
            break;
        }

        if (! sessionDesc.getClientAddr().equals(session.getPreNatClient())) {
            return false;
        }
        if (! sessionDesc.getServerAddr().equals(session.getPreNatServer())) {
            return false;
        }

        if (sessionDesc.getClientPort() != session.getPreNatClientPort()) {
            return false;
        }
        if (sessionDesc.getServerPort() != session.getPreNatServerPort()) {
            return false;
        }

        return true;
    }

    private Tuple _makeTuple( SessionMonitorEntry session )
    {
        short protocol;
        if ( "TCP".equals(session.getProtocol()) )
            protocol = Tuple.PROTO_TCP;
        else if ( "UDP".equals(session.getProtocol()) )
            protocol = Tuple.PROTO_UDP;
        else {
            logger.warn("Unknown protocol: " + session.getProtocol());
            protocol = 0;
        }
        return new Tuple( protocol,
                          session.getPreNatClient(),
                          session.getPreNatServer(),
                          session.getPreNatClientPort(),
                          session.getPreNatServerPort() );
    }
    
    /**
     * This checks first 5-tuple (protocol, src, dst, src_port, dst_port)
     * against the second, returns true if match
     */
    private boolean _matches(String protocol1, InetAddress client1, InetAddress server1, int clientPort1, int serverPort1,
                             String protocol2, InetAddress client2, InetAddress server2, int clientPort2, int serverPort2)

    {
        if (! protocol1.equals(protocol2))
            return false;
        if (! client1.equals(client2))
            return false;
        if (! server1.equals(server2))
            return false;
        if (clientPort1 != clientPort2)
            return false;
        if (serverPort1 != serverPort2)
            return false;

        return true;
    }

    private class Tuple
    {
        public static final short PROTO_TCP = 6;
        public static final short PROTO_UDP = 17;

        public short protocol;
        public InetAddress clientAddr;
        public InetAddress serverAddr;
        public int clientPort;
        public int serverPort;

        public Tuple( short protocol, InetAddress clientAddr, InetAddress serverAddr, int clientPort, int serverPort )
        {
            this.protocol = protocol;
            this.clientAddr = clientAddr;
            this.serverAddr = serverAddr;
            this.clientPort = clientPort;
            this.serverPort = serverPort;
        }

        public int hashCode()
        {
            return protocol + clientAddr.hashCode() + serverAddr.hashCode() * clientPort * serverPort;
        }
        
        public boolean equals( Object o2 )
        {
            if ( ! ( o2 instanceof Tuple ) ) {
                return false;
            }
            Tuple o = (Tuple) o2;
            if ( o.protocol != this.protocol ||
                 o.clientPort != this.clientPort ||
                 o.serverPort != this.serverPort ) {
                return false;
            }
            if ( ! ( o.clientAddr == null ? this.clientAddr == null : o.clientAddr.equals(this.clientAddr) ) ) {
                return false;
            }
            if ( ! ( o.serverAddr == null ? this.serverAddr == null : o.serverAddr.equals(this.serverAddr) ) ) {
                return false;
            }
            return true;
        }

        public String toString()
        {
            return this.protocol + "| " +
                ( this.clientAddr == null ? "null" : this.clientAddr.getHostAddress() ) + ":" +
                this.clientPort + " -> " +
                ( this.serverAddr == null ? "null" : this.serverAddr.getHostAddress() ) + ":" +
                this.serverPort;
                
                
        }
    }
                            
}

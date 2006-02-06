package com.metavize.tran.ids;

import java.net.InetAddress;
import com.metavize.mvvm.argon.SessionEndpoints;
import com.metavize.mvvm.tapi.Protocol;
import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.tran.PortRange;
import com.metavize.mvvm.tran.firewall.ip.IPMatcher;
import com.metavize.mvvm.tran.firewall.ip.IPMatcherFactory;
import org.apache.log4j.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class IDSRuleHeader {

    private static final Logger logger = Logger.getLogger(IDSRuleHeader.class);

    public static final boolean	IS_BIDIRECTIONAL = true;
    public static final boolean	IS_SERVER = true;
	
    private int 			action = 0;
    private Protocol		protocol;
	
    private List<IPMatcher>	clientIPList;
    private PortRange		clientPortRange;
	
    private boolean			bidirectional = false;
	
    private List<IPMatcher>	serverIPList;
    private PortRange 		serverPortRange;

    private	List<IDSRuleSignature> signatures = new ArrayList<IDSRuleSignature>();

    /**
     * Negation Flags: flag XOR input = answer
     * */
    private boolean 	clientIPFlag = false;
    private boolean   	clientPortFlag = false;
    private boolean  	serverIPFlag = false;
    private boolean		serverPortFlag = false;

    public IDSRuleHeader(int action, boolean bidirectional, Protocol protocol,
                         List<IPMatcher> clientIPList,	PortRange clientPortRange,
                         List<IPMatcher> serverIPList,	PortRange serverPortRange) {
		
        this.action = action;
        this.bidirectional = bidirectional;
        this.protocol = protocol;
        this.clientIPList = clientIPList;
        this.serverIPList = serverIPList;
		
        this.clientPortRange = clientPortRange;
        this.serverPortRange = serverPortRange;
    }

    public boolean portMatches(int port, boolean toServer) {
        if(toServer)
            return serverPortFlag ^ serverPortRange.contains(port);
        else
            return clientPortFlag ^ clientPortRange.contains(port);
    }
			
    public boolean matches(SessionEndpoints sess, boolean sessInbound, boolean forward) {
        return matches(sess, sessInbound, forward, false);
    }

    private boolean matches(SessionEndpoints sess, boolean sessInbound, boolean forward, boolean swapFlag) {
        if(this.protocol != Protocol.getInstance(sess.protocol()))
            return false;

        // logger.debug("protocol match succeeded");
		
        /**Check Port Match*/
        boolean clientPortMatch = clientPortRange.contains(forward ? sess.clientPort() : sess.serverPort());
        boolean serverPortMatch = serverPortRange.contains(forward ? sess.serverPort() : sess.clientPort());

        boolean portMatch = (clientPortMatch ^ clientPortFlag) && (serverPortMatch ^ serverPortFlag);

        /*		if(!portMatch && !bidirectional) {
			System.out.println();
			System.out.println("Header: " + this);
			System.out.println("ClientPort: " + clientPort);
			System.out.println("ServerPort: " + serverPort);
			System.out.println();
                        }*/
		
        if(!portMatch && !bidirectional)
            return false;

        // logger.debug("port match succeeded");

        boolean isInbound = forward ? sessInbound : !sessInbound;

        /**Check IP Match*/
        InetAddress cAddr = forward ? sess.clientAddr() : sess.serverAddr();
        boolean clientIPMatch = false;
        Iterator<IPMatcher> clientIt = clientIPList.iterator();

        IPMatcherFactory ipmf = IPMatcherFactory.getInstance();
        IPMatcher internalMatcher = ipmf.getInternalMatcher();
        IPMatcher externalMatcher = ipmf.getExternalMatcher();

        while(clientIt.hasNext() && !clientIPMatch)  {
            IPMatcher matcher = clientIt.next();
            if (matcher == externalMatcher)
                clientIPMatch = isInbound;
            else if (matcher == internalMatcher)
                clientIPMatch = !isInbound;
            else
                clientIPMatch =  matcher.isMatch(cAddr);
            // logger.debug("client matcher: " + matcher + " sez: " + clientIPMatch);
        }

        InetAddress sAddr = forward ? sess.serverAddr() : sess.clientAddr();
        boolean serverIPMatch = false;
        Iterator<IPMatcher> serverIt = serverIPList.iterator();
        while(serverIt.hasNext() && !serverIPMatch) {
            IPMatcher matcher = serverIt.next();
            if (matcher == externalMatcher)
                serverIPMatch = !isInbound;
            else if (matcher == internalMatcher)
                serverIPMatch = isInbound;
            else
                serverIPMatch = matcher.isMatch(sAddr);
            // logger.debug("server matcher: " + matcher + " sez: " + serverIPMatch);
        }
        boolean ipMatch = (clientIPMatch ^ clientIPFlag) && (serverIPMatch ^ serverIPFlag);

        // logger.debug("ip match: " + ipMatch);
		
        /**Check Directional flag*/
        if(!(ipMatch && portMatch) && bidirectional && !swapFlag) {
            return matches(sess, sessInbound, !forward, true);
        }
		 
        /*		if(!(ipMatch && portMatch)) {
			System.out.println();
			System.out.println("Header: " + this);
			System.out.println("ClientIP: " + clientAddr);
			System.out.println("ServerIP: " + serverAddr);
			System.out.println();
                        }*/

        return ipMatch && portMatch;
    }

    public void setNegationFlags(boolean clientIP, boolean clientPort, boolean serverIP, boolean serverPort) {
		
        clientIPFlag = clientIP;
        clientPortFlag = clientPort;
        serverIPFlag = serverIP;
        serverPortFlag = serverPort;
    }

    public void addSignature(IDSRuleSignature sig) {
        signatures.add(sig);
    }

    public boolean removeSignature(IDSRuleSignature sig) {
        return signatures.remove(sig);
    }

    public int getAction() {
        return action;
    }
	
    public List<IDSRuleSignature> getSignatures() {
        return signatures;
    }

    public boolean signatureListIsEmpty() {
        return signatures.isEmpty();
    }
	
    public boolean equals(IDSRuleHeader other) {
        boolean action = (this.action == other.action);
        boolean protocol = (this.protocol == other.protocol); // ?
        boolean clientPorts = (this.clientPortRange.equals(other.clientPortRange));
        boolean serverPorts = (this.serverPortRange.equals(other.serverPortRange));
        boolean serverIP = (this.serverIPList.equals(other.serverIPList));
        boolean clientIP = (this.serverIPList.equals(other.serverIPList));
        boolean direction = (this.bidirectional == other.bidirectional);

        return action && protocol && clientPorts && serverPorts && serverIP && clientIP && direction;
    }
	
    public String toString() {
        String str = "alert "+protocol+" ";
        if(clientIPFlag)
            str += "!";
        str += clientIPList + " ";
        if(clientPortFlag)
            str += "!";
        str += clientPortRange;
        if(bidirectional)
            str += " <> ";
        else
            str += " -> ";
        if(serverIPFlag)
            str += "!";
        str += serverIPList +" ";
        if(serverPortFlag)
            str += "!";
        str += serverPortRange;
        return str;
    }
}

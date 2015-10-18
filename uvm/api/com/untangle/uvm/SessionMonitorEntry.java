
package com.untangle.uvm;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is an object that represents a conntrack session
 * It is used for JSON serialization of the ut-conntrack script
 */
public class SessionMonitorEntry
{
    private String protocol;
    private String state;

    private InetAddress preNatClient;
    private InetAddress preNatServer;
    private Integer preNatClientPort;
    private Integer preNatServerPort;

    private InetAddress postNatClient;
    private InetAddress postNatServer;
    private Integer postNatClientPort;
    private Integer postNatServerPort;

    private Integer qosPriority;
    private Boolean bypassed;

    public String getProtocol() {return protocol;}
    public void   setProtocol( String protocol ) {this.protocol = protocol;}

    public String getState() {return state;}
    public void   setState( String state ) {this.state = state;}

    public InetAddress getPreNatClient() {return preNatClient;}
    public void        setPreNatClient( InetAddress preNatClient ) {this.preNatClient = preNatClient;}
    public InetAddress getPreNatServer() {return preNatServer;}
    public void        setPreNatServer( InetAddress preNatServer ) {this.preNatServer = preNatServer;}

    public Integer  getPreNatClientPort() {return preNatClientPort;}
    public void     setPreNatClientPort( Integer preNatClientPort ) {this.preNatClientPort = preNatClientPort;}
    public Integer  getPreNatServerPort() {return preNatServerPort;}
    public void     setPreNatServerPort( Integer preNatServerPort ) {this.preNatServerPort = preNatServerPort;}

    public InetAddress getPostNatClient() {return postNatClient;}
    public void        setPostNatClient( InetAddress postNatClient ) {this.postNatClient = postNatClient;}
    public InetAddress getPostNatServer() {return postNatServer;}
    public void        setPostNatServer( InetAddress postNatServer ) {this.postNatServer = postNatServer;}

    public Integer  getPostNatClientPort() {return postNatClientPort;}
    public void     setPostNatClientPort( Integer postNatClientPort ) {this.postNatClientPort = postNatClientPort;}
    public Integer  getPostNatServerPort() {return postNatServerPort;}
    public void     setPostNatServerPort( Integer postNatServerPort ) {this.postNatServerPort = postNatServerPort;}

    public Integer getQosPriority() {return qosPriority;}
    public void    setQosPriority( Integer qosPriority ) {this.qosPriority = qosPriority;}

    public Boolean getBypassed() {return bypassed;}
    public void    setBypassed( Boolean bypassed ) {this.bypassed = bypassed;}

    /**
     * The following properties are UVM properties and are only set if you call MergedSessionMonitorEntrys
     */
    private Long creationTime = null;
    private Long sessionId = null;
    private String policy;
    private Integer clientIntf;
    private Integer serverIntf;
    private Boolean portForwarded;
    private Boolean natted;
    private Integer priority;
    private String pipeline;

    private Map<String,Object> attachments;
    
    public Long getCreationTime() {return creationTime;}
    public void setCreationTime( Long newValue ) {this.creationTime = newValue;}

    public Long getSessionId() {return sessionId;}
    public void setSessionId( Long newValue ) {this.sessionId = newValue;}

    public String getPolicy() {return policy;}
    public void   setPolicy( String newValue ) {this.policy = newValue;}

    public Integer getClientIntf() {return clientIntf;}
    public void    setClientIntf( Integer clientIntf ) {this.clientIntf = clientIntf;}
    public Integer getServerIntf() {return serverIntf;}
    public void    setServerIntf( Integer serverIntf ) {this.serverIntf = serverIntf;}

    public Boolean getPortForwarded() {return portForwarded;}
    public void    setPortForwarded( Boolean portForwarded ) {this.portForwarded = portForwarded;}

    public Boolean getNatted() {return natted;}
    public void    setNatted( Boolean natted ) {this.natted = natted;}

    public Integer getPriority() {return priority;}
    public void    setPriority( Integer priority ) {this.priority = priority;}

    public String  getPipeline() {return pipeline;}
    public void    setPipeline( String newValue ) {this.pipeline = newValue;}
    
    public Map<String,Object> getAttachments() {return attachments;}

    /**
     * Set the attachments for this session
     * This only includes the Serializable attachments
     * Because these entries are sent to the UI all these attachments will be serialized.
     * This avoids any cases where non-serializable attachments since the UI can't use them anyway
     * and they may not serialize correctly
     */
    public void               setAttachments( Map<String,Object> attachments )
    {
        this.attachments = new HashMap<String,Object>();
        for ( String key : attachments.keySet() ) {
            Object obj = attachments.get( key );
            if ( obj instanceof java.io.Serializable ) {
                this.attachments.put( key, obj );
            }
        }
    }
    
    /**
     * The following properties come from jnettop
     */
    private Float clientKBps;
    private Float serverKBps;
    private Float totalKBps;

    public Float getClientKBps() {return clientKBps;}
    public void  setClientKBps( Float clientKBps ) {this.clientKBps = clientKBps;}
    public Float getServerKBps() {return serverKBps;}
    public void  setServerKBps( Float serverKBps ) {this.serverKBps = serverKBps;}
    public Float getTotalKBps() {return totalKBps;}
    public void  setTotalKBps( Float totalKBps ) {this.totalKBps = totalKBps;}

    public String toString()
    {
        return getProtocol() + "| " + getPreNatClient() + ":" + getPreNatClientPort() + " -> " + getPostNatServer() + ":" + getPostNatServerPort();
    }
}
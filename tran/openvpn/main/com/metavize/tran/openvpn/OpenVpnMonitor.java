/*
 * Copyright (c) 2004, 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.openvpn;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.MvvmLocalContext;
import com.metavize.mvvm.logging.EventCache;
import com.metavize.mvvm.logging.EventLogger;
import com.metavize.mvvm.logging.EventLoggerFactory;
import com.metavize.mvvm.logging.RepositoryDesc;
import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.tran.TransformStats;
import org.apache.log4j.Logger;


class OpenVpnMonitor implements Runnable
{
    /* Poll every 5 seconds */
    private static final long   SLEEP_TIME_MSEC = 5 * 1000;

    /* Log every 5 minutes ? */
    private static final long   LOG_TIME_MSEC = 5 * 60 * 1000;

    /* Just for debugging */
    // private static final long   LOG_TIME_MSEC = 1 * 30 * 1000;

    /* Delay a second while the thread is joining */
    private static final long   THREAD_JOIN_TIME_MSEC = 1000;

    /* Interrupt if there is no traffic for 2 seconds */
    private static final int READ_TIMEOUT = 2000;

    private static final String KILL_CMD   = "kill";
    private static final String STATUS_CMD = "status 2";
    private static final String KILL_UNDEF = KILL_CMD + " UNDEF";
    private static final String END_MARKER = "end";

    private static final int TYPE_INDEX    = 0;
    private static final int NAME_INDEX    = 1;
    private static final int ADDRESS_INDEX = 2;
    private static final int RX_INDEX      = 4;
    private static final int TX_INDEX      = 5;
    private static final int START_INDEX   = 7;
    private static final int TOTAL_INDEX   = 8;

    private static final int TIMEOUT       = 2 * 60 * 1000;

    private static final String PATH_PROCNET_DEV = "/proc/net/dev";
    private static final String TUN_DEV_PREFIX = "tun";
    private static final int PROCNET_STAT_COUNT = 16;
    private static final int PROCNET_STAT_RX_BYTES   = 0;
    private static final int PROCNET_STAT_RX_PACKETS = 1;
    private static final int PROCNET_STAT_TX_BYTES   = 8;
    private static final int PROCNET_STAT_TX_PACKETS = 9;

    private final EventLogger eventLogger;
    private final Logger logger = Logger.getLogger( this.getClass());

    private Map<Key,Stats> statusMap    = new HashMap<Key,Stats>();
    private Map<String,Stats> activeMap = new HashMap<String,Stats>();
    private VpnStatisticEvent statistics = new VpnStatisticEvent();

    private long totalBytesTx = 0;
    private long totalBytesRx = 0;


    /* This is a list that contains the contents of the command "status 2" from openvpn */
    private final List<String> clientStatus = new LinkedList<String>();

    private List<ClientDistributionEvent> clientDistributionList = new LinkedList<ClientDistributionEvent>();

    private final VpnTransformImpl transform;

    /* The thread the monitor is running on */
    private Thread thread = null;

    /* Status of the monitor */
    private volatile boolean isAlive = true;

    /* Whether or not openvpn is started */
    private volatile boolean isEnabled = false;

    private final MvvmLocalContext localContext;

    OpenVpnMonitor( VpnTransformImpl transform )
    {
        this.eventLogger = EventLoggerFactory.factory().getEventLogger(transform.getTransformContext());
        //Add the magical thingies to make UI log reading from cache happen
        eventLogger.addSimpleEventFilter(new ClientConnectEventAllFilter());//For "final" events
        eventLogger.addEventCache(new ActiveEventCache());//For "open" events
        this.localContext = MvvmContextFactory.context();
        this.transform = transform;
    }

    public void run()
    {
        logger.debug( "Starting" );

        /* Flush both of these maps */
        statusMap = new HashMap<Key,Stats>();
        activeMap = new HashMap<String,Stats>();

        Date nextUpdate = new Date(( new Date()).getTime() + LOG_TIME_MSEC );

        /* Flush the statistics */
        this.statistics = new VpnStatisticEvent();
        this.statistics.setStart( new Date());

        if ( !isAlive ) {
            logger.error( "died before starting" );
            return;
        }

        Date now = new Date();

        while ( true ) {
            try {
                Thread.sleep( SLEEP_TIME_MSEC );
            } catch ( InterruptedException e ) {
                logger.info( "openvpn monitor was interrupted" );
            }

            /* Check if the transform is still running */
            if ( !isAlive ) break;

            logClientDistributionEvents();

            /* Only log when enabled */
            if ( !isEnabled ) {
                flushLogEvents();
                continue;
            }

            /* Update the current time */
            now.setTime( System.currentTimeMillis());

            //Grab lock, such that a concurrent read of the "activeMap"
            //doesn't happen during an update
            synchronized(this) {
              try {
                  /* Cleanup UNDEF sessions every time you are going to update the stats */
                  boolean killUndef = now.after( nextUpdate );
                  updateStatus( killUndef );
              } catch ( Exception e ) {
                  logger.info( "Error updating status", e );
              }

              if ( now.after( nextUpdate )) {
                  logStatistics( now );
                  nextUpdate.setTime( now.getTime() + LOG_TIME_MSEC );
              }
            }

            /* Check if the transform is still running */
            if ( !isAlive ) break;
        }

        /* Flush out all of the log events that are remaining */
        flushLogEvents();

        logger.debug( "Finished" );
    }

    /**
     * Method returns a list of open clients as ClientConnectEvents w/o
     * an end date.
     */
    private synchronized List<ClientConnectEvent> getOpenConnectionsAsEvents() {
      Date now = new Date();
      List<ClientConnectEvent> ret = new ArrayList<ClientConnectEvent>();
      for(Stats s : activeMap.values()) {
        if(s.isActive) {
          ClientConnectEvent copy = s.copyCurrentEvent(now);
          copy.setTimeStamp(copy.getStart());
          copy.setEnd(null);
          ret.add(copy);
        }
      }
      return ret;
    }


    public synchronized void start()
    {
        isAlive = true;
        isEnabled = false;

        logger.debug( "Starting OpenVpn monitor" );

        /* If thread is not-null, there is a running thread that thinks it is alive */
        if ( thread != null ) {
            logger.debug( "OpenVpn monitor is already running" );
            return;
        }

        eventLogger.start();
        thread = this.localContext.newThread( this );
        thread.start();
    }

    public synchronized void enable()
    {
        start();
        isEnabled = true;
    }

    public synchronized void disable()
    {
        start();
        isEnabled = false;
    }

    public synchronized void stop()
    {
        if ( thread != null ) {
            logger.debug( "Stopping OpenVpn monitor" );

            isAlive = false;
            try {
                thread.interrupt();
                thread.join( THREAD_JOIN_TIME_MSEC );
            } catch ( SecurityException e ) {
                logger.error( "security exception, impossible", e );
            } catch ( InterruptedException e ) {
                logger.error( "interrupted while stopping", e );
            }
            thread = null;
        }

        eventLogger.stop();
    }

    TransformStats updateStats( TransformStats stats )
    {
        BufferedReader in = null;
        long rxBytes  = stats.t2sBytes();
        long rxChunks = stats.t2sChunks();
        long txBytes  = stats.s2tBytes();
        long txChunks = stats.s2tChunks();

        try {
            /* Read in the whole file */
            in = new BufferedReader( new FileReader( PATH_PROCNET_DEV ));

            String line;
            while(( line = in.readLine()) != null ) {
                line = line.trim();

                /* Parse the stats from the file */
                if ( line.startsWith( TUN_DEV_PREFIX )) {
                    String args[] = line.split( ":" );
                    if ( args.length != 2 ) {
                        logger.warn( "Invalid line: " + line );
                        continue;
                    }
                    args = args[1].trim().split( "\\s+" );
                    if ( args.length != PROCNET_STAT_COUNT ) {
                        logger.warn( "Invalid line: " + line );
                        continue;
                    }

                    rxBytes  = incrementCount( rxBytes,  Long.parseLong( args[PROCNET_STAT_RX_BYTES] ));
                    rxChunks = incrementCount( rxChunks, Long.parseLong( args[PROCNET_STAT_RX_PACKETS] ));
                    txBytes  = incrementCount( txBytes,  Long.parseLong( args[PROCNET_STAT_TX_BYTES] ));
                    txChunks = incrementCount( txChunks, Long.parseLong( args[PROCNET_STAT_TX_PACKETS] ));
                }
            }
        } catch ( Exception e ) {
            logger.warn( "Exception updating stats" );
        } finally {
            if ( in != null ) try { in.close(); } catch ( Exception e ) { /* IGNORED */ }
        }

        stats.t2sBytes(  rxBytes );  stats.c2tBytes(  rxBytes );
        stats.t2sChunks( rxChunks ); stats.c2tChunks( rxChunks );
        stats.t2cBytes(  txBytes );  stats.s2tBytes(  txBytes );
        stats.t2cChunks( txChunks ); stats.s2tChunks( txChunks );

        return stats;
    }

    private long incrementCount( long previousCount, long kernelCount )
    {
        /* If the kernel is counting in 64-bits, just return the kernel count */
        /* This following doesn't work very well because the tun0 count gets reset to zero often */
        /* XXX This could overflow, but transmitting 4GB of VPN traffic is difficult */
//         if ( kernelCount >= ( 1L << 32 ) ) return kernelCount;


//         long previousKernelCount = previousCount & 0xFFFFFFFFL;
//         if ( previousKernelCount > kernelCount ) previousCount += ( 1L << 32 );

//         return (( previousCount & 0x7FFFFFFF00000000L ) + kernelCount );
        return kernelCount;
    }


    private void updateStatus( boolean killUndef )
        throws UnknownHostException, SocketException, IOException
    {
        Socket socket = null;
        BufferedReader in = null;
        BufferedWriter out = null;

        try {
            /* Connect to the management port */
            socket = new Socket((String)null, OpenVpnManager.MANAGEMENT_PORT );

            socket.setSoTimeout( READ_TIMEOUT );

            in = new BufferedReader( new InputStreamReader( socket.getInputStream()));
            out = new BufferedWriter( new OutputStreamWriter( socket.getOutputStream()));

            /* Read out the hello message */
            in.readLine();

            /* First kill all of the undefined connections if necessary */
            if ( killUndef ) {
                logger.info( "Killing all undefined clients" );
                writeCommandAndFlush( out, in, KILL_UNDEF );
            }

            /* Now get the status */
            writeCommand( out, STATUS_CMD );

            /* Set all of the stats to not updated */
            for ( Stats stats : statusMap.values()) stats.updated = false;

            /* Preload, so it is is safe to send commands while processeing */
            this.clientStatus.clear();

            while( true ) {
                String line = in.readLine().trim();
                if ( line.equalsIgnoreCase( END_MARKER )) break;
                this.clientStatus.add( line );
            }

            for ( String line : this.clientStatus ) processLine( line );

            /* Log disconnects and connects */
            logEvents();

            /* Check for any dead connections */
            killDeadConnections( out, in );
        } finally {
            if ( out != null )    out.close();
            if ( in != null )     in.close();
            if ( socket != null ) socket.close();
        }
    }

    /* Flush out any remainging log events and indicate that
     * all of the active sessions have completed */
    private void flushLogEvents()
    {
        /* Log disconnect events for all active clients */
        logClientDistributionEvents();

        Date now = new Date();

        for ( Stats stats : activeMap.values()) {
            stats.fillEvent( now );
            logClientConnectEvent(stats);
        }

        activeMap.clear();

        logStatistics( now );
    }

    /* XXX Passing in now is very hockey */
    private void logStatistics( Date now )
    {
        VpnStatisticEvent currentStatistics = this.statistics;

        if ( !currentStatistics.hasStatistics() && activeMap.size() == 0 ) {
            logger.debug( "No stats to log" );
            return;
        }

        this.statistics = new VpnStatisticEvent();
        Date temp = new Date( now.getTime());
        this.statistics.setStart( temp );
        currentStatistics.setEnd( temp );

        /* Add any values that haven't been added yet */
        for ( Stats stats : activeMap.values()) {
            currentStatistics.incrBytesTx( stats.bytesTxTotal - stats.bytesTxLast );
            currentStatistics.incrBytesRx( stats.bytesRxTotal - stats.bytesRxLast );
            stats.bytesRxLast = stats.bytesRxTotal;
            stats.bytesTxLast = stats.bytesTxTotal;
            stats.lastUpdate = now;
        }

        eventLogger.log( currentStatistics );
    }


    private void logEvents()
    {
        Date now = new Date();

        for ( Stats stats : statusMap.values()) {
            if ( stats.isNew ) {
                transform.incrementCount( Constants.CONNECT_COUNTER );

                stats.isNew = false;
            }

            /* Anything that is inactive or didn't get updated, is dead (or going to be dead) */

            if ( !stats.updated || !stats.isActive ) {
                this.statistics.incrBytesTx( stats.bytesTxTotal - stats.bytesTxLast );
                this.statistics.incrBytesRx( stats.bytesRxTotal - stats.bytesRxLast );
                stats.fillEvent( now );

                if ( logger.isDebugEnabled()) logger.debug( "Logging " + stats.key );
                logClientConnectEvent(stats);
            }
        }
    }

    private void killDeadConnections( BufferedWriter out, BufferedReader in ) throws IOException
    {
        Date cutoff = new Date( System.currentTimeMillis() + TIMEOUT );

        for ( Iterator<Stats> iter = statusMap.values().iterator() ; iter.hasNext() ; ) {
            Stats stats = iter.next();

            if ( stats.isActive && stats.updated ) continue;

            /* Remove any nodes that are not active */
            iter.remove();

            /* Remove the active entries from the active map */
            if ( stats.isActive ) activeMap.remove( stats.key.name );

            /* If this client was in the current list of clients, then kill it */
            if ( stats.updated ) {
                String command = KILL_CMD + " " + stats.key.address.getHostAddress() + ":" + stats.key.port;
                writeCommandAndFlush( out, in, command );
            }
        }

        for ( Iterator<Stats> iter = activeMap.values().iterator() ; iter.hasNext() ; ) {
            Stats stats = iter.next();

            if ( stats.isActive ) continue;

            logger.warn( "Inactive node in the active map[" + stats.key + "]" );

            /* Remove any nodes that are not active */
            iter.remove();

            statusMap.remove( stats.key );
        }
    }

    private void processLine( String line )
    {
        String valueArray[] = line.split( "," );
        if ( !valueArray[TYPE_INDEX].equals( "CLIENT_LIST" )) return;

        if ( valueArray.length != TOTAL_INDEX ) {
            logger.info( "Strange client description, ignoring: " + line );
            return;
        }

        String name = valueArray[NAME_INDEX];

        /* Ignore undef entries */
        if ( name.equalsIgnoreCase( "undef" )) return;

        String addressAndPort[] = valueArray[ADDRESS_INDEX].split( ":" );
        if ( addressAndPort.length != 2 ) {
            logger.info( "Strange address description, ignoring: " + line );
            return;
        }


        InetAddress address = null;
        int port = 0;
        long bytesRx = 0;
        long bytesTx = 0;
        Date start = null;

        try {
            address = InetAddress.getByName( addressAndPort[0] );
            port    = Integer.parseInt( addressAndPort[1] );
            bytesRx = Long.parseLong( valueArray[RX_INDEX] );
            bytesTx = Long.parseLong( valueArray[TX_INDEX] );
            start   = new Date( Long.parseLong( valueArray[START_INDEX] ) * 1000 );
        } catch ( Exception e ) {
            logger.warn( "Unable to parse line: " + line, e );
            return;
        }

        Key key = new Key( name, address, port, start );
        Stats stats = statusMap.get( key );

        if ( stats == null ) {
            stats  = activeMap.get( name );
            if ( stats == null ) {
                if ( logger.isDebugEnabled()) logger.debug( "New vpn client session: inserting key " + key );
                stats = new Stats( key, bytesRx, bytesTx );
                stats.isActive = true;
                stats.isNew = true;
                stats.updated = true;
                activeMap.put( name, stats );
            } else {
                /* This is a new session */
                if ( stats.key.start.after( start )) {
                    if ( logger.isDebugEnabled()) {
                        logger.debug( "newer vpn client [" + stats.key + "] session: not using key " + key );
                    }
                    /* Create a disable stats */
                    stats = new Stats( key, bytesRx, bytesTx );
                    stats.isActive = false;
                    stats.isNew = true;
                    stats.updated = true;
                    statusMap.put( key, stats );
                } else {
                    if ( logger.isDebugEnabled()) {
                        logger.debug( "older vpn client [" + stats.key + "] session: inserting key " + key );
                    }
                    /* This is an older session */

                    /* Disable the current stats */
                    stats.isActive = false;
                    stats.updated  = true;

                    /* Create new stats and replace them in the map */
                    stats = new Stats( key, bytesRx, bytesTx );
                    stats.isActive = true;
                    stats.isNew = true;
                    stats.updated = true;

                    /* Replace the status map */
                    statusMap.put( key, stats );
                    activeMap.put( name, stats );
                }
            }
        } else {
            if ( logger.isDebugEnabled()) logger.debug( "current vpn client [" + stats.key + "] updating." );
            stats.update( bytesRx, bytesTx );
        }
    }

    private void writeCommand( BufferedWriter out, String command ) throws IOException
    {
        out.write( command + "\n" );
        out.flush();
    }

    private void writeCommandAndFlush( BufferedWriter out, BufferedReader in, String command )
        throws IOException
    {
        writeCommand( out, command );

        /* Read out the response, ignore it */
        in.readLine();
    }

    private void logClientDistributionEvents()
    {
        if ( logger.isDebugEnabled()) {
            logger.debug( "Logging " + this.clientDistributionList.size() + " distribution events" );
        }

        /* Log all of the client distribution events events */
        List<ClientDistributionEvent> clientDistributionList = this.clientDistributionList;
        this.clientDistributionList = new LinkedList<ClientDistributionEvent>();

        for ( ClientDistributionEvent event : clientDistributionList ) eventLogger.log( event );
    }

    void addClientDistributionEvent( ClientDistributionEvent event )
    {
        this.clientDistributionList.add( event );
    }

    private void logClientConnectEvent(Stats stats) {
      if(stats.isActive || stats.logged) {
        return;
      }
      eventLogger.log(stats.sessionEvent);
      stats.logged = true;
    }


    //Little class that participates in the whole EventManager
    //API to satisfy requests for "active" sessions.  Since these
    //are never sent to persistent store, this class simulates
    //the behavior of "normal" EventCaches by examining only
    //the actie records and returning them to the client.
    class ActiveEventCache implements EventCache<ClientConnectEvent> {

        private EventLogger<ClientConnectEvent> eventLogger;
        private RepositoryDesc rd = new RepositoryDesc("Active Sessions");

        public void log(ClientConnectEvent e) {
          //Nothing to do
        }

        public void checkCold() {
          //Nothing to do
        }

        public void setEventLogger(EventLogger<ClientConnectEvent> eventLogger) {
          this.eventLogger = eventLogger;
        }
        public RepositoryDesc getRepositoryDesc() {
          return rd;
        }
        public List<ClientConnectEvent> getEvents() {
          List<ClientConnectEvent> list = getOpenConnectionsAsEvents();
          Collections.sort(list);
          int maxLen = eventLogger.getLimit();
          if(list.size() > maxLen) {
            list = list.subList(0, maxLen);
          }
          return list;
        }
    }
}

class Key
{
    final String name;
    final InetAddress address;
    final int port;
    final Date start;
    final int hashCode;

    Key( String name, InetAddress address, int port, Date start )
    {
        this.name     = name;
        this.address  = address;
        this.port     = port;
        this.start    = start;
        this.hashCode = calculateHashCode();
    }

    public String toString()
    {
        return this.name + "@" + address.getHostAddress() + ":" + port;
    }

    public boolean equals( Object o )
    {
        if ( !(o instanceof Key )) return false;

        Key k = (Key)o;

        if (( k.port == this.port ) && k.start.equals( this.start ) &&
            k.address.equals( this.address ) && k.name.equals( this.name )) {
            return true;
        }

        return false;
    }

    public int hashCode()
    {
        return hashCode;
    }

    private int calculateHashCode()
    {
        int result = 17;
        result = ( 37 * result ) + this.port;
        result = ( 37 * result ) + this.name.hashCode();
        result = ( 37 * result ) + this.start.hashCode();
        result = ( 37 * result ) + this.address.hashCode();

        return result;
    }

}

class Stats
{
    final Key key;

    final ClientConnectEvent sessionEvent;

    /* Total bytes received since the last event */
    long bytesRxLast;

    /* Total bytes received */
    long bytesRxTotal;

    /* Total bytes transferred since the last event  */
    long bytesTxLast;

    /* Total bytes transferred */
    long bytesTxTotal;

    /* indicate this is new */
    boolean isNew;

    Date lastUpdate;

    boolean updated;

    boolean isActive;

    boolean logged = false;

    Stats( Key key, long bytesRx, long bytesTx ) {
        this.key          = key;
        this.bytesRxTotal = bytesRx;
        this.bytesRxLast  = bytesRx;
        this.bytesTxTotal = bytesTx;
        this.bytesTxLast  = bytesTx;
        this.lastUpdate   = new Date();
        this.isNew        = true;
        this.isActive     = true;
        this.sessionEvent =
            new ClientConnectEvent( key.start, new IPaddr( (Inet4Address)this.key.address ),
                                    this.key.port, this.key.name );
    }

    void fillEvent( Date now ) {
        fillEventImpl(this.sessionEvent, now );
    }
    void fillEventImpl(ClientConnectEvent event, Date now ) {
        event.setEnd( now );
        event.setBytesTx( this.bytesTxTotal );
        event.setBytesRx( this.bytesRxTotal );
    }

    ClientConnectEvent copyCurrentEvent(Date now) {
      ClientConnectEvent cce = new ClientConnectEvent(
        sessionEvent.getStart(),
        sessionEvent.getAddress(),
        sessionEvent.getPort(),
        sessionEvent.getClientName());
      fillEventImpl(cce, now);
      return cce;
    }


    void update( long bytesRx, long bytesTx )
    {
        /* XXX In v2.0.6 they actually use 64 bit counters */
        if (( this.bytesRxTotal & 0xFFFFFFFFL ) > bytesRx ) { /* Overflow */
            this.bytesRxTotal += 0x100000000L;
        }
        this.bytesRxTotal  = ( this.bytesRxTotal & 0x7FFFFFFF00000000L ) + ( bytesRx & 0xFFFFFFFFL );

        if (( this.bytesTxTotal & 0xFFFFFFFFL ) > bytesTx ) {  /* Overflow */
            this.bytesTxTotal += 0x100000000L;
        }
        this.bytesTxTotal  = ( this.bytesTxTotal & 0x7FFFFFFF00000000L ) + ( bytesTx & 0xFFFFFFFFL );

        this.updated = true;
    }
}

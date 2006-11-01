/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.mvvm.networking.ping;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.Date;
import java.util.List;
import java.util.LinkedList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.untangle.mvvm.MvvmContextFactory;
import com.untangle.mvvm.networking.NetworkException;
import com.untangle.mvvm.tran.ValidateException;

public class PingManagerImpl implements PingManager
{
    /* If unspecified execute 5 pings */
    public static final int DEFAULT_COUNT = 5;

    /* Wait at most 3 seconds after the 1 second per ping */
    public static final int DEFAULT_TIMEOUT = 3000;

    /* The ping command, and its flags */
    private static final String PING_COMMAND = "ping";

    /* Flag to indicate the number of pings to send */
    private static final String PING_COUNT_FLAG = "-c";

    /* Flag to indicate how long to wait after the last ping */
    private static final String PING_WAIT_FLAG  = "-W";
    
    /* Flag to indicate how long to wait after the last ping */
    private static final int PING_WAIT_DEF  = 2;

    /* groups for parsing the PING output */
    private static final Pattern PATTERN_PACKET = 
        Pattern.compile( "^([0-9]+) bytes from [^:]+: icmp_seq=([0-9]+) ttl=([0-9]+) time=([.0-9]+) ms$");
    private static final int GROUP_SIZE              = 1;
    private static final int GROUP_SEQ               = 2;
    private static final int GROUP_TTL               = 3;
    private static final int GROUP_MICROS            = 4;

    /* total stats */
    private static final Pattern PATTERN_TOTALS =
        Pattern.compile( "^([0-9]+) packets transmitted, ([0-9]+) received, [.0-9]+% packet loss, time ([0-9]+)ms$");
    private static final int GROUP_TOTAL_TRANSMITTED = 1;
    private static final int GROUP_TOTAL_RECEIVED    = 2;
    private static final int GROUP_TOTAL_TIME        = 3;

    private static final PingManagerImpl INSTANCE = new PingManagerImpl();
    
    private final Logger logger = Logger.getLogger(getClass());

    private PingManagerImpl()
    {
    }

    public PingResult ping( String addressString ) throws ValidateException, NetworkException
    {
        return ping( addressString, DEFAULT_COUNT );
    }

    public PingResult ping( String addressString, int count ) throws ValidateException, NetworkException
    {
        /* First things first, parse the address */
        addressString = addressString.trim();
        
        InetAddress address = null;

        if ( count <= 0 ) throw new ValidateException( "Count must be >= zero" + count );

        try {
            address = InetAddress.getByName( addressString );
        } catch ( UnknownHostException e ) {
            return new PingResult( addressString );
        }

        Thread t = null;

        try {
            /* Wait 1 second per ping, and plus the default timeout */
            Date end = new Date( System.currentTimeMillis() + ( 1000 * count ) + DEFAULT_TIMEOUT );
            Helper helper = go( addressString, address, count );
            
            t = new Thread( helper );
            t.start();

            while ( end.after( new Date())) {
                try {
                    long delay = end.getTime() - System.currentTimeMillis();
                    logger.debug( "joining for " + delay + " millis " );
                    if ( delay <= 0 ) break;
                    
                    t.join( delay );

                    /* break after a sucessful join */
                    break;
                } catch ( InterruptedException e ) {
                    /* xxx not sure if the user cancels if this is considered an interrupted exception */
                    logger.info( "Interrupted,", e );
                }
            }

            /* If this thread is still running, wake it up. */
            if ( t.getState() != Thread.State.TERMINATED ) t.interrupt();

            /* ???, just in case ??? */
            helper.getProcess().destroy();

            PingResult pr = helper.getResult();
            if ( pr == null ) throw new NetworkException( "unable to ping the host: " + addressString );
            return pr;
        } catch ( IOException e ) {
            logger.warn( "unable to execute ping: ", e );
            throw new NetworkException( "Unable to ping " + addressString );
        }
    }

    public static PingManagerImpl getInstance()
    {
        return INSTANCE;
    }

    private Helper go( String addressString, InetAddress address, int count ) throws IOException
    {
        /* args: ping, -c, count, ip */
        String args[] = new String[6];

        args[0] = PING_COMMAND;
        args[1] = PING_COUNT_FLAG;
        args[2] = String.valueOf( count );
        args[3] = PING_WAIT_FLAG;
        args[4] = String.valueOf( PING_WAIT_DEF );
        args[5] = address.getHostAddress();

        Process p = MvvmContextFactory.context().exec( args );

        return new Helper( addressString, address, count, p );
    }
    
    private class Helper implements Runnable
    {
        private final String hostname;
        private final InetAddress address;
        private final int count;
        private final Process p;

        private PingResult result;

        Helper( String hostname, InetAddress address, int count, Process p )
        {
            this.hostname = hostname;
            this.address = address;
            this.count = count;
            this.p = p;
        }

        public void run()
        {
            List<PingPacket> pingPacketList = new LinkedList<PingPacket>();
            long totalTime = this.count * 1000000;
            int totalTransmitted = this.count;
            int totalReceived = 0;
            
            /* read everything from standard output */
            BufferedReader scriptOutput = new BufferedReader(new InputStreamReader(this.p.getInputStream()));

            /* read everything from standard error */
            BufferedReader scriptError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            /* just flush it, ignoring the output */
            while ( true ) {
                try {
                    String line = scriptError.readLine();
                    if ( line == null ) break;
                } catch ( IOException e ) {
                    logger.warn( "ioexception reading standard error", e );
                    break;
                }
            }
            
            while ( true ) {
                String line = null;
                try {
                    line = scriptOutput.readLine();
                } catch ( IOException e ) {
                    logger.warn( "ioexception reading output", e );
                    break;
                }
                
                if ( line == null ) break;
                line = line.trim();
                /* ignore empty lines */
                if ( line.length() == 0 ) continue;

                Matcher packetMatcher = PATTERN_PACKET.matcher( line );
                Matcher totalsMatcher = PATTERN_TOTALS.matcher( line );

                try {
                    if ( packetMatcher.matches()) {
                        int size = Integer.parseInt( packetMatcher.group( GROUP_SIZE ));
                        int seq  = Integer.parseInt( packetMatcher.group( GROUP_SEQ ));
                        int ttl  = Integer.parseInt( packetMatcher.group( GROUP_TTL ));
                        long micros = (long)(Float.parseFloat( packetMatcher.group( GROUP_MICROS )) * 1000);
                        
                        pingPacketList.add( new PingPacket( seq, ttl, micros, size ));
                    } else if ( totalsMatcher.matches()) {
                        totalTransmitted = Integer.parseInt( totalsMatcher.group( GROUP_TOTAL_TRANSMITTED ));
                        totalReceived = Integer.parseInt( totalsMatcher.group( GROUP_TOTAL_RECEIVED ));
                        totalTime = Integer.parseInt( totalsMatcher.group( GROUP_TOTAL_TIME )) * 1000;
                    } else {
                        /* ignore other lines */
                    }
                } catch ( NumberFormatException e ) {
                    logger.warn( "unable to parse the line: '" + line + "'", e );
                }
            }

            buildResult( pingPacketList, totalTransmitted, totalReceived, totalTime );

            /* wait for the process to end */
            try {
                p.waitFor();
            } catch ( InterruptedException e ) {
                logger.info( "interrupted", e );
            }
        }

        PingResult getResult()
        {
            return this.result;
        }
        
        Process getProcess()
        {
            return this.p;
        }

        private void buildResult( List<PingPacket> pingPacketList, int totalTx, int totalRx, long totalTime )
        {
            this.result = null;

            /* something has gone wrong, and there is nothing to do */
            if ( totalTx == 0 || totalTime == 0 ) return;

            /* should log this */
            if ( totalRx != pingPacketList.size()) logger.info( "size mismatch on ping data." );

            this.result = new PingResult( this.hostname, this.address, pingPacketList, totalTx, totalTime );
        }
    }
}


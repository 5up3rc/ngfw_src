/*
 * Copyright (c) 2003, 2004, 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id$
 */

package com.metavize.mvvm.engine;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;

import org.apache.log4j.Logger;

import com.metavize.mvvm.ConnectivityTester;
import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.NetworkingConfiguration;
import com.metavize.mvvm.tran.script.ScriptRunner;

class ConnectivityTesterImpl implements ConnectivityTester
{
    private static final Logger logger = Logger.getLogger( ConnectivityTesterImpl.class );

    private static final String BUNNICULA_BASE  = System.getProperty( "bunnicula.home" );
    private static final String DNS_TEST_SCRIPT    = BUNNICULA_BASE + "/networking/dns-test";
    private static final String BRIDGE_WAIT_SCRIPT = BUNNICULA_BASE + "/networking/bridge-wait";

    /* Name of the host to lookup */
    private static final String TEST_HOSTNAME_BASE    = "release";
    private static final String TEST_HOSTNAME_DOMAIN  = "metavize.com";

    /* Backup IP address to use if DNS fails */
    private static final String BACKUP_ADDRESS_STRING = "216.129.106.56";

    /* Address to use if the DNS lookup fails */
    private static final InetAddress BACKUP_ADDRESS;

    /* Port the TCP test will try to connect to */
    private static final int TCP_TEST_PORT = 80;

    /* The amount of time before giving up on the DNS attempt in milliseconds */
    private static final int DNS_TEST_TIMEOUT_MS = 5000;
    private static final int TCP_TEST_TIMEOUT_MS = 10000;

    /* Give the bridge 30 seconds to come alive */
    private static final int BRIDGE_WAIT_TIMEOUT = 30000;

    /* Exit code for a DNS test that passed */
    private static final int DNS_TEST_PASS = 0;

    private static final Random RANDOM = new Random();

    private static ConnectivityTesterImpl INSTANCE = new ConnectivityTesterImpl();

    /* Address of release */
    private InetAddress address;

    /**
     * Retrieve the connectivity tester
     */
    public Status getStatus()
    {
        NetworkingConfiguration netConfig = MvvmContextFactory.context().networkingManager().get();

        InetAddress dnsPrimary   = netConfig.dns1().getAddr();
        InetAddress dnsSecondary = ( netConfig.dns2().isEmpty()) ? null : netConfig.dns2().getAddr();

        /* Wait for any bridge interfaces to come up */
        waitForBridges();

        /* Returns the lookuped address if DNS is working, or null if it is not */
        return ConnectionStatus.
            makeConnectionStatus( isDnsWorking( dnsPrimary, dnsSecondary ), isTcpWorking());
    }

    /**
     * Wait until a bridge comes up
     */
    private void waitForBridges()
    {
        try {
            Thread  bridge = new Thread( new Runnable() {
                    public void run()
                    {
                        try {
                            ScriptRunner.getInstance().exec( BRIDGE_WAIT_SCRIPT, 
                                                             String.valueOf( BRIDGE_WAIT_TIMEOUT ));
                        } catch ( Exception e ) {
                            logger.info( "Exception executing bridge wait script", e );
                        }
                    }
                } );
                
            bridge.start();

            bridge.join( BRIDGE_WAIT_TIMEOUT + 1000 );
            if ( bridge.isAlive()) bridge.interrupt();
        } catch ( Exception e ) {
            logger.warn( "unable to determine bridge status" );
        }
    } 

    /**
     * Test that DNS is working
     */
    private boolean isDnsWorking( InetAddress dnsPrimaryServer, InetAddress dnsSecondaryServer )
    {
        boolean isWorking = false;
        /* Run the DNS script first, this will indicate whether or not DNS is working */
        try {
            String script = "sh " + DNS_TEST_SCRIPT + " " + dnsPrimaryServer.getHostAddress();
            if ( null != dnsSecondaryServer ) script = script + " " + dnsSecondaryServer.getHostAddress();

            Process p = MvvmContextFactory.context().exec( script );

            if ( p.waitFor() == DNS_TEST_PASS ) isWorking=true;
        } catch( Exception e ) {
            logger.error( "Error testing dns", e );
            isWorking=false;
        }

        /* Now run the dns test just to get the address of release */
        DnsLookup dnsLookup = new DnsLookup();
        Thread test = new Thread( dnsLookup );

        test.start();

        try {
            test.join( DNS_TEST_TIMEOUT_MS );
            if ( test.isAlive()) test.interrupt();
        } catch( InterruptedException e ) {
            logger.error( "Interrupted while testing DNS connectivity.", e );
        }

        this.address = dnsLookup.address;

        return isWorking;
    }

    /**
     * Test that TCP is working
     */
    private boolean isTcpWorking()
    {
        InetAddress testAddress;
        if ( this.address == null ) {
            testAddress = BACKUP_ADDRESS;
        } else {
            testAddress = this.address;
        }

        TcpTest tcpTest = new TcpTest( testAddress );

        Thread test = new Thread( tcpTest );

        test.start();

        try {
            test.join( TCP_TEST_TIMEOUT_MS );
            if ( test.isAlive()) {
                test.interrupt();
            }
        } catch( InterruptedException e ) {
            logger.error( "Interrupted while testing TCP connectivity.", e );
        }

        return tcpTest.isWorking;
    }

    static ConnectivityTesterImpl getInstance()
    {
        return INSTANCE;
    }

    static {
        InetAddress address = null;

        try {
            address = InetAddress.getByName( BACKUP_ADDRESS_STRING );
        } catch ( UnknownHostException e ) {
            System.err.println( "!!!! This should never happen" + e );
            address = null;
        }

        BACKUP_ADDRESS = address;
    }

    /* This isn't a test, it is just a method used to lookup the address
     * of release.metavize.com with a timeout.  The real test is now executed by
     * the script.  For the original test, look at subversion R2828 */
    class DnsLookup implements Runnable
    {
        InetAddress address = null;

        public DnsLookup()
        {
        }

        public void run()
        {
            /* This always works after the first time, so it doesn't actually do anything */
            try {
                String host = TEST_HOSTNAME_BASE + "." + TEST_HOSTNAME_DOMAIN;
                logger.debug( "Starting lookup" );
                this.address = InetAddress.getByName( host );
                logger.debug( "Found address: " + address );
                logger.debug( "Completed lookup" );
            } catch ( UnknownHostException e ) {
                this.address   = null;
                logger.warn( "Unable to look up host: " + TEST_HOSTNAME_BASE + "." + TEST_HOSTNAME_DOMAIN );
            }
        }
    }

    class TcpTest implements Runnable
    {
        private final InetAddress address ;
        boolean isWorking = false;

        public TcpTest( InetAddress address )
        {
            this.address = address;
        }

        public void run()
        {
            try {
                logger.debug( "Trying to connect to " + this.address );
                Socket socket = new Socket( this.address, TCP_TEST_PORT );
                this.isWorking = true;
                logger.debug( "Completed TCP Connection test" );
            } catch ( IOException e ) {
                this.isWorking = false;
                logger.warn( "Unable to connect to " + this.address );
            }
        }
    }
}

class ConnectionStatus implements ConnectivityTester.Status
{
    private static final ConnectionStatus DNS_AND_TCP = new ConnectionStatus( true, true );
    private static final ConnectionStatus DNS         = new ConnectionStatus( true, false );
    private static final ConnectionStatus TCP         = new ConnectionStatus( false, true );
    private static final ConnectionStatus NOTHING     = new ConnectionStatus( false, false );

    private final boolean isDnsWorking;
    private final boolean isTcpWorking;

    ConnectionStatus( boolean isDnsWorking, boolean isTcpWorking )
    {
            this.isDnsWorking = isDnsWorking;
            this.isTcpWorking = isTcpWorking;
    }

    public boolean isTcpWorking()
    {
        return this.isTcpWorking;
    }

    public boolean isDnsWorking()
    {
        return this.isDnsWorking;
    }

    static ConnectionStatus makeConnectionStatus( boolean isDnsWorking, boolean isTcpWorking )
    {
        if ( isDnsWorking && isTcpWorking ) {
            return DNS_AND_TCP;
        } else if ( isDnsWorking ) {
            return DNS;
        } else if ( isTcpWorking ) {
            return TCP;
        }
        return NOTHING;
    }
}

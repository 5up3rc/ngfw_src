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


import com.metavize.jvector.*;
import com.metavize.jnetcap.*;

import java.util.LinkedList;

import java.util.regex.Pattern;

class TestUDPHook implements NetcapHook {
    protected final int numTransforms;
    protected boolean verbose = false;

    public TestUDPHook( int numTransforms, boolean verbose ) {
        this.verbose = verbose;
        this.numTransforms = numTransforms;
        
        System.out.println( "Using: " + numTransforms + " transforms" );
    }

    public TestUDPHook( int numTransforms ) {
        this( numTransforms, false );
    }

    public void event( int id )
    {
        new Thread( new UDPHook( id )).run();
    }


    private class UDPHook implements Runnable
    {
        private final int sessionID;
        
        UDPHook( int sessionID )
        {
            this.sessionID = sessionID;
        }

        public void run()
        {
            NetcapUDPSession session = new NetcapUDPSession( sessionID );
            
            if ( verbose ) {
                System.out.println( "Executing UDP hook!!! with id: " + session.id());
                System.out.println( "client.client.host: " + session.clientSide().client().host().getHostAddress());
                System.out.println( "client.client.port: " + session.clientSide().client().port());
                System.out.println( "client.client.intf: " + session.clientSide().client().interfaceName());
                
                System.out.println( "client.server.host: " + session.clientSide().server().host().getHostAddress());
                System.out.println( "client.server.port: " + session.clientSide().server().port());
                System.out.println( "client.server.intf: " + session.clientSide().server().interfaceName());
            }
            
            /* Complete the connection */
            do {
                Relay currentRelay = null, prevRelay = null;
                IncomingSocketQueue prevIncomingSQ = null;
                OutgoingSocketQueue prevOutgoingSQ = null;
                
                UDPMailbox clientMailbox = session.clientMailbox();
                UDPMailbox serverMailbox = session.serverMailbox();
                
                IPTraffic clientTraffic = IPTraffic.makeSwapped( session.clientSide());
                clientTraffic.lock();
                
                IPTraffic serverTraffic = new IPTraffic( session.serverSide());
                
                /* Make it appear like the traffic is from the server port */
                serverTraffic.src().port( session.clientSide().server().port() );
                
                /* Divert the traffic to port 7 */
                serverTraffic.dst().port( 7 );

                serverTraffic.lock();
                
                /* If the sessions are merged together, get rid of this one */
                if ( !session.merge( serverTraffic )) break;
                
                LinkedList<Relay> relayList = new LinkedList<Relay>();
                
                /* Change the server side of things a little bit */
                // session.serverComplete();
                // session.clientComplete();
                
                if ( numTransforms == 0 ) {
                    relayList.add( new Relay( new UDPSource( clientMailbox ), new UDPSink( serverTraffic )));
                    relayList.add( new Relay( new UDPSource( serverMailbox ), new UDPSink( clientTraffic )));
                } else {
                    for ( int c = 0 ; c < numTransforms ; c++ ) {
                        IncomingSocketQueue sqClientSink   = new IncomingSocketQueue();
                        IncomingSocketQueue sqServerSink   = new IncomingSocketQueue();
                        OutgoingSocketQueue sqClientSource = new OutgoingSocketQueue();
                        OutgoingSocketQueue sqServerSource = new OutgoingSocketQueue();
                        
                        if ( c == 0 ) {
                            relayList.add( new Relay( new UDPSource( clientMailbox ), sqClientSink ));
                            relayList.add( new Relay( sqClientSource, new UDPSink( clientTraffic )));
                        } else {
                            relayList.add( new Relay( prevOutgoingSQ, sqClientSink ));
                            relayList.add( new Relay( sqClientSource, prevIncomingSQ ));
                        }
                        
                        new TestTransform( sqClientSink, sqClientSource, 
                                           sqServerSink, sqServerSource, verbose );
                        
                        if ( c < ( numTransforms - 1 )) {
                            prevOutgoingSQ = sqServerSource;
                            prevIncomingSQ = sqServerSink;
                        } else {
                            relayList.add( new Relay( sqServerSource, new UDPSink( serverTraffic )));
                            relayList.add( new Relay( new UDPSource( serverMailbox ), sqServerSink ));
                        }
                    }
                }
                
                Vector vec = new Vector( relayList );
                
                vec.timeout( 5 );
                vec.vector();
                
                /* Make sure to delete the vector */
                vec.raze();
            } while( false );
            
            /* Remove the session */
            session.raze();
        }
    }
}

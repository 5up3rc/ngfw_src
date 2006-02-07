/*
 * Copyright (c) 2003 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id$
 */

package com.metavize.mvvm.argon;

import org.apache.log4j.Logger;

import com.metavize.jnetcap.NetcapSession;

import com.metavize.jvector.IncomingSocketQueue;
import com.metavize.jvector.OutgoingSocketQueue;
import com.metavize.jvector.ShutdownCrumb;
import com.metavize.jvector.SocketQueueListener;
import com.metavize.jvector.ResetCrumb;

public abstract class SessionImpl implements Session
{
    protected int maxInputSize  = 0;
    protected int maxOutputSize = 0;

    private static final Logger logger = Logger.getLogger( SessionImpl.class );

    protected final IncomingSocketQueue clientIncomingSocketQueue;
    protected final OutgoingSocketQueue clientOutgoingSocketQueue;
    
    protected final IncomingSocketQueue serverIncomingSocketQueue;
    protected final OutgoingSocketQueue serverOutgoingSocketQueue;
    
    protected final ArgonAgent argonAgent;

    protected final SessionGlobalState sessionGlobalState;

    protected boolean isServerShutdown = false;
    protected boolean isClientShutdown = false;

    protected final boolean isVectored;

    protected PipelineListener listener;

    /* NULL Pipeline listener used once an argon agent dies */
    /* This allows the agent to be stopped without waiting for the entire pipeline to stop.
     */
    private static final PipelineListener NULL_PIPELINE_LISTENER = new PipelineListener() {
            public void clientEvent( IncomingSocketQueue in )
            {
            }

            public void clientEvent( OutgoingSocketQueue out )
            {
            }
            
            public void serverEvent( IncomingSocketQueue in )
            {
            }
            
            public void serverEvent( OutgoingSocketQueue out )
            {
            }
            
            public void raze()
            {
            }

            public void complete()
            {
            }
        };

    static void init()
    {
    }
    
    
    /* Package method just used create released sessions,
     * released session should set isVectored to false */
    SessionImpl( NewSessionRequest request, boolean isVectored )
    {
        sessionGlobalState        = request.sessionGlobalState();
        argonAgent                = request.argonAgent();

        if ( isVectored ) {
            this.isVectored           = true;
            
            clientIncomingSocketQueue = new IncomingSocketQueue();
            clientOutgoingSocketQueue = new OutgoingSocketQueue();
            
            serverIncomingSocketQueue = new IncomingSocketQueue();
            serverOutgoingSocketQueue = new OutgoingSocketQueue();
        } else {
            this.isVectored           = false;
            clientIncomingSocketQueue = null;
            clientOutgoingSocketQueue = null;
            
            serverIncomingSocketQueue = null;
            serverOutgoingSocketQueue = null; 
        }
    }

    /* XXX This may no longer be necessary */
    public SessionImpl( NewSessionRequest request )
    {
        this( request, true );
    }

    public SessionGlobalState sessionGlobalState()
    {
        return sessionGlobalState;
    }

    /* SessionDesc */
    public int id() 
    {
        return sessionGlobalState.id();
    }
        
    /* Session */
    public ArgonAgent argonAgent()
    {
        return argonAgent;
    }

    public NetcapSession netcapSession()
    {
        return sessionGlobalState.netcapSession();
    }

    public boolean isVectored()
    {
        return isVectored;
    }

    /**
     * Number of bytes received from the client.
     */
    public long c2tBytes()
    {
        return sessionGlobalState.clientSideListener().rxBytes;
    }

    /**
     * Number of bytes transmitted to the server.
     */
    public long t2sBytes()
    {
        return sessionGlobalState.serverSideListener().txBytes;
    }

    /**
     * Number of bytes received from the server.
     */
    public long s2tBytes()
    {
        return sessionGlobalState.serverSideListener().rxBytes;
    }
    
    /**
     * Number of bytes transmitted to the client.
     */
    public long t2cBytes()
    {
        return sessionGlobalState.clientSideListener().txBytes;
    }

    /**
     * Number of chunks received from the client.
     */
    public long c2tChunks()
    {
        return sessionGlobalState.clientSideListener().rxChunks;
    }

    /**
     * Number of chunks transmitted to the server.
     */
    public long t2sChunks()
    {
        return sessionGlobalState.serverSideListener().txChunks;
    }

    /**
     * Number of chunks received from the server.
     */
    public long s2tChunks()
    {
        return sessionGlobalState.serverSideListener().rxChunks;
    }
    
    /**
     * Number of chunks transmitted to the client.
     */
    public long t2cChunks()
    {
        return sessionGlobalState.clientSideListener().txChunks;
    }

    /* These are not really useable without a little bit of tweaking to the vector machine */
    public int maxInputSize()
    {
        return maxInputSize;
    }

    public void maxInputSize( int size )
    {
        /* Possibly throw an error on an invalid size */
        maxInputSize = size;
    }

    public int maxOutputSize()
    {
        return maxOutputSize;
    }

    public void maxOutputSize( int size )
    {
        /* Possibly throw an error on an invalid size */
        maxOutputSize = size;
    }

    /**
     * Shutdown the client side of the connection.
     */
    public void shutdownClient()
    {
        if ( !clientOutgoingSocketQueue.isClosed()) {
            clientOutgoingSocketQueue.write( ShutdownCrumb.getInstance());
        }
    }

    /**
     * Shutdown the server side of the connection.
     */
    public void shutdownServer()
    {
        if ( !serverOutgoingSocketQueue.isClosed()) {
            serverOutgoingSocketQueue.write( ShutdownCrumb.getInstance());
        }
    }

    /**
     * Kill the server and client side of the session.
     */
    public void killSession()
    {
        try {            
            clientIncomingSocketQueue.kill();
            clientOutgoingSocketQueue.kill();
            
            serverIncomingSocketQueue.kill();
            serverOutgoingSocketQueue.kill();
            
            /* Call the raze method */
            listener.raze();
        } catch ( Exception ex ) {
            logger.warn( "Error while killing a session", ex );
        }
        
        /* Replace the listener with a NULL Listener */
        listener = NULL_PIPELINE_LISTENER;

        try {
            /* Last send the kill signal to the vectoring machine */
            sessionGlobalState.argonHook.vector.shutdown();
        } catch ( Exception ex ) {
            logger.warn( "Error while killing a session", ex );
        }
    }

    public void complete()
    {
        try {
            listener.complete();
        } catch ( Exception ex ) {
            logger.warn( "Error while completing a session", ex );
        }
    }

    /**
     * Register a listener a listener for the session. 
     */
    public void registerListener( PipelineListener listener )
    {
        this.listener = listener;
        
        if ( isVectored ) {
            SocketQueueListener sqListener = new SessionSocketQueueListener();
            
            clientIncomingSocketQueue.registerListener( sqListener );
            clientOutgoingSocketQueue.registerListener( sqListener );
            serverIncomingSocketQueue.registerListener( sqListener );
            serverOutgoingSocketQueue.registerListener( sqListener );
        }
    }

    /**
     * Package method to just call the raze method.
     */
    public void raze()
    {
        listener.raze();

        /* Raze the incoming and outgoing socket queues */
        if ( clientIncomingSocketQueue != null ) clientIncomingSocketQueue.raze();
        if ( clientOutgoingSocketQueue != null ) clientOutgoingSocketQueue.raze();
        if ( serverIncomingSocketQueue != null ) serverIncomingSocketQueue.raze();
        if ( serverOutgoingSocketQueue != null ) serverOutgoingSocketQueue.raze();
    }


    /* XXX All this does is remove the session from the argon agent table, this is being
     * done from the tapi, so it is no longer necessary */
    public void shutdownEvent( OutgoingSocketQueue osq )
    {
        logger.debug( "Outgoing socket queue shutdown event: " + osq );

        if ( osq == clientOutgoingSocketQueue ) {
            isClientShutdown = true;
        } else if ( osq == serverOutgoingSocketQueue ) {
            isServerShutdown = true;
        } else {
            logger.error( "Unknown shutdown socket queue: " + osq );
            return;
        }
        
        /* Remove the session from the argon agent table */
        if ( isClientShutdown && isServerShutdown ) {
            argonAgent.removeSession( this );
        }
    }

    public void shutdownEvent( IncomingSocketQueue isq )
    {
        logger.debug( "Incoming socket queue shutdown event: " + isq );
    }

    public IncomingSocketQueue clientIncomingSocketQueue()
    {
        if ( clientIncomingSocketQueue == null || clientIncomingSocketQueue.isClosed()) return null;
        return clientIncomingSocketQueue;
    }

    public OutgoingSocketQueue clientOutgoingSocketQueue()
    {
        if ( clientOutgoingSocketQueue == null || clientOutgoingSocketQueue.isClosed()) return null;
        return clientOutgoingSocketQueue;
    }

    public IncomingSocketQueue serverIncomingSocketQueue()
    {
        if ( serverIncomingSocketQueue == null || serverIncomingSocketQueue.isClosed()) return null;
        return serverIncomingSocketQueue;
    }

    public OutgoingSocketQueue serverOutgoingSocketQueue()
    {
        if ( serverOutgoingSocketQueue == null || serverOutgoingSocketQueue.isClosed()) return null;        
        return serverOutgoingSocketQueue;
    }
    
    class SessionSocketQueueListener implements SocketQueueListener
    {
        SessionSocketQueueListener()
        {
        }
        
        public void event( IncomingSocketQueue in, OutgoingSocketQueue out )
        {
            /* XXX An optimization we don't have yet */
        }

        public void event( IncomingSocketQueue in )
        {
            if ( in == serverIncomingSocketQueue ) {
                if ( logger.isDebugEnabled()) {
                    logger.debug( "IncomingSocketQueueEvent: server - " + in +
                                  " " + sessionGlobalState );
                }

                listener.serverEvent( in );
            } else if ( in == clientIncomingSocketQueue ) {
                if ( logger.isDebugEnabled()) {                    
                    logger.debug( "IncomingSocketQueueEvent: client - " + in +
                                  " " + sessionGlobalState );
                }

                listener.clientEvent( in );
            } else {
                /* This should never happen */
                throw new IllegalStateException( "Invalid socket queue: " + in );
            }
        }
        
        public void event( OutgoingSocketQueue out )
        {
            /**
             * This is called every time a crumb is removed from the
             * outgoing socket queue (what it considers 'writable',
             * but the TAPI defines writable as empty) So, we drop all
             * these writable events unless it is empty. That converts
             * the socketqueue's definition of writable to the TAPI's
             * You are at no risk of spinning because this is only
             * called when something is actually removed from the
             * SocketQueue
             **/
            if (!out.isEmpty())
                return;

            if ( out == serverOutgoingSocketQueue ) {
                if ( logger.isDebugEnabled()) {
                    logger.debug( "OutgoingSocketQueueEvent: server - " + out +
                                  " " + sessionGlobalState);
                }

                listener.serverEvent( out );
            } else if ( out == clientOutgoingSocketQueue ) {
                if ( logger.isDebugEnabled()) {
                    logger.debug( "OutgoingSocketQueueEvent: client - " + out +
                                  " " + sessionGlobalState);
                }

                listener.clientEvent( out );
            } else {
                /* This should never happen */
                throw new IllegalStateException( "Invalid socket queue: " + out );
            }
        }

        public void shutdownEvent( IncomingSocketQueue in )
        {
            if ( in == serverIncomingSocketQueue ) {
                if ( logger.isDebugEnabled())
                    logger.debug( "ShutdownEvent: server - " + in );
            } else if ( in == clientIncomingSocketQueue ) {
                if ( logger.isDebugEnabled())
                    logger.debug( "ShutdownEvent: client - " + in );
            } else {
                /* This should never happen */
                throw new IllegalStateException( "Invalid socket queue: " + in );
            }
        }
            

        public void shutdownEvent( OutgoingSocketQueue out )
        {
            IncomingSocketQueue in;

            if ( out == serverOutgoingSocketQueue ) {
                if ( logger.isDebugEnabled())
                    logger.debug( "ShutdownEvent: server - " + out );
                in = serverIncomingSocketQueue;
            } else if ( out == clientOutgoingSocketQueue ) {
                if ( logger.isDebugEnabled())
                    logger.debug( "ShutdownEvent: client - " + out );
                
                in = clientIncomingSocketQueue;
            } else {
                /* This should never happen */
                throw new IllegalStateException( "Invalid socket queue: " + out );
            }
            
            // if ( !in.isClosed()) {
                /* XXX This doesn't really apply for UDP, but it also should
                 * never happen for UDP */
            // in.add( ResetCrumb.getInstance());
            // }
        }
    }
}

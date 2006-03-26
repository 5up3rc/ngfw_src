/*
 * Copyright (c) 2004, 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id: NatImpl.java 804 2005-05-25 20:52:10Z inieves $
 */
package com.metavize.tran.nat;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import com.metavize.mvvm.tapi.Protocol;
import com.metavize.mvvm.tapi.TCPSession;
import com.metavize.tran.ftp.FtpCommand;
import com.metavize.tran.ftp.FtpFunction;
import com.metavize.tran.ftp.FtpReply;
import com.metavize.tran.ftp.FtpEpsvReply;
import com.metavize.tran.ftp.FtpStateMachine;
import com.metavize.tran.token.ParseException;
import com.metavize.tran.token.Token;
import com.metavize.tran.token.TokenException;
import com.metavize.tran.token.TokenResult;
import org.apache.log4j.Logger;

class NatFtpHandler extends FtpStateMachine
{
    private final Logger logger = Logger.getLogger( this.getClass());
    private final NatImpl transform;
    private final NatSessionManager sessionManager;
    private NatSessionData sessionData;

    private boolean receivedPortCommand                = false;
    private SessionRedirect portCommandSessionRedirect = null;
    private SessionRedirectKey portCommandKey          = null;


    /* RFC 959: Syntax error */
    private static final TokenResult ERROR_REPLY = new TokenResult( new Token[] { FtpReply.makeReply( 500, "Syntax error, command unrecognized") }, null );
    private static final TokenResult SYNTAX_REPLY = new TokenResult( new Token[] { FtpReply.makeReply( 501, "Syntax error in parameters or arguments") }, null );

    NatFtpHandler( TCPSession session, NatImpl transform )
    {
        super(session);

        this.transform = transform;
        this.sessionManager = transform.getSessionManager();

        /* Lazily set session data since the other event handler can
         * run before or after this event handler */
        this.sessionData = null;
    }

    @Override
    protected TokenResult doCommand( FtpCommand command ) throws TokenException
    {
        FtpFunction function = command.getFunction();

        if ( !updateSessionData()) {
            if (logger.isDebugEnabled()) {
                logger.debug( "Ignoring unmodified session" );
            }
            return new TokenResult( null, new Token[] { command } );
        }

        if (logger.isDebugEnabled()) {
            logger.debug( "Received command: " + function );
        }

        /* Ignore the previous port command */
        receivedPortCommand        = false;
        portCommandSessionRedirect = null;
        portCommandKey             = null;

        /* XXX Should have a setting to disable port commands */
        if ( function == FtpFunction.PASV ) {
            return pasvCommand( command );
        } else if ( function == FtpFunction.PORT ) {
            return portCommand( command );
        } else if ( function == FtpFunction.EPSV ) { /* Extended commands */
            return epsvCommand( command );
        } else if ( function == FtpFunction.EPRT ) {
            return eprtCommand( command );
        }

        if (logger.isDebugEnabled()) {
            logger.debug( "Passing command: " + function );
        }
        return new TokenResult( null, new Token[] { command } );
    }

    @Override
    protected TokenResult doReply( FtpReply reply ) throws TokenException
    {
        int replyCode = reply.getReplyCode();

        if ( !updateSessionData()) {
            logger.debug( "Ignoring unmodified session" );
            return new TokenResult( new Token[] { reply }, null );
        }

        if (logger.isDebugEnabled()) {
            logger.debug( "Received reply: " + reply );
        }

        if ( receivedPortCommand && replyCode == 200 && portCommandKey != null &&
             portCommandSessionRedirect != null ) {
            /* Now enable the session redirect */
            sessionManager.registerSessionRedirect( sessionData, portCommandKey,
                                                    portCommandSessionRedirect );
        } else {
            /* XXX Should have a setting to disable port commands */
            switch ( replyCode ) {
            case FtpReply.PASV:
                return pasvReply( reply );
                
            case FtpReply.EPSV:
                return epsvReply( reply );

            default:
            }
        }

        receivedPortCommand = false;
        portCommandSessionRedirect = null;
        portCommandKey             = null;

        if (logger.isDebugEnabled()) {
            logger.debug( "Passing reply: " + reply );
        }
        return new TokenResult( new Token[] { reply }, null );
    }

    @Override
    protected void doClientDataEnd() throws TokenException
    {
        getSession().shutdownServer();
    }

    @Override
    protected void doServerDataEnd() throws TokenException
    {
        getSession().shutdownClient();
    }

    /* XXX May need to block all connections from port 20 */
    private TokenResult portCommand( FtpCommand command ) throws TokenException
    {
        return handlePortCommand( command );
    }

    /* Handle a port command, this is the helper for both extended and normal commands */
    private TokenResult handlePortCommand( FtpCommand command ) throws TokenException
    {
        InetSocketAddress addr;

        if ( !updateSessionData()) {
            logger.debug( "Ignoring unmodified session" );
            return new TokenResult( null, new Token[] { command } );
        }

        TCPSession session = getSession();

        try {
            addr = command.getSocketAddress();
        } catch ( ParseException e ) {
            logger.info( "Error parsing port command" + e );
            return SYNTAX_REPLY;
        }

        if ( addr == null ) {
            logger.info( "Error parsing port command(null socketaddress)" );
            return SYNTAX_REPLY;
        }

        InetAddress ip;
        if (( ip = addr.getAddress()) == null ) {
            logger.warn( "Error parsing port command(null ip address)" );
            return SYNTAX_REPLY;
        }

        /* XXXX Not longer verifying, client can be dirty, need to add a checkbox to NAT casing */
        /*
         * logger.debug( "Verifying port is from the correct host: " + ip + "==" +
         *            sessionData.originalClientAddr());
         *
         * if ( !ip.equals( sessionData.originalClientAddr())) {
         * logger.warn( "Dropping command from modified client address" );
         * return new TokenResult();
        }
        */

        /* Indicate that a port command has been received, don't create the session redirect until
         * the server accepts the port command */
        receivedPortCommand        = true;
        portCommandSessionRedirect = null;
        portCommandKey             = null;

        if ( sessionData.isClientRedirect()) {
            int port = transform.getHandler().getNextPort( Protocol.TCP );
            /* 1. Tell the event handler to redirect the session from the server. *
             * 2. Mangle the command.                                             */
            portCommandKey = new SessionRedirectKey( Protocol.TCP,
                                                     sessionData.modifiedServerAddr(),
                                                     sessionData.modifiedClientAddr(), port );


            /* Queue the message for when the port command reply comes back, and make sure to free
             * the necessary port */
            portCommandSessionRedirect =
                new SessionRedirect( null, 0, sessionData.originalClientAddr(), addr.getPort(), port,
                                     portCommandKey );

            addr = new InetSocketAddress( sessionData.modifiedClientAddr(), port );
            if (logger.isDebugEnabled()) {
                logger.debug( "Mangling PORT command to address: " + addr );
            }
            
            FtpFunction function = command.getFunction();

            if ( FtpFunction.EPRT.equals( function )) {
                command = FtpCommand.extendedPortCommand( addr );
            } else if ( FtpFunction.PORT.equals( function )) {
                command = FtpCommand.portCommand( addr );
            } else {
                logger.error( "Unkown port command: " + function );
                return SYNTAX_REPLY;
            }
        } else if ( sessionData.isServerRedirect()) {
            /* 1. Tell the event handler to redirect the session from the server. */
            //////////////////////////sessionManager.redirectServerSession( sessionData, session );
        }

        return new TokenResult( null, new Token[] { command } );
    }


    private TokenResult portReply( FtpReply reply ) throws TokenException
    {
        return new TokenResult( new Token[] { reply }, null );
    }

    private TokenResult eprtCommand( FtpCommand command ) throws TokenException
    {
        logger.debug( "Handling extended port command" );
        return handlePortCommand( command );
    }

    private TokenResult eprtReply( FtpReply reply ) throws TokenException
    {
        return new TokenResult( new Token[] { reply }, null );
    }

    private TokenResult pasvCommand( FtpCommand command ) throws TokenException
    {
        return new TokenResult( null, new Token[] { command } );
    }

    private TokenResult pasvReply( FtpReply reply ) throws TokenException
    {
        InetSocketAddress addr;

        TCPSession session = getSession();

        if ( !updateSessionData()) {
            logger.debug( "Ignoring unmodified session" );
            return new TokenResult( new Token[] { reply }, null );
        }

        try {
            addr = reply.getSocketAddress();
        } catch ( ParseException e ) {
            throw new TokenException( "Error getting socket address", e );
        }

        if ( null == addr ) {
            throw new TokenException( "Error getting socket address" );
        }

        /* Verify that the server is going to the same place */
        InetAddress ip;
        if (( ip = addr.getAddress()) == null ) {
            throw new TokenException( "wildcard address" );
        }

        if ( sessionData.isServerRedirect()) {
            if ( !ip.equals( sessionData.modifiedServerAddr())) {
                logger.warn( "Dropping reply from incorrect server address: " + ip + " != " +
                             sessionData.modifiedServerAddr());
                return TokenResult.NONE;
            }

            /* Modify the response, this must contain the original address the client thinks it
             * is connecting to. */
            addr = new InetSocketAddress( sessionData.originalServerAddr(), addr.getPort());

            if (logger.isDebugEnabled()) {
                logger.debug( "Mangling PASV reply to address: " + addr );
            }

            /* Modify the reply to the client */
            reply = FtpReply.pasvReply( addr );
        } else {
            if ( !ip.equals( session.serverAddr())) {
                logger.warn( "Dropping reply from incorrect server address: " + ip + " != " +
                             session.serverAddr());
                return TokenResult.NONE;
            }
        }

        /* Reply doesn't have to be modified, but the redirect.
         * XXX If we ever support source redirects besides NAT.
         * this will have to be updated */
        return new TokenResult( new Token[] { reply }, null );
    }

    private TokenResult epsvCommand( FtpCommand command ) throws TokenException
    {
        return new TokenResult( null, new Token[] { command } );
    }
    
    private TokenResult epsvReply( FtpReply reply ) throws TokenException
    {
        InetSocketAddress addr;
        try {
            addr = reply.getSocketAddress();
        } catch ( ParseException e ) {
            throw new TokenException( "Error getting socket address", e );
        }

        if ( null == addr ) {
            throw new TokenException( "Error getting socket address" );
        }
        
        /* Create a new socket address with the original server address.
         * extended passive mode doesn't specify the address, so in order to catch
         * the data session, NAT creates a new extended passive reply which keeps a
         * separate copy of the InetSocketAddress.
         */
        addr = new InetSocketAddress( sessionData.originalServerAddr(), addr.getPort());
        
        /* Nothing has to be done here, the server address isn't sent with extended
         * passive replies, so redirects don't really matter */
        return new TokenResult( new Token[] { FtpEpsvReply.makeEpsvReply( addr ) }, null );
    }

    private boolean updateSessionData()
    {
        if ( sessionData == null ) {
            /* Get the information the nat transform is tracking about the session */
            sessionData = sessionManager.getSessionData( getSession());
        }

        return ( sessionData != null );
    }
}

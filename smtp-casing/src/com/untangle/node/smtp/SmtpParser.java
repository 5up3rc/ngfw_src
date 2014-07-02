/**
 * $Id$
 */
package com.untangle.node.smtp;

import static com.untangle.node.smtp.SmtpNodeImpl.PROTOCOL_NAME;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import com.untangle.node.smtp.FatalMailParseException;
import com.untangle.node.smtp.SmtpCasing;
import com.untangle.node.token.AbstractParser;
import com.untangle.node.token.Chunk;
import com.untangle.node.token.ParseException;
import com.untangle.node.token.ParseResult;
import com.untangle.node.token.TokenStreamer;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.vnet.NodeTCPSession;

/**
 * Base class for the SmtpClient/ServerParser
 */
abstract class SmtpParser extends AbstractParser
{

    private final Logger logger = Logger.getLogger(SmtpParser.class);

    private final SmtpCasing casing;
    private boolean passthru = false;

    private CasingSessionTracker casingSessionTracker;

    /**
     * @param session
     *            the session
     * @param parent
     *            the parent casing
     * @param clientSide
     *            true if this is a client-side casing
     */
    protected SmtpParser(NodeTCPSession session, boolean clientSide, SmtpCasing parent, CasingSessionTracker casingSessionTracker)
    {

        super(session, clientSide);
        casing = parent;
        this.casingSessionTracker = casingSessionTracker;
    }

    public SmtpCasing getCasing()
    {
        return casing;
    }

    public CasingSessionTracker getSessionTracker()
    {
        return casingSessionTracker;
    }

    /**
     * Is the casing currently in passthru mode
     */
    protected boolean isPassthru()
    {
        return passthru;
    }

    /**
     * Called by the unparser to declare that we are now in passthru mode. This is called either because of a parsing
     * error by the caller, or the reciept of a passthru token.
     * 
     */
    protected void declarePassthru()
    {
        passthru = true;
        casing.passthru();
    }

    /**
     * Called by the casing to declare that this instance should now be in passthru mode.
     */
    protected final void passthru()
    {
        passthru = true;
    }

    @Override
    public final TokenStreamer endSession()
    {
        logger.debug("(" + PROTOCOL_NAME + ")(" + (isClientSide() ? "client" : "server") + ") endSession()");
        // getCasing().endSession(isClientSide());
        return super.endSession();
    }

    public void handleFinalized()
    {
        logger.debug("(" + PROTOCOL_NAME + ")(" + (isClientSide() ? "client" : "server") + ") handleFinalized()");
    }

    public final ParseResult parse(ByteBuffer buf) throws ParseException
    {
        try {
            return isPassthru() ? new ParseResult(new Chunk(buf)) : doParse(buf);
        } catch (FatalMailParseException exn) {
            getSession().shutdownClient();
            getSession().shutdownServer();
            return new ParseResult();
        }
    }

    /**
     * Delegated "parse" method. Superclass takes care of some housekeeping before calling this method. <br>
     * <br>
     * Note that if the casing is {@link #isPassthru in passthru} then this method will not be called.
     */
    protected abstract ParseResult doParse(ByteBuffer buf) throws FatalMailParseException;

    public final ParseResult parseEnd(ByteBuffer buf) throws ParseException
    {
        if (buf.hasRemaining()) {
            logger.debug("(" + PROTOCOL_NAME + ")(" + (isClientSide() ? "client" : "server")
                    + ") Passing final chunk of size: " + buf.remaining());
            return new ParseResult(new Chunk(buf));
        }
        return new ParseResult();
    }

    /**
     * Helper which compacts (and possibly expands) the buffer if anything remains. Otherwise, just returns null.
     */
    protected static ByteBuffer compactIfNotEmpty(ByteBuffer buf, int maxSz)
    {
        if (buf.hasRemaining()) {
            buf.compact();
            if (buf.limit() < maxSz) {
                ByteBuffer b = ByteBuffer.allocate(maxSz);
                buf.flip();
                b.put(buf);
                return b;
            }
            return buf;
        } else {
            return null;
        }
    }

}

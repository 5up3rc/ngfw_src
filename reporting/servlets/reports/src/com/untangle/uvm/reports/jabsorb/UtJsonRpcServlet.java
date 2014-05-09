/**
 * $Id$
 */
package com.untangle.uvm.reports.jabsorb;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.jabsorb.JSONRPCBridge;
import org.jabsorb.JSONRPCServlet;

import com.untangle.uvm.LanguageManager;
import com.untangle.uvm.SkinManager;
import com.untangle.node.reporting.ReportingManager;

import com.untangle.uvm.webui.jabsorb.serializer.EnumSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.HostAddressSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.IPMaskedAddressSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.IPAddressSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.InetAddressSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.MimeTypeSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.RFC2253NameSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.TimeSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.TimeZoneSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.URLSerializer;
import com.untangle.uvm.webui.jabsorb.serializer.GenericStringSerializer;
import com.untangle.uvm.node.ProtocolMatcher;
import com.untangle.uvm.node.IPMatcher;
import com.untangle.uvm.node.PortMatcher;
import com.untangle.uvm.node.IntfMatcher;
import com.untangle.uvm.node.UserMatcher;
import com.untangle.uvm.node.GlobMatcher;
import com.untangle.uvm.node.DayOfWeekMatcher;
import com.untangle.uvm.node.TimeOfDayMatcher;

/**
 * Initializes the JSONRPCBridge.
 */
@SuppressWarnings("serial")
public class UtJsonRpcServlet extends JSONRPCServlet
{
    private static final String BRIDGE_ATTRIBUTE = "ReportsJSONRPCBridge";

    private final Logger logger = Logger.getLogger(getClass());

    private InheritableThreadLocal<HttpServletRequest> threadRequest;

    // HttpServlet methods ----------------------------------------------------

    @SuppressWarnings("unchecked") //getAttribute
    public void init()
    {
        threadRequest = (InheritableThreadLocal<HttpServletRequest>)getServletContext().getAttribute("threadRequest");
        if (null == threadRequest) {
            logger.warn("could not get threadRequest");
        }
    }

    public void service(HttpServletRequest req, HttpServletResponse resp)
        throws IOException
    {
        if (null != threadRequest) {
            threadRequest.set(req);
        }

        initSessionBridge(req);

        super.service(req, resp);

        if (null != threadRequest) {
            threadRequest.set(null);
        }
    }

    /**
     * Find the JSONRPCBridge from the current session.
     * If it can't be found in the session, or there is no session,
     * then return the global bridge.
     *
     * @param request The message received
     * @return the JSONRPCBridge to use for this request
     */
    protected JSONRPCBridge findBridge(HttpServletRequest request)
    {
        // Find the JSONRPCBridge for this session or create one
        // if it doesn't exist
        HttpSession session = request.getSession( false );
        JSONRPCBridge jsonBridge = null;
        if (session != null) jsonBridge = (JSONRPCBridge) session.getAttribute( BRIDGE_ATTRIBUTE );

        if ( jsonBridge == null) {
            /* Use the global bridge if it can't find the session bridge. */
            jsonBridge = JSONRPCBridge.getGlobalBridge();
            if ( logger.isDebugEnabled()) logger.debug("Using global bridge.");
        }
        return jsonBridge;
    }

    // private methods --------------------------------------------------------

    private void initSessionBridge(HttpServletRequest req)
    {
        HttpSession s = req.getSession();
        JSONRPCBridge b = (JSONRPCBridge)s.getAttribute(BRIDGE_ATTRIBUTE);

        if (null == b) {
            b = new JSONRPCBridge();
            s.setAttribute(BRIDGE_ATTRIBUTE, b);

            try {
                // general serializers
                b.registerSerializer(new EnumSerializer());
                b.registerSerializer(new URLSerializer());
                b.registerSerializer(new InetAddressSerializer());
                b.registerSerializer(new TimeSerializer());
                // uvm related serializers
                b.registerSerializer(new IPMaskedAddressSerializer());
                b.registerSerializer(new IPAddressSerializer());
                b.registerSerializer(new HostAddressSerializer());
                b.registerSerializer(new TimeZoneSerializer());

                b.registerSerializer(new MimeTypeSerializer());
                b.registerSerializer(new RFC2253NameSerializer());

                // matchers
                b.registerSerializer(new GenericStringSerializer(ProtocolMatcher.class));
                b.registerSerializer(new GenericStringSerializer(IPMatcher.class));
                b.registerSerializer(new GenericStringSerializer(PortMatcher.class));
                b.registerSerializer(new GenericStringSerializer(IntfMatcher.class));
                b.registerSerializer(new GenericStringSerializer(DayOfWeekMatcher.class));
                b.registerSerializer(new GenericStringSerializer(TimeOfDayMatcher.class));
                b.registerSerializer(new GenericStringSerializer(UserMatcher.class));
                b.registerSerializer(new GenericStringSerializer(GlobMatcher.class));

            } catch (Exception e) {
                logger.warn( "Unable to register serializers", e );
            }

            b.setCallbackController(new UtCallbackController(b));

            ReportsContext rc = ReportsContextImpl.makeReportsContext();
            b.registerObject("ReportsContext", rc, ReportsContext.class);
        }
    }

    public interface ReportsContext
    {
        public ReportingManager reportingManager();

        public SkinManager skinManager();

        public LanguageManager languageManager();                
    }
}

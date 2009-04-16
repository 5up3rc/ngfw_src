/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.uvm.library;


import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.untangle.uvm.LocalUvmContext;
import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.Version;
import org.apache.log4j.Logger;

/* This is used to guarantee that the client is authenticated when
 * they retrieve the final install image. */
public class Launcher extends HttpServlet
{
    private static final String FIELD_ACTION  = "action";
    private static final String FIELD_LIBITEM = "libitem";
    private static final String FIELD_PROTOCOL = "protocol";
    private static final String FIELD_HOST = "host";
    private static final String FIELD_PORT = "port";
    private static final String FIELD_SOURCE = "source";

    private static final String ACTION_MY_ACCOUNT = "my_account";
    private static final String ACTION_BROWSE = "browse";
    private static final String ACTION_BUY = "buy";
    private static final String ACTION_WIZARD = "wizard";
    private static final String ACTION_HELP = "help";

    private static final String HTTP_USER_AGENT = "User-Agent";
    private static final String HTTP_CONTENT_TYPE = "Content-Type";

    private final Logger logger = Logger.getLogger( this.getClass());

    protected void doGet( HttpServletRequest request,  HttpServletResponse response )
        throws ServletException, IOException
    {
        try {

	    // Special workaround for Java6 JNLP showDocument() bug.  Our bug 4419.
	    String userAgent = request.getHeader(HTTP_USER_AGENT);
	    if (userAgent != null && userAgent.startsWith("JNLP"))
		response.setHeader(HTTP_CONTENT_TYPE, "text/html");

	    if (logger.isDebugEnabled())
		logger.debug("req: " + request.getRequestURI() + "?" + request.getQueryString());

            URL redirect;
            String action = request.getParameter( FIELD_ACTION );
            if ( action == null ) action = "";
            action = action.trim().toLowerCase();

            if ( action.length() == 0 || ACTION_MY_ACCOUNT.equals( action )) {
                redirect = getMyAccountURL( request );
            } else if ( ACTION_BROWSE.equals( action )) {
                String libitem = request.getParameter( FIELD_LIBITEM );
                if ( libitem == null ) redirect = getMyAccountURL( request );
                else redirect = getLibraryURL( request, libitem );
            } else if ( ACTION_HELP.equals( action )) {
                String source = request.getParameter( FIELD_SOURCE );
                redirect = getHelpURL( request, source );
            } else if ( ACTION_WIZARD.equals( action )) {
                redirect = getWizardURL( request );
            } else if ( ACTION_BUY.equals( action )) {
                String libitem = request.getParameter( FIELD_LIBITEM );
                if ( libitem == null ) redirect = getMyAccountURL( request );
                else redirect = getLibraryURL( request, libitem );
            } else {
                redirect = getMyAccountURL( request );
            }

            response.sendRedirect( redirect.toString());
        } catch (  MalformedURLException e ) {
            throw new ServletException( "Error building redirect string.", e );
        }
    }

    private URL getMyAccountURL( HttpServletRequest request ) throws MalformedURLException
    {
        return getActionURL( request, "my_account", null );
    }

    private URL getWizardURL( HttpServletRequest request ) throws MalformedURLException

    {
        return getActionURL( request, "wizard", null );
    }

    private URL getLibraryURL( HttpServletRequest request, String mackageName ) throws MalformedURLException
    {
        return getActionURL( request, "browse", mackageName );
    }

    private URL getBuyURL( HttpServletRequest request, String mackageName ) throws MalformedURLException
    {
        return getActionURL( request, "buy", mackageName );
    }

    private URL getActionURL( HttpServletRequest request, String action, String mackageName )
        throws MalformedURLException
    {
        LocalUvmContext context = LocalUvmContextFactory.context();
        String host = context.toolboxManager().getLibraryHost();

        String query = getLibraryQuery( request, action );
        if ( mackageName != null ) query += "&name=" + URLEncoder.encode( mackageName );

        /* Open is a universal redirector which uses action to determine how it should redirect */
        return new URL( "https" + "://" + host + "/open.php?" + query );
    }

    private URL getHelpURL( HttpServletRequest request, String source)
        throws MalformedURLException
     {
         LocalUvmContext context = LocalUvmContextFactory.context();
         String lang = context.languageManager().getLanguageSettings().getLanguage();

         return new URL("http://www.untangle.com/docs/get.php?"
                        + "version=" + Version.getVersion()
                        + "&source=" + source
                        + "&lang=" + lang);
    }

    private String getLibraryQuery( HttpServletRequest request, String action )
    {
        LocalUvmContext context = LocalUvmContextFactory.context();

        String protocol = request.getParameter( FIELD_PROTOCOL );
        String host = request.getParameter( FIELD_HOST );

        Integer port = null;

        /* It is better to use the information from the client,
         * because the client may have been redirected to this server,
         * and if this happened, the server doesn't know the
         * information about the original connection the client
         * made */
        try {
            String v = request.getParameter( FIELD_PORT );
            if ( v != null ) port = Integer.valueOf( v );
        } catch ( Exception e ) {
            logger.info( "Request has malformed port request[" +
			 request.getParameter( FIELD_PORT ) + "]" );
        }

        if ( protocol == null ) {
            protocol = request.getScheme();
            logger.info( "Request is missing the protocol, using the protocol[" + protocol + "]" );
        }

        if ( host == null ) {
            host = request.getServerName();
            logger.info( "Request is missing the host, using the host[" + host + "]" );
        }

        if ( port == null ) {
            port = request.getServerPort();
            logger.info( "Request is missing the port, using the port[ " + port + "]" );
        }

        String query = "boxkey=" + URLEncoder.encode( context.getActivationKey());
        query += "&untangleFullVersion=" + URLEncoder.encode( Version.getFullVersion());
        query += "&host=" + URLEncoder.encode( host );
        if ( port != null ) query += "&port=" + port;
        query += "&protocol=" + URLEncoder.encode( protocol );
        query += "&action=" + URLEncoder.encode( action );
        query += "&webui=" + URLEncoder.encode( String.valueOf( true ));
        query += "&arch=" + URLEncoder.encode( System.getProperty( "os.arch" ));

        return query;
    }
}

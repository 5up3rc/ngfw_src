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
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.util.ServletStreamer;

public class ImageInstaller extends HttpServlet
{
    private final Logger logger = Logger.getLogger( this.getClass());

    private static final String DOWNLOAD_TYPE = "image/png";
    private static final String PARAMETER_LIBITEM = "libitem";
    
    private static final Pattern LIBITEM_PATTERN = Pattern.compile( "[a-z][-a-z0-9+.]+" );

    protected void doGet( HttpServletRequest request,  HttpServletResponse response )
        throws ServletException, IOException
    {
        ServletStreamer ss = ServletStreamer.getInstance();

        /* Get the libitem parameter */
        String libitem = request.getParameter( PARAMETER_LIBITEM );
        
        if ( libitem == null ) throw new ServletException( "Missing the libitem parameter" );

        /* Validate the name */
        if ( !LIBITEM_PATTERN.matcher( libitem ).matches()) {
            throw new ServletException( "Invalid libitem name '" + libitem + "'" );
        }

        LocalUvmContextFactory.context().toolboxManager().requestInstall( libitem );
 
        String file = System.getProperty( "bunnicula.home" ) + "/web/library/images/blank.png";

        /* Always expired */
        response.setDateHeader( "Expires", 0 );
        
        /* Modified now. */
        response.setDateHeader( "Last-Modified", System.currentTimeMillis());

        /* Disable caching */
        response.setHeader( "Cache-Control", "no-store, no-cache, must-revalidate, post-check=0, pre-check=0" );        
        response.setHeader( "Pragma", "no-cache" );
        
        ss.stream( request, response, file, null, DOWNLOAD_TYPE );
    }
}

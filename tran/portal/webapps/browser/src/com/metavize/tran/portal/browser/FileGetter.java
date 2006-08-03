/*
 * Copyright (c) 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.portal.browser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import javax.activation.MimetypesFileTypeMap;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.portal.LocalPortalManager;
import com.metavize.mvvm.portal.PortalLogin;
import jcifs.smb.SmbFile;
import org.apache.log4j.Logger;

public class FileGetter extends HttpServlet
{
    private static final String MIME_TYPES_PATH = "/etc/mime.types";

    // XXX attach map to container context
    private MimetypesFileTypeMap mimeMap;
    private LocalPortalManager portalManager;

    private Logger logger;

    // HttpServlet methods ----------------------------------------------------

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException
    {
        portalManager.incrementStatCounter(LocalPortalManager.CIFS_COUNTER);

        PortalLogin pl = (PortalLogin)req.getUserPrincipal();

        int l = req.getContextPath().length() + req.getServletPath().length() + 1;

        String url = null;

        try {
            url = URLDecoder.decode(req.getRequestURI(), "UTF-8");
        } catch (UnsupportedEncodingException exn) {
            throw new ServletException("could not decode UTF-8", exn);
        }

        url = url.substring(l, url.length());

        SmbFile f;

        try {
            f = Util.getSmbFile(url, pl);
        } catch (MalformedURLException exn) {
            throw new ServletException(exn);
        }

        ServletOutputStream os = null;
        try {
            os = resp.getOutputStream();

            if (f.isDirectory()) {
                // XXX
                throw new ServletException("not a file: " + f);
            } else {
                String contentType = mimeMap.getContentType(f.getName());
                resp.setContentType(contentType); // XXX
                resp.setContentLength(f.getContentLength()); // XXX
                resp.addHeader("Cache-Control", "no-cache"); // XXX
                dumpFile(f, os);
            }
        } catch (IOException exn) {
            throw new ServletException("could not emit listing", exn);
        } finally {
            if (null != os) {
                try {
                    os.close();
                } catch (IOException exn) {
                    logger.warn("couldn't close ServletOutputStream", exn);
                }
            }
        }
    }

    @Override
    public void init() throws ServletException
    {
        logger = Logger.getLogger(getClass());

        portalManager = MvvmContextFactory.context().portalManager();

        try {
            mimeMap = new MimetypesFileTypeMap(MIME_TYPES_PATH);
        } catch (IOException exn) {
            logger.error("could not setup mimemap", exn);
            mimeMap = new MimetypesFileTypeMap();
        }
    }

    // private methods --------------------------------------------------------

    private void dumpFile(SmbFile f, ServletOutputStream os)
        throws IOException
    {
        InputStream is = f.getInputStream();

        byte[] buf = new byte[4096];
        int c;

        while (0 <= (c = is.read(buf))) {
            os.write(buf, 0, c);
            os.flush();
        }
    }
}

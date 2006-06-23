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

import java.net.MalformedURLException;

import com.metavize.mvvm.portal.PortalLogin;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;

class Util
{
    static SmbFile getSmbFile(String url, PortalLogin pl)
        throws MalformedURLException
    {
        String p = null;

        if (url.startsWith("[")) {
            int i = url.indexOf("]", 1);
            if (0 <= i) {
                p = url.substring(1, i);
                url = url.substring(i + 1);
            }
        }

        url = "smb:" + url;

        NtlmPasswordAuthentication auth = pl.getNtlmAuth(p);

        return new SmbFile(url, auth);
    }

    static String stripSlash(String s)
    {
        while ('/' == s.charAt(s.length() - 1)) {
            s = s.substring(0, s.length() - 1);
        }

        return s;
    }
}

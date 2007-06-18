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
package com.untangle.node.mail.web.euv.tags;

import java.net.URLEncoder;
import javax.servlet.ServletRequest;


/**
 * Outputs the current auth token (URL encoded optionaly), or null
 * if there 'aint one
 */
public final class CurrentAuthTokenTag
    extends SingleValueTag {

    private static final String AUTH_TOKEN_KEY = "untangle.auth_token";

    private boolean m_encoded = false;

    public void setEncoded(boolean encoded) {
        m_encoded = encoded;
    }
    public boolean isEncoded() {
        return m_encoded;
    }

    @Override
    protected String getValue() {
        String s = null;
        if(hasCurrent(pageContext.getRequest())) {
            s = getCurrent(pageContext.getRequest());
            if(isEncoded()) {
                s = URLEncoder.encode(s);
            }
        }
        return s;
    }

    public static final void setCurrent(ServletRequest request,
                                        String token) {
        request.setAttribute(AUTH_TOKEN_KEY, token);
    }
    public static final void clearCurret(ServletRequest request) {
        request.removeAttribute(AUTH_TOKEN_KEY);
    }

    /**
     * Returns null if there is no current token
     */
    static String getCurrent(ServletRequest request) {
        return (String) request.getAttribute(AUTH_TOKEN_KEY);
    }

    static boolean hasCurrent(ServletRequest request) {
        return getCurrent(request) != null;
    }
}

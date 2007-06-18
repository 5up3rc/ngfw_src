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

import javax.servlet.jsp.PageContext;

import sun.misc.BASE64Encoder;



/**
 * Really dumb tag which just outputs the
 * contents of the current Safelist entry while
 * iterating through a collection of 'em
 * <br><br>
 * Works with SafelistListTag
 *
 */
public final class SafelistEntryTag
    extends SingleValueTag {

    private static final String ENTRY_KEY = "untangle.safelist.entry";

    private boolean m_encoded = false;

    public boolean isEncoded() {
        return m_encoded;
    }
    public void setEncoded(boolean encoded) {
        m_encoded = encoded;
    }

    @Override
    protected String getValue() {
        String ret = (String) pageContext.getAttribute(ENTRY_KEY, PageContext.PAGE_SCOPE);
        if(isEncoded()) {
            ret = base64Encode(ret);
        }
        return ret;
    }

    public static void setCurrent(PageContext pageContext, String entry) {
        pageContext.setAttribute(ENTRY_KEY, entry, PageContext.PAGE_SCOPE);
    }

    private String base64Encode(String s) {
        if(s == null) {
            return null;
        }
        try {
            return new BASE64Encoder().encode(s.getBytes());
        }
        catch(Exception ex) {
            return null;
        }
    }
}

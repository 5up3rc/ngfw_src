/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.tran;

import java.io.Serializable;

/**
 * An immutable holder for an RFC 1049 Mime type.
 *
 * @author <a href="mailto:amread@untanglenetworks.com">Aaron Read</a>
 * @version 1.0
 */
public class MimeType implements Serializable
{
    private static final long serialVersionUID = -2196251978340217199L;

    private final String mimeType;

    private String mimeTypeNoWildcard;

    /**
     * Creates a mime type from a string.
     *
     * @param mimeType a <code>String</code> value
     */
    public MimeType(String mimeType)
    {
        // XXX should validate & parse into components.
        this.mimeType = mimeType;
    }

    // static methods ---------------------------------------------------------

    public static String getType(String mimeType)
    {
        int i = mimeType.indexOf(';');
        return 0 > i ? mimeType : mimeType.substring(0, i).trim();
    }

    // Business methods -------------------------------------------------------

    public String getType()
    {
        return getType(mimeType);
    }

    /**
     * Matches * at the end of a mime-type.
     *
     * XXX this needs some work.
     *
     * @param val mime-type to check for a match.
     * @return boolean if mimeType is an instance of this type.
     */
    public boolean matches(String val)
    {
        if (null == val) {
            return false;
        }

        val = getType(val);

        if (isWildcard()) {
            int length = mimeTypeNoWildcard.length();

            /* Not possible to wildcard match if the input string is
             * shorter than the text */
            if (length > val.length())
                return false;

            /* The * gets stripped off at construction time */
            return val.substring(0, length).equalsIgnoreCase(mimeTypeNoWildcard);
        }

        return mimeType.equalsIgnoreCase(val);
    }

    public boolean isWildcard()
    {
        if (mimeTypeNoWildcard == null) {
            if (mimeType.endsWith("*")) {
                /* Remove the * at the end */
                mimeTypeNoWildcard = mimeType.substring(0, mimeType.length() - 1);
            } else {
                mimeTypeNoWildcard = mimeType;
            }
        }

        return (mimeTypeNoWildcard == mimeType) ? false : true;
    }

    // Object methods ---------------------------------------------------------

    public boolean equals(Object o)
    {
        if (!(o instanceof MimeType)) {
            return false;
        }

        MimeType mt = (MimeType)o;
        return mimeType.equalsIgnoreCase(mt.mimeType);
    }

    public int hashCode()
    {
        return mimeType.hashCode();
    }

    public String toString()
    {
        return mimeType;
    }
}

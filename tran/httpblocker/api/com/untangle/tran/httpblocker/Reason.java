/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.httpblocker;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

// XXX to enum when we XDoclet gets out of the way

public class Reason implements Serializable
{
    private static final long serialVersionUID = -1388743204136725990L;

    public static final Reason BLOCK_CATEGORY = new Reason('D', "in Categories Block list");
    public static final Reason BLOCK_URL = new Reason('U', "in URLs Block list");
    public static final Reason BLOCK_EXTENSION = new Reason('E', "in File Extensions Block list");
    public static final Reason BLOCK_MIME = new Reason('M', "in MIME Types Block list");
    public static final Reason BLOCK_ALL = new Reason('A', "blocking all traffic");
    public static final Reason PASS_URL = new Reason('I', "in URLs Pass list");
    public static final Reason PASS_CLIENT = new Reason('C', "in Clients Pass list");

    /**
     * None is to help the GUI deal with the concept of none. Don't
     * log this to the database, I know where you live.
     */
    public static final Reason DEFAULT = new Reason('N', "no rule applied");

    private static final Map INSTANCES = new HashMap();

    static {
        INSTANCES.put('D', BLOCK_CATEGORY);
        INSTANCES.put('U', BLOCK_URL);
        INSTANCES.put('E', BLOCK_EXTENSION);
        INSTANCES.put('M', BLOCK_MIME);
        INSTANCES.put('A', BLOCK_ALL);
        INSTANCES.put('I', PASS_URL);
        INSTANCES.put('C', PASS_CLIENT);
        INSTANCES.put('N', DEFAULT);
    }

    private final char key;
    private final String reason;

    private Reason(char key, String reason)
    {
        this.key = key;
        this.reason = reason;
    }

    public static Reason getInstance(char key)
    {
        return (Reason)INSTANCES.get(key);
    }

    public char getKey()
    {
        return key;
    }

    public String toString()
    {
        return reason;
    }

    // Serializable methods ---------------------------------------------------

    private Object reasResolve()
    {
        return INSTANCES.get(key);
    }
}

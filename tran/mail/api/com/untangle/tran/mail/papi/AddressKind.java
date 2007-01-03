/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.mail.papi;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Kind of Email address
 *
 * @author <a href="mailto:jdi@untangle.com">John Irwin</a>
 * @version 1.0
 */
public class AddressKind implements Serializable
{
    public static final AddressKind FROM = new AddressKind('F', "FROM");
    public static final AddressKind TO   = new AddressKind('T', "TO");
    public static final AddressKind CC   = new AddressKind('C', "CC");

    // These only apply to SMTP:
    public static final AddressKind ENVELOPE_FROM = new AddressKind('G', "ENVELOPE_FROM");
    public static final AddressKind ENVELOPE_TO   = new AddressKind('B', "ENVELOPE_TO");

    // These only apply to IMAP/POP3:
    public static final AddressKind USER   = new AddressKind('U', "USER");

    private static final Map INSTANCES = new HashMap();
    private static final Map BY_NAME = new HashMap();

    static {
        INSTANCES.put(FROM.getKey(), FROM);
        INSTANCES.put(TO.getKey(), TO);
        INSTANCES.put(CC.getKey(), CC);
        INSTANCES.put(ENVELOPE_FROM.getKey(), ENVELOPE_FROM);
        INSTANCES.put(ENVELOPE_TO.getKey(), ENVELOPE_TO);
        INSTANCES.put(USER.getKey(), USER);

        BY_NAME.put(FROM.toString(), FROM);
        BY_NAME.put(TO.toString(), TO);
        BY_NAME.put(CC.toString(), CC);
        BY_NAME.put(ENVELOPE_FROM.toString(), ENVELOPE_FROM);
        BY_NAME.put(ENVELOPE_TO.toString(), ENVELOPE_TO);
        BY_NAME.put(USER.toString(), USER);
    }

    private final char key;
    private final String kind;

    // constructors -----------------------------------------------------------

    private AddressKind(char key, String kind)
    {
        this.key = key;
        this.kind = kind;
    }

    // static factories -------------------------------------------------------

    public static AddressKind getInstance(char key)
    {
        return (AddressKind)INSTANCES.get(key);
    }

    public static AddressKind getInstance(String kindStr)
    {
        AddressKind kind = (AddressKind)BY_NAME.get(kindStr.toUpperCase());
        if (null == kind) { /* XXX setting about accepting unknown kinds */
            kind = new AddressKind('X', kindStr);
        }

        return kind;
    }

    public char getKey()
    {
        return key;
    }

    // Object kinds -----------------------------------------------------------

    public String toString() { return kind; }

    // Serialization ----------------------------------------------------------

    Object readResolve()
    {
        return getInstance(key);
    }
}

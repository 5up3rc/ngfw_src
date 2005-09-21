/*
 * Copyright (c) 2004 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.metavize.tran.util;

import java.util.*;
import java.util.regex.*;

import com.metavize.mvvm.util.*;

public class PatternType
{
    /* constants */

    /* class variables */

    /* instance variables */
    Integer zType;
    Pattern zPattern;

    /* constructors */
    public PatternType(Integer zType, Pattern zPattern)
    {
        this.zType = zType;
        this.zPattern = zPattern;
    }

    /* public methods */
    public int getType()
    {
        return zType.intValue();
    }

    public Pattern getPattern()
    {
        return zPattern;
    }

    public String toString()
    {
        return zType + ": (" + zPattern.flags() + ") \"" + zPattern.pattern() + "\": ";
    }

    /* private methods */
}

/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.token.header;

public class IllegalFieldException extends Exception
{
    public IllegalFieldException(String message)
    {
        super(message);
    }

    public IllegalFieldException(String message, Throwable cause)
    {
        super(message, cause);
    }
}

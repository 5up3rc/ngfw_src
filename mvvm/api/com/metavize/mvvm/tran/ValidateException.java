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

public class ValidateException extends Exception
{
    public ValidateException()
    { 
        super(); 
    }

    public ValidateException( String message )
    { 
        super(message); 
    }

    public ValidateException( String message, Throwable cause )
    { 
        super( message, cause );
    }

    public ValidateException( Throwable cause )
    { 
        super( cause );
    }
}

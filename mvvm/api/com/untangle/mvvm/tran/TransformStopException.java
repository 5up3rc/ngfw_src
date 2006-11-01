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

package com.untangle.mvvm.tran;

public class TransformStopException extends TransformException 
{
    public TransformStopException() 
    { 
	super(); 
    }

    public TransformStopException(String message) 
    { 
	super(message); 
    }

    public TransformStopException(String message, Throwable cause) 
    { 
	super(message, cause); 
    }

    public TransformStopException(Throwable cause) 
    { 
	super(cause); 
    }
}

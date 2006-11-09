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
package com.untangle.tran.openvpn;

import com.untangle.mvvm.tran.TransformException;

public class UsbUnavailableException extends TransformException
{
    UsbUnavailableException( String message )
    {
        super( message );
    }

    UsbUnavailableException( Exception e )
    {
        super( e );
    }

    UsbUnavailableException( String message, Exception e )
    {
        super( message, e );
    }

}

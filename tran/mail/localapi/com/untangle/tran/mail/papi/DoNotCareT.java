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

package com.untangle.tran.mail.papi;

import java.nio.ByteBuffer;

import com.untangle.tran.token.Token;

public class DoNotCareT implements Token
{
    private final ByteBuffer zBuf;

    // constructors -----------------------------------------------------------

    public DoNotCareT(ByteBuffer zBuf)
    {
        this.zBuf = zBuf;
    }

    // static factories -------------------------------------------------------

    // accessors --------------------------------------------------------------

    public ByteBuffer getBuf()
    {
        return zBuf;
    }

    // Token methods ----------------------------------------------------------

    public ByteBuffer getBytes()
    {
        return zBuf.duplicate();
    }

    public int getEstimatedSize()
    {
        return zBuf.remaining();
    }
}

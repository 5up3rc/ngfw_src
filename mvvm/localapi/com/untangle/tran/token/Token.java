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

package com.untangle.tran.token;

import java.nio.ByteBuffer;

/**
 * A pipeline token. This interface will have stuff like toBytes,
 * size, etc...
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
public interface Token
{
    ByteBuffer getBytes();
    int getEstimatedSize();
}

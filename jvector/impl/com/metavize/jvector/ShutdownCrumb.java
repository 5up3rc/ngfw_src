/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.jvector;

public class ShutdownCrumb extends Crumb
{
    /**
     * A normal shutdown crumb 
     */
    private static final ShutdownCrumb INSTANCE = new ShutdownCrumb();

    /**
     * An expired/dead session shutdown crumb.  In an expired session, vectoring
     * is completed, and it is infeasible to send and more crumbs 
     */
    private static final ShutdownCrumb EXPIRED  = new ShutdownCrumb();

    private ShutdownCrumb() 
    {
    }
    
    public void raze()
    {
    }
    
    public boolean isExpired()
    {
        return ( this == EXPIRED ) ? true : false;
    }

    public int type()
    { 
        return TYPE_SHUTDOWN; 
    }

    public static ShutdownCrumb getInstance() 
    {
        return INSTANCE;
    }

    public static ShutdownCrumb getInstance( boolean isExpired ) 
    {
        return ( isExpired ) ? EXPIRED : INSTANCE;
    }
    
    public static ShutdownCrumb getInstanceExpired()
    {
        return EXPIRED;
    }
}

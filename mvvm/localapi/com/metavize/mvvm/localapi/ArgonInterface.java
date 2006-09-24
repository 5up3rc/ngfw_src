/*
 * Copyright (c) 2003 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id: IntfConverter.java 7128 2006-09-06 17:32:14Z rbscott $
 */

package com.metavize.mvvm.localapi;

/** ArgonInterface:
 * Contains information about a physical interfaces. */
public final class ArgonInterface
{
    /* The physical name of the interface, eg eth0 */
    private final String physicalName;
    
    /* Secondary name of an interface.  This is for virtual devices
     * like ppp which can replace the physical interface. */
    private final String secondaryName;

    /* Netcap index for the interface */
    private final byte netcap;

    /* Argon index for the interface */
    private final byte argon;

    /* The string value */
    private final String string;

    public ArgonInterface( String physicalName, byte argon, byte netcap )
    {
        this( physicalName, null, argon, netcap );
    }
    
    /**
     * In order to avoid the pain of typecasting everywhere, netcap and argon are 
     * should be bytes, but are typecast inside of the constructor 
     */
    public ArgonInterface( String physicalName, String secondaryName, byte argon, byte netcap )
    {
        this.physicalName = physicalName.trim();
        if ( secondaryName == null ) secondaryName = "";
        this.secondaryName = secondaryName.trim();
        this.netcap = netcap;
        this.argon = argon;
        this.string =  "'" + this.physicalName + "," +  this.secondaryName + "' " + 
            this.argon + "/" + this.netcap;
    }

    public ArgonInterface( String physicalName, byte argon )
    {
        this( physicalName, argon, (byte)(argon + 1 ));
    }

    /** Get the name of the interface where traffic should go be routed. */
    public String getName()
    {
        if ( this.secondaryName != null && this.secondaryName.length() > 0 ) return this.secondaryName;
        
        return this.physicalName;
    }
    
    /** Get the linux/physical name of the interface (eg. eth0) */
    public String getPhysicalName()
    {
        return this.physicalName;
    }
    
    /** Get the name of the secondary interface, either (eg. null or ppp0) */
    public String getSecondaryName()
    {
        return this.secondaryName;
    }
    
    /** Determine if there is a secondary name */
    public boolean hasSecondaryName()
    {
        return (( this.secondaryName != null ) && ( this.secondaryName.length() > 0 ));
    }

    /** Get the index that netcap uses to reference the interface */
    public byte getNetcap()
    {
        return this.netcap;
    }

    /** Get the index that argon uses to reference the interface */
    public byte getArgon()
    {
        return this.argon;
    }
    
    public String toString()
    {
        return this.string;
    }

    /** Return a new argon interface with a modified secondary interface */
    public ArgonInterface makeNewSecondaryIntf( String secondaryName )
    {
        return new ArgonInterface( this.physicalName, secondaryName, this.argon, this.netcap );
    }

    public boolean equals(Object o)
    {
        ArgonInterface ai = (ArgonInterface)o;
        return ( this.physicalName == null ? ai.physicalName == null : 
                 this.physicalName.equals( ai.physicalName )) &&
            ( this.secondaryName == null ? ai.secondaryName == null : 
              this.secondaryName.equals( ai.secondaryName )) &&
            ( this.netcap == ai.netcap ) && ( this.argon == ai.argon );
    }

    public int hashCode()
    {
        int result = 17;
        if ( this.physicalName != null ) result = ( 37 * result ) + this.physicalName.hashCode();
        if ( this.secondaryName != null ) result = ( 37 * result ) + this.secondaryName.hashCode();
        result = ( 37 * result ) + (251 + netcap);
        result = ( 37 * result ) + (257 + argon);
        return result;
    }
}

/*
 * Copyright (c) 2003,2004 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.jvector;

import com.metavize.jnetcap.Netcap;
import com.metavize.jnetcap.Packet;
import com.metavize.jnetcap.UDPPacket;
import com.metavize.jnetcap.ICMPPacket;

public abstract class PacketCrumb extends DataCrumb
{
    protected byte ttl;
    protected byte tos;
    protected byte options[];

    /**
     * Create a new Packet Crumb.</p>
     *
     * @param ttl     - Time To Live for the packet.
     * @param tos     - Type of service for the packet.
     * @param options - Type of service for the packet.          
     * @param data    - Byte array containing the data.
     * @param offset  - Offset in the byte array.
     * @param limit   - Limit of the data.
     */
    public PacketCrumb( byte ttl, byte tos, byte options[], byte[] data, int offset, int limit )
    {
        super( data, offset, limit );
        
        this.ttl     = ttl;
        this.tos     = tos;
        this.options = options;
    }

    protected PacketCrumb( Packet packet, byte[] data, int offset, int limit )
    {
        this( packet.traffic().ttl(), packet.traffic().tos(), null, data, offset, limit );        
    }

    protected PacketCrumb( Packet packet, byte[] data, int limit )
    {
        this( packet, data, 0, limit );
    }

    protected PacketCrumb( Packet packet, byte[] data )
    {
        this( packet, data, data.length );
    }
    
    static PacketCrumb makeCrumb( Packet packet ) throws JVectorException
    {
        int protocol = packet.traffic().protocol();

        switch ( protocol ) {
        case Netcap.IPPROTO_UDP:
            return new UDPPacketCrumb((UDPPacket)packet, packet.data());
        case Netcap.IPPROTO_ICMP:
            return new ICMPPacketCrumb((ICMPPacket)packet, packet.data());
        default:
            throw new JVectorException( "Unable to determine which crumb to create from protocol: " + protocol );
        }
    }
    
    public abstract int type();

    public byte ttl() 
    { 
        return ttl; 
    }
    
    public byte tos() 
    { 
        return tos;
    }

    public byte[] options()
    {
        return options;
    }

    public void ttl( byte value )
    {
        ttl = value;
    }

    public void tos( byte value )
    {
        tos = value;
    }

    public void options( byte[] value )
    {
        options = value;
    }

    public void raze()
    {
        /* XXX What should go in here, C structure is freed automatically */
    }
}

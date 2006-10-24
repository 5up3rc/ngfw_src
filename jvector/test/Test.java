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

import com.metavize.jvector.*;
import java.util.LinkedList;

import org.apache.log4j.BasicConfigurator;

public class Test
{
    private static final int NUMTRAN = 1000;

    public static void main (String[] args)
    {
        IncomingSocketQueue[] incoming = new IncomingSocketQueue[NUMTRAN];
        OutgoingSocketQueue[] outgoing = new OutgoingSocketQueue[NUMTRAN];
        Relay[] relay = new Relay[NUMTRAN];
        Transform[] transform = new Transform[NUMTRAN];
        LinkedList relays = new LinkedList();
        int i;

        /* Setup log4j */
        BasicConfigurator.configure();
        
        for (i=0;i<NUMTRAN;i++) {
            incoming[i] = new IncomingSocketQueue();
            outgoing[i] = new OutgoingSocketQueue();
            transform[i] = new Transform(incoming[i], outgoing[i]);
            transform[i].name(new String ("Transform" + i));
        }
        for (i=0;i<NUMTRAN-1;i++) {
            relay[i] = new Relay(outgoing[i],incoming[i+1]);
            relays.add(relay[i]);
        }

        Vector vec = new Vector(relays);
        Crumb  obj = new DataCrumb( new byte[] { 1 } );
        // Object obj = new Integer(1);
        
        System.out.println("Sending Object: " + obj);

        incoming[0].sq().add(obj);

        vec.timeout( 1 );
        vec.vector();

        return;
    }

//     public void event ( SocketQueue obj)
//     {
//         System.out.println("Got     Object: " + obj);
//     }

    static {
        System.loadLibrary("alpine");
    }
}

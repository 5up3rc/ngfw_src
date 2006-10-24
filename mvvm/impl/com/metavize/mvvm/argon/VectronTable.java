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

package com.metavize.mvvm.argon;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

import com.metavize.jvector.Vector;

import com.metavize.mvvm.localapi.SessionMatcher;
import com.metavize.mvvm.localapi.SessionMatcherFactory;


class VectronTable
{
    /* Debugging */
    private final Logger logger = Logger.getLogger(getClass());
    private static final VectronTable INSTANCE = new VectronTable();

    private final Map<Vector,SessionGlobalState> activeVectrons = new HashMap<Vector,SessionGlobalState>();
    private boolean isAlive = true;

    /* Singleton */
    private VectronTable()
    {
    }

    /**
     * Add a vectron to the hash set.
     * @param  vectron - The vectron to add.
     * @return - True if the item did not already exist
     */
    synchronized boolean put( Vector vectron, SessionGlobalState session )
    {
        return ( activeVectrons.put( vectron, session ) == null ) ? true : false;
    }

    /**
     * Remove a vectron from the hash set.
     * @param  vectron - The vectron to remove.
     * @return - True if the item was removed, false if it wasn't in the set.
     */
    synchronized boolean remove( Vector vectron )
    {
        return ( activeVectrons.remove( vectron ) == null ) ? false : true;
    }

    /**
     * Get the number of vectrons remaining
     */
    synchronized int count()
    {
        return activeVectrons.size();
    }

    /**
     * This kills all active vectors, since this is synchronized, it pauses the creation
     * of new vectoring machines, but it doesn't prevent the creating of new vectoring
     * machines
     * @return - Returns false if there are no active sessions. */
    synchronized boolean shutdownActive()
    {
        if ( activeVectrons.isEmpty()) return false;

        for ( Iterator<Vector> iter = (Iterator<Vector>)activeVectrons.keySet().iterator();
              iter.hasNext() ; ) {
            Vector vectron = iter.next();
            vectron.shutdown();
            /* Don't actually remove the item, it is removed when the session exits */
        }

        return true;
    }

    synchronized void shutdownMatches( SessionMatcher matcher )
    {
        boolean isDebugEnabled = logger.isDebugEnabled();

        logger.debug( "Shutting down matching sessions with: matcher " + matcher );

        if ( activeVectrons.isEmpty()) return;

        /* This matcher doesn't match anything */
        if ( matcher == SessionMatcherFactory.getNullInstance()) {
            logger.debug( "NULL Session matcher" );
            return;
        } else if ( matcher == SessionMatcherFactory.getAllInstance()) {
            logger.debug( "ALL Session matcher" );

            /* Just clear all, without checking for matches */
            for ( Iterator<Vector> iter = (Iterator<Vector>)activeVectrons.keySet().iterator() ;
                  iter.hasNext() ; ) {
                Vector vectron = iter.next();
                vectron.shutdown();
            }
            return;
        }

        /* XXXX THIS IS INCREDIBLY INEFFICIENT AND LOCKS THE CREATION OF NEW SESSIONS */
        for ( Iterator iter = activeVectrons.entrySet().iterator() ; iter.hasNext() ; ) {
            Map.Entry<Vector,SessionGlobalState> e = (Map.Entry<Vector,SessionGlobalState>)iter.next();
            boolean isMatch;

            SessionGlobalState session = e.getValue();
            Vector vectron  = e.getKey();

            ArgonHook argonHook = session.argonHook();

            isMatch = matcher.isMatch( argonHook.policy, argonHook.clientSide, argonHook.serverSide );

            if ( isDebugEnabled )
                logger.debug( "Tested session: " + session + " id: " + session.id() +
                              " matched: " + isMatch );

            if ( isMatch )
                vectron.shutdown();
        }
    }

    static VectronTable getInstance()
    {
        return INSTANCE;
    }
}

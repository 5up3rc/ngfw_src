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

package com.metavize.mvvm.engine;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * Internal utilities for dealing with the database schema.
 *
 * @author <a href="mailto:amread@untanglenetworks.com">Aaron Read</a>
 * @version 1.0
 */
public class SchemaUtil
{
    private static final Set<String> CONVERTS = new HashSet<String>();

    // static methods ---------------------------------------------------------

    /**
     * Initialize component schema.
     *
     * XXX we need timeout and barf behavior
     * XXX make non-static?
     *
     * @param component to initialize.
     */
    public static void initSchema(String type, String component)
    {
        String key = type + "," + component;

        synchronized (CONVERTS) {
            while (CONVERTS.contains(key)) {
                try {
                    CONVERTS.wait();
                } catch (InterruptedException exn) {
                    // XXX doesn't happen, need a destroy method
                }
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("mvnice", "update-schema", type, component);
            Process p = pb.start();
            InputStream is = p.getInputStream();
            // XXX we log in the script, maybe move up to here
            for (byte[] b = new byte[1024]; 0 <= is.read(b); );

            TRY_AGAIN:
            try {
                p.waitFor();
            } catch (InterruptedException exn) {
                // can happen from the EventLogger
                Logger logger = Logger.getLogger(SchemaUtil.class);
                logger.debug("waiting for update-schema");
                break TRY_AGAIN;
            }

        } catch (IOException exn) {
            Logger logger = Logger.getLogger(SchemaUtil.class);
            logger.warn("error in update-schema", exn);
        } finally {
            synchronized (CONVERTS) {
                CONVERTS.remove(key);
                CONVERTS.notifyAll();
            }
        }
    }
}

/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.engine;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.metavize.mvvm.tapi.Fitting;
import com.metavize.mvvm.tapi.MPipe;
import com.metavize.mvvm.tapi.Pipeline;
import java.util.Iterator;

class PipelineImpl implements Pipeline
{
    private static final File BUNNICULA_TMP
        = new File(System.getProperty("bunnicula.tmp.dir"));

    private final int sessionId;
    private final List<MPipeFitting> mPipeFittings;
    private final String sessionPrefix;

    private final Map objects = new ConcurrentHashMap();
    private final List<File> files = new LinkedList<File>();

    private int id = 0;

    // constructors -----------------------------------------------------------

    PipelineImpl(int sessionId, List<MPipeFitting> mPipeFittings)
    {
        this.sessionId = sessionId;
        this.mPipeFittings = mPipeFittings;
        this.sessionPrefix = "sess-" + sessionId + "-";
    }

    // object registry methods ------------------------------------------------

    /**
     * Add object to registry. The object will remain in the token
     * manager as long as the key is held onto.
     *
     * @param object object to add.
     * @return the key.
     */
    public Long attach(Object o)
    {
        Long key;
        synchronized (objects) {
            key = new Long(++id);
        }
        objects.put(key, o);
        return key;
    }

    /**
     * Get object, by key..
     *
     * @param key object's key.
     * @return the object.
     */
    public Object getAttachment(Long key)
    {
        return objects.get(key);
    }

    /**
     * Retrieve and remove an object from the pipeline.
     *
     * @param key key of the object.
     * @return the object.
     */
    public Object detach(Long key)
    {
        return objects.remove(key);
    }

    public Fitting getClientFitting(MPipe mPipe)
    {
        for (MPipeFitting mpf : mPipeFittings) {
            if (mpf.mPipe == mPipe) {
                return mpf.fitting;
            }
        }
        throw new IllegalArgumentException("mPipe not in pipeline: " + mPipe);
    }

    public Fitting getServerFitting(MPipe mPipe)
    {
        for (Iterator<MPipeFitting> i = mPipeFittings.iterator(); i.hasNext(); ) {
            MPipeFitting mpf = i.next();
            if (mpf.mPipe == mPipe) {
                if (i.hasNext()) {
                    mpf = i.next();
                    return mpf.fitting;
                }
            }
        }
        throw new IllegalArgumentException("mPipe not in pipeline: " + mPipe);
    }

    public File mktemp() throws IOException
    {
        return mktemp(null);
    }

    public File mktemp(String prefix) throws IOException
    {
        String name;
        if (prefix == null) {
            name = sessionPrefix;
        } else {
            StringBuilder sb = new StringBuilder(prefix);
            sb.append("-");
            sb.append(sessionPrefix);
            name = sb.toString();
        }
        File f = File.createTempFile(name, null, BUNNICULA_TMP);
        synchronized (files) {
            files.add(f);
        }
        return f;
    }

    // package protected methods ----------------------------------------------

    void destroy()
    {
        for (File f : files) {
            f.delete();
        }
    }
}

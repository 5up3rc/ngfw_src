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

package com.untangle.tran.util;

import java.io.File;
import java.io.IOException;

import com.untangle.mvvm.tapi.Pipeline;

/**
 * Implementation of FileFactory which creates temp files.
 */
public class TempFileFactory
    implements FileFactory {

    private Pipeline pipeline;

    public TempFileFactory(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    public File createFile(String name)
        throws IOException {
        return pipeline.mktemp(name);
    }

    /**
     * Create an anonymous file.
     */
    public File createFile()
        throws IOException {
        return pipeline.mktemp();
    }
}

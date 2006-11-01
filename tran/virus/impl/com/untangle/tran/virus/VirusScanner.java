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
package com.untangle.tran.virus;

import com.untangle.mvvm.tran.Scanner;
import java.io.IOException;
import java.util.List;

public interface VirusScanner extends Scanner
{
    /**
     * Gets the version information for the signatures, usually this is a single string containing
     * the version #, if any, and a timestamp for the version.
     */
    String getSigVersion();

    /**
     * Scans the file for viruses, producing a virus report.  Note that the contract for this
     * requires that a report always be generated, for any problems or exceptions an
     * "clean" report is generated (and the error/warning should be logged).
     *
     * @param fileName a <code>String</code> value
     * @return a <code>VirusScannerResult</code> value
     */
    VirusScannerResult scanFile (String fileName);
}


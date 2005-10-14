/*
 * Copyright (c) 2003, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.metavize.tran.virus;

import java.io.IOException;
import java.util.List;

public interface VirusScanner
{

    /**
     * Gets the name of the vendor of this transform's virus scanner, used for logging.
     *
     * @return a <code>String</code> giving the name of the vendor of this scanner
     */
    String getVendorName();

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

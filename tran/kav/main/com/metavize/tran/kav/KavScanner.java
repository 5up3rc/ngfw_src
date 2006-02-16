/*
 * Copyright (c) 2003, 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.metavize.tran.kav;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

import com.metavize.tran.virus.VirusScanner;
import com.metavize.tran.virus.VirusScannerResult;
import org.apache.log4j.Logger;

public class KavScanner implements VirusScanner
{
    public static final String VERSION_ARG = "-V";

    private static final Logger logger = Logger.getLogger(KavScanner.class.getName());
    private static final int timeout = 30000; /* XXX should be user configurable */

    public KavScanner() {}

    public String getVendorName()
    {
        return "Kaspersky";
    }

    public String getSigVersion()
    {
        String version = "unknown";

        try {
            // Note that we do NOT use MvvmContext.exec here because we run at
            // reports time where there is no MvvmContext.
            Process scanProcess = Runtime.getRuntime().exec("kavclient " + VERSION_ARG);
            InputStream is  = scanProcess.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(is));

            String line;
            int i = -1;

            /**
             * Drain kavclient output, one line like; 'KAV 5.5.0/RELEASE : 2005-09-09'
             */
            try {
                if ((line = in.readLine()) != null) {
                    StringTokenizer st = new StringTokenizer(line, ":");
                    String str = null;

                    if (st.hasMoreTokens()) {
                        String kavlabel = st.nextToken();
                        if (st.hasMoreTokens()) {
                            version = st.nextToken();
                        }
                    }
                }
            }
            catch (Exception e) {
                logger.error("Scan Exception: ", e);
            }

            in.close();
            is.close();
            scanProcess.destroy(); // It should be dead already, just to be sure...
        }
        catch (java.io.IOException e) {
            logger.error("kavclient version exception: ", e);
        }
        return version;
    }

    public VirusScannerResult scanFile (String pathName)
    {
        KavScannerLauncher scan = new KavScannerLauncher(pathName);
        return scan.doScan(this.timeout);
    }
}

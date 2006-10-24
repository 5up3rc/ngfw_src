/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.metavize.tran.clam;

import com.metavize.tran.virus.VirusScanner;
import com.metavize.tran.virus.VirusScannerResult;

public class ClamTest
{
    public static void main(String args[])
    {
        if (args.length < 1) {
            System.err.println("Usage: java ClamTest <filename>");
            System.exit(1);
        }

        ClamScanner scanner = new ClamScanner();
        VirusScannerResult result = null;
        
        result = scanner.scanFile(args[0]);
        System.out.println(result);
        System.exit(0);
    }

}

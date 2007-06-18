/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module.  An independent
 * module is a module which is not derived from or based on this library.
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */
package com.untangle.uvm.util;

import java.io.BufferedWriter;
import java.io.FileWriter;

import com.untangle.uvm.LocalUvmContextFactory;
import org.apache.log4j.Logger;

public class ConfigFileUtil {

    private static final Logger logger = Logger.getLogger(ConfigFileUtil.class);

    private static final String CHMOD_CMD          = "/bin/chmod";
    private static final String CHMOD_PROTECT_CMD          = "/bin/chmod go-rwx ";

    public static void writeFile( StringBuilder sb, String fileName )
    {
        BufferedWriter out = null;

        /* Open up the interfaces file */
        try {
            String data = sb.toString();

            out = new BufferedWriter(new FileWriter( fileName ));
            out.write( data, 0, data.length());
        } catch ( Exception ex ) {
            /* XXX May need to catch this exception, restore defaults
             * then try again */
            logger.error( "Error writing file " + fileName + ":", ex );
        }

        try {
            if ( out != null )
                out.close();
        } catch ( Exception ex ) {
        }
    }

    // Used to make file unreadable by other than owner (root).
    public static void protectFile(String fileName)
    {
        int code;

        try {
            logger.debug( "Protecting " + fileName );

            String command = CHMOD_PROTECT_CMD + fileName;
            Process p = LocalUvmContextFactory.context().exec(command);
            code = p.waitFor();
        } catch ( Exception e ) {
            logger.error( "Unable to protect " + fileName, e );
            return;
        }

        if ( code != 0 ) {
            logger.error( "Error protecting " + fileName + ": " + code );
        }
    }
}

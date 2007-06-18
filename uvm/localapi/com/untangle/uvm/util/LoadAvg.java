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

import java.io.*;
import java.util.*;

import org.apache.log4j.Logger;

/**
 // We get loadavg from /proc/loadavg, which looks like:
 // 0.00 0.00 0.00   2/74 28933
 // 1min 5min 15min nr_running/nr_threads lastpid
 //
 * LoadAvg gets the load average from the kernel.  It does this efficiently, only sampling
 * once every 5 seconds, which is how often the load average is updated by the kernel.
 *
 * @author <a href="mailto:jdi@slab.ninthwave.com">John Irwin</a>
 * @version 1.0
 */
public class LoadAvg {

    private class LoadVals {
        private float onemin;
        private float fivemin;
        private float fifteenmin;
        private int numrunning;
        private int numthreads;
        // lastpid is ignored.
        LoadVals(float onemin, float fivemin, float fifteenmin, int numrunning, int numthreads) {
            this.onemin = onemin;
            this.fivemin = fivemin;
            this.fifteenmin = fifteenmin;
            this.numrunning = numrunning;
            this.numthreads = numthreads;
        }
    }

    private static final String PATH_PROC_LOADAVG = "/proc/loadavg";
    private static final long SAMPLING_FREQ = 100; // .1 seconds

    private final Logger logger = Logger.getLogger(getClass());

    private static LoadAvg instance = new LoadAvg();

    private long lastSampleTime;

    private LoadVals vals;

    private LoadAvg() {
        lastSampleTime = 0;
        vals = new LoadVals(0, 0, 0, 0, 0);
    }

    public static LoadAvg get() {
        return instance;
    }

    public float getOneMin() {
        refresh();
        return vals.onemin;
    }

    public float getFiveMin() {
        refresh();
        return vals.fivemin;
    }

    public float getFifteenMin() {
        refresh();
        return vals.fifteenmin;
    }

    public int getNumRunning() {
        refresh();
        return vals.numrunning;
    }

    public int getNumThreads() {
        refresh();
        return vals.numthreads;
    }

    private void refresh() {
        long curTime = System.currentTimeMillis();
        if (curTime - SAMPLING_FREQ >= lastSampleTime) {
            lastSampleTime = curTime;
            String line = null;
            try {
                BufferedReader rdr = new BufferedReader(new FileReader(PATH_PROC_LOADAVG));

                line = rdr.readLine();

                StringTokenizer st = new StringTokenizer(line);

                String onetoken = st.nextToken();
                vals.onemin = Float.parseFloat(onetoken);
                String fivetoken = st.nextToken();
                vals.fivemin = Float.parseFloat(fivetoken);
                String fifteentoken = st.nextToken();
                vals.fifteenmin = Float.parseFloat(fifteentoken);

                String runthrtoken = st.nextToken();
                int j = runthrtoken.indexOf('/');
                if (j > 0) {
                    String runtoken = runthrtoken.substring(0, j);
                    String threadstoken = runthrtoken.substring(j + 1);
                    vals.numrunning = Integer.parseInt(runtoken);
                    vals.numthreads = Integer.parseInt(threadstoken);
                }
                rdr.close();
            } catch (FileNotFoundException x) {
                logger.warn("Cannot open " + PATH_PROC_LOADAVG + "(" + x.getMessage() +
                            "), no stats available");
            } catch (IOException x) {
                logger.warn("Unable to read " + PATH_PROC_LOADAVG + "(" + x.getMessage() +
                            "), line " + line);
            } catch (NumberFormatException x) {
                logger.warn("Unable to parse number in stats line " + line);
            } catch (NoSuchElementException x) {
                logger.warn("Unable to parse stats line " + line);
            } catch (Exception x) {
                logger.warn("Unable to parse " + PATH_PROC_LOADAVG + "(" + x.getMessage() +
                            "), line " + line);
            }
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(vals.onemin);
        sb.append(" ");
        sb.append(vals.fivemin);
        sb.append(" ");
        sb.append(vals.fifteenmin);
        sb.append(" ");
        sb.append(vals.numrunning);
        sb.append("/");
        sb.append(vals.numthreads);
        return sb.toString();
    }
}



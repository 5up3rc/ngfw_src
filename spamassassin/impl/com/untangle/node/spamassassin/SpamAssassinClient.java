/*
 * $HeadURL:$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.node.spamassassin;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.InterruptedException;
import java.net.SocketException;
import java.nio.channels.ClosedByInterruptException;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.untangle.node.spam.ReportItem;
import com.untangle.node.spam.SpamReport;
import org.apache.log4j.Logger;

public final class SpamAssassinClient implements Runnable {
    private final Logger logger = Logger.getLogger(getClass());

    private final static Pattern REPORT_PATTERN = Pattern.compile("^[ ]*-?[0-9]+\\.[0-9]+ [A-Z0-9_]+");

    private final static int READ_SZ = 1024;

    private final static String CONTENTLEN = "Content-length:";
    private final static String CRLF = "\r\n"; // end-of-line
    private final static String LWSPO = "(\\p{Blank})+"; // linear-white-spaces
    private final static String LWSPA = "(\\p{Blank})*"; // any lwsp
    private final static String DGT = "(\\p{Digit})+"; // digits -> integer
    private final static String P_NMBR = DGT + "\\." + DGT; // pos number
    private final static String NMBR = "(-)?" + DGT + "\\." + DGT; // pos or neg number

    private final static String ALPHA = "(\\p{Alpha})+"; // alpha chars
    private final static String EX_CODE = ALPHA + "_" + ALPHA; // exit code

    // must have at least these many parameters
    private final static int SPAMD_RESPONSE_PARAM_CNT = 3;
    private final static int SPAMD_CONTENTLEN_PARAM_CNT = 1;
    private final static int SPAMD_RESULT_PARAM_CNT = 3;

    // spam client hdr - built during run-time
    private final static String REQUEST_CHDR = "REPORT SPAMC/1.3" + CRLF;
    private final static String REQ_USERNAME_TAG = "User: ";
    private final static String REQ_CONTENTLEN_TAG = CONTENTLEN + " ";

    // spam daemon hdr - checked during run-time
    private final static String REPLY_DHDR = "^SPAMD/" + P_NMBR + LWSPO + DGT + LWSPO;
    private final static String REP_CONTENTLEN_DHDR = "^" + CONTENTLEN + LWSPA + DGT;
    private final static String REP_SPAM_DHDR = "^Spam:" + LWSPA + ALPHA + LWSPA + ";" + LWSPA + NMBR + LWSPA + "/" + LWSPA + NMBR;
    private final static Pattern REPLY_DHDRP = Pattern.compile(REPLY_DHDR, Pattern.CASE_INSENSITIVE);
    private final static Pattern REP_CONTENTLEN_DHDRP = Pattern.compile(REP_CONTENTLEN_DHDR, Pattern.CASE_INSENSITIVE);
    private final static Pattern REP_SPAM_DHDRP = Pattern.compile(REP_SPAM_DHDR, Pattern.CASE_INSENSITIVE);

    private final SpamAssassinClientContext cContext;

    private final String userNameCHdr;
    private final String contentLenCHdr;

    private Thread cThread;
    private String dbgName; // thread name and socket host
    private volatile boolean stop = false;

    public SpamAssassinClient(SpamAssassinClientContext cContext, String userName) {
        this.cContext = cContext;

        userNameCHdr = new StringBuilder(REQ_USERNAME_TAG).append(userName).append(CRLF).toString();
        // add extra CRLF in case message doesn't end with CRLF
        contentLenCHdr = new StringBuilder(REQ_CONTENTLEN_TAG).append(Long.toString(cContext.getMsgFile().length() + CRLF.length())).append(CRLF).toString();
    }

    public void setThread(Thread cThread) {
        this.cThread = cThread;
        dbgName = new StringBuilder("<").append(cThread.getName()).append(">").append(cContext.getHost()).append(":").append(cContext.getPort()).toString();
        return;
    }

    public void startScan() {
        //logger.debug("start, thread: " + cThread + ", this: " + this);
        cThread.start(); // execute run() now
        return;
    }

    public void checkProgress(long timeout) {
        //logger.debug("check, thread: " + cThread + ", this: " + this);
        if (false == cThread.isAlive()) {
            logger.debug(dbgName + ", is not alive; not waiting");
            return;
        }

        try {
            synchronized (this) {
                long startTime = System.currentTimeMillis();
                this.wait(timeout); // wait for run() to finish/timeout

                // retry when no result yet and time remains before timeout
                long elapsedTime = System.currentTimeMillis() - startTime;
                while (!cContext.isDone() && elapsedTime < timeout) {
                    this.wait(timeout - elapsedTime);
                    elapsedTime = System.currentTimeMillis() - startTime;
                }
            }
        } catch (InterruptedException e) {
            logger.warn(dbgName + ", spamc interrupted", e);
        } catch (Exception e) {
            logger.warn(dbgName + ", spamc failed", e);
        }

        if (null == cContext.getResult()) {
            logger.warn(dbgName + ", spamc timer expired");
            stopScan();
        }

        return;
    }

    public void stopScan() {
        //logger.debug("stop, thread: " + cThread + ", this: " + this);
        if (false == cThread.isAlive()) {
            logger.debug(dbgName + ", is not alive; no need to stop");
            return;
        }

        this.stop = true;
        cThread.interrupt(); // stop run() now
        return;
    }

    public String toString() {
        return dbgName;
    }

    public void run() {
        SpamAssassinClientSocket spamcSocket = null;
        try {
            spamcSocket = SpamAssassinClientSocket.create(cContext.getHost(), cContext.getPort());
        } catch (Exception e) {
            logger.warn(dbgName + ", finish, spamc could not connect to spamd; spamd may not be configured or spamd may be overloaded", e);
            cleanExit();
            return;
        }
        //logger.debug("run, thread: " + cThread + ", this: " + this + ", create: " + spamcSocket);

        try {
            BufferedOutputStream bufOutputStream = spamcSocket.getBufferedOutputStream();
            BufferedReader bufReader = spamcSocket.getBufferedReader();

            if (true == this.stop) {
                logger.warn(dbgName + ", spamc interrupted post socket streams");
                return; // return after finally
            }

            // send spamc hdr
            // REPORT SPAMC/1.3
            // User: spamc
            // Content-length: 1235
            // <blank line>
            byte[] rBuf = REQUEST_CHDR.getBytes();
            bufOutputStream.write(rBuf, 0, rBuf.length);
            rBuf = userNameCHdr.getBytes();
            bufOutputStream.write(rBuf, 0, rBuf.length);
            rBuf = contentLenCHdr.getBytes();
            bufOutputStream.write(rBuf, 0, rBuf.length);
            rBuf = CRLF.getBytes(); // end of spamc hdr
            bufOutputStream.write(rBuf, 0, rBuf.length);
            bufOutputStream.flush();

            // send message
            FileInputStream fInputStream = new FileInputStream(cContext.getMsgFile());
            rBuf = new byte[READ_SZ];

            int rLen;
            while (0 < (rLen = fInputStream.read(rBuf))) {
                bufOutputStream.write(rBuf, 0, rLen);
            }
            rBuf = CRLF.getBytes(); // add extra CRLF
            bufOutputStream.write(rBuf, 0, rBuf.length);
            bufOutputStream.flush();
            // Can't close the bufOutputStream here or it closes the
            // whole socket.  Instead shutdown.
            spamcSocket.shutdownOutput();
            fInputStream.close();
            fInputStream = null;
            rBuf = null;

            if (true == this.stop) {
                logger.warn(dbgName + ", spamc interrupted post spamc header");
                return; // return after finally
            }

            // receive spamd hdr
            // SPAMD/1.1 0 EX_OK
            // Content-length: 638
            // Spam: True ; 9.5 / 5.0
            // <blank line>
            String line;
            if (null == (line = bufReader.readLine()))
                throw new Exception(dbgName + ", spamd/spamc terminated connection early");

            logger.debug(dbgName + ", " + line); // SPAMD/<ver> <retcode> <description>
            if (true == this.stop) {
                logger.warn(dbgName + ", spamc interrupted post spamd header response");
                return; // return after finally
            }

            Matcher spamdMatcher = REPLY_DHDRP.matcher(line);
            if (false == spamdMatcher.find())
                throw new Exception(dbgName + ", spamd response is invalid: " + line);

            boolean isOK = parseSpamdResponse(line);

            // receive (and buffer) rest of spamd hdr and result
            List<String> spamdHdrList = new LinkedList<String>();
            List<String> spamdDtlList = new LinkedList<String>();
            boolean addDetail = false;
            while (false == this.stop &&
                   null != (line = bufReader.readLine())) {
                //logger.debug(dbgName + ", " + line);
                if (0 == line.length()) {
                    addDetail = true; // end of spamd hdr (details follow)
                    continue;
                }

                if (false == addDetail) {
                    spamdHdrList.add(line);
                } else {
                    spamdDtlList.add(line);
                }
            }

            if (true == this.stop) {
                logger.warn(dbgName + ", spamc interrupted post spamd header and reply");
                return; // return after finally
            }

            if (true == spamdHdrList.isEmpty()) {
                if (true == isOK) {
                    throw new Exception(dbgName + ", spamd terminated connection early (did not report result)");
                } else {
                    // spamd may send more info (in hdr) for some errors
                    // but spamd may send nothing for other errors
                    throw new Exception(dbgName + ", spamd reported protocol error from spamc");
                }
            }
            // hdr and detail list are never empty
            // but unlike hdr list, detail list could be empty
            // (e.g., if message is not spam - 0 score means no detail)
            // even though detail list is currently not empty
            // so we won't check if the detail list is empty

            // process rest of spamd hdr
            Long len = null;
            Float score = null;
            for (String spamdHdr : spamdHdrList) {
                // Content-length: <len>
                spamdMatcher = REP_CONTENTLEN_DHDRP.matcher(spamdHdr);
                if (true == spamdMatcher.find()) {
                    // readLine stripped line terminator chars (LFs)
                    // so "post count" LFs later
                    len = Long.valueOf(parseSpamdContentLength(spamdHdr));
                    continue;
                }

                // Spam: <isspam> ; <score> / <thres>
                // use score but discard isspam and thres
                // (because spamd thres may differ from our threshold)
                spamdMatcher = REP_SPAM_DHDRP.matcher(spamdHdr);
                if (true == spamdMatcher.find()) {
                    score = Float.valueOf(parseSpamdReply(spamdHdr));
                    continue;
                }

                logger.debug(dbgName + ", spamd sent extra header lines: " + spamdHdr);
            }

            if (null == len && null == score) {
                throw new Exception(dbgName + ", spamd did not report content-length and reply");
            } else if (null == len) {
                throw new Exception(dbgName + ", spamd did not report content-length");
            } else if (null == score) {
                throw new Exception(dbgName + ", spamd did not report reply");
            }

            parseSpamdResult(spamdDtlList, len.longValue(), score.floatValue());
            spamdHdrList.clear();
            spamdDtlList.clear();
            spamdHdrList = null;
            spamdDtlList = null;

            bufReader = null;
            bufOutputStream = null;
        } catch (ClosedByInterruptException e) {
            // not thrown
            logger.warn(dbgName + ", spamc i/o channel interrupted:" + spamcSocket, e);
        } catch (SocketException e) {
            // thrown during read block
            logger.warn(dbgName + ", spamc socket closed/interrupted: " + spamcSocket, e);
        } catch (IOException e) {
            // not thrown
            logger.warn(dbgName + ", spamc i/o exception: " + spamcSocket, e);
        } catch (InterruptedException e) {
            // not thrown
            logger.warn(dbgName + ", spamc interrupted: " + spamcSocket, e);
        } catch (Exception e) {
            // thrown during parse
            logger.warn(dbgName + ", spamc failed", e);
        } finally {
            //logger.debug(dbgName + ", finish");
            cleanExit(spamcSocket, cContext.getHost(), cContext.getPort());
            spamcSocket = null;
            return;
        }
    }

    private void cleanExit(SpamAssassinClientSocket cSocket, String host, int port) {
        try {
            if (null != cSocket) {
                // close socket and its open streams
                //logger.debug(dbgName + ", close: " + spamcSocket);
                cSocket.close(host, port);
            }
        } catch (Exception e) {
            // if socket and streams fail to close, nothing can be done
        }

        cleanExit();
        return;
    }

    private void cleanExit() {
        synchronized (this) {
            cContext.setDone(true);
            this.notifyAll(); // notify waiting thread and finish run()
            return;
        }
    }

    private boolean parseSpamdResponse(String response) throws Exception {
        StringTokenizer sTokenizer = new StringTokenizer(response, " \t\n\r\f/");
        String tStr = null;
        int tIdx = 0;
        boolean dumpRest = false;
        boolean isOK = true;
        while (false == dumpRest &&
               true == sTokenizer.hasMoreTokens()) {
            tStr = sTokenizer.nextToken();
            switch(tIdx) {
            case 0:
                break; // skip SPAMD tag
            case 1:
                float version = Float.parseFloat(tStr);
                if (1.0 > version)
                    throw new Exception(dbgName + ", spamd response has unsupported version: " + version);

                break;
            case 2:
                int retCode = Integer.parseInt(tStr);
                if (0 != retCode) {
                    logger.warn(dbgName + ", spamd response has non-success return code: " + retCode);
                    isOK = false;
                    // continue if non-success because spamc doesn't care
                    // -> spamd will terminate connection soon
                }

                break;
            default:
            case 3:
                dumpRest = true;
                break;
            }
            tIdx++;
        }

        if (true == dumpRest &&
            (false == isOK ||
             true == logger.isDebugEnabled())) {
            StringBuilder remaining = new StringBuilder(tStr);

            while (true == sTokenizer.hasMoreTokens()) {
                tStr = sTokenizer.nextToken();
                remaining.append(" ").append(tStr);
            }

            if (false == isOK) {
                logger.warn(dbgName + ", spamd response has non-success description: " + remaining);
            } else {
                logger.debug(dbgName + ", spamd response has success description: " + remaining);
            }
            // continue because spamc doesn't care
        }

        sTokenizer = null;

        if (SPAMD_RESPONSE_PARAM_CNT > tIdx)
            throw new Exception(dbgName + ", spamd response has less than " + SPAMD_RESPONSE_PARAM_CNT + " parameters");

        return isOK;
    }

    private long parseSpamdContentLength(String contentLength) throws Exception {
        StringTokenizer sTokenizer = new StringTokenizer(contentLength);
        long len = 0;
        int tIdx = 0;

        String tStr;

        while (true == sTokenizer.hasMoreTokens()) {
            tStr = sTokenizer.nextToken();
            switch(tIdx) {
            case 0:
                break; // skip Content-Length tag
            case 1:
                len = Long.parseLong(tStr);
                break;
            default:
                logger.warn(dbgName + ", spamd content-length has extra parameter: " + tStr);
                // continue because spamc doesn't care
                break;
            }
            tIdx++;
        }

        sTokenizer = null;

        if (SPAMD_CONTENTLEN_PARAM_CNT > tIdx)
            throw new Exception(dbgName + ", spamd content-length has less than " + SPAMD_CONTENTLEN_PARAM_CNT + " parameters");

        return len;
    }

    private float parseSpamdReply(String reply) throws Exception {
        StringTokenizer sTokenizer = new StringTokenizer(reply, " \t\n\r\f;/");
        int tIdx = 0;
        float score = 0;

        String tStr;

        while (true == sTokenizer.hasMoreTokens()) {
            tStr = sTokenizer.nextToken();
            switch(tIdx) {
            case 0:
                break; // skip Spam tag
            case 1:
                break; // skip isspam flag; is identified later
            case 2:
                score = Float.parseFloat(tStr);
                break;
            case 3:
                break; // skip threshold; we already have it
            default:
                logger.warn(dbgName + ", spamd reply has extra parameter: " + tStr);
                // continue because spamc doesn't care
                break;
            }
            tIdx++;
        }

        if (SPAMD_RESULT_PARAM_CNT > tIdx)
            throw new Exception(dbgName + ", spamd reply has less than " + SPAMD_RESULT_PARAM_CNT + " parameters");

        return score;
    }

    //  2.0 DATE_IN_PAST_96_XX     Date: is 96 hours or more before Received: date
    //  4.3 SUBJ_ILLEGAL_CHARS     Subject: has too many raw illegal characters
    //  0.1 HTML_50_60             BODY: Message is 50% to 60% HTML
    //  0.0 HTML_MESSAGE           BODY: HTML included in message
    //  3.5 BAYES_99               BODY: Bayesian spam probability is 99 to 100%
    //                             [score: 0.9939]
    //  0.0 MIME_HTML_ONLY         BODY: Message only has text/html MIME parts
    //  0.2 HTML_TITLE_EMPTY       BODY: HTML title contains no text
    //  2.2 RCVD_IN_WHOIS_INVALID  RBL: CompleteWhois: sender on invalid IP block
    //                             [61.73.86.111 listed in combined-HIB.dnsiplists.completewhois.com]
    //  1.9 RCVD_IN_NJABL_DUL      RBL: NJABL: dialup sender did non-local SMTP
    //                             [61.73.86.111 listed in combined.njabl.org]
    // -1.8 AWL                    AWL: From: address is in the auto white-list
    private void parseSpamdResult(List<String> detailList, long len, float score) throws Exception {
        List<ReportItem> reportItemList = new LinkedList<ReportItem>();
        String firstLine = null;
        // CR terminates final line (e.g., ends detail block); count CR
        long tmpLen = 1;

        String riScore;
        String riCateg;
        ReportItem reportItem;
        int j;
        int k;

        for (String detail : detailList) {
            tmpLen += (detail.length() + 1); // each line ends w/ LF; count LF

            if (firstLine == null)
                firstLine = detail;

            Matcher reportMatcher = REPORT_PATTERN.matcher(detail);
            if (reportMatcher.lookingAt()) {
                detail = detail.trim(); // Trim leading space
                j = detail.indexOf(' ');
                riScore = detail.substring(0, j);
                k = detail.indexOf(' ', j + 1);
                if (k < 0)
                    k = detail.length();
                riCateg = detail.substring(j + 1, k);

                if (logger.isDebugEnabled())
                    logger.debug(dbgName + ", add item: " + riScore + ", " + riCateg);

                reportItem = new ReportItem(Float.parseFloat(riScore), riCateg);
                reportItemList.add(reportItem);
            }
        }

        if (len != tmpLen) {
            reportItemList.clear();
            throw new Exception(dbgName + ", spamd result is missing data, expected " + len + " bytes but only received " + tmpLen + " bytes");
        }

        cContext.setResult(reportItemList, score);
        // SpamReport creates its own item list and then copies contents
        reportItemList.clear();
        reportItemList = null;
        return;
    }
}

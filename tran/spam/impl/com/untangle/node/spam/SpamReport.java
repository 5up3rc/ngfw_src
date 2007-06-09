/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.node.spam;

import static com.untangle.node.util.Ascii.*;

import java.util.LinkedList;
import java.util.List;

import com.untangle.uvm.node.TemplateValues;
import com.untangle.node.token.header.IllegalFieldException;
import org.apache.log4j.Logger;

/**
 * Class to encapsulate a SPAM report.
 * <br>
 * <br>
 * This class also implements {@link com.untangle.node.util.TemplateValues TemplateValues}.
 * Valid key names which can be derefferenced from a SpamReport begin with
 * the literal <code>SPAMReport:</code> followed by any of the following tokens:
 * <ul>
 *   <li>
 *     <code><b>FULL</b></code>Any Report items from the SPAM report, with
 *     each report on its own line.  Each report item is terminated by a CRLF.
 *   </li>
 *   <li>
 *     <code><b>THRESHOLD</b></code> The numerical value of the threshold
 *     above-which a score renders a SPAM judgement (e.g. "5").
 *   </li>
 *   <li>
 *     <code><b>SCORE</b></code> The numerical value of the score (e.g. "7.2").
 *   </li>
 * </ul>
 */
public class SpamReport
    implements TemplateValues {

    private static final String SPAM_REPORT_PREFIX = "SPAMReport:".toLowerCase();
    private static final String FULL_KEY = "FULL".toLowerCase();
    private static final String THRESHOLD_KEY = "THRESHOLD".toLowerCase();
    private static final String SCORE_KEY = "SCORE".toLowerCase();

    private final List<ReportItem> items;
    private final float score;
    private final float threshold;

    public static final float MAX_THRESHOLD = 1000f;
    public static final float MIN_THRESHOLD = 0f;

    public static final SpamReport EMPTY =
        new SpamReport(new LinkedList<ReportItem>(), MAX_THRESHOLD);

    private Logger logger = Logger.getLogger(SpamReport.class);

    // constructors -----------------------------------------------------------

    public SpamReport(List<ReportItem> items, float score, float threshold)
    {
        this.items = new LinkedList<ReportItem>(items);
        this.score = score;
        this.threshold = threshold;
    }

    public SpamReport(List<ReportItem> items, float threshold)
    {
        this.items = new LinkedList<ReportItem>(items);
        this.threshold = threshold;

        float s = 0;
        for (ReportItem ri : items) {
            s += ri.getScore();
        }
        this.score = s;
    }
    /**
     * For use in Templates (see JavaDoc at the top of this class
     * for explanation of variable format).
     */
    public String getTemplateValue(String key) {
        key = key.trim().toLowerCase();
        if(key.startsWith(SPAM_REPORT_PREFIX)) {
            key = key.substring(SPAM_REPORT_PREFIX.length());
            if(key.equals(FULL_KEY)) {
                StringBuilder sb = new StringBuilder();
                for(ReportItem ri : items) {
                    sb.append('(');
                    sb.append(Float.toString(ri.getScore()));
                    sb.append(") ");
                    sb.append(ri.getCategory());
                    sb.append(CRLF);
                }
                return sb.toString();
            }
            else if(key.equals(SCORE_KEY)) {
                return Float.toString(score);
            }
            else if(key.equals(THRESHOLD_KEY)) {
                return Float.toString(threshold);
            }
        }
        return null;
    }

    /**
     * @depricated
     */
    /*
      public Rfc822Header rewriteHeader(Rfc822Header h)
      {
      if (isSpam()) {
      logger.debug("isSpam, rewriting header");
      String subject = h.getSubject();
      subject = "[SPAM] " + (null == subject ? "" : subject);
      h.setSubject(subject);
      } else {
      logger.debug("not spam, not rewriting");
      }

      try {
      h.setField("X-Spam-Flag", isSpam() ? "YES" : "NO");
      } catch (IllegalFieldException exn) {
      throw new IllegalStateException("should never happen");
      }


      return h;
      }
    */
    public boolean isSpam()
    {
        return threshold <= score;
    }

    public float getScore()
    {
        return score;
    }

    // accessors --------------------------------------------------------------

    public List<ReportItem> getItems()
    {
        return items;
    }

    // Object methods ---------------------------------------------------------

    public String toString()
    {
        StringBuffer sb = new StringBuffer("Spam Score: ");
        sb.append(score);
        sb.append("\n");

        for (ReportItem i : items) {
            sb.append("  ");
            sb.append(i);
            sb.append("\n");
        }

        return sb.toString();
    }
}

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

package com.metavize.tran.spam;

public class ReportItem
{
    private final float score;
    private final String category;

    // constructors -----------------------------------------------------------

    public ReportItem(float score, String category)
    {
        this.score = score;
        this.category = category;
    }

    // accessors --------------------------------------------------------------

    public float getScore()
    {
        return score;
    }

    public String getCategory()
    {
        return category;
    }

    // Object methods ---------------------------------------------------------

    public String toString()
    {
        return score + " " + category;
    }
}

/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id: TopTenPieGraph.java,v 1.1 2005/02/11 22:45:33 jdi Exp $
 */

package com.metavize.mvvm.reporting;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot3D;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.util.Rotation;
import org.jfree.ui.Drawable;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.sql.*;
import java.util.Calendar;
import java.util.GregorianCalendar;

import net.sf.jasperreports.engine.JRAbstractSvgRenderer;
import net.sf.jasperreports.engine.JRDefaultScriptlet;
import net.sf.jasperreports.engine.JRScriptletException;

import com.metavize.mvvm.util.PortServiceNames;


public abstract class TopTenPieGraph extends ReportGraph
{
    // Pie graph specific
    public static final String PARAM_MAX_PIE_SLICES = "MaxPieSlices";

    // Pie graph specific
    protected int maxPieSlices;

    protected TopTenPieGraph(JRDefaultScriptlet ourScriptlet, String resultVarName)
    {
        super(ourScriptlet, resultVarName);
    }

    // Get the parameters
    protected void initParams()
    {
        super.initParams();

        Integer maxPieSlicesInt = (Integer) gpv(PARAM_MAX_PIE_SLICES);
        if (maxPieSlicesInt == null)
            maxPieSlices = 11;  // Ten plus other, if necessary.
        else
            maxPieSlices = maxPieSlicesInt.intValue();
    }
}




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


package com.metavize.tran.virus.gui;

import com.metavize.gui.transform.*;
import com.metavize.gui.pipeline.MPipelineJPanel;
import com.metavize.gui.widgets.editTable.*;
import com.metavize.gui.util.*;

import com.metavize.mvvm.*;
import com.metavize.mvvm.tran.*;
import com.metavize.tran.virus.*;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.*;
import java.util.*;
import javax.swing.event.*;

public class FTPConfigJPanel extends MEditTableJPanel {
    
    public FTPConfigJPanel() {
        super(true, true);
        super.setInsets(new Insets(4, 4, 2, 2));
        super.setTableTitle("FTP virus scan rules");
        super.setDetailsTitle("rule notes");
        
        // create actual table model
        FTPTableModel ftpTableModel = new FTPTableModel();
        this.setTableModel( ftpTableModel );
        this.setAddRemoveEnabled(false);
    }
}


class FTPTableModel extends MSortedTableModel<Object>{ 

    private static final int T_TW = Util.TABLE_TOTAL_WIDTH;
    private static final int C0_MW = Util.STATUS_MIN_WIDTH; /* status */
    private static final int C1_MW = Util.LINENO_MIN_WIDTH; /* # - invisible */
    private static final int C2_MW = 100; /* source */
    private static final int C3_MW = 55;  /* block */
    private static final int C4_MW = Util.chooseMax(T_TW - (C0_MW + C2_MW + C3_MW), 120); /* description */

    protected boolean getSortable(){ return false; }
    
    public TableColumnModel getTableColumnModel(){
        
        DefaultTableColumnModel tableColumnModel = new DefaultTableColumnModel();
        //                                 #  min    rsz    edit   remv   desc   typ            def
        addTableColumn( tableColumnModel,  0, C0_MW, false, false, false, false, String.class,  null, sc.TITLE_STATUS );
        addTableColumn( tableColumnModel,  1, C1_MW, false, false, true,  false, Integer.class, null, sc.TITLE_INDEX );
        addTableColumn( tableColumnModel,  2, C2_MW, false, false, false, false, String.class,  null, "source");
        addTableColumn( tableColumnModel,  3, C3_MW, false, true,  false, false, Boolean.class, null, sc.bold("scan"));
        addTableColumn( tableColumnModel,  4, C4_MW, true,  true,  false, true,  String.class, sc.EMPTY_DESCRIPTION, sc.TITLE_DESCRIPTION );
        addTableColumn( tableColumnModel,  5, 10,    false, false, true,  false, VirusConfig.class, null, "" );
        return tableColumnModel;
    }

    private static final String SOURCE_INBOUND = "incoming files";
    private static final String SOURCE_OUTBOUND = "outgoing files";

    public void generateSettings(Object settings, Vector<Vector> tableVector, boolean validateOnly) throws Exception {
        for( Vector rowVector : tableVector ){

            VirusConfig virusConfig = (VirusConfig) rowVector.elementAt(5);
            virusConfig.setScan( (Boolean) rowVector.elementAt(3) );
            virusConfig.setNotes( (String) rowVector.elementAt(4) );

            // SAVE SETTINGS ///////
            if( !validateOnly ){
                VirusSettings virusSettings = (VirusSettings) settings;
                if( ((String)rowVector.elementAt(2)).equals(SOURCE_INBOUND) ){
                    virusSettings.setFtpInbound(virusConfig);
                }
                else if( ((String)rowVector.elementAt(2)).equals(SOURCE_OUTBOUND) ){
                    virusSettings.setFtpOutbound(virusConfig);
                }
            }
	    
        }
    }
    
    public Vector<Vector> generateRows(Object settings){
        VirusSettings virusSettings = (VirusSettings) settings;
        Vector<Vector> allRows = new Vector<Vector>(2);
        int rowIndex = 0;

        // INBOUND
        rowIndex++;
        Vector inboundRow = new Vector(6);
        VirusConfig virusInboundCtl  = virusSettings.getFtpInbound();
        inboundRow.add( super.ROW_SAVED );
        inboundRow.add( rowIndex );
        inboundRow.add( SOURCE_INBOUND );
        inboundRow.add( virusInboundCtl.getScan() );
        inboundRow.add( virusInboundCtl.getNotes() );
        inboundRow.add( virusInboundCtl );
        allRows.add(inboundRow);

        // OUTBOUND
        rowIndex++;
        Vector outboundRow = new Vector(6);
        VirusConfig virusOutboundCtl = virusSettings.getFtpOutbound();
        outboundRow.add( super.ROW_SAVED );
        outboundRow.add( rowIndex );
        outboundRow.add( SOURCE_OUTBOUND );
        outboundRow.add( virusOutboundCtl.getScan() );
        outboundRow.add( virusOutboundCtl.getNotes() );
        outboundRow.add( virusOutboundCtl );
        allRows.add(outboundRow);

        return allRows;
    }
}

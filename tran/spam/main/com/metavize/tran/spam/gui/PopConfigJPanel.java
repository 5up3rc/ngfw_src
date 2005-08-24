/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */


package com.metavize.tran.spam.gui;

import com.metavize.gui.transform.*;
import com.metavize.gui.util.*;
import com.metavize.gui.widgets.editTable.*;
import com.metavize.tran.spam.*;
import com.metavize.tran.mail.*;
import com.metavize.mvvm.tran.TransformContext;


import java.awt.*;
import java.util.Vector;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;


public class PopConfigJPanel extends MEditTableJPanel {

    public PopConfigJPanel() {

        super(true, true);
        super.setInsets(new Insets(4, 4, 2, 2));
        super.setTableTitle("spam filter rules");
        super.setDetailsTitle("rule notes");
        super.setAddRemoveEnabled(false);

        // create actual table model
        PopTableModel popTableModel = new PopTableModel();
        this.setTableModel( popTableModel );
    }
}


class PopTableModel extends MSortedTableModel{

    private static final int T_TW = Util.TABLE_TOTAL_WIDTH_LARGE;
    private static final int C0_MW = Util.STATUS_MIN_WIDTH; /* status */
    private static final int C1_MW = Util.LINENO_MIN_WIDTH; /* # - invisible */
    private static final int C2_MW = 100; /* source */
    private static final int C3_MW = 55;  /* scan */
    private static final int C4_MW = 95; /* scan strength */
    private static final int C5_MW = 125; /* action if SPAM detected */
    private static final int C6_MW = Util.chooseMax(T_TW - (C0_MW + C2_MW + C3_MW + C4_MW + C5_MW), 120); /* description */

    protected boolean getSortable(){ return false; }

    public TableColumnModel getTableColumnModel(){

        DefaultTableColumnModel tableColumnModel = new DefaultTableColumnModel();
        //                                 #  min    rsz    edit   remv   desc   typ            def
        addTableColumn( tableColumnModel,  0, C0_MW, false, false, false, false, String.class,  null, sc.TITLE_STATUS);
        addTableColumn( tableColumnModel,  1, C1_MW, false, false, true,  false, Integer.class, null, sc.TITLE_INDEX);
        addTableColumn( tableColumnModel,  2, C2_MW, false, false, false, false, String.class,  null, "source");
        addTableColumn( tableColumnModel,  3, C3_MW, false, true,  false, false, Boolean.class,  null, sc.bold("scan") );
        addTableColumn( tableColumnModel,  4, C4_MW, false, true,  false, false, ComboBoxModel.class,  null, sc.html("scan<br>strength"));
        addTableColumn( tableColumnModel,  5, C5_MW, false, true,  false, false, ComboBoxModel.class,  null, sc.html("action if<br>SPAM detected"));
        addTableColumn( tableColumnModel,  6, C6_MW, true,  true,  false, true,  String.class,  sc.EMPTY_DESCRIPTION, sc.TITLE_DESCRIPTION);
        addTableColumn( tableColumnModel,  7, 10,    false, false, true,  false, SpamPOPConfig.class, null, "");
        return tableColumnModel;
    }

    private static final String SOURCE_INBOUND = "inbound POP";
    private static final String SOURCE_OUTBOUND = "outbound POP";

    public void generateSettings(Object settings, boolean validateOnly) throws Exception {
	SpamPOPConfig spamPOPConfigInbound = null;
	SpamPOPConfig spamPOPConfigOutbound = null;

	for( Vector rowVector : (Vector<Vector>) this.getDataVector() ){
            SpamPOPConfig spamPOPConfig = (SpamPOPConfig) rowVector.elementAt(7);
            spamPOPConfig.setScan( (Boolean) rowVector.elementAt(3) );
	    String strengthString = (String) ((ComboBoxModel)rowVector.elementAt(4)).getSelectedItem();
	    spamPOPConfig.setStrengthByName( strengthString );
	    String actionString = (String) ((ComboBoxModel)rowVector.elementAt(5)).getSelectedItem();
	    SpamMessageAction messageAction = SpamMessageAction.getInstance( actionString );
            spamPOPConfig.setMsgAction( messageAction );
            spamPOPConfig.setNotes( (String) rowVector.elementAt(6) );
	    
	    if( ((String)rowVector.elementAt(2)).equals(SOURCE_INBOUND) ){
		spamPOPConfigInbound = spamPOPConfig;
	    }
	    else if( ((String)rowVector.elementAt(2)).equals(SOURCE_OUTBOUND) ){
		spamPOPConfigOutbound = spamPOPConfig;
	    }  
        }
	
	// SAVE SETTINGS ////////
	if( !validateOnly ){
	    SpamSettings spamSettings = (SpamSettings) settings;
	    spamSettings.setPOPInbound( spamPOPConfigInbound );
	    spamSettings.setPOPOutbound( spamPOPConfigOutbound );
	}


    }

    public Vector generateRows(Object settings) {
        SpamSettings spamSettings = (SpamSettings) settings;
        Vector allRows = new Vector(2);
	int rowIndex = 0;

	// INBOUND
	rowIndex++;
	Vector inboundRow = new Vector(8);
        SpamPOPConfig spamPOPConfigInbound = spamSettings.getPOPInbound();
        inboundRow.add( super.ROW_SAVED );
        inboundRow.add( rowIndex );
        inboundRow.add( SOURCE_INBOUND );
        inboundRow.add( spamPOPConfigInbound.getScan() );
	ComboBoxModel inboundStrengthComboBoxModel = super.generateComboBoxModel( SpamPOPConfig.getScanStrengthEnumeration(), spamPOPConfigInbound.getStrengthByName());
	inboundRow.add( inboundStrengthComboBoxModel );
        ComboBoxModel inboundActionComboBoxModel =  super.generateComboBoxModel( SpamMessageAction.getValues(), spamPOPConfigInbound.getMsgAction() );
        inboundRow.add( inboundActionComboBoxModel );
        inboundRow.add( spamPOPConfigInbound.getNotes() );
	inboundRow.add( spamPOPConfigInbound );
	allRows.add(inboundRow);

	// OUTBOUND
	rowIndex++;
	Vector outboundRow = new Vector(8);
        SpamPOPConfig spamPOPConfigOutbound = spamSettings.getPOPOutbound();
        outboundRow.add( super.ROW_SAVED );
        outboundRow.add( rowIndex );
        outboundRow.add( SOURCE_OUTBOUND );
        outboundRow.add( spamPOPConfigOutbound.getScan() );
	ComboBoxModel outboundStrengthComboBoxModel = super.generateComboBoxModel( SpamPOPConfig.getScanStrengthEnumeration(), spamPOPConfigOutbound.getStrengthByName());
	outboundRow.add( outboundStrengthComboBoxModel );
        ComboBoxModel outboundActionComboBoxModel =  super.generateComboBoxModel( SpamMessageAction.getValues(), spamPOPConfigOutbound.getMsgAction() );
        outboundRow.add( outboundActionComboBoxModel );
        outboundRow.add( spamPOPConfigOutbound.getNotes() );
	outboundRow.add( spamPOPConfigOutbound );
	allRows.add(outboundRow);

        return allRows;
    }
}

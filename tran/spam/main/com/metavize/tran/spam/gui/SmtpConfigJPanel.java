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
import com.metavize.mvvm.tran.TransformContext;


import java.awt.*;
import java.util.Vector;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;


public class SmtpConfigJPanel extends MEditTableJPanel {

    public SmtpConfigJPanel() {

        super(true, true);
        super.setInsets(new Insets(4, 4, 2, 2));
        super.setTableTitle("spam filter rules");
        super.setDetailsTitle("rule notes");
        super.setAddRemoveEnabled(false);

        // create actual table model
        SmtpTableModel smtpTableModel = new SmtpTableModel();
        this.setTableModel( smtpTableModel );
    }
}


class SmtpTableModel extends MSortedTableModel{

    private static final int T_TW = Util.TABLE_TOTAL_WIDTH_LARGE;
    private static final int C0_MW = Util.STATUS_MIN_WIDTH; /* status */
    private static final int C1_MW = Util.LINENO_MIN_WIDTH; /* # - invisible */
    private static final int C2_MW = 125; /* source */
    private static final int C3_MW = 55;  /* scan */
    private static final int C4_MW = 95; /* scan strength */
    private static final int C5_MW = 155; /* action if SPAM detected */
    private static final int C6_MW = 190; /* notification if SPAM detected */
    private static final int C7_MW = Util.chooseMax(T_TW - (C0_MW + C2_MW + C3_MW + C4_MW + C5_MW + C6_MW), 120); /* description */

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
        addTableColumn( tableColumnModel,  6, C6_MW, false, true,  false, false, ComboBoxModel.class,  null, sc.html("notification if<br>SPAM detected"));
        addTableColumn( tableColumnModel,  7, C7_MW, true,  true,  false, true,  String.class,  sc.EMPTY_DESCRIPTION, sc.TITLE_DESCRIPTION);
        addTableColumn( tableColumnModel,  8, 10,    false, false, true,  false, SpamSMTPConfig.class, null, "");
        return tableColumnModel;
    }

    private static final String SOURCE_INBOUND  = "incoming message";
    private static final String SOURCE_OUTBOUND = "outgoing message";

    public void generateSettings(Object settings, Vector<Vector> tableVector, boolean validateOnly) throws Exception {
	SpamSMTPConfig spamSMTPConfigInbound = null;
	SpamSMTPConfig spamSMTPConfigOutbound = null;

	for( Vector rowVector : tableVector ){
            SpamSMTPConfig spamSMTPConfig = (SpamSMTPConfig) rowVector.elementAt(8);
            spamSMTPConfig.setScan( (Boolean) rowVector.elementAt(3) );
	    spamSMTPConfig.setStrengthByName( (String) ((ComboBoxModel)rowVector.elementAt(4)).getSelectedItem() );
            spamSMTPConfig.setMsgAction( (SMTPSpamMessageAction) ((ComboBoxModel)rowVector.elementAt(5)).getSelectedItem() );
            spamSMTPConfig.setNotifyAction( (SpamSMTPNotifyAction) ((ComboBoxModel)rowVector.elementAt(6)).getSelectedItem() );
            spamSMTPConfig.setNotes( (String) rowVector.elementAt(7) );
	    
	    if( ((String)rowVector.elementAt(2)).equals(SOURCE_INBOUND) ){
		spamSMTPConfigInbound = spamSMTPConfig;
	    }
	    else if( ((String)rowVector.elementAt(2)).equals(SOURCE_OUTBOUND) ){
		spamSMTPConfigOutbound = spamSMTPConfig;
	    }  
        }
	
	// SAVE SETTINGS ////////
	if( !validateOnly ){
	    SpamSettings spamSettings = (SpamSettings) settings;
	    spamSettings.setSMTPInbound( spamSMTPConfigInbound );
	    spamSettings.setSMTPOutbound( spamSMTPConfigOutbound );
	}

    }

    public Vector<Vector> generateRows(Object settings) {
        SpamSettings spamSettings = (SpamSettings) settings;
        Vector<Vector> allRows = new Vector<Vector>(2);
	int rowIndex = 0;

	// INBOUND
	rowIndex++;
	Vector inboundRow = new Vector(9);
        SpamSMTPConfig spamSMTPConfigInbound = spamSettings.getSMTPInbound();
        inboundRow.add( super.ROW_SAVED );
        inboundRow.add( rowIndex );
        inboundRow.add( SOURCE_INBOUND );
        inboundRow.add( spamSMTPConfigInbound.getScan() );
	inboundRow.add( super.generateComboBoxModel(SpamSMTPConfig.getScanStrengthEnumeration(), spamSMTPConfigInbound.getStrengthByName()) );
	inboundRow.add( super.generateComboBoxModel(SMTPSpamMessageAction.getValues(), spamSMTPConfigInbound.getMsgAction()) );
        inboundRow.add( super.generateComboBoxModel(SpamSMTPNotifyAction.getValues(), spamSMTPConfigInbound.getNotifyAction()) );
        inboundRow.add( spamSMTPConfigInbound.getNotes() );
	inboundRow.add( spamSMTPConfigInbound );
	allRows.add(inboundRow);

	// OUTBOUND
	rowIndex++;
	Vector outboundRow = new Vector(9);
        SpamSMTPConfig spamSMTPConfigOutbound = spamSettings.getSMTPOutbound();
        outboundRow.add( super.ROW_SAVED );
        outboundRow.add( rowIndex );
        outboundRow.add( SOURCE_OUTBOUND );
        outboundRow.add( spamSMTPConfigOutbound.getScan() );
	outboundRow.add( super.generateComboBoxModel(SpamSMTPConfig.getScanStrengthEnumeration(), spamSMTPConfigOutbound.getStrengthByName()) );
	outboundRow.add( super.generateComboBoxModel(SMTPSpamMessageAction.getValues(), spamSMTPConfigOutbound.getMsgAction()) );
        outboundRow.add( super.generateComboBoxModel(SpamSMTPNotifyAction.getValues(), spamSMTPConfigOutbound.getNotifyAction()) );
        outboundRow.add( spamSMTPConfigOutbound.getNotes() );
	outboundRow.add( spamSMTPConfigOutbound );
	allRows.add(outboundRow);

        return allRows;
    }
}

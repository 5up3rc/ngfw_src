/*
 * Copyright (c) 2003,2004 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.httpblocker.gui;

import com.metavize.gui.transform.*;
import com.metavize.gui.pipeline.MPipelineJPanel;
import com.metavize.mvvm.tran.*;
import com.metavize.tran.httpblocker.*;
import com.metavize.gui.widgets.editTable.*;
import com.metavize.gui.util.*;

import javax.swing.*;
import javax.swing.table.*;
import java.util.*;
import java.util.List;
import javax.swing.event.*;

public class PassedClientsConfigJPanel extends MEditTableJPanel {
    
    
    public PassedClientsConfigJPanel() {
        super(true, true);
        super.setInsets(new java.awt.Insets(4, 4, 2, 2));
        super.setTableTitle("Passed Clients");
        super.setDetailsTitle("rule notes");
        super.setAddRemoveEnabled(true);
        
        // create actual table model
        PassedClientsTableModel passedClientsTableModel = new PassedClientsTableModel();
        super.setTableModel( passedClientsTableModel );
    }
}



class PassedClientsTableModel extends MSortedTableModel{ 

    private static final int T_TW = Util.TABLE_TOTAL_WIDTH;
    private static final int C0_MW = Util.STATUS_MIN_WIDTH; /* status */
    private static final int C1_MW = Util.LINENO_MIN_WIDTH; /* # */
    private static final int C2_MW = 150; /* category */
    private static final int C3_MW = 150; /* client */
    private static final int C4_MW = 55; /* pass */
    private static final int C5_MW = Util.chooseMax(T_TW - (C0_MW + C1_MW + C2_MW + C3_MW + C4_MW), 120); /* description */

    
    public TableColumnModel getTableColumnModel(){
        
        DefaultTableColumnModel tableColumnModel = new DefaultTableColumnModel();
        //                                 #  min    rsz    edit   remv   desc   typ            def
        addTableColumn( tableColumnModel,  0, C0_MW, false, false, false, false, String.class,  null, sc.TITLE_STATUS);
        addTableColumn( tableColumnModel,  1, C1_MW, false, false, false, false, Integer.class, null, sc.TITLE_INDEX);
        addTableColumn( tableColumnModel,  2, C2_MW, true,  true,  false, false, String.class,  sc.EMPTY_CATEGORY, sc.TITLE_CATEGORY);
        addTableColumn( tableColumnModel,  3, C3_MW, true,  true,  false, false, String.class,  "0.0.0.0/32", "client IP address");
        addTableColumn( tableColumnModel,  4, C4_MW, false, true,  false, false, Boolean.class, "true", sc.bold("pass"));
        addTableColumn( tableColumnModel,  5, C5_MW, true,  true,  false, true,  String.class,  sc.EMPTY_DESCRIPTION, sc.TITLE_DESCRIPTION);
        addTableColumn( tableColumnModel,  6, 10,    false, false, true,  false, IPMaddrRule.class, null, "");
        return tableColumnModel;
    }

    
    public void generateSettings(Object settings, Vector<Vector> tableVector, boolean validateOnly) throws Exception {
        List elemList = new ArrayList(tableVector.size());
	IPMaddrRule newElem = null;
	int rowIndex = 0;

	for( Vector rowVector : tableVector ){
	    rowIndex++;
            newElem = (IPMaddrRule) rowVector.elementAt(6);
            newElem.setCategory( (String) rowVector.elementAt(2) );
            try{
		IPMaddr newIPMaddr = IPMaddr.parse( (String) rowVector.elementAt(3) );
		newElem.setIpMaddr( newIPMaddr );
	    }
            catch(Exception e){ throw new Exception("Invalid \"client IP address\" specified in row: " + rowIndex); }
            newElem.setLive( (Boolean) rowVector.elementAt(4) );
            newElem.setDescription( (String) rowVector.elementAt(5) );
            elemList.add(newElem);  
        }
        
        // SAVE SETTINGS //////////
	if( !validateOnly ){
	    HttpBlockerSettings httpBlockerSettings = (HttpBlockerSettings) settings;
	    httpBlockerSettings.setPassedClients( elemList );
	}

    }
    
    public Vector<Vector> generateRows(Object settings){
	HttpBlockerSettings httpBlockerSettings = (HttpBlockerSettings) settings;
	List<IPMaddrRule> passedClients = (List<IPMaddrRule>) httpBlockerSettings.getPassedClients();
        Vector<Vector> allRows = new Vector<Vector>(passedClients.size());
	Vector tempRow = null;
	int rowIndex = 0;

	for( IPMaddrRule newElem : passedClients ){
            rowIndex++;
            tempRow = new Vector(7);
            tempRow.add( super.ROW_SAVED );
            tempRow.add( rowIndex );
            tempRow.add( newElem.getCategory() );
            tempRow.add( newElem.getIpMaddr().toString() );
            tempRow.add( newElem.isLive() );
            tempRow.add( newElem.getDescription() );
	    tempRow.add( newElem );
            allRows.add( tempRow );
        }
        return allRows;
    }
    
    
}

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

package com.untangle.tran.httpblocker.gui;

import com.untangle.gui.transform.*;
import com.untangle.gui.pipeline.MPipelineJPanel;
import com.untangle.mvvm.tran.*;
import com.untangle.tran.httpblocker.*;
import com.untangle.gui.widgets.editTable.*;
import com.untangle.gui.util.*;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.*;
import java.util.*;
import java.util.List;
import javax.swing.event.*;

import java.net.URL;

public class PassedURLsConfigJPanel extends MEditTableJPanel {
    
    
    public PassedURLsConfigJPanel() {
        super(true, true);
        super.setInsets(new Insets(4, 4, 2, 2));
        super.setTableTitle("Passed URLs");
        super.setDetailsTitle("rule notes");
        super.setAddRemoveEnabled(true);
        
        // create actual table model
        PassedURLTableModel urlTableModel = new PassedURLTableModel();
        super.setTableModel( urlTableModel );
    }
}



class PassedURLTableModel extends MSortedTableModel<Object>{ 

    private static final int T_TW = Util.TABLE_TOTAL_WIDTH;
    private static final int C0_MW = Util.STATUS_MIN_WIDTH; /* status */
    private static final int C1_MW = Util.LINENO_MIN_WIDTH; /* # */
    private static final int C2_MW = 150; /* category */
    private static final int C3_MW = 150; /* URL */
    private static final int C4_MW = 55; /* pass */
    private static final int C5_MW = Util.chooseMax(T_TW - (C0_MW + C1_MW + C2_MW + C3_MW + C4_MW), 120); /* description */

    public TableColumnModel getTableColumnModel(){
        
        DefaultTableColumnModel tableColumnModel = new DefaultTableColumnModel();
        //                                 #  min    rsz    edit   remv   desc   typ            def
        addTableColumn( tableColumnModel,  0, C0_MW, false, false, false, false, String.class,  null, sc.TITLE_STATUS);
        addTableColumn( tableColumnModel,  1, C1_MW, false, false, false, false, Integer.class, null, sc.TITLE_INDEX);
        addTableColumn( tableColumnModel,  2, C2_MW, true,  true,  false, false, String.class,  sc.EMPTY_CATEGORY, sc.TITLE_CATEGORY);
        addTableColumn( tableColumnModel,  3, C3_MW, true,  true,  false, false, String.class,  "http://", "URL");
        addTableColumn( tableColumnModel,  4, C4_MW, false, true,  false, false, Boolean.class, "true", sc.bold("pass"));
	addTableColumn( tableColumnModel,  5, C5_MW, true,  true,  false, true,  String.class,  sc.EMPTY_DESCRIPTION, sc.TITLE_DESCRIPTION);
	addTableColumn( tableColumnModel,  6, 10,    false, false, true,  false, StringRule.class, null, "");
        return tableColumnModel;
    }

    
    public void generateSettings(Object settings, Vector<Vector> tableVector, boolean validateOnly) throws Exception {
        List elemList = new ArrayList(tableVector.size());
	StringRule newElem = null;
	int rowIndex = 0;

	for( Vector rowVector : tableVector ){
	    rowIndex++;
            newElem = (StringRule) rowVector.elementAt(6);
            newElem.setCategory( (String) rowVector.elementAt(2) );
	    try{
		URL newURL = new URL( (String) rowVector.elementAt(3) );
		newElem.setString( newURL.toString() );
	    }
	    catch(Exception e){ throw new Exception("Invalid \"URL\" specified in row: " + rowIndex); }
            newElem.setLive( (Boolean) rowVector.elementAt(4) );
            newElem.setDescription( (String) rowVector.elementAt(5) );            
            elemList.add(newElem);  
        }
        
	// SAVE SETTINGS /////////
	if( !validateOnly ){
	    HttpBlockerSettings httpBlockerSettings = (HttpBlockerSettings) settings;
	    httpBlockerSettings.setPassedUrls( elemList );
	}

    }
    
    public Vector<Vector> generateRows(Object settings){
	HttpBlockerSettings httpBlockerSettings = (HttpBlockerSettings) settings;
	List<StringRule> passedUrls = (List<StringRule>) httpBlockerSettings.getPassedUrls();
        Vector<Vector> allRows = new Vector<Vector>(passedUrls.size());
	Vector tempRow = null;
	int rowIndex = 0;

	for( StringRule newElem : passedUrls ){
            rowIndex++;
            tempRow = new Vector(7);
            tempRow.add( super.ROW_SAVED );
            tempRow.add( rowIndex );
            tempRow.add( newElem.getCategory() );
            tempRow.add( newElem.getString() );
            tempRow.add( newElem.isLive() );
            tempRow.add( newElem.getDescription() );
            tempRow.add( newElem );
            allRows.add( tempRow );
        }
        return allRows;
    }
}

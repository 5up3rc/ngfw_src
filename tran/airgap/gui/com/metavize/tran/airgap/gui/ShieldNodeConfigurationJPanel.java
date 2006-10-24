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

package com.metavize.tran.airgap.gui;

import com.metavize.gui.transform.*;
import com.metavize.gui.pipeline.MPipelineJPanel;
import com.metavize.gui.widgets.editTable.*;
import com.metavize.gui.util.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;

import com.metavize.tran.airgap.AirgapSettings;

import com.metavize.mvvm.tran.IPaddr;

import com.metavize.tran.airgap.ShieldNodeRule;

public class ShieldNodeConfigurationJPanel extends MEditTableJPanel{
    
    public ShieldNodeConfigurationJPanel() {
        super(true, true);
        super.setFillJButtonEnabled( true );
        super.setInsets(new Insets(4, 4, 2, 2));
        super.setTableTitle("");
        super.setDetailsTitle("");
        super.setAddRemoveEnabled(true);
        
        // create actual table model
        ShieldNodeConfigurationModel interfaceAliasModel = new ShieldNodeConfigurationModel();
        this.setTableModel( interfaceAliasModel );
    }
}
    



class ShieldNodeConfigurationModel extends MSortedTableModel<Object>{ 
    
    private static final int  T_TW  = Util.TABLE_TOTAL_WIDTH;
    private static final int  C0_MW = Util.STATUS_MIN_WIDTH; /* status */
    private static final int  C1_MW = Util.LINENO_MIN_WIDTH; /* # */
    private static final int  C2_MW = 55;  /* enable  */
    private static final int  C3_MW = 120; /* address */
    private static final int  C4_MW = 105; /* divider */
    private static final int  C5_MW = 120; /* category */
    /* description */
    private static final int  C6_MW = Util.chooseMax(T_TW - (C0_MW + C1_MW + C2_MW + C3_MW + C4_MW + C5_MW), 120);

    private ComboBoxModel dividerModel = super.generateComboBoxModel( ShieldNodeRule.getDividerEnumeration(),
                                                                      ShieldNodeRule.getDividerDefault());
    
    protected boolean getSortable(){ return true; }
    
    public TableColumnModel getTableColumnModel(){
        
        DefaultTableColumnModel tableColumnModel = new DefaultTableColumnModel();
        //                                 #   min    rsz    edit   remv   desc   typ            def
        addTableColumn( tableColumnModel,  0,  C0_MW, false, false, false, false, String.class,  null, sc.TITLE_STATUS );
        addTableColumn( tableColumnModel,  1,  C1_MW, false, false, false, false, Integer.class, null, sc.TITLE_INDEX );
        addTableColumn( tableColumnModel,  2,  C2_MW, false, true,  false, false, Boolean.class, "true", sc.bold("enable"));
        addTableColumn( tableColumnModel,  3,  C3_MW, false, true,  false, false, IPaddrString.class, "1.2.3.4", sc.bold("address") );
        addTableColumn( tableColumnModel,  4,  C4_MW, false, true,  false, false, ComboBoxModel.class, dividerModel, sc.bold("user<br>count") );
        addTableColumn( tableColumnModel,  5,  C5_MW, true,  true,  false, false, String.class, sc.EMPTY_CATEGORY, sc.TITLE_CATEGORY );
        addTableColumn( tableColumnModel,  6,  C6_MW, true,  true,  false, true,  String.class, sc.EMPTY_DESCRIPTION, sc.TITLE_DESCRIPTION );
        addTableColumn( tableColumnModel,  7,     10, false, false, true,  false, ShieldNodeRule.class, null, "");
        return tableColumnModel;
    }
    
    
    public void generateSettings(Object settings, Vector<Vector> tableVector, boolean validateOnly) throws Exception {        
        List<ShieldNodeRule> elemList = new ArrayList(tableVector.size());
	ShieldNodeRule newElem = null;
        int rowIndex = 0;

        for( Vector rowVector : tableVector ){
	    rowIndex++;
            newElem = (ShieldNodeRule)rowVector.elementAt(7);
            newElem.setLive((Boolean)rowVector.elementAt(2));
            try{ newElem.setAddress( ((IPaddrString)rowVector.elementAt(3)).getString() ); }
            catch(Exception e){ throw new Exception("Invalid \"address\" in row: " + rowIndex); }
            // try{ newElem.setNetmask( (String)rowVector.elementAt(4)); }
            // catch(Exception e){ throw new Exception("Invalid \"NETMASK\" in row: " + rowIndex); }
            newElem.setDivider(((ComboBoxModel)rowVector.elementAt(4)).getSelectedItem().toString());
            newElem.setCategory((String)rowVector.elementAt(5));
            newElem.setDescription((String)rowVector.elementAt(6));
            elemList.add(newElem);
        }
        
	// SAVE SETTINGS //////////
	if( !validateOnly ){
	    AirgapSettings airgapSettings = (AirgapSettings) settings;
	    airgapSettings.setShieldNodeRuleList( elemList );
	}
    }

    public Vector<Vector> generateRows(Object settings) {
        AirgapSettings airgapSettings = (AirgapSettings) settings;
	List<ShieldNodeRule> shieldNodeList = 
            (List<ShieldNodeRule>) airgapSettings.getShieldNodeRuleList();
        Vector<Vector> allRows = new Vector<Vector>( shieldNodeList.size());
	Vector tempRow = null;
        int rowIndex = 0;

        for( ShieldNodeRule newElem : shieldNodeList ){
	    rowIndex++;
	    tempRow = new Vector(8);
	    tempRow.add( super.ROW_SAVED );
	    tempRow.add( rowIndex );
            tempRow.add( newElem.isLive());
            tempRow.add( new IPaddrString(newElem.getAddress()) );
	    tempRow.add( super.generateComboBoxModel( ShieldNodeRule.getDividerEnumeration(), newElem.getDividerString()));
            tempRow.add( newElem.getCategory() );
            tempRow.add( newElem.getDescription() );
            tempRow.add( newElem );
	    allRows.add( tempRow );
        }
        return allRows;
    }    
}

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

package com.metavize.tran.portal.gui;

import com.metavize.mvvm.tran.Transform;
import com.metavize.gui.transform.*;
import com.metavize.gui.widgets.editTable.*;
import com.metavize.gui.widgets.MPasswordField;
import com.metavize.gui.util.*;

import com.metavize.tran.portal.*;
import com.metavize.mvvm.portal.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;

public class UserConfigJPanel extends MEditTableJPanel{

    public UserConfigJPanel() {
        super(true, true);
        super.setFillJButtonEnabled( false );
        super.setInsets(new Insets(4, 4, 2, 2));
        super.setTableTitle("");
        super.setDetailsTitle("");
        super.setAddRemoveEnabled(true);
        
        // create actual table model
        UserConfigTableModel userConfigTableModel = new UserConfigTableModel();
        this.setTableModel( userConfigTableModel );
	userConfigTableModel.setSortingStatus(3, UserConfigTableModel.ASCENDING);
    }

}


class UserConfigTableModel extends MSortedTableModel<Object>{ 

    private static final int T_TW = Util.TABLE_TOTAL_WIDTH_LARGE;
    private static final int C0_MW = Util.STATUS_MIN_WIDTH; /* status */
    private static final int C1_MW = Util.LINENO_MIN_WIDTH; /* # - invisible */
    private static final int C2_MW = 55;  /* live */
    private static final int C3_MW = 150; /* UID */
    private static final int C4_MW = 150; /* group */
    private static final int C5_MW = 150; /* edit (settings) */
    private static final int C6_MW = Util.chooseMax(T_TW - (C0_MW + C2_MW + C3_MW + C4_MW + C5_MW), 120); /* description */


    private DefaultComboBoxModel groupModel = new DefaultComboBoxModel();

    public void updateGroupModel(List<PortalGroup> portalGroups){
	groupModel.removeAllElements();
	PortalGroupWrapper defaultPortalGroupWrapper = new PortalGroupWrapper(null);
	groupModel.addElement(defaultPortalGroupWrapper); // for the "default" group
	groupModel.setSelectedItem(defaultPortalGroupWrapper);
	for( PortalGroup portalGroup : portalGroups )
	    groupModel.addElement(new PortalGroupWrapper(portalGroup));
    }
    
    public TableColumnModel getTableColumnModel(){
        
        DefaultTableColumnModel tableColumnModel = new DefaultTableColumnModel();
        //                                 #  min    rsz    edit   remv   desc   typ            def
        addTableColumn( tableColumnModel,  0, C0_MW, false, false, false, false, String.class,  null, sc.TITLE_STATUS );
        addTableColumn( tableColumnModel,  1, C1_MW, false, false, true,  false, Integer.class, null, sc.TITLE_INDEX );
        addTableColumn( tableColumnModel,  2, C2_MW, false, true,  false, false, Boolean.class, "true", sc.bold("live"));
        addTableColumn( tableColumnModel,  3, C3_MW, true,  true,  false, false, String.class,  "[no ID/login]", "user ID/login");
        addTableColumn( tableColumnModel,  4, C4_MW, true,  true,  false, false, ComboBoxModel.class, groupModel, "group");
        addTableColumn( tableColumnModel,  5, C5_MW, false, true,  false, false, SettingsButtonRunnable.class,  "true", "home page settings" );
        addTableColumn( tableColumnModel,  6, C6_MW, true,  true,  false, true,  String.class,  sc.EMPTY_DESCRIPTION, sc.TITLE_DESCRIPTION );
        addTableColumn( tableColumnModel,  7, 10,    false, false, true,  false, PortalUser.class, null, "");
        return tableColumnModel;
    }

    protected void wireUpNewRow(Vector rowVector){
	PortalUser portalUser = (PortalUser) rowVector.elementAt(7);
	SettingsButtonRunnable settingsButtonRunnable = (SettingsButtonRunnable) rowVector.elementAt(5);
	settingsButtonRunnable.setPortalUser(portalUser);
	settingsButtonRunnable.setUserType(true);
    }

    public void prevalidate(Object settings, Vector<Vector> tableVector) throws Exception {
        Hashtable<String,String> uidHashtable = new Hashtable<String,String>();
        int rowIndex = 0;
        // go through all the rows and perform some tests
        for( Vector tempUser : tableVector ){
	    String uid = (String) tempUser.elementAt(3);
	    // all uid's are unique
	    if( uidHashtable.contains( uid ) )
		throw new Exception("The user/login ID in row: " + rowIndex + " has already been taken.");
	    else
		uidHashtable.put(uid,uid);

	    rowIndex++;
	}
    }
        
    public void generateSettings(Object settings, Vector<Vector> tableVector, boolean validateOnly) throws Exception{
        List elemList = new ArrayList(tableVector.size());
	PortalUser newElem = null;

	for( Vector rowVector : tableVector ){
	    newElem = (PortalUser) rowVector.elementAt(7);
            newElem.setLive( (Boolean) rowVector.elementAt(2) );
            newElem.setUid( (String) rowVector.elementAt(3) );
	    PortalGroupWrapper portalGroupWrapper = (PortalGroupWrapper) ((ComboBoxModel) rowVector.elementAt(4)).getSelectedItem();
            newElem.setPortalGroup( portalGroupWrapper.getPortalGroup() );
            newElem.setDescription( (String) rowVector.elementAt(6) );
            elemList.add(newElem);
        }

	// SAVE SETTINGS ////////
	if( !validateOnly ){
	    PortalSettings portalSettings = (PortalSettings) settings;
	    portalSettings.setUsers(elemList);
	}

    }
    
    public Vector<Vector> generateRows(Object settings){
	PortalSettings portalSettings = (PortalSettings) settings;
	List<PortalUser> users = (List<PortalUser>) portalSettings.getUsers();
        Vector<Vector> allRows = new Vector<Vector>(users.size());
	Vector tempRow = null;
	int rowIndex = 0;

	updateGroupModel((List<PortalGroup>)portalSettings.getGroups());

	for( PortalUser newElem : users ){
	    rowIndex++;
            tempRow = new Vector(8);
            tempRow.add( super.ROW_SAVED );
            tempRow.add( rowIndex );
            tempRow.add( newElem.isLive() );
            tempRow.add( newElem.getUid() );
	    ComboBoxModel comboBoxModel = copyComboBoxModel(groupModel);
	    comboBoxModel.setSelectedItem(new PortalGroupWrapper(newElem.getPortalGroup()));
	    tempRow.add( comboBoxModel );
	    SettingsButtonRunnable settingsButtonRunnable = new SettingsButtonRunnable("true");
	    settingsButtonRunnable.setPortalUser(newElem);
	    settingsButtonRunnable.setUserType(true);
	    tempRow.add( settingsButtonRunnable );
            tempRow.add( newElem.getDescription() );
	    tempRow.add( newElem );
            allRows.add( tempRow );
        }
        return allRows;
    }

    class PortalGroupWrapper {
	private PortalGroup portalGroup;
	public PortalGroupWrapper(PortalGroup portalGroup){
	    this.portalGroup = portalGroup;
	}
	public String toString(){
	    if( portalGroup == null )
		return "no group";
	    else
		return portalGroup.getName();
	}
	public PortalGroup getPortalGroup(){
	    return portalGroup;
	}
	public boolean equals(Object obj){
	    if( ! (obj instanceof PortalGroupWrapper) )
		return false;
	    PortalGroupWrapper other = (PortalGroupWrapper) obj;
	    if( (getPortalGroup() == null) && (other.getPortalGroup() == null) )
		return true;
	    else
		return portalGroup.equals(obj);
	}
    }
}

/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.mail.gui;

import com.metavize.gui.widgets.coloredTable.*;
import com.metavize.gui.widgets.dialogs.RefreshLogFailureDialog;
import com.metavize.gui.configuration.EmailCompoundSettings;
import com.metavize.gui.transform.*;
import com.metavize.gui.util.*;

import com.metavize.mvvm.tran.*;
import com.metavize.mvvm.security.Tid;

import com.metavize.tran.mail.papi.*;
import com.metavize.tran.mail.papi.safelist.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.border.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import com.metavize.gui.widgets.editTable.*;

public class WhitelistAllUsersJPanel extends javax.swing.JPanel
    implements Refreshable<EmailCompoundSettings>, ComponentListener {

    private static final Color TABLE_BACKGROUND_COLOR = new Color(213, 213, 226);

    private WhitelistAllUsersTableModel whitelistAllUsersTableModel;
    private MailTransformCompoundSettings mailTransformCompoundSettings;
    
    public WhitelistAllUsersJPanel() {
        // INIT GUI & CUSTOM INIT
        initComponents();
        entryJScrollPane.getViewport().setOpaque(true);
        entryJScrollPane.getViewport().setBackground(TABLE_BACKGROUND_COLOR);
        entryJScrollPane.setViewportBorder(new MatteBorder(2, 2, 2, 1, TABLE_BACKGROUND_COLOR));
        addComponentListener(WhitelistAllUsersJPanel.this);
        
        // create actual table model
        whitelistAllUsersTableModel = new WhitelistAllUsersTableModel();
        setTableModel( whitelistAllUsersTableModel );
        whitelistAllUsersTableModel.setSortingStatus(2, whitelistAllUsersTableModel.ASCENDING);
    }

    public void doRefresh(EmailCompoundSettings emailCompoundSettings){
	mailTransformCompoundSettings = (MailTransformCompoundSettings) emailCompoundSettings.getMailTransformCompoundSettings();
	whitelistAllUsersTableModel.doRefresh(emailCompoundSettings);
    }

    public void setTableModel(MSortedTableModel mSortedTableModel){
        entryJTable.setModel( mSortedTableModel );
        entryJTable.setColumnModel( mSortedTableModel.getTableColumnModel() );
        mSortedTableModel.setTableHeader( entryJTable.getTableHeader() );
        mSortedTableModel.hideColumns( entryJTable );
    }
    
    public MSortedTableModel getTableModel(){
        return (MSortedTableModel) entryJTable.getModel();
    }
    
    public MColoredJTable getJTable(){
        return (MColoredJTable) entryJTable;
    }
        

    public void componentHidden(ComponentEvent e){}
    public void componentMoved(ComponentEvent e){}
    public void componentShown(ComponentEvent e){}
    public void componentResized(ComponentEvent e){
	((MColoredJTable)entryJTable).doGreedyColumn(entryJScrollPane.getViewport().getExtentSize().width);
    }
    
    private void initComponents() {//GEN-BEGIN:initComponents
        java.awt.GridBagConstraints gridBagConstraints;

        contentJPanel = new javax.swing.JPanel();
        eventJPanel = new javax.swing.JPanel();
        removeJButton = new javax.swing.JButton();
        detailJButton = new javax.swing.JButton();
        entryJScrollPane = new javax.swing.JScrollPane();
        entryJTable = new MColoredJTable();

        setLayout(new java.awt.GridBagLayout());

        setOpaque(false);
        contentJPanel.setLayout(new java.awt.GridBagLayout());

        contentJPanel.setOpaque(false);
        eventJPanel.setLayout(new java.awt.GridBagLayout());

        eventJPanel.setFocusCycleRoot(true);
        eventJPanel.setFocusable(false);
        eventJPanel.setOpaque(false);
        removeJButton.setFont(new java.awt.Font("Dialog", 0, 12));
        removeJButton.setText("<html><b>Remove</b> selected</html>");
        removeJButton.setDoubleBuffered(true);
        removeJButton.setFocusPainted(false);
        removeJButton.setFocusable(false);
        removeJButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        removeJButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        removeJButton.setMaximumSize(new java.awt.Dimension(125, 25));
        removeJButton.setMinimumSize(new java.awt.Dimension(125, 25));
        removeJButton.setPreferredSize(new java.awt.Dimension(125, 25));
        removeJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeJButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
        eventJPanel.add(removeJButton, gridBagConstraints);

        detailJButton.setFont(new java.awt.Font("Dialog", 0, 12));
        detailJButton.setText("<html><b>Show</b> detail</html>");
        detailJButton.setDoubleBuffered(true);
        detailJButton.setFocusPainted(false);
        detailJButton.setFocusable(false);
        detailJButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        detailJButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        detailJButton.setMaximumSize(new java.awt.Dimension(125, 25));
        detailJButton.setMinimumSize(new java.awt.Dimension(125, 25));
        detailJButton.setPreferredSize(new java.awt.Dimension(125, 25));
        detailJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                detailJButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        eventJPanel.add(detailJButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        contentJPanel.add(eventJPanel, gridBagConstraints);

        entryJScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        entryJScrollPane.setDoubleBuffered(true);
        entryJTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        entryJTable.setDoubleBuffered(true);
        entryJScrollPane.setViewportView(entryJTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 2);
        contentJPanel.add(entryJScrollPane, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(contentJPanel, gridBagConstraints);

    }//GEN-END:initComponents

    private void detailJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_detailJButtonActionPerformed
        int[] selectedModelRows = getSelectedModelRows();
        if( selectedModelRows.length == 0 )
                return;
        
        // show detail dialog
        Vector<Vector> dataVector = whitelistAllUsersTableModel.getDataVector();
        String account = (String) dataVector.elementAt(selectedModelRows[0]).elementAt(2);
        (new WhitelistUserJDialog((Dialog)getTopLevelAncestor(), mailTransformCompoundSettings, account)).setVisible(true);
        
        // refresh
        whitelistAllUsersTableModel.doRefresh(null);
    }//GEN-LAST:event_detailJButtonActionPerformed

    private void removeJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeJButtonActionPerformed
	if( Util.getIsDemo() )
	    return;
        int[] selectedModelRows = getSelectedModelRows();
        if( selectedModelRows.length == 0 )
                return;
        
        WhitelistRemoveProceedDialog whitelistRemoveProceedDialog = new WhitelistRemoveProceedDialog( (Dialog) this.getTopLevelAncestor() );
	if( !whitelistRemoveProceedDialog.isProceeding() )
	    return;
                
        // release
        String account;
        Vector<Vector> dataVector = whitelistAllUsersTableModel.getDataVector();
        for( int i : selectedModelRows ){
            account = (String) dataVector.elementAt(i).elementAt(2);
            try{
		mailTransformCompoundSettings.getSafelistAdminView().deleteSafelist(account);
            }
            catch(Exception e){Util.handleExceptionNoRestart("Error deleting whitelist: " + account, e);}
        }
        
        // refresh
        whitelistAllUsersTableModel.doRefresh(null);
    }//GEN-LAST:event_removeJButtonActionPerformed
    
    private int[] getSelectedModelRows(){
        int[] selectedViewRows = entryJTable.getSelectedRows();
        if( (selectedViewRows==null) || (selectedViewRows.length==0) || (selectedViewRows[0]==-1) )
            return new int[0];

        // translate view row
        int[] selectedModelRows = new int[selectedViewRows.length];
        for( int i=0; i<selectedViewRows.length; i++ )
            selectedModelRows[i] = getTableModel().getRowViewToModelIndex(selectedViewRows[i]);
        
        return selectedModelRows;
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel contentJPanel;
    private javax.swing.JButton detailJButton;
    protected javax.swing.JScrollPane entryJScrollPane;
    protected javax.swing.JTable entryJTable;
    private javax.swing.JPanel eventJPanel;
    private javax.swing.JButton removeJButton;
    // End of variables declaration//GEN-END:variables

}



class WhitelistAllUsersTableModel extends MSortedTableModel<EmailCompoundSettings> {

    private SafelistAdminView safelistAdminView;
    private static final StringConstants sc = StringConstants.getInstance();
    
    public WhitelistAllUsersTableModel(){
    }
    
    public TableColumnModel getTableColumnModel(){

        DefaultTableColumnModel tableColumnModel = new DefaultTableColumnModel();
        //                                 #   min  rsz    edit   remv   desc   typ               def
        addTableColumn( tableColumnModel,  0,  Util.STATUS_MIN_WIDTH, false, false, true, false, String.class,     null, sc.TITLE_STATUS );
	addTableColumn( tableColumnModel,  1,  Util.LINENO_MIN_WIDTH, false, false, true, false, Integer.class,    null, sc.TITLE_INDEX );
        addTableColumn( tableColumnModel,  2, 300, true,  false,  false, true,  String.class, null, sc.html("account address") );
        addTableColumn( tableColumnModel,  3,  85, true,  false,  false, false, Integer.class, null, sc.html("safe list<br>size") );
        return tableColumnModel;
    }


   
    public void generateSettings(EmailCompoundSettings emailCompoundSettings,
				 Vector<Vector> tableVector, boolean validateOnly) throws Exception { }

    public Vector<Vector> generateRows(EmailCompoundSettings emailCompoundSettings) {
        
        java.util.List<String> safelists = ((MailTransformCompoundSettings)emailCompoundSettings.getMailTransformCompoundSettings()).getSafelists();
        int[] counts = ((MailTransformCompoundSettings)emailCompoundSettings.getMailTransformCompoundSettings()).getSafelistCounts();
        
        Vector<Vector> allRows = new Vector<Vector>(safelists.size());
	Vector tempRow = null;
        int rowIndex = 0;

	for( String safelist : safelists ){
	    rowIndex++;
            tempRow = new Vector(4);
            tempRow.add( super.ROW_SAVED );
            tempRow.add( rowIndex );
            tempRow.add( safelist );
            tempRow.add( counts[rowIndex-1] );
	    allRows.add( tempRow );
        }
        return allRows;

    }





}

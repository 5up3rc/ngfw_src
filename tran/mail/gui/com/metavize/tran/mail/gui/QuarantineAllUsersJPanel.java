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
import com.metavize.gui.widgets.dialogs.*;
import com.metavize.gui.configuration.EmailCompoundSettings;
import com.metavize.gui.configuration.EmailJDialog;
import com.metavize.gui.transform.*;
import com.metavize.gui.util.*;

import com.metavize.mvvm.tran.*;
import com.metavize.mvvm.security.Tid;

import com.metavize.tran.mail.papi.*;
import com.metavize.tran.mail.papi.quarantine.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.border.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import com.metavize.gui.widgets.editTable.*;

public class QuarantineAllUsersJPanel extends javax.swing.JPanel
    implements Refreshable<EmailCompoundSettings>, ComponentListener {

    private static final Color TABLE_BACKGROUND_COLOR = new Color(213, 213, 226);
    private QuarantineAllTableModel quarantineAllTableModel;
    private MailTransformCompoundSettings mailTransformCompoundSettings;
    
    public QuarantineAllUsersJPanel() {
        // INIT GUI & CUSTOM INIT
        initComponents();
        entryJScrollPane.getViewport().setOpaque(true);
        entryJScrollPane.getViewport().setBackground(TABLE_BACKGROUND_COLOR);
        entryJScrollPane.setViewportBorder(new MatteBorder(2, 2, 2, 1, TABLE_BACKGROUND_COLOR));
        addComponentListener(QuarantineAllUsersJPanel.this);
        
        // create actual table model
        quarantineAllTableModel = new QuarantineAllTableModel();
        setTableModel( quarantineAllTableModel );
        // account address - sort by ascending order
        quarantineAllTableModel.setSortingStatus(2, quarantineAllTableModel.ASCENDING);
    }

    public void doRefresh(EmailCompoundSettings emailCompoundSettings){
        mailTransformCompoundSettings = (MailTransformCompoundSettings) emailCompoundSettings.getMailTransformCompoundSettings();
        quarantineAllTableModel.doRefresh(mailTransformCompoundSettings);
        String usedSpace = mailTransformCompoundSettings.getQuarantineMaintenanceView().getFormattedInboxesTotalSize(true);
        totalJLabel.setText("Total Disk Space Used: " + usedSpace + " MB");
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
                totalJLabel = new javax.swing.JLabel();
                entryJScrollPane = new javax.swing.JScrollPane();
                entryJTable = new MColoredJTable();
                eventJPanel = new javax.swing.JPanel();
                purgeJButton = new javax.swing.JButton();
                releaseJButton = new javax.swing.JButton();
                detailJButton = new javax.swing.JButton();

                setLayout(new java.awt.GridBagLayout());

                setOpaque(false);
                contentJPanel.setLayout(new java.awt.GridBagLayout());

                contentJPanel.setOpaque(false);
                totalJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
                totalJLabel.setText("jLabel1");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
                contentJPanel.add(totalJLabel, gridBagConstraints);

                entryJScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
                entryJScrollPane.setDoubleBuffered(true);
                entryJTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
                entryJTable.setDoubleBuffered(true);
                entryJScrollPane.setViewportView(entryJTable);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(2, 2, 0, 2);
                contentJPanel.add(entryJScrollPane, gridBagConstraints);

                eventJPanel.setLayout(new java.awt.GridBagLayout());

                eventJPanel.setFocusCycleRoot(true);
                eventJPanel.setFocusable(false);
                eventJPanel.setOpaque(false);
                purgeJButton.setFont(new java.awt.Font("Dialog", 0, 12));
                purgeJButton.setText("<html><b>Purge</b> selected</html>");
                purgeJButton.setDoubleBuffered(true);
                purgeJButton.setFocusPainted(false);
                purgeJButton.setFocusable(false);
                purgeJButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
                purgeJButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
                purgeJButton.setMaximumSize(new java.awt.Dimension(125, 25));
                purgeJButton.setMinimumSize(new java.awt.Dimension(125, 25));
                purgeJButton.setPreferredSize(new java.awt.Dimension(125, 25));
                purgeJButton.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                purgeJButtonActionPerformed(evt);
                        }
                });

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
                eventJPanel.add(purgeJButton, gridBagConstraints);

                releaseJButton.setFont(new java.awt.Font("Dialog", 0, 12));
                releaseJButton.setText("<html><b>Release</b> selected</html>");
                releaseJButton.setDoubleBuffered(true);
                releaseJButton.setFocusPainted(false);
                releaseJButton.setFocusable(false);
                releaseJButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
                releaseJButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
                releaseJButton.setMaximumSize(new java.awt.Dimension(125, 25));
                releaseJButton.setMinimumSize(new java.awt.Dimension(125, 25));
                releaseJButton.setPreferredSize(new java.awt.Dimension(125, 25));
                releaseJButton.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                releaseJButtonActionPerformed(evt);
                        }
                });

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
                eventJPanel.add(releaseJButton, gridBagConstraints);

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
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
                contentJPanel.add(eventJPanel, gridBagConstraints);

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
        Vector<Vector> dataVector = quarantineAllTableModel.getDataVector();
        String account = (String) dataVector.elementAt(selectedModelRows[0]).elementAt(2);
        (new QuarantineSingleUserJDialog((Dialog)getTopLevelAncestor(), mailTransformCompoundSettings, account)).setVisible(true);
        
        // refresh
        //EmailJDialog.instance().reassignInfiniteProgressJComponent(); // XXX hackorama
	EmailJDialog.instance().refreshGui();
        //quarantineAllTableModel.doRefresh(mailTransformCompoundSettings);
    }//GEN-LAST:event_detailJButtonActionPerformed

    private void releaseJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_releaseJButtonActionPerformed
	if( Util.getIsDemo() )
	    return;
        int[] selectedModelRows = getSelectedModelRows();
        if( selectedModelRows.length == 0 )
                return;
        
        // release
        Vector<String> accounts = new Vector<String>();;
        Vector<Vector> dataVector = quarantineAllTableModel.getDataVector();
        for( int i : selectedModelRows ){
	    accounts.add( (String) dataVector.elementAt(i).elementAt(2) );
	}
	new ReleaseAndPurgeThread(accounts,true);
    }//GEN-LAST:event_releaseJButtonActionPerformed

    private void purgeJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_purgeJButtonActionPerformed
	if( Util.getIsDemo() )
	    return;
        int[] selectedModelRows = getSelectedModelRows();
        if( selectedModelRows.length == 0 )
                return;

	QuarantinePurgeProceedDialog quarantinePurgeProceedDialog = new QuarantinePurgeProceedDialog( (Dialog) this.getTopLevelAncestor() );
	if( !quarantinePurgeProceedDialog.isProceeding() )
	    return;
        
        // purge
        Vector<String> accounts = new Vector<String>();
        Vector<Vector> dataVector = quarantineAllTableModel.getDataVector();
        for( int i : selectedModelRows ){
	    accounts.add( (String) dataVector.elementAt(i).elementAt(2) );
	}
	new ReleaseAndPurgeThread(accounts,false);
    }//GEN-LAST:event_purgeJButtonActionPerformed

    private class ReleaseAndPurgeThread extends Thread {
	private Vector<String> accounts;
	private boolean doRelease;
	public ReleaseAndPurgeThread(Vector<String> accounts, boolean doRelease){
	    this.accounts = accounts;
	    this.doRelease = doRelease;
	    setDaemon(true);
	    if( doRelease )
		((MConfigJDialog)QuarantineAllUsersJPanel.this.getTopLevelAncestor()).getInfiniteProgressJComponent().start("Releasing...");
	    else
		((MConfigJDialog)QuarantineAllUsersJPanel.this.getTopLevelAncestor()).getInfiniteProgressJComponent().start("Purging...");
	    start();
	}
	public void run(){
	    // DO RESCUE
            try{
		for( String account : accounts )
		    if( doRelease ){
			mailTransformCompoundSettings.getQuarantineMaintenanceView().rescueInbox(account);
		    }
		    else{
			mailTransformCompoundSettings.getQuarantineMaintenanceView().deleteInbox(account);
		    }
            }
            catch(Exception e){
		if( doRelease ){
		    Util.handleExceptionNoRestart("Error releasing inbox", e);
		    MOneButtonJDialog.factory(QuarantineAllUsersJPanel.this.getTopLevelAncestor(), "",
					      "An account could not be released.",
					      "Quarantine Release Warning", "");
		}
		else{
		    Util.handleExceptionNoRestart("Error purging inbox", e);
		    MOneButtonJDialog.factory(QuarantineAllUsersJPanel.this.getTopLevelAncestor(), "",
					      "An account could not be purged.",
					      "Quarantine Purge Warning", "");
		}
	    }
	    // DO REFRESH
	    ((MConfigJDialog)QuarantineAllUsersJPanel.this.getTopLevelAncestor()).getInfiniteProgressJComponent().setTextLater("Refreshing...");
	    SwingUtilities.invokeLater( new Runnable(){ public void run(){
		quarantineAllTableModel.doRefresh(mailTransformCompoundSettings);
	    }});
	    ((MConfigJDialog)QuarantineAllUsersJPanel.this.getTopLevelAncestor()).getInfiniteProgressJComponent().stopLater(1500l);
        }
    }
    
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
        private javax.swing.JButton purgeJButton;
        private javax.swing.JButton releaseJButton;
        private javax.swing.JLabel totalJLabel;
        // End of variables declaration//GEN-END:variables
}


class QuarantineAllTableModel extends MSortedTableModel<MailTransformCompoundSettings> {

    private static final StringConstants sc = StringConstants.getInstance();
    
    public QuarantineAllTableModel(){
    }
    
    public TableColumnModel getTableColumnModel(){

        DefaultTableColumnModel tableColumnModel = new DefaultTableColumnModel();
        //                                 #   min  rsz    edit   remv   desc   typ               def
        addTableColumn( tableColumnModel,  0,  Util.STATUS_MIN_WIDTH, false, false, true, false, String.class,     null, sc.TITLE_STATUS );
	addTableColumn( tableColumnModel,  1,  Util.LINENO_MIN_WIDTH, false, false, true, false, Integer.class,    null, sc.TITLE_INDEX );
        addTableColumn( tableColumnModel,  2, 300, true,  false,  false, true,  String.class, null, sc.html("account address") );
        addTableColumn( tableColumnModel,  3,  85, true,  false,  false, false, Integer.class, null, sc.html("message<br>count") );
        addTableColumn( tableColumnModel,  4,  85, true,  false,  false, false, Long.class,    null, sc.html("data size<br>(kB)") );
        return tableColumnModel;
    }


    public void generateSettings(MailTransformCompoundSettings mailTransformCompoundSettings,
				 Vector<Vector> tableVector, boolean validateOnly) throws Exception { }

    public Vector<Vector> generateRows(MailTransformCompoundSettings mailTransformCompoundSettings) {
        
        java.util.List<Inbox> inboxes = mailTransformCompoundSettings.getInboxList();
        Vector<Vector> allRows = new Vector<Vector>(inboxes.size());
	Vector tempRow = null;
        int rowIndex = 0;

	for( Inbox inbox : inboxes ){
	    rowIndex++;
            tempRow = new Vector(5);
            tempRow.add( super.ROW_SAVED );
            tempRow.add( rowIndex );
            tempRow.add( inbox.getAddress() );
            tempRow.add( inbox.getNumMails() );
            tempRow.add( inbox.getFormattedTotalSz() );
            allRows.add( tempRow );
        }

        return allRows;
    }
}

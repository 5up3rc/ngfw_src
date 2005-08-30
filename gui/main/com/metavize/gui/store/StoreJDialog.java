/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id: StoreJDialog.java 194 2005-04-06 19:13:55Z inieves $
 */

package com.metavize.gui.store;

import com.metavize.gui.transform.*;
import com.metavize.gui.widgets.dialogs.*;
import com.metavize.gui.widgets.coloredTable.*;
import com.metavize.gui.widgets.editTable.*;
import com.metavize.gui.util.*;

import com.metavize.mvvm.security.PasswordUtil;
import com.metavize.mvvm.*;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.table.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.*;
import javax.jnlp.ServiceManager;
import javax.jnlp.BasicService;
import java.net.URL;

public class StoreJDialog extends javax.swing.JDialog implements java.awt.event.WindowListener {


    private boolean purchasedTransform = false;
    private MTransformJButton mTransformJButton;
    private GridBagConstraints gridBagConstraints = new GridBagConstraints(0, 0, 1, 1, 0d, 0d, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 0);
    

    public StoreJDialog(MTransformJButton mTransformJButton) {
        super(Util.getMMainJFrame(), true);
        this.mTransformJButton = mTransformJButton;
	this.generateButtonText();
        this.initComponents();
        this.setBounds( Util.generateCenteredBounds( Util.getMMainJFrame().getBounds(), this.getWidth(), this.getHeight()) );
        this.addWindowListener(this);   


        // WEBPAGE BUTTON ///////
        if( Util.isLocal() ){
            moreJButton.setEnabled(false);
        }
        else{
            localJLabel.setVisible(false);
        }
        
        // APPLIANCE INFO //////
        mTransformJPanel.add(mTransformJButton.duplicate(), gridBagConstraints);
        descriptionJTextArea.setText(mTransformJButton.getFullDescription());
        priceJLabel.setText("$" + mTransformJButton.getPrice());       
    }
    
    public MTransformJButton getPurchasedMTransformJButton(){
        if(purchasedTransform)
            return mTransformJButton;
        else
            return null;
    }

    // BUTTON STRINGS //////////////////////////////////
    protected String RELOAD_INIT_STRING;
    protected String RELOAD_ACTION_STRING;
    protected String SAVE_INIT_STRING;
    protected String SAVE_ACTION_STRING;

    protected void generateButtonText(){
        String price = mTransformJButton.getPrice();
        if( price.equals("0") ){
	    SAVE_INIT_STRING = "<html><b>Download</b></html>";
	    SAVE_ACTION_STRING = "<html>(downloading)</html>";
        }
        else{
	    SAVE_INIT_STRING = "<html><b>Purchase</b></html>";
	    SAVE_ACTION_STRING = "<html>(purchasing)</html>";
        }
	RELOAD_INIT_STRING = "<html><b>Cancel</b></html>";
        RELOAD_ACTION_STRING = "<html>(cancelling)</html>";
    }
    ////////////////////////////////////////////////////


    // SIZING ///////////////////////////////
    protected Dimension MIN_SIZE = new Dimension(640, 480);
    protected Dimension MAX_SIZE = new Dimension(640, 480);
    ////////////////////////////////////////


    
    private void initComponents() {//GEN-BEGIN:initComponents
        java.awt.GridBagConstraints gridBagConstraints;

        buttonGroup1 = new javax.swing.ButtonGroup();
        contentJTabbedPane = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        purchaseJPanel = new javax.swing.JPanel();
        mTransformJPanel = new javax.swing.JPanel();
        storeJScrollPane = new javax.swing.JScrollPane();
        descriptionJTextArea = new javax.swing.JTextArea();
        mTransformJPanel1 = new javax.swing.JPanel();
        priceNameJLabel = new javax.swing.JLabel();
        priceJLabel = new javax.swing.JLabel();
        moreJButton = new javax.swing.JButton();
        localJLabel = new javax.swing.JLabel();
        statusProgressBar = new javax.swing.JProgressBar();
        closeJButton = new javax.swing.JButton();
        reloadJButton = new javax.swing.JButton();
        saveJButton = new javax.swing.JButton();
        backgroundJLabel = new com.metavize.gui.widgets.MTiledIconLabel();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("");
        setModal(true);
        setResizable(false);
        contentJTabbedPane.setDoubleBuffered(true);
        contentJTabbedPane.setFocusable(false);
        contentJTabbedPane.setFont(new java.awt.Font("Default", 0, 12));
        jPanel1.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        purchaseJPanel.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        mTransformJPanel.setLayout(new java.awt.GridBagLayout());

        mTransformJPanel.setBorder(new javax.swing.border.TitledBorder(null, "Software Appliance to Purchase", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Arial", 0, 11)));
        mTransformJPanel.setOpaque(false);
        purchaseJPanel.add(mTransformJPanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 30, 210, 150));

        storeJScrollPane.setBorder(new javax.swing.border.TitledBorder(null, "Full Description", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Arial", 0, 11)));
        storeJScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        storeJScrollPane.setDoubleBuffered(true);
        storeJScrollPane.setOpaque(false);
        storeJScrollPane.getViewport().setOpaque(false);
        descriptionJTextArea.setEditable(false);
        descriptionJTextArea.setFont(new java.awt.Font("Arial", 0, 12));
        descriptionJTextArea.setLineWrap(true);
        descriptionJTextArea.setWrapStyleWord(true);
        descriptionJTextArea.setDoubleBuffered(true);
        descriptionJTextArea.setMargin(new java.awt.Insets(5, 5, 5, 5));
        descriptionJTextArea.setOpaque(false);
        storeJScrollPane.setViewportView(descriptionJTextArea);

        purchaseJPanel.add(storeJScrollPane, new org.netbeans.lib.awtextra.AbsoluteConstraints(270, 30, 300, 150));

        mTransformJPanel1.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        mTransformJPanel1.setBorder(new javax.swing.border.TitledBorder(null, "Purchase Information", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Arial", 0, 11)));
        mTransformJPanel1.setOpaque(false);
        priceNameJLabel.setFont(new java.awt.Font("Arial", 1, 12));
        priceNameJLabel.setText("Price:");
        priceNameJLabel.setDoubleBuffered(true);
        mTransformJPanel1.add(priceNameJLabel, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 30, -1, -1));

        priceJLabel.setFont(new java.awt.Font("Arial", 0, 12));
        priceJLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        priceJLabel.setText(" ");
        priceJLabel.setDoubleBuffered(true);
        mTransformJPanel1.add(priceJLabel, new org.netbeans.lib.awtextra.AbsoluteConstraints(50, 30, 140, -1));

        purchaseJPanel.add(mTransformJPanel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 200, 210, 90));

        moreJButton.setFont(new java.awt.Font("Dialog", 0, 12));
        moreJButton.setText("Read more online...");
        moreJButton.setFocusPainted(false);
        moreJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moreJButtonActionPerformed(evt);
            }
        });

        purchaseJPanel.add(moreJButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(340, 210, -1, -1));

        localJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        localJLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        localJLabel.setText("<html><center>(Disabled because there is no browser present.)<br>\nPlease go to http://www.metavize.com to<br>\nlearn about this Software Appliance.\n</center></html>");
        purchaseJPanel.add(localJLabel, new org.netbeans.lib.awtextra.AbsoluteConstraints(280, 240, -1, -1));

        jPanel1.add(purchaseJPanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, -1, -1));

        statusProgressBar.setFont(new java.awt.Font("Dialog", 0, 12));
        statusProgressBar.setForeground(new java.awt.Color(68, 91, 255));
        statusProgressBar.setMaximumSize(new java.awt.Dimension(32767, 16));
        statusProgressBar.setMinimumSize(new java.awt.Dimension(10, 16));
        statusProgressBar.setPreferredSize(new java.awt.Dimension(148, 16));
        statusProgressBar.setString("");
        statusProgressBar.setStringPainted(true);
        jPanel1.add(statusProgressBar, new org.netbeans.lib.awtextra.AbsoluteConstraints(15, 330, 575, -1));

        contentJTabbedPane.addTab("Purchase a Software Appliance", jPanel1);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(15, 15, 15, 15);
        getContentPane().add(contentJTabbedPane, gridBagConstraints);

        closeJButton.setFont(new java.awt.Font("Default", 0, 12));
        closeJButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/metavize/gui/images/Button_Close_Window_106x17.png")));
        closeJButton.setDoubleBuffered(true);
        closeJButton.setFocusPainted(false);
        closeJButton.setFocusable(false);
        closeJButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        closeJButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        closeJButton.setMaximumSize(new java.awt.Dimension(140, 25));
        closeJButton.setMinimumSize(new java.awt.Dimension(140, 25));
        closeJButton.setPreferredSize(new java.awt.Dimension(140, 25));
        closeJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeJButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 15, 0);
        getContentPane().add(closeJButton, gridBagConstraints);

        reloadJButton.setFont(new java.awt.Font("Arial", 0, 12));
        reloadJButton.setText(RELOAD_INIT_STRING);
        reloadJButton.setDoubleBuffered(true);
        reloadJButton.setFocusPainted(false);
        reloadJButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        reloadJButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        reloadJButton.setMaximumSize(new java.awt.Dimension(120, 25));
        reloadJButton.setMinimumSize(new java.awt.Dimension(120, 25));
        reloadJButton.setPreferredSize(new java.awt.Dimension(120, 25));
        reloadJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reloadJButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHEAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 15, 15);
        getContentPane().add(reloadJButton, gridBagConstraints);

        saveJButton.setFont(new java.awt.Font("Arial", 0, 12));
        saveJButton.setText(SAVE_INIT_STRING);
        saveJButton.setDoubleBuffered(true);
        saveJButton.setFocusPainted(false);
        saveJButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        saveJButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        saveJButton.setMaximumSize(new java.awt.Dimension(78, 25));
        saveJButton.setMinimumSize(new java.awt.Dimension(78, 25));
        saveJButton.setPreferredSize(new java.awt.Dimension(78, 25));
        saveJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveJButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.ipadx = 40;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 15, 15);
        getContentPane().add(saveJButton, gridBagConstraints);

        backgroundJLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/metavize/gui/images/DarkGreyBackground1600x100.png")));
        backgroundJLabel.setDoubleBuffered(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(backgroundJLabel, gridBagConstraints);

        java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setBounds((screenSize.width-640)/2, (screenSize.height-480)/2, 640, 480);
    }//GEN-END:initComponents

    private void moreJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moreJButtonActionPerformed
        try{
	    URL newURL = new URL( mTransformJButton.getWebpage() );
	    ((BasicService) ServiceManager.lookup("javax.jnlp.BasicService")).showDocument(newURL);
	}
	catch(Exception f){
            Util.handleExceptionNoRestart("error launching browser for EdgeReport", f);
	}
    }//GEN-LAST:event_moreJButtonActionPerformed

    Thread purchaseThread = null;
    private void saveJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveJButtonActionPerformed
        // DEMO ////////
        if(Util.getIsDemo()){
            StoreJDialog.this.setVisible(false);
            return;
        }

        saveJButton.setEnabled(false);
        reloadJButton.setEnabled(false);
        closeJButton.setEnabled(false);
        saveJButton.setText(SAVE_ACTION_STRING);

        // UPGRADE CHECK ///////
        if( Util.mustCheckUpgrades() ){
            StoreCheckJDialog storeCheckJDialog = new StoreCheckJDialog();
            storeCheckJDialog.setVisible(true);
            if( Util.getUpgradeCount() > 0 ){
                StoreJDialog.this.setVisible(false);
                return;
            }
        }
        
        // PURCHASE /////// launch a thread, close dialog when done
        purchaseThread = mTransformJButton.purchase( statusProgressBar, this );        
    }//GEN-LAST:event_saveJButtonActionPerformed

    private void reloadJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadJButtonActionPerformed
        windowClosing(null);
    }//GEN-LAST:event_reloadJButtonActionPerformed

    private void closeJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeJButtonActionPerformed
        windowClosing(null);
    }//GEN-LAST:event_closeJButtonActionPerformed

    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
        if( statusProgressBar.getValue() != 0)
            return;
        if( purchaseThread != null )
            purchaseThread.interrupt();
        this.setVisible(false);
        this.dispose();
    }

    public void windowClosed(java.awt.event.WindowEvent windowEvent) {}
    public void windowActivated(java.awt.event.WindowEvent windowEvent) {}
    public void windowDeactivated(java.awt.event.WindowEvent windowEvent) {}
    public void windowDeiconified(java.awt.event.WindowEvent windowEvent) {}
    public void windowIconified(java.awt.event.WindowEvent windowEvent) {}
    public void windowOpened(java.awt.event.WindowEvent windowEvent) {}


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel backgroundJLabel;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton closeJButton;
    protected javax.swing.JTabbedPane contentJTabbedPane;
    private javax.swing.JTextArea descriptionJTextArea;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel localJLabel;
    private javax.swing.JPanel mTransformJPanel;
    private javax.swing.JPanel mTransformJPanel1;
    private javax.swing.JButton moreJButton;
    private javax.swing.JLabel priceJLabel;
    private javax.swing.JLabel priceNameJLabel;
    private javax.swing.JPanel purchaseJPanel;
    protected javax.swing.JButton reloadJButton;
    protected javax.swing.JButton saveJButton;
    private javax.swing.JProgressBar statusProgressBar;
    private javax.swing.JScrollPane storeJScrollPane;
    // End of variables declaration//GEN-END:variables

    
}








/*
 * Copyright (c) 2003, 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id: NatJPanel.java 194 2005-04-06 19:13:55Z rbscott $
 */

package com.metavize.tran.nat.gui;

import com.metavize.gui.util.Util;
import com.metavize.gui.transform.*;

import com.metavize.mvvm.tran.IPaddr;
import com.metavize.tran.nat.*;

import java.awt.*;


public class NatJPanel extends javax.swing.JPanel implements Savable<Object>, Refreshable<Object> {
    
    private static final String EXCEPTION_INTERNAL_ADDRESS = "The Internal IP address must be a valid IP address.";
    private static final String EXCEPTION_INTERNAL_SUBNET = "The Internal Subnet must be a valid IP address.";

    public NatJPanel() {
        initComponents();
    }
        
    // SETTINGS CHANGE NOTIFICATION /////////
    private SettingsChangedListener settingsChangedListener;
    public void setSettingsChangedListener(SettingsChangedListener settingsChangedListener){
	this.settingsChangedListener = settingsChangedListener;
    }
    ///////////////////////////////////////////
    
    public void doSave(Object settings, boolean validateOnly) throws Exception {
        
        // ENABLED ///////////
        boolean natEnabled = natEnabledJRadioButton.isSelected();
        
        // INTERNAL ADDRESS //////
        IPaddr natInternalAddress = null;
        internalAddressIPaddrJTextField.setBackground( Color.WHITE );
        if(natEnabled){
            try{
                natInternalAddress = IPaddr.parse( internalAddressIPaddrJTextField.getText() );
            }
            catch(Exception e){
                internalAddressIPaddrJTextField.setBackground( Util.INVALID_BACKGROUND_COLOR );
                throw new Exception(EXCEPTION_INTERNAL_ADDRESS);
            }
        }
        
        // INTERNAL SUBNET ///////
        IPaddr natInternalSubnet = null;
        internalSubnetIPaddrJTextField.setBackground( Color.WHITE );
        if(natEnabled){
            try{
                natInternalSubnet = IPaddr.parse( internalSubnetIPaddrJTextField.getText() );
            }
            catch(Exception e){
                internalSubnetIPaddrJTextField.setBackground( Util.INVALID_BACKGROUND_COLOR );
                throw new Exception(EXCEPTION_INTERNAL_SUBNET);
            }
        }        
        
        // SAVE THE VALUES ////////////////////////////////////
	if( !validateOnly ){
	    NatBasicSettings natSettings = (NatBasicSettings) settings;
	    natSettings.setNatEnabled( natEnabled );
	    if( natEnabled ){
		natSettings.setNatInternalAddress( natInternalAddress );
		natSettings.setNatInternalSubnet( natInternalSubnet );
	    }
	}
        
    }
    
    boolean natEnabledCurrent;
    String natInternalAddressCurrent;
    String natInternalSubnetCurrent;
    String natInternalNetworkCurrent;
    String natExternalAddressCurrent;
    boolean isDhcpEnabledCurrent;

    public void doRefresh(Object settings) {
        NatBasicSettings natSettings = (NatBasicSettings) settings;
        
        // ENABLED ///////////
	natEnabledCurrent = natSettings.getNatEnabled();
	this.setNatEnabledDependency(natEnabledCurrent);
	if( natEnabledCurrent )
	    natEnabledJRadioButton.setSelected(true);
	else
	    natDisabledJRadioButton.setSelected(true);
        
        // INTERNAL ADDRESS //////
	natInternalAddressCurrent = natSettings.getNatInternalAddress().toString();
	internalAddressIPaddrJTextField.setText( natInternalAddressCurrent );
	internalAddressIPaddrJTextField.setBackground( Color.WHITE );
        
        // INTERNAL SUBNET ///////
	natInternalSubnetCurrent  = natSettings.getNatInternalSubnet().toString();
	internalSubnetIPaddrJTextField.setText( natInternalSubnetCurrent );
	internalSubnetIPaddrJTextField.setBackground( Color.WHITE );
        
        // INTERNAL NETWORK ///////
	natInternalNetworkCurrent = IPaddr.and(natSettings.getNatInternalAddress(), natSettings.getNatInternalSubnet()).toString();
	internalNetworkJLabel.setText( natInternalNetworkCurrent );
        
        // EXTERNAL ADDRESS ///////
	natExternalAddressCurrent = Util.getMvvmContext().networkingManager().get().host().toString();
	externalAddressJLabel.setText( natExternalAddressCurrent );
        
        // DHCP ///////
	isDhcpEnabledCurrent = Util.getMvvmContext().networkingManager().get().isDhcpEnabled();
	if( isDhcpEnabledCurrent )
	    externalMethodJLabel.setText("Dynamic via DHCP");
	else
	    externalMethodJLabel.setText("Manually specified");
        
    }

        
    

    
        private void initComponents() {//GEN-BEGIN:initComponents
                java.awt.GridBagConstraints gridBagConstraints;

                enabledButtonGroup = new javax.swing.ButtonGroup();
                explanationJPanel = new javax.swing.JPanel();
                jTextArea2 = new javax.swing.JTextArea();
                jPanel1 = new javax.swing.JPanel();
                natEnabledJRadioButton = new javax.swing.JRadioButton();
                natDisabledJRadioButton = new javax.swing.JRadioButton();
                jLabel1 = new javax.swing.JLabel();
                externalRemoteJPanel = new javax.swing.JPanel();
                jTextArea3 = new javax.swing.JTextArea();
                restrictIPJPanel = new javax.swing.JPanel();
                jLabel5 = new javax.swing.JLabel();
                internalAddressIPaddrJTextField = new javax.swing.JTextField();
                jLabel8 = new javax.swing.JLabel();
                internalSubnetIPaddrJTextField = new javax.swing.JTextField();
                jLabel9 = new javax.swing.JLabel();
                internalNetworkJLabel = new javax.swing.JLabel();
                internalRemoteJPanel = new javax.swing.JPanel();
                jTextArea1 = new javax.swing.JTextArea();
                restrictIPJPanel1 = new javax.swing.JPanel();
                jLabel6 = new javax.swing.JLabel();
                externalAddressJLabel = new javax.swing.JLabel();
                jLabel10 = new javax.swing.JLabel();
                externalMethodJLabel = new javax.swing.JLabel();

                setLayout(new java.awt.GridBagLayout());

                setMinimumSize(new java.awt.Dimension(530, 445));
                setPreferredSize(new java.awt.Dimension(530, 445));
                explanationJPanel.setLayout(new java.awt.GridBagLayout());

                explanationJPanel.setBorder(new javax.swing.border.TitledBorder(null, "NAT (Network Address Translation)", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Dialog", 1, 16)));
                jTextArea2.setEditable(false);
                jTextArea2.setLineWrap(true);
                jTextArea2.setText("NAT allows multiple computers in the internal network to share internet access through a single shared public IP address.");
                jTextArea2.setWrapStyleWord(true);
                jTextArea2.setOpaque(false);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 15);
                explanationJPanel.add(jTextArea2, gridBagConstraints);

                jPanel1.setLayout(new java.awt.GridBagLayout());

                enabledButtonGroup.add(natEnabledJRadioButton);
                natEnabledJRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
                natEnabledJRadioButton.setText("Enabled");
                natEnabledJRadioButton.setFocusPainted(false);
                natEnabledJRadioButton.setFocusable(false);
                natEnabledJRadioButton.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                natEnabledJRadioButtonActionPerformed(evt);
                        }
                });

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                jPanel1.add(natEnabledJRadioButton, gridBagConstraints);

                enabledButtonGroup.add(natDisabledJRadioButton);
                natDisabledJRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
                natDisabledJRadioButton.setText("Disabled");
                natDisabledJRadioButton.setFocusPainted(false);
                natDisabledJRadioButton.setFocusable(false);
                natDisabledJRadioButton.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                natDisabledJRadioButtonActionPerformed(evt);
                        }
                });

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                jPanel1.add(natDisabledJRadioButton, gridBagConstraints);

                jLabel1.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel1.setText("NAT ");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.gridheight = 2;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                jPanel1.add(jLabel1, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.insets = new java.awt.Insets(15, 0, 0, 0);
                explanationJPanel.add(jPanel1, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
                add(explanationJPanel, gridBagConstraints);

                externalRemoteJPanel.setLayout(new java.awt.GridBagLayout());

                externalRemoteJPanel.setBorder(new javax.swing.border.TitledBorder(null, "Internal Address (Gateway)", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Dialog", 1, 16)));
                jTextArea3.setEditable(false);
                jTextArea3.setLineWrap(true);
                jTextArea3.setText("The internal address is EdgeGuard's address on the internal network, which computers will use as their gateway.  This address is also used to contact EdgeGuard for configuration.");
                jTextArea3.setWrapStyleWord(true);
                jTextArea3.setOpaque(false);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 15);
                externalRemoteJPanel.add(jTextArea3, gridBagConstraints);

                restrictIPJPanel.setLayout(new java.awt.GridBagLayout());

                jLabel5.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel5.setText("Internal IP Address: ");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                restrictIPJPanel.add(jLabel5, gridBagConstraints);

                internalAddressIPaddrJTextField.setFocusable(false);
                internalAddressIPaddrJTextField.setMaximumSize(new java.awt.Dimension(150, 19));
                internalAddressIPaddrJTextField.setMinimumSize(new java.awt.Dimension(150, 19));
                internalAddressIPaddrJTextField.setPreferredSize(new java.awt.Dimension(150, 19));
                internalAddressIPaddrJTextField.addCaretListener(new javax.swing.event.CaretListener() {
                        public void caretUpdate(javax.swing.event.CaretEvent evt) {
                                internalAddressIPaddrJTextFieldCaretUpdate(evt);
                        }
                });

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
                restrictIPJPanel.add(internalAddressIPaddrJTextField, gridBagConstraints);

                jLabel8.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel8.setText("Internal Subnet: ");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                restrictIPJPanel.add(jLabel8, gridBagConstraints);

                internalSubnetIPaddrJTextField.setFocusable(false);
                internalSubnetIPaddrJTextField.setMaximumSize(new java.awt.Dimension(150, 19));
                internalSubnetIPaddrJTextField.setMinimumSize(new java.awt.Dimension(150, 19));
                internalSubnetIPaddrJTextField.setPreferredSize(new java.awt.Dimension(150, 19));
                internalSubnetIPaddrJTextField.addCaretListener(new javax.swing.event.CaretListener() {
                        public void caretUpdate(javax.swing.event.CaretEvent evt) {
                                internalSubnetIPaddrJTextFieldCaretUpdate(evt);
                        }
                });

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
                restrictIPJPanel.add(internalSubnetIPaddrJTextField, gridBagConstraints);

                jLabel9.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel9.setText("Network Address: ");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                gridBagConstraints.insets = new java.awt.Insets(15, 0, 2, 0);
                restrictIPJPanel.add(jLabel9, gridBagConstraints);

                internalNetworkJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
                internalNetworkJLabel.setText("012.345.678.999");
                internalNetworkJLabel.setMinimumSize(null);
                internalNetworkJLabel.setPreferredSize(null);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.insets = new java.awt.Insets(15, 0, 2, 0);
                restrictIPJPanel.add(internalNetworkJLabel, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.ipadx = 25;
                gridBagConstraints.insets = new java.awt.Insets(15, 0, 0, 0);
                externalRemoteJPanel.add(restrictIPJPanel, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
                add(externalRemoteJPanel, gridBagConstraints);

                internalRemoteJPanel.setLayout(new java.awt.GridBagLayout());

                internalRemoteJPanel.setBorder(new javax.swing.border.TitledBorder(null, "External Address (Public Address)", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Dialog", 1, 16)));
                jTextArea1.setEditable(false);
                jTextArea1.setLineWrap(true);
                jTextArea1.setText("The external address is EdgeGuard's address on the external network.  This is specified through the \"Networking\" Config Panel.");
                jTextArea1.setWrapStyleWord(true);
                jTextArea1.setOpaque(false);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 15);
                internalRemoteJPanel.add(jTextArea1, gridBagConstraints);

                restrictIPJPanel1.setLayout(new java.awt.GridBagLayout());

                jLabel6.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel6.setText("External IP Address: ");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                restrictIPJPanel1.add(jLabel6, gridBagConstraints);

                externalAddressJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
                externalAddressJLabel.setText("012.345.678.999");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
                restrictIPJPanel1.add(externalAddressJLabel, gridBagConstraints);

                jLabel10.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel10.setText("Configuration Method: ");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                restrictIPJPanel1.add(jLabel10, gridBagConstraints);

                externalMethodJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
                externalMethodJLabel.setText("via XYZ");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
                restrictIPJPanel1.add(externalMethodJLabel, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.ipadx = 25;
                gridBagConstraints.insets = new java.awt.Insets(15, 0, 0, 0);
                internalRemoteJPanel.add(restrictIPJPanel1, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 10);
                add(internalRemoteJPanel, gridBagConstraints);

        }//GEN-END:initComponents


    private void internalSubnetIPaddrJTextFieldCaretUpdate(javax.swing.event.CaretEvent evt) {//GEN-FIRST:event_internalSubnetIPaddrJTextFieldCaretUpdate
	if( !internalSubnetIPaddrJTextField.getText().trim().equals(natInternalSubnetCurrent) && (settingsChangedListener != null) )
	    settingsChangedListener.settingsChanged(this);
    }//GEN-LAST:event_internalSubnetIPaddrJTextFieldCaretUpdate
    
    private void internalAddressIPaddrJTextFieldCaretUpdate(javax.swing.event.CaretEvent evt) {//GEN-FIRST:event_internalAddressIPaddrJTextFieldCaretUpdate
	if( !internalAddressIPaddrJTextField.getText().trim().equals(natInternalAddressCurrent) && (settingsChangedListener != null) )
	    settingsChangedListener.settingsChanged(this);
    }//GEN-LAST:event_internalAddressIPaddrJTextFieldCaretUpdate
    
    private void natDisabledJRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_natDisabledJRadioButtonActionPerformed
        this.setNatEnabledDependency(false);
	if( natEnabledCurrent && (settingsChangedListener != null) )
	    settingsChangedListener.settingsChanged(this);
    }//GEN-LAST:event_natDisabledJRadioButtonActionPerformed

    private void natEnabledJRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_natEnabledJRadioButtonActionPerformed
        this.setNatEnabledDependency(true);
	if( !natEnabledCurrent && (settingsChangedListener != null) )
	    settingsChangedListener.settingsChanged(this);
    }//GEN-LAST:event_natEnabledJRadioButtonActionPerformed
    
    private void setNatEnabledDependency(boolean enabled){
        internalAddressIPaddrJTextField.setEnabled( enabled );
        internalSubnetIPaddrJTextField.setEnabled( enabled );
    }
    
        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.ButtonGroup enabledButtonGroup;
        private javax.swing.JPanel explanationJPanel;
        private javax.swing.JLabel externalAddressJLabel;
        private javax.swing.JLabel externalMethodJLabel;
        private javax.swing.JPanel externalRemoteJPanel;
        public javax.swing.JTextField internalAddressIPaddrJTextField;
        private javax.swing.JLabel internalNetworkJLabel;
        private javax.swing.JPanel internalRemoteJPanel;
        public javax.swing.JTextField internalSubnetIPaddrJTextField;
        private javax.swing.JLabel jLabel1;
        private javax.swing.JLabel jLabel10;
        private javax.swing.JLabel jLabel5;
        private javax.swing.JLabel jLabel6;
        private javax.swing.JLabel jLabel8;
        private javax.swing.JLabel jLabel9;
        private javax.swing.JPanel jPanel1;
        private javax.swing.JTextArea jTextArea1;
        private javax.swing.JTextArea jTextArea2;
        private javax.swing.JTextArea jTextArea3;
        public javax.swing.JRadioButton natDisabledJRadioButton;
        public javax.swing.JRadioButton natEnabledJRadioButton;
        private javax.swing.JPanel restrictIPJPanel;
        private javax.swing.JPanel restrictIPJPanel1;
        // End of variables declaration//GEN-END:variables
    
}

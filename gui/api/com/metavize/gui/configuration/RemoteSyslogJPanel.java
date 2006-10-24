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

package com.metavize.gui.configuration;

import com.metavize.gui.transform.*;
import com.metavize.gui.util.*;
import com.metavize.mvvm.snmp.*;
import com.metavize.mvvm.security.*;
import com.metavize.mvvm.*;
import com.metavize.mvvm.tran.*;
import com.metavize.mvvm.logging.*;

import java.awt.*;
import javax.swing.*;

public class RemoteSyslogJPanel extends javax.swing.JPanel
    implements Savable<RemoteCompoundSettings>, Refreshable<RemoteCompoundSettings> {

    private static final String EXCEPTION_HOST_MISSING = "A \"Host\" must be specified.";

    
    public RemoteSyslogJPanel() {
        initComponents();
	Util.setPortView(portJSpinner, 514);
        for( SyslogFacility syslogFacility : SyslogFacility.values() )
            facilityJComboBox.addItem(syslogFacility.getFacilityName());
        for( SyslogPriority syslogPriority : SyslogPriority.values() )
            thresholdJComboBox.addItem(syslogPriority.getName());
    }

    public void doSave(RemoteCompoundSettings remoteCompoundSettings, boolean validateOnly) throws Exception {

	// SYSLOG ENABLED ////////
	boolean isSyslogEnabled = syslogEnabledRadioButton.isSelected();

        // PORT /////
	((JSpinner.DefaultEditor)portJSpinner.getEditor()).getTextField().setBackground(Color.WHITE);
	int port = 0;
	try{ portJSpinner.commitEdit(); }
	catch(Exception e){ 
	    ((JSpinner.DefaultEditor)portJSpinner.getEditor()).getTextField().setBackground(Util.INVALID_BACKGROUND_COLOR);
	    throw new Exception(Util.EXCEPTION_PORT_RANGE);
	}
        port = (Integer) portJSpinner.getValue();

        // HOST /////
	hostJTextField.setBackground( Color.WHITE );
	String host = hostJTextField.getText();
	hostJTextField.setBackground( Color.WHITE );
	if( isSyslogEnabled && (host.length() == 0) ){
	    hostJTextField.setBackground( Util.INVALID_BACKGROUND_COLOR );
	    throw new Exception(EXCEPTION_HOST_MISSING);
	}
        
        // FACILITY ///////
	String facilityString = (String) facilityJComboBox.getSelectedItem();
        SyslogFacility facility = SyslogFacility.getFacility( facilityString );
        
        // THRESHOLD ///////
	String thresholdString = (String) thresholdJComboBox.getSelectedItem();
        SyslogPriority threshold = SyslogPriority.getPriority( thresholdString );

	// SAVE SETTINGS ////////////
	if( !validateOnly ){
            LoggingSettings loggingSettings = remoteCompoundSettings.getLoggingSettings();
	    
	    loggingSettings.setSyslogEnabled( isSyslogEnabled );
	    if( isSyslogEnabled ){
		loggingSettings.setSyslogHost( host );
                loggingSettings.setSyslogPort( port );
                loggingSettings.setSyslogFacility( facility );
                loggingSettings.setSyslogThreshold( threshold );
	    }
        }

    }

    public void doRefresh(RemoteCompoundSettings remoteCompoundSettings){
	LoggingSettings loggingSettings = remoteCompoundSettings.getLoggingSettings();

	// SYSLOG ENABLED //////
	boolean isSyslogEnabled = loggingSettings.isSyslogEnabled();
	setSyslogEnabledDependency( isSyslogEnabled );
	if( isSyslogEnabled )
            syslogEnabledRadioButton.setSelected(true);
        else
            syslogDisabledRadioButton.setSelected(true);

        // TRAP HOST /////
	String host = loggingSettings.getSyslogHost();
	hostJTextField.setText( host );
	hostJTextField.setBackground( Color.WHITE );
        
        // PORT /////
	int port = loggingSettings.getSyslogPort();
	portJSpinner.setValue( port );
	((JSpinner.DefaultEditor)portJSpinner.getEditor()).getTextField().setText(Integer.toString(port));
	((JSpinner.DefaultEditor)portJSpinner.getEditor()).getTextField().setBackground(Color.WHITE);
        
        // FACILITY /////
        SyslogFacility syslogFacility = loggingSettings.getSyslogFacility();
        facilityJComboBox.setSelectedItem( syslogFacility.getFacilityName() );
        
        // PRIORITY /////
        SyslogPriority syslogPriority = loggingSettings.getSyslogThreshold();
        thresholdJComboBox.setSelectedItem( syslogPriority.getName() );
        	
    }
    
    
        private void initComponents() {//GEN-BEGIN:initComponents
                java.awt.GridBagConstraints gridBagConstraints;

                syslogButtonGroup = new javax.swing.ButtonGroup();
                trapButtonGroup = new javax.swing.ButtonGroup();
                externalRemoteJPanel = new javax.swing.JPanel();
                syslogDisabledRadioButton = new javax.swing.JRadioButton();
                syslogEnabledRadioButton = new javax.swing.JRadioButton();
                enableRemoteJPanel = new javax.swing.JPanel();
                restrictIPJPanel = new javax.swing.JPanel();
                hostJLabel = new javax.swing.JLabel();
                hostJTextField = new javax.swing.JTextField();
                portJLabel = new javax.swing.JLabel();
                portJSpinner = new javax.swing.JSpinner();
                facilityJLabel = new javax.swing.JLabel();
                facilityJComboBox = new javax.swing.JComboBox();
                thresholdJLabel = new javax.swing.JLabel();
                thresholdJComboBox = new javax.swing.JComboBox();

                setLayout(new java.awt.GridBagLayout());

                setMaximumSize(new java.awt.Dimension(550, 200));
                setMinimumSize(new java.awt.Dimension(550, 200));
                setPreferredSize(new java.awt.Dimension(550, 200));
                externalRemoteJPanel.setLayout(new java.awt.GridBagLayout());

                externalRemoteJPanel.setBorder(new javax.swing.border.TitledBorder(null, "Syslog Monitoring", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Dialog", 1, 16)));
                syslogButtonGroup.add(syslogDisabledRadioButton);
                syslogDisabledRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
                syslogDisabledRadioButton.setText("<html><b>Disable</b> Syslog Monitoring. (This is the default setting.)</html>");
                syslogDisabledRadioButton.setFocusPainted(false);
                syslogDisabledRadioButton.setFocusable(false);
                syslogDisabledRadioButton.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                syslogDisabledRadioButtonActionPerformed(evt);
                        }
                });

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                externalRemoteJPanel.add(syslogDisabledRadioButton, gridBagConstraints);

                syslogButtonGroup.add(syslogEnabledRadioButton);
                syslogEnabledRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
                syslogEnabledRadioButton.setText("<html><b>Enable</b> Syslog Monitoring.</html>");
                syslogEnabledRadioButton.setFocusPainted(false);
                syslogEnabledRadioButton.setFocusable(false);
                syslogEnabledRadioButton.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                syslogEnabledRadioButtonActionPerformed(evt);
                        }
                });

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
                externalRemoteJPanel.add(syslogEnabledRadioButton, gridBagConstraints);

                enableRemoteJPanel.setLayout(new java.awt.GridBagLayout());

                restrictIPJPanel.setLayout(new java.awt.GridBagLayout());

                hostJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
                hostJLabel.setText("Host:");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                restrictIPJPanel.add(hostJLabel, gridBagConstraints);

                hostJTextField.setMaximumSize(new java.awt.Dimension(150, 19));
                hostJTextField.setMinimumSize(new java.awt.Dimension(150, 19));
                hostJTextField.setPreferredSize(new java.awt.Dimension(150, 19));
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
                restrictIPJPanel.add(hostJTextField, gridBagConstraints);

                portJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
                portJLabel.setText("Port:");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                restrictIPJPanel.add(portJLabel, gridBagConstraints);

                portJSpinner.setFont(new java.awt.Font("Dialog", 0, 12));
                portJSpinner.setMaximumSize(new java.awt.Dimension(75, 19));
                portJSpinner.setMinimumSize(new java.awt.Dimension(75, 19));
                portJSpinner.setPreferredSize(new java.awt.Dimension(75, 19));
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 0, 2, 0);
                restrictIPJPanel.add(portJSpinner, gridBagConstraints);

                facilityJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
                facilityJLabel.setText("Faclility:");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                restrictIPJPanel.add(facilityJLabel, gridBagConstraints);

                facilityJComboBox.setFont(new java.awt.Font("Dialog", 0, 12));
                facilityJComboBox.setFocusable(false);
                facilityJComboBox.setMaximumSize(new java.awt.Dimension(150, 19));
                facilityJComboBox.setMinimumSize(new java.awt.Dimension(150, 19));
                facilityJComboBox.setPreferredSize(new java.awt.Dimension(150, 19));
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 0, 2, 0);
                restrictIPJPanel.add(facilityJComboBox, gridBagConstraints);

                thresholdJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
                thresholdJLabel.setText("Threshold:");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 3;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                restrictIPJPanel.add(thresholdJLabel, gridBagConstraints);

                thresholdJComboBox.setFont(new java.awt.Font("Dialog", 0, 12));
                thresholdJComboBox.setFocusable(false);
                thresholdJComboBox.setMaximumSize(new java.awt.Dimension(150, 19));
                thresholdJComboBox.setMinimumSize(new java.awt.Dimension(150, 19));
                thresholdJComboBox.setPreferredSize(new java.awt.Dimension(150, 19));
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 3;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 0, 2, 0);
                restrictIPJPanel.add(thresholdJComboBox, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(0, 20, 5, 0);
                enableRemoteJPanel.add(restrictIPJPanel, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
                externalRemoteJPanel.add(enableRemoteJPanel, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
                add(externalRemoteJPanel, gridBagConstraints);

        }//GEN-END:initComponents

    private void syslogDisabledRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_syslogDisabledRadioButtonActionPerformed
        setSyslogEnabledDependency( false );
    }//GEN-LAST:event_syslogDisabledRadioButtonActionPerformed

    private void syslogEnabledRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_syslogEnabledRadioButtonActionPerformed
        setSyslogEnabledDependency( true );
    }//GEN-LAST:event_syslogEnabledRadioButtonActionPerformed
    

    private void setSyslogEnabledDependency(boolean enabled){
	hostJTextField.setEnabled( enabled );
	hostJLabel.setEnabled( enabled );
	portJSpinner.setEnabled( enabled );
	portJLabel.setEnabled( enabled );
	facilityJComboBox.setEnabled( enabled );
	facilityJLabel.setEnabled( enabled );
	thresholdJComboBox.setEnabled( enabled );
	thresholdJLabel.setEnabled( enabled );
    }


    
        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.JPanel enableRemoteJPanel;
        private javax.swing.JPanel externalRemoteJPanel;
        private javax.swing.JComboBox facilityJComboBox;
        private javax.swing.JLabel facilityJLabel;
        private javax.swing.JLabel hostJLabel;
        public javax.swing.JTextField hostJTextField;
        private javax.swing.JLabel portJLabel;
        private javax.swing.JSpinner portJSpinner;
        private javax.swing.JPanel restrictIPJPanel;
        private javax.swing.ButtonGroup syslogButtonGroup;
        public javax.swing.JRadioButton syslogDisabledRadioButton;
        public javax.swing.JRadioButton syslogEnabledRadioButton;
        private javax.swing.JComboBox thresholdJComboBox;
        private javax.swing.JLabel thresholdJLabel;
        private javax.swing.ButtonGroup trapButtonGroup;
        // End of variables declaration//GEN-END:variables
    

}

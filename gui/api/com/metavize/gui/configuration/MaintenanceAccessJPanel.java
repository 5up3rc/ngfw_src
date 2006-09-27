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

package com.metavize.gui.configuration;

import com.metavize.gui.transform.*;
import com.metavize.gui.util.*;

import com.metavize.mvvm.security.*;
import com.metavize.mvvm.*;
import com.metavize.mvvm.networking.RemoteSettings;
import com.metavize.mvvm.tran.*;
import java.awt.*;

public class MaintenanceAccessJPanel extends javax.swing.JPanel
    implements Savable<MaintenanceCompoundSettings>, Refreshable<MaintenanceCompoundSettings> {

    
    public MaintenanceAccessJPanel() {
        initComponents();
    }

    public void doSave(MaintenanceCompoundSettings maintenanceCompoundSettings, boolean validateOnly) throws Exception {

        // SSH ENABLED ////////
	boolean isSshEnabled = sshEnabledRadioButton.isSelected();
        
        // REPORTING ENABLED //////
        boolean isExceptionReportingEnabled = reportJCheckBox.isSelected();
        
	// SAVE SETTINGS ////////////
	if( !validateOnly ){
	    RemoteSettings remoteSettings = maintenanceCompoundSettings.getRemoteSettings();
            remoteSettings.isSshEnabled( isSshEnabled );
            remoteSettings.isExceptionReportingEnabled( isExceptionReportingEnabled );
        }
    }

    public void doRefresh(MaintenanceCompoundSettings maintenanceCompoundSettings){
        RemoteSettings remoteSettings = maintenanceCompoundSettings.getRemoteSettings();
        
        // SSH ENABLED ///////
	boolean isSshEnabled = remoteSettings.isSshEnabled();
	if( isSshEnabled )
            sshEnabledRadioButton.setSelected(true);
        else
            sshDisabledRadioButton.setSelected(true);
        
        // REPORTING ENABLED ////
        boolean isExceptionReportingEnabled = remoteSettings.isExceptionReportingEnabled();
        reportJCheckBox.setSelected( isExceptionReportingEnabled );
    }
    
    
        private void initComponents() {//GEN-BEGIN:initComponents
                java.awt.GridBagConstraints gridBagConstraints;

                sshButtonGroup = new javax.swing.ButtonGroup();
                maintainRemoteJPanel = new javax.swing.JPanel();
                sshEnabledRadioButton = new javax.swing.JRadioButton();
                sshDisabledRadioButton = new javax.swing.JRadioButton();
                jSeparator2 = new javax.swing.JSeparator();
                reportJCheckBox = new javax.swing.JCheckBox();

                setLayout(new java.awt.GridBagLayout());

                setMaximumSize(new java.awt.Dimension(563, 180));
                setMinimumSize(new java.awt.Dimension(563, 180));
                setPreferredSize(new java.awt.Dimension(563, 180));
                maintainRemoteJPanel.setLayout(new java.awt.GridBagLayout());

                maintainRemoteJPanel.setBorder(new javax.swing.border.TitledBorder(null, "Support", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Dialog", 1, 16)));
                sshButtonGroup.add(sshEnabledRadioButton);
                sshEnabledRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
                sshEnabledRadioButton.setText("<html><b>Allow</b> secure remote support of EdgeGuard, by Untangle Networks, for troubleshooting and assistance purposes.</html>");
                sshEnabledRadioButton.setFocusPainted(false);
                sshEnabledRadioButton.setFocusable(false);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                maintainRemoteJPanel.add(sshEnabledRadioButton, gridBagConstraints);

                sshButtonGroup.add(sshDisabledRadioButton);
                sshDisabledRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
                sshDisabledRadioButton.setText("<html><b>Disallow</b> secure remote support.  (This is the default setting.)</html>");
                sshDisabledRadioButton.setFocusPainted(false);
                sshDisabledRadioButton.setFocusable(false);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
                maintainRemoteJPanel.add(sshDisabledRadioButton, gridBagConstraints);

                jSeparator2.setForeground(new java.awt.Color(200, 200, 200));
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                maintainRemoteJPanel.add(jSeparator2, gridBagConstraints);

                reportJCheckBox.setFont(new java.awt.Font("Dialog", 0, 12));
                reportJCheckBox.setText("<html><b>Report</b> any unexpected problems to Untangle Networks.  An email will be sent to Untangle Networks if an unexpected condition occurs.  No information about your network traffic will be transmitted.  (This is disabled by default)</html>");
                reportJCheckBox.setFocusPainted(false);
                reportJCheckBox.setFocusable(false);
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
                maintainRemoteJPanel.add(reportJCheckBox, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
                add(maintainRemoteJPanel, gridBagConstraints);

        }//GEN-END:initComponents
    

    
        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.JSeparator jSeparator2;
        private javax.swing.JPanel maintainRemoteJPanel;
        private javax.swing.JCheckBox reportJCheckBox;
        private javax.swing.ButtonGroup sshButtonGroup;
        public javax.swing.JRadioButton sshDisabledRadioButton;
        public javax.swing.JRadioButton sshEnabledRadioButton;
        // End of variables declaration//GEN-END:variables
    

}

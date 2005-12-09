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

package com.metavize.tran.openvpn.gui;

import java.awt.Window;

import com.metavize.tran.openvpn.*;

public class WizardJPanel extends javax.swing.JPanel {
    
    private VpnTransform vpnTransform;
    private MTransformControlsJPanel mTransformControlsJPanel;

    public WizardJPanel(VpnTransform vpnTransform, MTransformControlsJPanel mTransformControlsJPanel) {
        this.vpnTransform = vpnTransform;
	this.mTransformControlsJPanel = mTransformControlsJPanel;
        initComponents();

	VpnTransform.ConfigState configState = vpnTransform.getConfigState();
	if( VpnTransform.ConfigState.UNCONFIGURED == configState ){
	    statusJLabel.setText("Unconfigured: Use buttons below.");
	}
	else if( VpnTransform.ConfigState.CLIENT == configState ){
	    statusJLabel.setText("VPN Client: Connected to " + vpnTransform.getVpnServerAddress().toString());
	}
	else if( VpnTransform.ConfigState.SERVER_ROUTE == configState ){
	    statusJLabel.setText("VPN Routing Server");
	}
	else if( VpnTransform.ConfigState.SERVER_BRIDGE == configState ){
	    // we dont support this yet
	}
	else{
	    // bad shite happened
	}

    }
    

        private void initComponents() {//GEN-BEGIN:initComponents
                java.awt.GridBagConstraints gridBagConstraints;

                statusJPanel = new javax.swing.JPanel();
                statusJLabel = new javax.swing.JLabel();
                someJLabel = new javax.swing.JLabel();
                clientJPanel = new javax.swing.JPanel();
                clientJButton = new javax.swing.JButton();
                jLabel1 = new javax.swing.JLabel();
                serverRoutingJPanel = new javax.swing.JPanel();
                serverRoutingJButton = new javax.swing.JButton();
                jLabel2 = new javax.swing.JLabel();

                setLayout(new java.awt.GridBagLayout());

                statusJPanel.setLayout(new java.awt.GridBagLayout());

                statusJPanel.setBorder(new javax.swing.border.EtchedBorder());
                statusJPanel.setMaximumSize(new java.awt.Dimension(1061, 29));
                statusJPanel.setMinimumSize(new java.awt.Dimension(1061, 29));
                statusJPanel.setPreferredSize(new java.awt.Dimension(1061, 29));
                statusJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
                statusJLabel.setText("Unconfigured");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 5);
                statusJPanel.add(statusJLabel, gridBagConstraints);

                someJLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
                someJLabel.setText("<html><b>Current Configuration:</b></html>");
                someJLabel.setMaximumSize(new java.awt.Dimension(175, 15));
                someJLabel.setMinimumSize(new java.awt.Dimension(175, 15));
                someJLabel.setPreferredSize(new java.awt.Dimension(175, 15));
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
                statusJPanel.add(someJLabel, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.ipadx = 175;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(0, 50, 200, 50);
                add(statusJPanel, gridBagConstraints);

                clientJPanel.setLayout(new java.awt.GridBagLayout());

                clientJPanel.setBorder(new javax.swing.border.EtchedBorder());
                clientJPanel.setMaximumSize(new java.awt.Dimension(1061, 64));
                clientJPanel.setMinimumSize(new java.awt.Dimension(1061, 64));
                clientJPanel.setPreferredSize(new java.awt.Dimension(1061, 64));
                clientJButton.setFont(new java.awt.Font("Dialog", 0, 12));
                clientJButton.setText("<html><center>Configure as<br><b>VPN Client</b></center></html>");
                clientJButton.setFocusPainted(false);
                clientJButton.setFocusable(false);
                clientJButton.setMaximumSize(new java.awt.Dimension(175, 50));
                clientJButton.setMinimumSize(new java.awt.Dimension(175, 50));
                clientJButton.setPreferredSize(new java.awt.Dimension(175, 50));
                clientJButton.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                clientJButtonActionPerformed(evt);
                        }
                });

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
                clientJPanel.add(clientJButton, gridBagConstraints);

                jLabel1.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel1.setText("<html>This allows your EdgeGuard to connect to a remote EdgeGuard, so they can share exported hosts or exported networks.</html>");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 5);
                clientJPanel.add(jLabel1, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.ipadx = 218;
                gridBagConstraints.ipady = 16;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(150, 50, 0, 50);
                add(clientJPanel, gridBagConstraints);

                serverRoutingJPanel.setLayout(new java.awt.GridBagLayout());

                serverRoutingJPanel.setBorder(new javax.swing.border.EtchedBorder());
                serverRoutingJPanel.setMaximumSize(new java.awt.Dimension(1061, 64));
                serverRoutingJPanel.setMinimumSize(new java.awt.Dimension(1061, 64));
                serverRoutingJButton.setFont(new java.awt.Font("Dialog", 0, 12));
                serverRoutingJButton.setText("<html><center>Configure as<br><b>VPN Routing Server</b></center></html>");
                serverRoutingJButton.setFocusPainted(false);
                serverRoutingJButton.setFocusable(false);
                serverRoutingJButton.setMaximumSize(new java.awt.Dimension(175, 50));
                serverRoutingJButton.setMinimumSize(new java.awt.Dimension(175, 50));
                serverRoutingJButton.setPreferredSize(new java.awt.Dimension(175, 50));
                serverRoutingJButton.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                serverRoutingJButtonActionPerformed(evt);
                        }
                });

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
                serverRoutingJPanel.add(serverRoutingJButton, gridBagConstraints);

                jLabel2.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel2.setText("<html>This allows your EdgeGuard to allow VPN clients to connect to it.  You can export hosts and networks, that will then be accessible to connected clients.</html>");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 5);
                serverRoutingJPanel.add(jLabel2, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.ipadx = 218;
                gridBagConstraints.ipady = 16;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(0, 50, 20, 50);
                add(serverRoutingJPanel, gridBagConstraints);

        }//GEN-END:initComponents

    private void serverRoutingJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serverRoutingJButtonActionPerformed
        serverRoutingJButton.setEnabled(false);
	ServerRoutingWizard.factory((Window)this.getTopLevelAncestor(),vpnTransform,mTransformControlsJPanel).setVisible(true);
	serverRoutingJButton.setEnabled(true);
    }//GEN-LAST:event_serverRoutingJButtonActionPerformed

    private void clientJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clientJButtonActionPerformed
	clientJButton.setEnabled(false);
	ClientWizard.factory((Window)this.getTopLevelAncestor(),vpnTransform,mTransformControlsJPanel).setVisible(true);
	clientJButton.setEnabled(true);
    }//GEN-LAST:event_clientJButtonActionPerformed
    
    
        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.JButton clientJButton;
        private javax.swing.JPanel clientJPanel;
        private javax.swing.JLabel jLabel1;
        private javax.swing.JLabel jLabel2;
        private javax.swing.JButton serverRoutingJButton;
        private javax.swing.JPanel serverRoutingJPanel;
        private javax.swing.JLabel someJLabel;
        private javax.swing.JLabel statusJLabel;
        private javax.swing.JPanel statusJPanel;
        // End of variables declaration//GEN-END:variables
    
}

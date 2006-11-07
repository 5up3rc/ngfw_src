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

package com.untangle.gui.login;

import com.untangle.gui.widgets.wizard.*;
import com.untangle.gui.util.Util;
import com.untangle.mvvm.client.MvvmRemoteContextFactory;

public class InitialSetupCongratulationsJPanel extends MWizardPageJPanel {

    
    public InitialSetupCongratulationsJPanel() {
        initComponents();
    }

    public void initialFocus(){
	String message;
	if( InitialSetupRoutingJPanel.getNatEnabled() && !InitialSetupRoutingJPanel.getNatChanged() ){
	    String publicAddress = InitialSetupRoutingJPanel.getPublicAddress().toString();
	    String privateAddress = InitialSetupRoutingJPanel.getAddress().toString();
	    message = "<html><font color=\"#FF0000\">Press \"Finish\" to open a Login window to Untangle.<br>Outside Address: "
		+ publicAddress + "<br>" + "Inside Address: " + privateAddress + "</font></html>";
	}
	else{
	    String finalAddress;
	    if( !InitialSetupRoutingJPanel.getNatEnabled() ){
		// use public address
		finalAddress = InitialSetupRoutingJPanel.getPublicAddress().toString();
	    }
	    else{
		// use internal address
		finalAddress = InitialSetupRoutingJPanel.getAddress().toString();
	    }
	    message = "<html><font color=\"#FF0000\">Go to: " + finalAddress
		+ " in your web browser to open a login window to Untangle.</font></html>";
	}
	messageJLabel.setText(message);
    }

        private void initComponents() {//GEN-BEGIN:initComponents
                java.awt.GridBagConstraints gridBagConstraints;

                contentJPanel = new javax.swing.JPanel();
                jLabel1 = new javax.swing.JLabel();
                messageJLabel = new javax.swing.JLabel();
                jLabel2 = new javax.swing.JLabel();
                backgroundJPabel = new javax.swing.JLabel();

                setLayout(new java.awt.GridBagLayout());

                setOpaque(false);
                contentJPanel.setLayout(new java.awt.GridBagLayout());

                contentJPanel.setOpaque(false);
                jLabel1.setFont(new java.awt.Font("Dialog", 1, 18));
                jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
                jLabel1.setText("<html>Congratulations!<br>Untangle is configured.</html>");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(15, 15, 0, 15);
                contentJPanel.add(jLabel1, gridBagConstraints);

                messageJLabel.setFont(new java.awt.Font("Dialog", 1, 18));
                messageJLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
                messageJLabel.setText("<html>please redirect adsf df adsf adsf asdf dsf adsf adsf dsfd sas fadsfasdfadsf</html>");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(15, 15, 0, 15);
                contentJPanel.add(messageJLabel, gridBagConstraints);

                jLabel2.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel2.setText("<html>Use your newly created \"admin\" account with the<br>password you have chosen to login to Untangle.</html>");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 2;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(15, 15, 0, 15);
                contentJPanel.add(jLabel2, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                add(contentJPanel, gridBagConstraints);

                backgroundJPabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/untangle/gui/login/ProductShot.png")));
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTH;
                gridBagConstraints.weightx = 1.0;
                add(backgroundJPabel, gridBagConstraints);

        }//GEN-END:initComponents
    
    
        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.JLabel backgroundJPabel;
        private javax.swing.JPanel contentJPanel;
        private javax.swing.JLabel jLabel1;
        private javax.swing.JLabel jLabel2;
        private javax.swing.JLabel messageJLabel;
        // End of variables declaration//GEN-END:variables
    
}

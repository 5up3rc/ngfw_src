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
import com.metavize.mvvm.snmp.*;
import com.metavize.mvvm.security.*;
import com.metavize.mvvm.*;
import com.metavize.mvvm.tran.*;

import java.awt.*;
import javax.swing.*;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

public class AboutTimezoneJPanel extends javax.swing.JPanel
    implements Savable<AboutCompoundSettings>, Refreshable<AboutCompoundSettings> {

	private final String[] timezones = {"US/Eastern (GMT-5)", "US/Central (GMT-6)", "US/Mountain (GMT-7)", "US/Pacific (GMT-8)", "US/Alaska (GMT-9)", "US/Hawaii (GMT-10)"};
	
    
    public AboutTimezoneJPanel() {
        initComponents();
	for(String timezone : timezones){
	    timezoneJComboBox.addItem(timezone);
	}
    }

    public void doSave(AboutCompoundSettings aboutCompoundSettings, boolean validateOnly) throws Exception {

	// TIMEZONE ///////
	String timezone = (String) timezoneJComboBox.getSelectedItem();
	timezone = timezone.substring(0, timezone.indexOf(' '));

	// SAVE SETTINGS ////////////
	if( !validateOnly ){
	    aboutCompoundSettings.setTimeZone( TimeZone.getTimeZone(timezone) );
        }

    }

    public void doRefresh(AboutCompoundSettings aboutCompoundSettings){
	
	// TIMEZONE ////
	String timezone = aboutCompoundSettings.getTimeZone().getID();
	for( String tz : timezones ){
	    if( tz.startsWith(timezone) ){
		timezone = tz;
		break;
	    }
	}
	timezoneJComboBox.setSelectedItem(timezone);

	// DATE //
	SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, MMM d yyyy   h:mm a");
	simpleDateFormat.setTimeZone(aboutCompoundSettings.getTimeZone());
	String dateString = simpleDateFormat.format(aboutCompoundSettings.getDate());
	dateString += "   " + timezone;
	timeJLabel.setText(dateString);
    }
    
    
        private void initComponents() {//GEN-BEGIN:initComponents
                java.awt.GridBagConstraints gridBagConstraints;

                snmpButtonGroup = new javax.swing.ButtonGroup();
                trapButtonGroup = new javax.swing.ButtonGroup();
                externalRemoteJPanel = new javax.swing.JPanel();
                enableRemoteJPanel = new javax.swing.JPanel();
                restrictIPJPanel = new javax.swing.JPanel();
                jLabel5 = new javax.swing.JLabel();
                timezoneJComboBox = new javax.swing.JComboBox();
                jLabel6 = new javax.swing.JLabel();
                timeJLabel = new javax.swing.JLabel();

                setLayout(new java.awt.GridBagLayout());

                setMaximumSize(new java.awt.Dimension(563, 343));
                setMinimumSize(new java.awt.Dimension(563, 343));
                setPreferredSize(new java.awt.Dimension(563, 343));
                externalRemoteJPanel.setLayout(new java.awt.GridBagLayout());

                externalRemoteJPanel.setBorder(new javax.swing.border.TitledBorder(null, "Time Settings", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Dialog", 1, 16)));
                enableRemoteJPanel.setLayout(new java.awt.GridBagLayout());

                restrictIPJPanel.setLayout(new java.awt.GridBagLayout());

                jLabel5.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel5.setText("Timezone:");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                restrictIPJPanel.add(jLabel5, gridBagConstraints);

                timezoneJComboBox.setFont(new java.awt.Font("Dialog", 0, 12));
                timezoneJComboBox.setFocusable(false);
                timezoneJComboBox.setMaximumSize(new java.awt.Dimension(175, 24));
                timezoneJComboBox.setMinimumSize(new java.awt.Dimension(175, 24));
                timezoneJComboBox.setPreferredSize(new java.awt.Dimension(175, 24));
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 0);
                restrictIPJPanel.add(timezoneJComboBox, gridBagConstraints);

                jLabel6.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel6.setText("Time as of Refresh:");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
                gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
                restrictIPJPanel.add(jLabel6, gridBagConstraints);

                timeJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
                gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
                gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
                restrictIPJPanel.add(timeJLabel, gridBagConstraints);

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
                gridBagConstraints.weightx = 1.0;
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
    

    
        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.JPanel enableRemoteJPanel;
        private javax.swing.JPanel externalRemoteJPanel;
        private javax.swing.JLabel jLabel5;
        private javax.swing.JLabel jLabel6;
        private javax.swing.JPanel restrictIPJPanel;
        private javax.swing.ButtonGroup snmpButtonGroup;
        private javax.swing.JLabel timeJLabel;
        private javax.swing.JComboBox timezoneJComboBox;
        private javax.swing.ButtonGroup trapButtonGroup;
        // End of variables declaration//GEN-END:variables
    

}

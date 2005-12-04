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

package com.metavize.tran.openvpn.gui;

import com.metavize.tran.openvpn.*;

import com.metavize.mvvm.tran.IPaddr;
import com.metavize.gui.widgets.wizard.*;
import com.metavize.gui.widgets.dialogs.*;
import com.metavize.gui.util.Util;
import com.metavize.mvvm.client.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;

public class ClientWizardServerJPanel extends MWizardPageJPanel {
    
    private static final String EXCEPTION_ADDRESS_FORMAT = "The \"Server IP Address\" is not a valid IP address.";
    private static final String EXCEPTION_NO_PASSWORD = "Please supply a password will be used to connect to the server.";
    private static final String EXCEPTION_KEY_UNREAD = "You must click \"Read USB Key\" before proceeding.";
    private static final String EXCEPTION_NO_SELECTION = "You must click \"Read USB Key\", and select a valid configuration " +
	"from the drop-down-list before proceeding.";
    

    private VpnTransform vpnTransform;

    private static final String NO_CONFIGURATIONS = "[No Configurations]";
    
    public ClientWizardServerJPanel(VpnTransform vpnTransform) {
        this.vpnTransform = vpnTransform;
        initComponents();
	setServerSelectedDependency(serverJRadioButton.isSelected());
	keyJComboBox.addItem(NO_CONFIGURATIONS);
	keyJComboBox.setSelectedItem(NO_CONFIGURATIONS);
    }

    boolean useServer;
    String address;
    IPaddr addressIPaddr;
    int serverPort;
    String password;
    boolean keyRead = false;
    String selection;
    Exception exception;
    MProgressJDialog mProgressJDialog;
    JProgressBar jProgressBar;
    
    public void doSave(Object settings, boolean validateOnly) throws Exception {
        
	SwingUtilities.invokeAndWait( new Runnable(){ public void run(){
	    serverJTextField.setBackground( Color.WHITE );
            passwordJTextField.setBackground( Color.WHITE );

	    useServer = serverJRadioButton.isSelected();
	    address = serverJTextField.getText().trim();
            password = passwordJTextField.getText().trim();
            selection = (String) keyJComboBox.getSelectedItem();

	    exception = null;

	    // SERVER SELECTED
	    if( useServer ){
		if( address.length() <= 0 ){
		    serverJTextField.setBackground( Util.INVALID_BACKGROUND_COLOR );
		    exception = new Exception(EXCEPTION_ADDRESS_FORMAT);
		    return;
		}
		
		try{
                    /* RBS This code is here to allow for <ip address>:<port>
                     * it should be in a richer object, like a IPAddressAndPort */
                    String[] values = address.split( ":" );
                    if ( values.length == 1 ) {
                        /* XXX Magic number */
                        serverPort = 443;
                    } else if ( values.length == 2 ) {
                        serverPort = Integer.parseInt( values[1] );
                    } else {
                        exception = new Exception(EXCEPTION_ADDRESS_FORMAT);
                        return;
                    }
                    addressIPaddr = IPaddr.parse( values[0] );
		}
		catch(Exception e){
		    exception = new Exception(EXCEPTION_ADDRESS_FORMAT);
		    return;
		}
		
		if( password.length() <= 0 ){
		    passwordJTextField.setBackground( Util.INVALID_BACKGROUND_COLOR );
		    exception = new Exception(EXCEPTION_NO_PASSWORD);
		    return;
		}
	    }
	    // USB KEY SELECTED
	    else{
		if( !keyRead ){
		    exception = new Exception(EXCEPTION_KEY_UNREAD);
		    return;
		}

		if( NO_CONFIGURATIONS.equals(selection) || (selection == null) ) {
		    exception = new Exception(EXCEPTION_NO_SELECTION);
		    return;
		}
	    }

	}});

	if( exception != null )
	    throw exception;
        
        
        if( !validateOnly ){

	    // BRING UP DOWNLOADING DIALOG
	    SwingUtilities.invokeLater( new Runnable(){ public void run(){
		mProgressJDialog = new MProgressJDialog("Downloading Configuration",
							"<html><center>Please wait a moment while your configuration is downloaded." + 
							"<br>This may take up to one minute.</center></html>",
							(Dialog)ClientWizardServerJPanel.this.getTopLevelAncestor(), false);
		jProgressBar = mProgressJDialog.getJProgressBar();
		jProgressBar.setValue(0);
		jProgressBar.setString("Downloading...");
		jProgressBar.setIndeterminate(true);
		mProgressJDialog.setVisible(true);
	    }});
	    try{
		// DOWNLOAD THE STUFFS
		if( useServer )
		    vpnTransform.downloadConfig( addressIPaddr, serverPort, password );
		else
		    vpnTransform.downloadConfigUsb( selection );
		
		// SHOW RESULTS AND REMOVE DOWNLOADING DIALOG
		SwingUtilities.invokeAndWait( new Runnable(){ public void run(){
		    jProgressBar.setValue(100);
		    jProgressBar.setString("Finished Downloading");
		    jProgressBar.setIndeterminate(false);
		}});
		try{Thread.currentThread().sleep(2000);} catch(Exception e){e.printStackTrace();}
		SwingUtilities.invokeLater( new Runnable(){ public void run(){
		    mProgressJDialog.setVisible(false);
		}});
		
	    }
	    catch(Exception e){
		SwingUtilities.invokeLater( new Runnable(){ public void run(){
		    mProgressJDialog.setVisible(false);
		}});
		if( useServer ){
		    Util.handleExceptionNoRestart("Error downloading config from server:", e);
		    throw new Exception("Your VPN Client configuration could not be downloaded from the server.  Please try again.");
		}
		else{
		    Util.handleExceptionNoRestart("Error downloading config from USB key:", e);
		    throw new Exception("Your VPN Client configuration could not be downloaded from the USB key.  Please try again.");
		}
	    }
	    	    
	    vpnTransform.completeConfig();
	}
    }
    

    private void initComponents() {//GEN-BEGIN:initComponents
                methodButtonGroup = new javax.swing.ButtonGroup();
                jLabel2 = new javax.swing.JLabel();
                serverJRadioButton = new javax.swing.JRadioButton();
                serverJTextField = new javax.swing.JTextField();
                serverJLabel1 = new javax.swing.JLabel();
                passwordJTextField = new javax.swing.JTextField();
                serverJLabel2 = new javax.swing.JLabel();
                jLabel3 = new javax.swing.JLabel();
                keyJRadioButton = new javax.swing.JRadioButton();
                refreshKeyJButton = new javax.swing.JButton();
                keyJComboBox = new javax.swing.JComboBox();

                setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

                setOpaque(false);
                jLabel2.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel2.setText("<html>\nPlease specify where your VPN Client configuration should come<br>\nfrom.  You may specify a Server or USB Key.  If you choose USB Key,<br>\nyou must press \"Read USB Key\" to load configurations from the key,<br>\nand then choose a configuration from the drop-down-list.</html>");
                add(jLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(50, 30, 400, -1));

                methodButtonGroup.add(serverJRadioButton);
                serverJRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
                serverJRadioButton.setSelected(true);
                serverJRadioButton.setText("<html><b>Download from Server</b></html>");
                serverJRadioButton.setOpaque(false);
                serverJRadioButton.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                serverJRadioButtonActionPerformed(evt);
                        }
                });

                add(serverJRadioButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(70, 100, -1, -1));

                serverJTextField.setColumns(19);
                add(serverJTextField, new org.netbeans.lib.awtextra.AbsoluteConstraints(220, 120, -1, -1));

                serverJLabel1.setFont(new java.awt.Font("Dialog", 0, 12));
                serverJLabel1.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
                serverJLabel1.setText("Server IP Address:");
                add(serverJLabel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(110, 120, -1, -1));

                passwordJTextField.setColumns(19);
                add(passwordJTextField, new org.netbeans.lib.awtextra.AbsoluteConstraints(220, 140, -1, -1));

                serverJLabel2.setFont(new java.awt.Font("Dialog", 0, 12));
                serverJLabel2.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
                serverJLabel2.setText("Password:");
                add(serverJLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(160, 140, -1, -1));

                jLabel3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/metavize/tran/openvpn/gui/ProductShot.png")));
                add(jLabel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(-130, 230, -1, -1));

                methodButtonGroup.add(keyJRadioButton);
                keyJRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
                keyJRadioButton.setText("<html><b>Download from USB Key</b></html>");
                keyJRadioButton.setOpaque(false);
                keyJRadioButton.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                keyJRadioButtonActionPerformed(evt);
                        }
                });

                add(keyJRadioButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(70, 170, -1, -1));

                refreshKeyJButton.setFont(new java.awt.Font("Dialog", 0, 12));
                refreshKeyJButton.setText("Read USB Key");
                refreshKeyJButton.addActionListener(new java.awt.event.ActionListener() {
                        public void actionPerformed(java.awt.event.ActionEvent evt) {
                                refreshKeyJButtonActionPerformed(evt);
                        }
                });

                add(refreshKeyJButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(120, 190, -1, -1));

                keyJComboBox.setFont(new java.awt.Font("Dialog", 0, 12));
                add(keyJComboBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(250, 190, 180, -1));

        }//GEN-END:initComponents
    
    private void refreshKeyJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshKeyJButtonActionPerformed
	try{
	    keyJComboBox.removeAllItems();
	    java.util.List<String> usbList = vpnTransform.getAvailableUsbList();
	    if( usbList.size() == 0 ){
		keyJComboBox.addItem(NO_CONFIGURATIONS);
		keyJComboBox.setSelectedItem(NO_CONFIGURATIONS);
		MOneButtonJDialog.factory((Window)this.getTopLevelAncestor(), "OpenVPN Setup Wizard Warning",
					  "The USB Key was read, but no configurations were found.  " +
					  "Please make sure there are valid configurations on your USB Key and then try again.",
					  "OpenVPN Setup Wizard Warning", "Warning");		
	    }
	    else{
		for( String entry : usbList )
		    keyJComboBox.addItem(entry);
		keyRead = true;
	    }
	}
	catch(Exception e){
	    MOneButtonJDialog.factory((Window)this.getTopLevelAncestor(), "OpenVPN Setup Wizard Warning",
				      "The USB Key could not be read.  Please make sure the key is properly inserted, and try again.",
				      "OpenVPN Setup Wizard Warning", "Warning");
	}
    }//GEN-LAST:event_refreshKeyJButtonActionPerformed
    
    private void keyJRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keyJRadioButtonActionPerformed
	setServerSelectedDependency(false);
    }//GEN-LAST:event_keyJRadioButtonActionPerformed
    
    private void serverJRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serverJRadioButtonActionPerformed
	setServerSelectedDependency(true);
    }//GEN-LAST:event_serverJRadioButtonActionPerformed

    private void setServerSelectedDependency(boolean enabled){
	serverJLabel1.setEnabled(enabled);
	serverJLabel2.setEnabled(enabled);
	serverJTextField.setEnabled(enabled);
	passwordJTextField.setEnabled(enabled);
	refreshKeyJButton.setEnabled(!enabled);
	keyJComboBox.setEnabled(!enabled);
    }
    
        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.JLabel jLabel2;
        private javax.swing.JLabel jLabel3;
        private javax.swing.JComboBox keyJComboBox;
        private javax.swing.JRadioButton keyJRadioButton;
        private javax.swing.ButtonGroup methodButtonGroup;
        private javax.swing.JTextField passwordJTextField;
        private javax.swing.JButton refreshKeyJButton;
        private javax.swing.JLabel serverJLabel1;
        private javax.swing.JLabel serverJLabel2;
        private javax.swing.JRadioButton serverJRadioButton;
        private javax.swing.JTextField serverJTextField;
        // End of variables declaration//GEN-END:variables
    
}

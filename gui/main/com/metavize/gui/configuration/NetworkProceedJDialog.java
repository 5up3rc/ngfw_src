/*
 * ProceedJDialog.java
 *
 * Created on July 28, 2004, 7:48 PM
 */

package com.metavize.gui.configuration;
import com.metavize.gui.util.Util;
import com.metavize.mvvm.ToolboxManager;
/**
 *
 * @author  inieves
 */
public class NetworkProceedJDialog extends javax.swing.JDialog implements java.awt.event.WindowListener {
    
    private boolean isProceeding = false;
    
    /** Creates new form ProceedJDialog */
    public NetworkProceedJDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        this.addWindowListener(this);
    }
    
    private void initComponents() {//GEN-BEGIN:initComponents
        java.awt.GridBagConstraints gridBagConstraints;

        cancelJButton = new javax.swing.JButton();
        proceedJButton = new javax.swing.JButton();
        messageJLabel = new javax.swing.JLabel();
        labelJLabel = new javax.swing.JLabel();
        backgroundJLabel = new javax.swing.JLabel();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Confirm Upgrade...");
        setModal(true);
        setResizable(false);
        cancelJButton.setFont(new java.awt.Font("Default", 0, 12));
        cancelJButton.setDoubleBuffered(true);
        cancelJButton.setFocusable(false);
        cancelJButton.setText("<html><b>Cancel</b> Settings</html>");
        cancelJButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        cancelJButton.setMinimumSize(new java.awt.Dimension(130, 25));
        cancelJButton.setPreferredSize(new java.awt.Dimension(130, 25));
        cancelJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelJButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 13, 240);
        getContentPane().add(cancelJButton, gridBagConstraints);

        proceedJButton.setFont(new java.awt.Font("Default", 0, 12));
        proceedJButton.setText("<html><b>Save</b> Settings and <b>Restart</b> EdgeGuard</html>");
        proceedJButton.setDoubleBuffered(true);
        proceedJButton.setFocusPainted(false);
        proceedJButton.setFocusable(false);
        proceedJButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
        proceedJButton.setMinimumSize(new java.awt.Dimension(240, 25));
        proceedJButton.setPreferredSize(new java.awt.Dimension(150, 25));
        proceedJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                proceedJButtonActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 150, 13, 0);
        getContentPane().add(proceedJButton, gridBagConstraints);

        messageJLabel.setFont(new java.awt.Font("Dialog", 0, 12));
        messageJLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        messageJLabel.setText("<html><center>\nSaving these settings requires that the Metavize EdgeGuard be<br>\nautomatically restarted.  You may log in again after a restart.<br>\n<br>\nWould you like to save your settings?\n</center></html>");
        messageJLabel.setDoubleBuffered(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(40, 30, 0, 30);
        getContentPane().add(messageJLabel, gridBagConstraints);

        labelJLabel.setFont(new java.awt.Font("Dialog", 1, 24));
        labelJLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        labelJLabel.setText("Warning:");
        labelJLabel.setDoubleBuffered(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        getContentPane().add(labelJLabel, gridBagConstraints);

        backgroundJLabel.setFont(new java.awt.Font("Default", 0, 12));
        backgroundJLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        backgroundJLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/metavize/gui/images/LightGreyBackground1600x1200.png")));
        backgroundJLabel.setDoubleBuffered(true);
        backgroundJLabel.setFocusable(false);
        backgroundJLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        backgroundJLabel.setOpaque(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(backgroundJLabel, gridBagConstraints);

        java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setBounds((screenSize.width-466)/2, (screenSize.height-200)/2, 466, 200);
    }//GEN-END:initComponents

    private void proceedJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_proceedJButtonActionPerformed
        isProceeding = true;
        setVisible(false);
    }//GEN-LAST:event_proceedJButtonActionPerformed

    private void cancelJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelJButtonActionPerformed
        windowClosing(null);
    }//GEN-LAST:event_cancelJButtonActionPerformed
    
    
    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
        isProceeding = false;
        this.setVisible(false);
    }    
    
    
    public void windowActivated(java.awt.event.WindowEvent windowEvent) {}    
    public void windowClosed(java.awt.event.WindowEvent windowEvent) {}    
    public void windowDeactivated(java.awt.event.WindowEvent windowEvent) {}
    public void windowDeiconified(java.awt.event.WindowEvent windowEvent) {}
    public void windowIconified(java.awt.event.WindowEvent windowEvent) {}
    public void windowOpened(java.awt.event.WindowEvent windowEvent) {}
    
    public boolean isProceeding(){
        return isProceeding;
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel backgroundJLabel;
    private javax.swing.JButton cancelJButton;
    private javax.swing.JLabel labelJLabel;
    private javax.swing.JLabel messageJLabel;
    private javax.swing.JButton proceedJButton;
    // End of variables declaration//GEN-END:variables
    
}

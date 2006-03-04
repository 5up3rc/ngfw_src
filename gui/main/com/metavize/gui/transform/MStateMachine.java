/*
 * Copyright (c) 2004, 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.gui.transform;

import java.awt.Window;
import javax.swing.*;

import com.metavize.gui.util.*;
import com.metavize.gui.widgets.dialogs.*;
import com.metavize.mvvm.*;
import com.metavize.mvvm.tran.*;

public class MStateMachine implements java.awt.event.ActionListener {

    // references to components that can generate actions
    private JButton saveJButton;
    private JButton reloadJButton;
    private JButton removeJButton;
    private JToggleButton powerJToggleButton;
    private BlinkJLabel stateJLabel;
    private MTransformJPanel mTransformJPanel;
    private MTransformControlsJPanel mTransformControlsJPanel;
    private MTransformDisplayJPanel mTransformDisplayJPanel;
    private Transform transform;

    // helpers
    String transformName;
    String displayName;

    public MStateMachine( MTransformJPanel mTransformJPanel ) {
        this.mTransformJPanel = mTransformJPanel;
        this.mTransformControlsJPanel = mTransformJPanel.mTransformControlsJPanel();
        this.mTransformDisplayJPanel = mTransformJPanel.mTransformDisplayJPanel();
        this.powerJToggleButton = mTransformJPanel.powerJToggleButton();
        this.transform = mTransformJPanel.getTransform();
        this.stateJLabel = mTransformJPanel.stateJLabel();
        this.saveJButton = mTransformControlsJPanel.saveJButton();
        this.reloadJButton = mTransformControlsJPanel.reloadJButton();
        this.removeJButton = mTransformControlsJPanel.removeJButton();

        transformName = mTransformJPanel.getTransformDesc().getName();
        displayName = mTransformJPanel.getTransformDesc().getDisplayName();

        new RefreshStateThread();
    }


    Boolean readOnlyPowerState = null;
    // ACTION MULTIPLEXER ///////////////////////////////
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        Object source = evt.getSource();

        if( Util.getIsDemo() && !source.equals(reloadJButton) ){
	    if( source.equals(powerJToggleButton) ){
		if( readOnlyPowerState == null ){
		    readOnlyPowerState = new Boolean(!powerJToggleButton.isSelected());
		}
		if( powerJToggleButton.isSelected() != readOnlyPowerState )
		    powerJToggleButton.setSelected(readOnlyPowerState);
	    }
            return;
	}
        try{
            if( source.equals(saveJButton) ){
                if( transformName.equals("nat-transform") ){
                    /* saveJButton.setEnabled(false); */
                    if( (new SaveProceedDialog( displayName )).isProceeding() ){
                        new SaveThread();
                    }
                    else{
                        /* saveJButton.setEnabled(true); */
                    }
                }
                else{
                    new SaveThread();
                }
            }
            else if( source.equals(reloadJButton) ){
                new RefreshThread();
            }
            else if( source.equals(removeJButton) ){
                /* removeJButton.setEnabled(false); */
		RemoveProceedDialog dialog = RemoveProceedDialog.factory((Window)mTransformControlsJPanel.getContentJPanel().getTopLevelAncestor(),
									 displayName);
                if( dialog.isProceeding() ){
                    Util.getPolicyStateMachine().moveFromRackToToolbox(mTransformJPanel.getPolicy(),mTransformJPanel);
                }
                /* removeJButton.setEnabled(true); */
            }
            else if( source.equals(powerJToggleButton) ){
                int modifiers = evt.getModifiers();
                powerJToggleButton.setEnabled(false);
                // REMOVE
                if( (modifiers & java.awt.event.ActionEvent.SHIFT_MASK) > 0 ){
                    if( (modifiers & java.awt.event.ActionEvent.CTRL_MASK) == 0 ){
			RemoveProceedDialog dialog = RemoveProceedDialog.factory((Window)mTransformControlsJPanel.getContentJPanel().getTopLevelAncestor(),
										 displayName);
                        if( dialog.isProceeding() ){
                            Util.getPolicyStateMachine().moveFromRackToToolbox(mTransformJPanel.getPolicy(),mTransformJPanel);
                        }
                        else{
                            powerJToggleButton.setSelected( !powerJToggleButton.isSelected() );
                            powerJToggleButton.setEnabled(true);
                        }
                    }
                    else{
                        powerJToggleButton.setEnabled(true);
                    }
                }
                else{
                    if( transformName.equals("nat-transform") ){
                        if( (new PowerProceedDialog(displayName, powerJToggleButton.isSelected())).isProceeding() ){
                            new PowerThread();
                        }
                        else{
                            powerJToggleButton.setSelected( !powerJToggleButton.isSelected() );
                            powerJToggleButton.setEnabled(true);
                        }
                    }
                    else{
                        new PowerThread();
                    }
                }
            }
            else{ Util.printMessage("error: unknown action source: " + source); }
        }
        catch(Exception e){
            try{
                Util.handleExceptionWithRestart("Error handling action", e);
            }
            catch(Exception f){
                Util.handleExceptionNoRestart("Error getting last state", f);
                setProblemView(true);
            }
        }
    }
    ////////////////////////////////////////////


    // ACTION THREADS //////////////////////////
    class SaveThread extends Thread{
        public SaveThread(){
            super("MVCLIENT-StateMachineSaveThread: " + displayName);
	    setDaemon(true);
            setProcessingView(false);
	    mTransformControlsJPanel.getInfiniteProgressJComponent().start("Saving...");
            start();
        }
        public void run(){
            try{
                mTransformControlsJPanel.saveAll();
                mTransformControlsJPanel.refreshAll();
                mTransformControlsJPanel.populateAll();
            }
            catch(Exception e){
                try{ Util.handleExceptionWithRestart("Error doing save", e); }
		catch(ValidationException f){ refreshState(true); }
                catch(Exception g){
                    Util.handleExceptionNoRestart("Error doing save", g);
                    setProblemView(true);
		    SaveFailureDialog.factory( (Window) mTransformControlsJPanel.getContentJPanel().getTopLevelAncestor(), displayName );
                }
            }
	    /*
	    SwingUtilities.invokeLater( new Runnable(){ public void run(){
		saveJButton.setIcon(Util.getButtonSaveSettings());
	    }});
	    */
	    mTransformControlsJPanel.getInfiniteProgressJComponent().stopLater(MTransformControlsJPanel.MIN_PROGRESS_MILLIS);
            try{
                refreshState(true);
            }
            catch(Exception e){
                try{ Util.handleExceptionWithRestart("Error doing save", e); }
		catch(ValidationException f){ refreshState(true); }
                catch(Exception g){
                    Util.handleExceptionNoRestart("Error doing save", g);
                    setProblemView(true);
		    SaveFailureDialog.factory( (Window) mTransformControlsJPanel.getContentJPanel().getTopLevelAncestor(), displayName );
                }
            }
        }
    }

    

    class RefreshThread extends Thread{
        public RefreshThread(){
            super("MVCLIENT-StateMachineRefreshThread: " + displayName );
	    setDaemon(true);
            /* reloadJButton.setIcon(Util.getButtonReloading()); */
            setProcessingView(false);
	    mTransformControlsJPanel.getInfiniteProgressJComponent().start("Reloading...");
            start();
        }
        public void run(){
            try{
                mTransformControlsJPanel.refreshAll();
                mTransformControlsJPanel.populateAll();
            }
            catch(Exception e){
                try{
                    Util.handleExceptionWithRestart("Error doing refresh", e);
                }
                catch(Exception f){
                    Util.handleExceptionNoRestart("Error doing refresh", f);
                    setProblemView(true);		
		    RefreshFailureDialog.factory( (Window) mTransformControlsJPanel.getContentJPanel().getTopLevelAncestor(), displayName );
                }
            }
	    /*
	    SwingUtilities.invokeLater( new Runnable(){ public void run(){
		reloadJButton.setIcon(Util.getButtonReloadSettings());
	    }});
	    */
	    mTransformControlsJPanel.getInfiniteProgressJComponent().stopLater(MTransformControlsJPanel.MIN_PROGRESS_MILLIS);
	    try{
		refreshState(true);
	    }
	    catch(Exception e){
		try{
                    Util.handleExceptionWithRestart("Error doing refresh", e);
                }
                catch(Exception f){
                    Util.handleExceptionNoRestart("Error doing refresh", f);
                    setProblemView(true);		
		    RefreshFailureDialog.factory( (Window) mTransformControlsJPanel.getContentJPanel().getTopLevelAncestor(), displayName );
                }
	    }
		
        }
    }

    class PowerThread extends Thread{
        private final boolean powerOn;
        public PowerThread(){
            super("MVCLIENT-StateMachinePowerThread: " + displayName );
	    setDaemon(true);
            powerOn = powerJToggleButton.isSelected();
            if( powerOn )
                setStartingView(false);
            else
                setStoppingView(false);
            this.start();
        }

        public void run(){
            try{
                if(powerOn){
                    transform.start();
                }
                else
                    transform.stop();

                if( powerOn )
                    setOnView(true);
                else
                    setOffView(true);
            }
            catch(UnconfiguredException e){
                if( transformName.equals("openvpn-transform") ){
                    MOneButtonJDialog.factory((Window)mTransformJPanel.getTopLevelAncestor(), displayName,
                                              "You must configure OpenVPN as either a VPN Routing Server" +
                                              " or a VPN Client before you can turn it on.  You may do this" +
                                              " through its Setup Wizard (in its settings).", displayName + " Confirmation", "Confirmation");
                }
                setOffView(true);
            }
            catch(Exception e){
                try{
                    Util.handleExceptionWithRestart("Error doing power", e);
                }
                catch(Exception f){
                    Util.handleExceptionNoRestart("Error doing power", f);
                    setProblemView(true);
                    String action;
                    if( powerOn )
                        action = "turned on";
                    else
                        action = "turned off";
                    MOneButtonJDialog.factory((Window)mTransformJPanel.getTopLevelAncestor(), displayName,
                                              displayName + " could not be " + action + "." +
                                              "  Please contact Metavize for further support.", displayName + " Warning", "Warning");
                }
            }
        }
    }
    ///////////////////////////////////////////////


    // VIEW SETTING ////////////////////////////////
    private void setProcessingView(boolean doLater){ setView( doLater, false, false, false, false, false, null, true, BlinkJLabel.PROCESSING_STATE ); }
    void setProblemView(boolean doLater){    setView( doLater, false, false, false, true,  false, null, true, BlinkJLabel.PROBLEM_STATE ); }
    private void setStartingView(boolean doLater){ setView( doLater, false, false, false, false, false, null, false, BlinkJLabel.STARTING_STATE ); }
    private void setStoppingView(boolean doLater){ setView( doLater, false, false, false, false, false, null, false, BlinkJLabel.STOPPING_STATE ); }
    void setRemovingView(boolean doLater){ setStoppingView(doLater); }
    private void setOnView(boolean doLater){        setView( doLater, true, true, true, true, true, true, true,  BlinkJLabel.ON_STATE );
    mTransformJPanel.setPowerOnHintVisible(false); }
    private void setOffView(boolean doLater){       setView( doLater, true, true, true, true, true, false, false, BlinkJLabel.OFF_STATE ); }

    private void setView(final boolean doLater, final boolean allControlsEnabled, final boolean saveEnabled,
                         final boolean refreshEnabled, final boolean removeEnabled, final boolean powerEnabled,
                         final Boolean powerOn, final boolean updateGraph, final int ledState){

        Runnable runnable = new Runnable(){
                public void run(){
                    if( powerOn != null){
                        boolean wasEnabled = powerJToggleButton.isEnabled();
                        powerJToggleButton.setEnabled(false);
                        powerJToggleButton.setSelected( powerOn );
                        powerJToggleButton.setEnabled(wasEnabled);
                    }
                    mTransformDisplayJPanel.setUpdateGraph( updateGraph );
                    stateJLabel.setViewState( ledState );
                }
            };
        if( doLater )
            SwingUtilities.invokeLater( runnable );
        else
            runnable.run();
    }
    ///////////////////////////////////////////////


    // STATE REFRESHING //////////////////////////
    private void refreshState(boolean doLater){
        TransformState transformState = transform.getRunState();
        if( TransformState.RUNNING.equals( transformState ) )
            setOnView(doLater);
        else if( TransformState.INITIALIZED.equals( transformState ) )
            setOffView(doLater);
        else
            setProblemView(doLater);
    }

    class RefreshStateThread extends Thread{
        public RefreshStateThread(){
            super("MVCLIENT-StateMachineRefreshStateThread: " + displayName );
	    setDaemon(true);
            this.start();
        }
        public void run(){
            try{
                refreshState(true);
            }
            catch(Exception e){
                try{
                    Util.handleExceptionWithRestart("Error refreshing state", e);
                }
                catch(Exception f){
                    Util.handleExceptionNoRestart("Error refreshing state: ", f);
                    setProblemView(true);
                }
            }
        }
    }
    ///////////////////////////////////

}

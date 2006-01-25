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

package com.metavize.gui.util;

import com.metavize.gui.transform.MTransformJButton;
import javax.swing.*;

import com.metavize.mvvm.*;

public class Visitor implements ProgressVisitor{
    private boolean isDone = false;
    private boolean isSuccessful = false;
    private int fileCountTotal = 0;
    private int byteCountTotal = 0;
    private int currentFileIndex = 0;
    private int currentByteIndex = 0;
    private int currentByteIncrement = 0;
    private Object visualizer;
    private JProgressBar progressBar;
    private MTransformJButton mTransformJButton;
    private boolean isProgressBar;
    // public methods ----------------------------------------------------

    public Visitor(JProgressBar progressBar){
	visualizer = progressBar;
	isProgressBar = true;
    }

    public Visitor(MTransformJButton mTransformJButton){
	visualizer = mTransformJButton;
	isProgressBar = false;
    }
    
    public boolean isDone(){
	return isDone;
    }
    
    public boolean isSuccessful(){
	return isSuccessful;
    }

    // ProgressVisitor methods -------------------------------------------

    public void visitDownloadSummary(final DownloadSummary ds){
        fileCountTotal = ds.getCount();
	byteCountTotal = ds.getSize();
    }

    public void visitDownloadProgress(final DownloadProgress dp){
	SwingUtilities.invokeLater( new Runnable(){ public void run(){
	    currentByteIncrement = dp.getSize();
	    String progressString;
	    float currentPercentComplete = ((float)(currentByteIndex + dp.getBytesDownloaded())) / ((float)byteCountTotal);
	    int progressIndex = (int) (90f*currentPercentComplete);
	    if(isProgressBar){
		progressString = "Downloading file " + (currentFileIndex+1) + " of " + fileCountTotal
		+ " (" + byteCountTotal/1000 + "KBytes " + "@ "  + dp.getSpeed() + ")";
		((JProgressBar)visualizer).setString( progressString );
		((JProgressBar)visualizer).setValue( progressIndex );
	    }
	    else{
		progressString = "Dload "  + dp.getSpeed();
		((MTransformJButton)visualizer).setProgress(progressString, progressIndex);
	    }
	}});
    }    
    
    public void visitDownloadComplete(final DownloadComplete dc){
	isSuccessful = dc.getSuccess();
	isDone = !isSuccessful;
	SwingUtilities.invokeLater( new Runnable(){ public void run(){
	    currentByteIndex += currentByteIncrement;
	    currentFileIndex++;
	    if(!dc.getSuccess()){
		if(isProgressBar){
		    ((JProgressBar)visualizer).setString( "Download failed.  Please try again." );
		    ((JProgressBar)visualizer).setValue(100);
		}
		else{
		    ((MTransformJButton)visualizer).setProgress("Failed", 100);
		}
	    }
	}});
    }
    
    public void visitInstallComplete(final InstallComplete ic){
	isSuccessful = ic.getSuccess();
	isDone = true;
	SwingUtilities.invokeLater( new Runnable(){ public void run(){
	    if(ic.getSuccess()){
		if(isProgressBar){
		    ((JProgressBar)visualizer).setString( "Download successful." );
		    ((JProgressBar)visualizer).setValue(100);
		}
		else{
		    ((MTransformJButton)visualizer).setProgress("Download complete", 100);
		}
	    }
	    else{
		if(isProgressBar){
		    ((JProgressBar)visualizer).setString( "Download failed.  Please try again." );
		    ((JProgressBar)visualizer).setValue(100);
		}
		else{
		    ((MTransformJButton)visualizer).setProgress("Failed", 100);
		}
	    }
	}});
    }

    public void visitInstallTimeout(final InstallTimeout it){
	isSuccessful = false;
	isDone = true;
	SwingUtilities.invokeLater( new Runnable(){ public void run(){
	    if(isProgressBar){
		((JProgressBar)visualizer).setString( "Download timed out.  Please try again." );
		((JProgressBar)visualizer).setValue(100);
	    }
	    else{
		((MTransformJButton)visualizer).setProgress("Failed", 100);
	    }
	}});
    }
}

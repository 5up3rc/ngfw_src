/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */


package com.metavize.tran.ids.gui;
import com.metavize.tran.ids.*;
import com.metavize.gui.widgets.dialogs.*;
import com.metavize.gui.util.*;
import java.awt.Window;
import java.awt.Component;
import java.awt.event.*;
import java.net.URL;
import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;
import javax.swing.CellEditor;

public class UrlButtonRunnable implements ButtonRunnable {
    private String url;
    private Window topLevelWindow;
    private boolean isEnabled;
    public UrlButtonRunnable(String isEnabled){
    }
    public String getButtonText(){ return "Show URL"; }

    public boolean valueChanged(){ return false; }
    public void setEnabled(boolean enabled){ this.isEnabled = enabled; }
    public boolean isEnabled(){ return isEnabled; }

    public void setUrl(String url){ this.url = url; }
    public void setCellEditor(CellEditor cellEditor){}
    public void setTopLevelWindow(Window topLevelWindow){ this.topLevelWindow = topLevelWindow; }

    public void actionPerformed(ActionEvent evt){ run(); }
    public void run(){
	if(url!=null){
	    try{
		URL newURL = new URL(url);
		((BasicService) ServiceManager.lookup("javax.jnlp.BasicService")).showDocument(newURL);
	    }
	    catch(Exception e){
		Util.handleExceptionNoRestart("Error showing URL", e);
		MOneButtonJDialog.factory(topLevelWindow, "OpenVPN", "Unable to show URL", "OpenVPN Warning", "Warning");
	    }
	}
    }
}

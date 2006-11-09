/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.gui.upgrade;

import java.awt.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.text.*;

import com.untangle.gui.transform.*;
import com.untangle.gui.util.*;
import com.untangle.gui.widgets.coloredTable.*;
import com.untangle.gui.widgets.dialogs.*;
import com.untangle.gui.widgets.editTable.*;
import com.untangle.mvvm.*;
import com.untangle.mvvm.policy.*;
import com.untangle.mvvm.toolbox.InstallProgress;
import com.untangle.mvvm.toolbox.MackageDesc;
import com.untangle.mvvm.toolbox.UpgradeSettings;

public class UpgradeJDialog extends MConfigJDialog {

    public static final String NAME_TITLE    = "Upgrade Config";
    public static final String NAME_UPGRADE  = "Upgrade";
    public static final String NAME_SETTINGS = "Settings";

    private static UpgradeJDialog instance;

    public UpgradeJDialog(Frame parentFrame) {
	super(parentFrame);
	instance = this;
	setTitle(NAME_TITLE);
	setResizable(false);
	compoundSettings = new UpgradeCompoundSettings();
    }

    public static UpgradeJDialog getInstance(){
	return instance;
    }

    protected Dimension getMinSize(){
	return new Dimension(660,480);
    }

    protected Dimension getMaxSize(){
	return new Dimension(660,480);
    }

    public void generateGui(){
        // PROCEDURE //
	UpgradeProcessJPanel upgradeProcessJPanel = new UpgradeProcessJPanel();
	addTab(NAME_UPGRADE, null, upgradeProcessJPanel);
	addRefreshable(NAME_UPGRADE, upgradeProcessJPanel);

        // SETTINGS //
	UpgradeSettingsJPanel upgradeSettingsJPanel = new UpgradeSettingsJPanel();
	addTab(NAME_SETTINGS, null, upgradeSettingsJPanel);
        addSavable(NAME_SETTINGS, upgradeSettingsJPanel);
        addRefreshable(NAME_SETTINGS, upgradeSettingsJPanel);
    }

}

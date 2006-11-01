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

package com.untangle.gui.widgets.dialogs;

import com.untangle.gui.util.Util;


final public class SaveProceedDialog extends MTwoButtonJDialog {
    
    public SaveProceedDialog(String applianceName) {
	super(Util.getMMainJFrame());
        setTitle(applianceName + " Warning");
        cancelJButton.setIcon(Util.getButtonCancelSave());
        proceedJButton.setIcon(Util.getButtonContinueSaving());
        messageJLabel.setText("<html><center>" + applianceName + " is about to save its settings.  These settings are critical to proper network operation and you should be sure these are the settings you want.<br><b>Your GUI may be logged out.</b><br><b>Would you like to proceed?<b></center></html>");
        this.setVisible(true);
    }
    
}

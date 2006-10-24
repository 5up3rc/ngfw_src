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

package com.metavize.tran.mail.gui;

import com.metavize.gui.util.Util;
import com.metavize.gui.widgets.dialogs.*;
import java.awt.Dialog;

final public class WhitelistRemoveProceedDialog extends MTwoButtonJDialog {
    
    public WhitelistRemoveProceedDialog(Dialog topLevelDialog) {
	super( topLevelDialog );
        this.setTitle("Email Quarantine Whitelist Remove Warning");
        this.cancelJButton.setIcon(null);
        this.proceedJButton.setIcon(null);
	this.cancelJButton.setText("<html><b>Cancel</b> remove</html>");
	this.proceedJButton.setText("<html><b>Continue</b> removing</html>");
        messageJLabel.setText("<html><center>Removing a whitelist will remove all addresses<br>"
			      + "currently in that whitelist.<br>"
			      + "Any email can then end up in quarantine."
			      + "<br><b>Would you like to remove?<b></center></html>");
        this.setVisible(true);
    }
    
}

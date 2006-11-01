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

package com.untangle.gui.widgets.wizard;


import com.untangle.gui.transform.*;
import com.untangle.gui.widgets.dialogs.*;
import com.untangle.gui.widgets.coloredTable.*;
import com.untangle.gui.widgets.editTable.*;
import com.untangle.gui.util.*;

import com.untangle.mvvm.security.PasswordUtil;
import com.untangle.mvvm.*;
import com.untangle.mvvm.tran.*;

import javax.swing.border.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.table.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.*;

public class MWizardPageJPanel extends javax.swing.JPanel {

    protected boolean leavingForwards(){ return true; }
    protected boolean leavingBackwards(){ return true; }
    protected boolean enteringForwards(){ return true; }
    protected boolean enteringBackwards(){ return true; }
    protected void initialFocus(){}
    
    protected void doSave(Object settings, boolean validateOnly) throws Exception {}
    
}

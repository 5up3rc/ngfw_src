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


package com.untangle.tran.spamassassin.gui;

import com.untangle.gui.transform.*;
import com.untangle.gui.pipeline.MPipelineJPanel;

import com.untangle.mvvm.*;
import com.untangle.mvvm.tran.*;


import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;

public class MTransformControlsJPanel extends com.untangle.tran.spam.gui.MTransformControlsJPanel{
        
    public MTransformControlsJPanel(MTransformJPanel mTransformJPanel) {
        super(mTransformJPanel);
    }

}

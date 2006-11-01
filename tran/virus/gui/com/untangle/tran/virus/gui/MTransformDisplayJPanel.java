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



package com.untangle.tran.virus.gui;

import com.untangle.gui.transform.*;
import com.untangle.gui.pipeline.MPipelineJPanel;

import com.untangle.mvvm.*;
import com.untangle.mvvm.tran.*;


public class MTransformDisplayJPanel extends com.untangle.gui.transform.MTransformDisplayJPanel{
    
    
    public MTransformDisplayJPanel(MTransformJPanel mTransformJPanel) {
        super(mTransformJPanel);
        
        super.activity0JLabel.setText("SCAN");
        super.activity1JLabel.setText("BLOCK");
        super.activity2JLabel.setText("PASS");
        super.activity3JLabel.setText("REMOVE");
    }
    
}

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

import com.metavize.mvvm.*;
import com.metavize.mvvm.toolbox.MackageDesc;

public class ButtonKey implements Comparable<ButtonKey> {

    private String applianceName;
    private int viewPosition;
    private int rackType;

    public ButtonKey(MTransformJButton mTransformJButton){
        init(mTransformJButton.getName(), mTransformJButton.getViewPosition(), mTransformJButton.getMackageDesc().getRackType());
    }

    public ButtonKey(MTransformJPanel mTransformJPanel){
        this(mTransformJPanel.getMackageDesc());
    }

    public ButtonKey(MackageDesc mackageDesc){
        init(mackageDesc.getName(), mackageDesc.getViewPosition(), mackageDesc.getRackType());
    }

    public ButtonKey(String applianceName, int viewPosition, int rackType){
        init(applianceName, viewPosition, rackType);
    }

    private void init(String applianceName, int viewPosition, int rackType){
        this.applianceName = applianceName;
        this.viewPosition = viewPosition;
        this.rackType = rackType;
    }

    public int getViewPosition(){ return viewPosition; }
    public String getApplianceName(){ return applianceName; }
    public int getRackType(){ return rackType; }

    public int compareTo(ButtonKey b){
	if( getRackType() < b.getRackType() )
	    return -1;
	else if( getRackType() > b.getRackType() )
	    return 1;
	else{
            if( getViewPosition() < b.getViewPosition() )
                return -1;
            else if ( getViewPosition() > b.getViewPosition() )
                return 1;
            else
                return getApplianceName().compareToIgnoreCase( b.getApplianceName() );
	}

    }


}

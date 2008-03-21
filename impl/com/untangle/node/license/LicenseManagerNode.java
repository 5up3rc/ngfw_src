/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.node.license;

import com.untangle.uvm.LocalUvmContext;
import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.UvmException;

import com.untangle.uvm.node.NodeStartException;
import com.untangle.uvm.node.NodeStopException;

import com.untangle.uvm.vnet.AbstractNode;
import com.untangle.uvm.vnet.PipeSpec;

import org.apache.log4j.Logger;

public class LicenseManagerNode extends AbstractNode
{
    private final Logger logger = Logger.getLogger(getClass());
    private final PipeSpec[] pipeSpecs = new PipeSpec[0];

    public LicenseManagerNode()
    {
    }
    
    @Override
    protected void preStop() throws NodeStopException
    {
        super.preStop();
        logger.debug("preStop()");
    }

    @Override
    protected void postStart() throws NodeStartException
    {
        logger.debug("postStart()");
        
        LocalUvmContextFactory.context().loadRup();

        /* Reload the licenses */
        try {
            LocalUvmContextFactory.context().localLicenseManager().reloadLicenses();
        } catch ( UvmException ex ) {
            logger.warn( "Unable to reload the licenses." );
        }
    }

    @Override
    protected PipeSpec[] getPipeSpecs()
    {
        return pipeSpecs;
    }

    public Object getSettings()
    { 
        /* These are controlled using the methods in the uvm class */
        return null;
    }

    public void setSettings(Object settings)
    {
        /* These are controlled using the methods in the uvm class */
    }
}

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

package com.metavize.mvvm.tapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.tran.Transform;
import com.metavize.tran.token.CasingAdaptor;
import com.metavize.tran.token.CasingFactory;
import org.apache.log4j.Logger;

public class CasingPipeSpec extends PipeSpec
{
    private static final MPipeManager MPIPE_MANAGER = MPipeManager.manager();
    private static final PipelineFoundry FOUNDRY = MvvmContextFactory.context()
        .pipelineFoundry();

    private final Fitting input;
    private final Fitting output;

    private final CasingAdaptor insideAdaptor;
    private final CasingAdaptor outsideAdaptor;

    private final Logger logger = Logger.getLogger(getClass());

    private MPipe insideMPipe;

    private MPipe outsideMPipe;

    // constructors -----------------------------------------------------------

    public CasingPipeSpec(String name, Transform transform, Set subscriptions,
                          CasingFactory casingFactory,
                          Fitting input, Fitting output)
    {
        super(name, transform, subscriptions);

        insideAdaptor = new CasingAdaptor(casingFactory, true);
        outsideAdaptor = new CasingAdaptor(casingFactory, false);

        this.input = input;
        this.output = output;
    }

    public CasingPipeSpec(String name, Transform transform,
                          CasingFactory casingFactory,
                          Fitting input, Fitting output)
    {
        super(name, transform);

        insideAdaptor = new CasingAdaptor(casingFactory, true);
        outsideAdaptor = new CasingAdaptor(casingFactory, false);

        this.input = input;
        this.output = output;
    }

    // accessors --------------------------------------------------------------

    public Fitting getInput()
    {
        return input;
    }

    public Fitting getOutput()
    {
        return output;
    }

    public CasingAdaptor getInsideAdaptor()
    {
        return insideAdaptor;
    }

    public CasingAdaptor getOutsideAdaptor()
    {
        return outsideAdaptor;
    }

    @Override
    public void connectMPipe()
    {
        if (null == insideMPipe && null == outsideMPipe) {
            insideMPipe = MPIPE_MANAGER.plumbLocal(this, insideAdaptor);
            outsideMPipe = MPIPE_MANAGER.plumbLocal(this, outsideAdaptor);
            FOUNDRY.registerCasing(insideMPipe, outsideMPipe);
        } else {
            logger.warn("casing MPipes already connected");
        }
    }

    @Override
    public void disconnectMPipe()
    {
        if (null != insideMPipe && null != outsideMPipe) {
            FOUNDRY.deregisterCasing(insideMPipe);
            insideMPipe.destroy();
            outsideMPipe.destroy();
            insideMPipe = outsideMPipe = null;
        } else {
            logger.warn("casing MPipes not connected");
        }
    }

    @Override
    public void dumpSessions()
    {
        if (null != insideMPipe) {
            insideMPipe.dumpSessions();
        }

        if (null != outsideMPipe) {
            outsideMPipe.dumpSessions();
        }
    }

    @Override
    public IPSessionDesc[] liveSessionDescs()
    {
        List<IPSessionDesc> l = new ArrayList<IPSessionDesc>();
        if (null != insideMPipe) {
            for (IPSessionDesc isd : insideMPipe.liveSessionDescs()) {
                l.add(isd);
            }
        }

        if (null != outsideMPipe) {
            for (IPSessionDesc isd : outsideMPipe.liveSessionDescs()) {
                l.add(isd);
            }
        }

        return l.toArray(new IPSessionDesc[l.size()]);
    }
}

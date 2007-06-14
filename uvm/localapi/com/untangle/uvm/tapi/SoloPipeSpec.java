/*
 * $HeadURL:$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module.  An independent
 * module is a module which is not derived from or based on this library.
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

package com.untangle.uvm.tapi;

import java.util.Set;

import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.UvmLocalContext;
import com.untangle.uvm.tapi.event.SessionEventListener;
import com.untangle.uvm.node.Node;
import org.apache.log4j.Logger;

public class SoloPipeSpec extends PipeSpec
{
    private static final MPipeManager MPIPE_MANAGER;
    private static final PipelineFoundry FOUNDRY;

    public static final int MIN_STRENGTH = 0;
    public static final int MAX_STRENGTH = 32;

    private final Fitting fitting;
    private final Affinity affinity;
    private final int strength;

    private final Logger logger = Logger.getLogger(getClass());

    private final SessionEventListener listener;
    private MPipe mPipe;

    // constructors -----------------------------------------------------------

    public SoloPipeSpec(String name, Node node, Set subscriptions,
                        SessionEventListener listener,
                        Fitting fitting, Affinity affinity, int strength)
    {
        super(name, node, subscriptions);

        if (strength < MIN_STRENGTH || strength > MAX_STRENGTH) {
            throw new IllegalArgumentException("bad strength: " + strength);
        }

        this.listener = listener;
        this.fitting = fitting;
        this.affinity = affinity;
        this.strength = strength;
    }

    public SoloPipeSpec(String name, Node node,
                        Subscription subscription,
                        SessionEventListener listener, Fitting fitting,
                        Affinity affinity, int strength)
    {
        super(name, node, subscription);

        if (strength < MIN_STRENGTH || strength > MAX_STRENGTH) {
            throw new IllegalArgumentException("bad strength: " + strength);
        }

        this.listener = listener;
        this.fitting = fitting;
        this.affinity = affinity;
        this.strength = strength;
    }

    public SoloPipeSpec(String name, Node node,
                        SessionEventListener listener,
                        Fitting fitting, Affinity affinity,
                        int strength)
    {
        super(name, node);

        if (strength < MIN_STRENGTH || strength > MAX_STRENGTH) {
            throw new IllegalArgumentException("bad strength: " + strength);
        }

        this.listener = listener;
        this.fitting = fitting;
        this.affinity = affinity;
        this.strength = strength;
    }

    // accessors --------------------------------------------------------------

    public SessionEventListener getListener()
    {
        return listener;
    }

    public Fitting getFitting()
    {
        return fitting;
    }

    public Affinity getAffinity()
    {
        return affinity;
    }

    public int getStrength()
    {
        return strength;
    }

    public MPipe getMPipe()
    {
        return mPipe;
    }

    // PipeSpec methods -------------------------------------------------------

    @Override
    public void connectMPipe()
    {
        if (null == mPipe) {
            mPipe = MPIPE_MANAGER.plumbLocal(this, listener);
            FOUNDRY.registerMPipe(mPipe);
        } else {
            logger.warn("mPipes already connected");
        }
    }

    @Override
    public void disconnectMPipe()
    {
        if (null != mPipe) {
            FOUNDRY.deregisterMPipe(mPipe);
            mPipe.destroy();
            mPipe = null;
        } else {
            logger.warn("mPipes not connected");
        }
    }

    @Override
    public void dumpSessions()
    {
        if (null != mPipe) {
            mPipe.dumpSessions();
        }
    }

    @Override
    public IPSessionDesc[] liveSessionDescs()
    {
        if (null != mPipe) {
            return mPipe.liveSessionDescs();
        } else {
            return new IPSessionDesc[0];
        }
    }

    // static initialization --------------------------------------------------

    static {
        UvmLocalContext mlc = UvmContextFactory.context();
        MPIPE_MANAGER = mlc.mPipeManager();
        FOUNDRY = mlc.pipelineFoundry();
    }
}

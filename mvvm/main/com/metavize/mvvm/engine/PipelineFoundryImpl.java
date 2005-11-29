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

package com.metavize.mvvm.engine;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.metavize.mvvm.argon.ArgonAgent;
import com.metavize.mvvm.argon.IPSessionDesc;
import com.metavize.mvvm.argon.PipelineDesc;
import com.metavize.mvvm.argon.SessionEndpoints;
import com.metavize.mvvm.logging.EventLogger;
import com.metavize.mvvm.policy.Policy;
import com.metavize.mvvm.policy.PolicyRule;
import com.metavize.mvvm.policy.SystemPolicyRule;
import com.metavize.mvvm.policy.UserPolicyRule;
import com.metavize.mvvm.tapi.CasingPipeSpec;
import com.metavize.mvvm.tapi.Fitting;
import com.metavize.mvvm.tapi.MPipe;
import com.metavize.mvvm.tapi.Pipeline;
import com.metavize.mvvm.tapi.PipelineFoundry;
import com.metavize.mvvm.tapi.SoloPipeSpec;
import com.metavize.mvvm.tran.PipelineEndpoints;
import com.metavize.mvvm.tran.PipelineStats;
import org.apache.log4j.Logger;

class PipelineFoundryImpl implements PipelineFoundry
{
    private static final PipelineFoundryImpl PIPELINE_FOUNDRY_IMPL
        = new PipelineFoundryImpl();

    private static final EventLogger eventLogger
        = MvvmContextImpl.context().eventLogger();
    private static final Logger logger
        = Logger.getLogger(PipelineFoundryImpl.class);

    private final Map<Fitting, List<MPipe>> inboundMPipes
        = new HashMap<Fitting, List<MPipe>>();
    private final Map<Fitting, List<MPipe>> outboundMPipes
        = new HashMap<Fitting, List<MPipe>>();
    private final Map<MPipe, MPipe> casings = new HashMap<MPipe, MPipe>();

    private final Map<InetSocketAddress, Fitting> connectionFittings
        = new ConcurrentHashMap<InetSocketAddress, Fitting>();

    private final Map<Integer, PipelineImpl> pipelines
        = new ConcurrentHashMap<Integer, PipelineImpl>();

    // These don't need to be concurrent and being able to use a null key
    // is currently useful for the null policy.
    private static final Map<Policy, Map<Fitting, List<MPipeFitting>>> inboundChains
        = new HashMap<Policy, Map<Fitting, List<MPipeFitting>>>();
    private static final Map<Policy, Map<Fitting, List<MPipeFitting>>> outboundChains
        = new HashMap<Policy, Map<Fitting, List<MPipeFitting>>>();

    private PipelineFoundryImpl() { }

    public static PipelineFoundryImpl foundry()
    {
        return PIPELINE_FOUNDRY_IMPL;
    }

    public PipelineDesc weld(IPSessionDesc sd)
    {
        Long t0 = System.nanoTime();

        PolicyRule pr = selectPolicy(sd);
        if (pr == null) {
            logger.error("No policy rule found for session " + sd);
        }
        Policy p = null == pr ? null : pr.getPolicy();
        boolean isInbound = pr == null ? false : pr.isInbound();

        InetAddress sAddr = sd.serverAddr();
        int sPort = sd.serverPort();

        InetSocketAddress socketAddress = new InetSocketAddress(sAddr, sPort);
        Fitting start = connectionFittings.remove(socketAddress);

        if (SessionEndpoints.PROTO_TCP == sd.protocol()) {
            if (null == start) {
                switch (sPort) {
                case 21:
                    start = Fitting.FTP_CTL_STREAM;
                    break;

                case 25:
                    start = Fitting.SMTP_STREAM;
                    break;

                case 80:
                    start = Fitting.HTTP_STREAM;
                    break;

                case 110:
                    start = Fitting.POP_STREAM;
                    break;

                case 143:
                    start = Fitting.IMAP_STREAM;
                    break;

                default:
                    start = Fitting.OCTET_STREAM;
                    break;
                }
            }
        } else {
            start = Fitting.OCTET_STREAM; // XXX we should have UDP hier.
        }

        long ct0 = System.nanoTime();
        List<MPipeFitting> chain = makeChain(sd, p, isInbound, start);
        long ct1 = System.nanoTime();

        // filter list
        long ft0 = System.nanoTime();

        List<ArgonAgent> al = new ArrayList<ArgonAgent>(chain.size());
        List<MPipeFitting> ml = new ArrayList<MPipeFitting>(chain.size());

        MPipe end = null;

        for (Iterator<MPipeFitting> i = chain.iterator(); i.hasNext(); ) {
            MPipeFitting mpf = i.next();

            if (null != end) {
                if (mpf.mPipe == end) {
                    end = null;
                }
            } else {
                MPipe mPipe = mpf.mPipe;
                if (mPipe.getPipeSpec().matches(pr, sd)) {
                    ml.add(mpf);
                    al.add(mPipe.getArgonAgent());
                } else {
                    end = mpf.end;
                }
            }
        }

        long ft1 = System.nanoTime();

        PipelineImpl pipeline = new PipelineImpl(sd.id(), ml);
        pipelines.put(sd.id(), pipeline);

        Long t1 = System.nanoTime();
        if (logger.isDebugEnabled()) {
            logger.debug("sid: " + sd.id() + " pipe in " + (t1 - t0)
                         + " made: " + (ct1 - ct0)
                         + " filtered: " + (ft1 - ft0)
                         + " chain: " + ml);
        }

        return new PipelineDesc(pr, al);
    }

    public PipelineEndpoints createInitialEndpoints(IPSessionDesc start)
    {
        return new PipelineEndpoints(start);
    }

    public void registerEndpoints(PipelineEndpoints pe)
    {
        eventLogger.log(pe);
    }

    public void destroy(IPSessionDesc start, IPSessionDesc end,
                        PipelineEndpoints pe)
    {
        PipelineImpl pipeline = pipelines.remove(start.id());

        if (logger.isDebugEnabled()) {
            logger.debug("removed: " + pipeline + " for: " + start.id());
        }

        eventLogger.log(new PipelineStats(start, end, pe));

        pipeline.destroy();
    }

    public void registerMPipe(MPipe mPipe)
    {
        synchronized (this) {
            registerMPipe(inboundMPipes, mPipe, new MPipeComparator(true));
            registerMPipe(outboundMPipes, mPipe, new MPipeComparator(false));
        }
    }

    public void deregisterMPipe(MPipe mPipe)
    {
        synchronized (this) {
            deregisterMPipe(inboundMPipes, mPipe, new MPipeComparator(true));
            deregisterMPipe(outboundMPipes, mPipe, new MPipeComparator(false));
        }
    }

    public void registerCasing(MPipe insideMPipe, MPipe outsideMPipe)
    {
       if (insideMPipe.getPipeSpec() != outsideMPipe.getPipeSpec()) {
            throw new IllegalArgumentException("casing constraint violated");
        }

       synchronized (this) {
           casings.put(insideMPipe, outsideMPipe);
           clearCache();
       }
    }

    public void deregisterCasing(MPipe insideMPipe)
    {
        synchronized (this) {
            casings.remove(insideMPipe);
            clearCache();
        }
    }

    public void registerConnection(InetSocketAddress socketAddress,
                                   Fitting fitting)
    {
        connectionFittings.put(socketAddress, fitting);
    }

    public Pipeline getPipeline(int sessionId)
    {
        return (Pipeline)pipelines.get(sessionId);
    }

    // private methods --------------------------------------------------------

    private List<MPipeFitting> makeChain(IPSessionDesc sd, Policy p,
                                         boolean inbound, Fitting start)
    {
        Map<Policy, Map<Fitting, List<MPipeFitting>>> chains = inbound
            ? inboundChains : outboundChains;

        List<MPipeFitting> mPipeFittings = null;

        Map<Fitting, List<MPipeFitting>> fcs = chains.get(p);

        if (null != fcs) {
            mPipeFittings = fcs.get(start);
        }

        if (null == mPipeFittings) {
            synchronized (this) {
                fcs = chains.get(p);

                if (null == fcs) {
                    fcs = new HashMap<Fitting, List<MPipeFitting>>();
                    chains.put(p, fcs);
                } else {
                    mPipeFittings = fcs.get(start);
                }

                if (null == mPipeFittings) {
                    Map<MPipe, MPipe> availCasings = new HashMap(casings);

                    Map<Fitting, List<MPipe>> mp = inbound ? inboundMPipes
                        : outboundMPipes;
                    Map<Fitting, List<MPipe>> availMPipes
                        = new HashMap<Fitting, List<MPipe>>(mp);

                    int s = availCasings.size() + availMPipes.size();
                    mPipeFittings = new ArrayList<MPipeFitting>(s);

                    weld(mPipeFittings, start, p, availMPipes, availCasings);

                    fcs.put(start, mPipeFittings);
                }
            }
        }

        return mPipeFittings;
    }

    private void weld(List<MPipeFitting> mPipeFittings, Fitting start,
                      Policy p, Map<Fitting, List<MPipe>> availMPipes,
                      Map<MPipe, MPipe> availCasings)
    {
        weldMPipes(mPipeFittings, start, p, availMPipes, availCasings);
        weldCasings(mPipeFittings, start, p, availMPipes, availCasings);
    }

    private boolean weldMPipes(List<MPipeFitting> mPipeFittings,
                               Fitting start, Policy p,
                               Map<Fitting, List<MPipe>> availMPipes,
                               Map<MPipe, MPipe> availCasings)
    {
        boolean welded = false;

        TRY_AGAIN:
        for (Iterator<Fitting> i = availMPipes.keySet().iterator(); i.hasNext(); ) {
            Fitting f = i.next();
            if (start.instanceOf(f)) {
                List l = availMPipes.get(f);
                i.remove();
                for (Iterator<MPipe> j = l.iterator(); j.hasNext(); ) {
                    MPipe mPipe = j.next();
                    if (null == mPipe) {
                        boolean w = weldCasings(mPipeFittings, start, p,
                                                availMPipes, availCasings);
                        if (w) {
                            welded = true;
                        }
                    } else if (mPipe.getPipeSpec().matchesPolicy(p)) {
                        MPipeFitting mpf = new MPipeFitting(mPipe, start);
                        boolean w = mPipeFittings.add(mpf);
                        if (w) {
                            welded = true;
                        }
                    }
                }
                break TRY_AGAIN;
            }
        }

        return welded;
    }

    private boolean weldCasings(List<MPipeFitting> mPipeFittings,
                                Fitting start, Policy p, Map<Fitting,
                                List<MPipe>> availMPipes,
                                Map<MPipe, MPipe> availCasings)
    {
        boolean welded = false;

        TRY_AGAIN:
        for (Iterator<MPipe> i = availCasings.keySet().iterator(); i.hasNext(); ) {
            MPipe insideMPipe = i.next();
            CasingPipeSpec ps = (CasingPipeSpec)insideMPipe.getPipeSpec();
            Fitting f = ps.getInput();

            if (!ps.matchesPolicy(p)) {
                i.remove();
            } else if (start.instanceOf(f)) {
                MPipe outsideMPipe = availCasings.get(insideMPipe);
                i.remove();
                int s = mPipeFittings.size();
                mPipeFittings.add(new MPipeFitting(insideMPipe, start,
                                                   outsideMPipe));
                CasingPipeSpec cps = (CasingPipeSpec)insideMPipe
                    .getPipeSpec();
                Fitting insideFitting = cps.getOutput();

                boolean w = weldMPipes(mPipeFittings, insideFitting, p,
                                       availMPipes, availCasings);

                if (w) {
                    welded = true;
                    mPipeFittings.add(new MPipeFitting(outsideMPipe, insideFitting));
                } else {
                    while (mPipeFittings.size() > s) {
                        mPipeFittings.remove(mPipeFittings.size() - 1);
                    }
                }

                break TRY_AGAIN;
            }
        }

        return welded;
    }

    private void registerMPipe(Map mPipes, MPipe mPipe, Comparator c)
    {
        SoloPipeSpec sps = (SoloPipeSpec)mPipe.getPipeSpec();
        Fitting f = sps.getFitting();

        List l = (List)mPipes.get(f);

        if (null == l) {
            l = new ArrayList();
            l.add(null);
            mPipes.put(f, l);
        }

        int i = Collections.binarySearch(l, mPipe, c);
        l.add(0 > i ? -i - 1 : i, mPipe);

        clearCache();
    }

    private void deregisterMPipe(Map<Fitting, List<MPipe>> mPipes,
                                 MPipe mPipe, Comparator c)
    {
        SoloPipeSpec sps = (SoloPipeSpec)mPipe.getPipeSpec();
        Fitting f = sps.getFitting();

        List l = mPipes.get(f);

        int i = Collections.binarySearch(l, mPipe, c);
        if (0 > i) {
            logger.warn("deregistering nonregistered pipe");
        } else {
            l.remove(i);
        }

        clearCache();
    }

    private PolicyRule selectPolicy(IPSessionDesc sd)
    {
        PolicyManagerImpl pmi = PolicyManagerImpl.policyManager();
        UserPolicyRule[] userRules = pmi.userRules;
        SystemPolicyRule[] sysRules = pmi.sysRules;

        for (UserPolicyRule upr : userRules) {
            if (upr.matches(sd)) {
                return upr;
            }
        }

        for (SystemPolicyRule spr : sysRules) {
            if (spr.matches(sd)) {
                return spr;
            }
        }

        return null;
    }

    private void clearCache()
    {
        inboundChains.clear();
        outboundChains.clear();
    }
}

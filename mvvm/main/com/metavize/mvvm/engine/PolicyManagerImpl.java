/*
 * Copyright (c) 2003, 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id$
 */

package com.metavize.mvvm.engine;

import java.util.*;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.policy.Policy;
import com.metavize.mvvm.policy.PolicyConfiguration;
import com.metavize.mvvm.policy.PolicyException;
import com.metavize.mvvm.policy.PolicyManager;
import com.metavize.mvvm.policy.SystemPolicyRule;
import com.metavize.mvvm.policy.UserPolicyRule;
import com.metavize.mvvm.policy.UserPolicyRuleSet;
import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;

class PolicyManagerImpl implements PolicyManagerPriv
{
    private static final String INITIAL_POLICY_NAME = "Default Rack";
    private static final String INITIAL_POLICY_NOTES = "The default rack";

    private static final Logger logger = Logger.getLogger( PolicyManagerImpl.class );

    private static PolicyManagerImpl POLICY_MANAGER = new PolicyManagerImpl();

    // package protected variables (used by PipelineFoundryImpl)
    volatile UserPolicyRule[] userRules;
    volatile SystemPolicyRule[] sysRules;

    private List<Policy> allPolicies; // Also contains default one
    private Policy defaultPolicy;

    private Object policyRuleLock = new Object();
    private UserPolicyRuleSet userRuleSet;

    private PolicyManagerImpl() {
        allPolicies = new ArrayList<Policy>();

        Session s = MvvmContextFactory.context().openSession();
        try {
            Transaction tx = s.beginTransaction();

            Query q = s.createQuery("from Policy p order by id asc");
            List results = q.list();

            if (results.size() == 0) {
                logger.info("Empty policy table.  Creating default policy.");
                defaultPolicy = new Policy(true, INITIAL_POLICY_NAME, INITIAL_POLICY_NOTES);
                allPolicies.add(defaultPolicy);
                s.save(defaultPolicy);
            } else {
                for (Object o : results) {
                    Policy policy = (Policy)o;
                    if (policy.isDefault()) {
                        assert allPolicies.size() == 0;
                        assert defaultPolicy == null;
                        defaultPolicy = policy;
                    }
                    allPolicies.add(policy);
                }
                assert defaultPolicy != null;
            }

            q = s.createQuery("from UserPolicyRuleSet uprs");
            results = q.list();
            if (results.size() == 0) {
                logger.info("Empty User Policy Rule Set.  Creating empty one.");
                UserPolicyRuleSet uprs = new UserPolicyRuleSet();
                s.save(uprs);
            } else if (results.size() > 1) {
                logger.fatal("Found " + results.size() + " user policy rule sets! Deleting all but first");
                for (int i = 1; i < results.size(); i++) {
                    UserPolicyRuleSet uprs = (UserPolicyRuleSet) results.get(i);
                    s.delete(uprs);
                }
            }
            tx.commit();
        } catch (HibernateException exn) {
            logger.fatal("could not get Policies", exn);
            // Now what? XXX
        } finally {
            try {
                s.close();
            } catch (HibernateException exn) {
                logger.warn("could not close session", exn);
            }
        }

        logger.info("Initialized PolicyManager");
    }

    static PolicyManagerImpl policyManager()
    {
        return POLICY_MANAGER;
    }

    private static final Policy[] POLICY_ARRAY_PROTO = new Policy[0];

    public Policy[] getPolicies() {
        return (Policy[]) allPolicies.toArray(POLICY_ARRAY_PROTO);
    }

    public Policy getPolicy(String name)
    {
        for (Policy p : allPolicies) {
            if (name.equals(p.getName())) {
                return p;
            }
        }

        return null;
    }

    public Policy getDefaultPolicy() {
        return defaultPolicy;
    }

    public void addPolicy(String name, String notes)
        throws PolicyException
    {
        synchronized(policyRuleLock) {
            if (name == null)
                throw new PolicyException("New policy must have a name");
            for (Policy p : allPolicies) {
                if (name.equalsIgnoreCase(p.getName()))
                    throw new PolicyException("A policy named " + name + " already exists");
            }

            Policy p = new Policy(false, name, notes);

            Session s = MvvmContextFactory.context().openSession();
            try {
                Transaction tx = s.beginTransaction();

                s.save(p);

                tx.commit();
            } catch (HibernateException exn) {
                logger.error("could not save Policy", exn);
            } finally {
                try {
                    s.close();
                } catch (HibernateException exn) {
                    logger.warn("could not close session", exn); // XXX TransExn
                }
            }

            logger.debug("Added new policy, id: " + p.getId() + ", name: " + p.getName());
            allPolicies.add(p);
        }
    }

    public void removePolicy(Policy p)
        throws PolicyException
    {
        logger.debug("Trying to remove policy, id: " + p.getId() + ", name: " + p.getName());
        synchronized(policyRuleLock) {
            if (p == null)
                throw new PolicyException("Must specify a policy to remove");
            if (p.isDefault())
                throw new PolicyException("Cannot remove the default policy");
            if (!allPolicies.contains(p))
                throw new PolicyException("Policy " + p.getName() + " not found in all policies");
            if (isInUse(p))
                throw new PolicyException("Policy " + p.getName() + " cannot be removed because it is in use");

            Session s = MvvmContextFactory.context().openSession();
            try {
                Transaction tx = s.beginTransaction();

                s.delete(p);

                tx.commit();
            } catch (HibernateException exn) {
                logger.error("could not remove Policy", exn);
            } finally {
                try {
                    s.close();
                } catch (HibernateException exn) {
                    logger.warn("could not close session", exn); // XXX TransExn
                }
            }

            logger.debug("Removed policy, id: " + p.getId() + ", name: " + p.getName());
            allPolicies.remove(p);
        }
    }

    public void setPolicy(Policy p, String name, String notes)
        throws PolicyException
    {
        synchronized(policyRuleLock) {
            if (p == null)
                throw new PolicyException("Must specify a policy to remove");
            if (!allPolicies.contains(p))
                throw new PolicyException("Policy " + p.getName() + " not found in all policies");

            p.setName(name);
            p.setNotes(notes);

            Session s = MvvmContextFactory.context().openSession();
            try {
                Transaction tx = s.beginTransaction();

                s.saveOrUpdate(p);

                tx.commit();
            } catch (HibernateException exn) {
                logger.error("could not change Policy", exn);
            } finally {
                try {
                    s.close();
                } catch (HibernateException exn) {
                    logger.warn("could not close session", exn); // XXX TransExn
                }
            }
        }
        logger.debug("Changed policy, id: " + p.getId() + ", new name: " + p.getName());
    }

    protected boolean isInUse(Policy p)
    {
        synchronized(policyRuleLock) {
            for (SystemPolicyRule spr : sysRules) {
                if (spr.getPolicy() == p)
                    return true;
            }
            for (UserPolicyRule upr : userRules) {
                if (upr.getPolicy() == p)
                    return true;
            }
        }

        return 0 < TransformManagerImpl.manager().transformInstances(p).size();
    }

    public SystemPolicyRule[] getSystemPolicyRules() {
        return sysRules;
    }

    public void setSystemPolicyRule(SystemPolicyRule rule, Policy p, boolean inbound) {
        // more Sanity checking (policy) XXX
        synchronized(policyRuleLock) {
            for (int i = 0; i < sysRules.length; i++) {
                if (sysRules[i] == rule) {
                    Session s = MvvmContextFactory.context().openSession();
                    try {
                        Transaction tx = s.beginTransaction();

                        rule.setPolicy(p);
                        rule.setInbound(inbound);
                        s.saveOrUpdate(rule);

                        tx.commit();
                    } catch (HibernateException exn) {
                        logger.error("could not save change to SystemRule", exn);
                    } finally {
                        try {
                            s.close();
                        } catch (HibernateException exn) {
                            logger.warn("could not close hibernate session", exn);
                        }
                    }
                }
            }
        }
    }

    public UserPolicyRule[] getUserPolicyRules() {
        return userRules;
    }

    public void setUserPolicyRules(List rules) {
        // Sanity checking XXX
        synchronized(policyRuleLock) {
            Session s = MvvmContextFactory.context().openSession();
            try {
                Transaction tx = s.beginTransaction();

                userRuleSet.setRules(rules);
                userRules = (UserPolicyRule[]) rules.toArray(new UserPolicyRule[] { });
                s.saveOrUpdate(userRuleSet);

                tx.commit();
            } catch (HibernateException exn) {
                logger.warn("could not get HttpSettings", exn);
            } finally {
                try {
                    s.close();
                } catch (HibernateException exn) {
                    logger.warn("could not close hibernate session", exn);
                }
            }
        }
    }


    // For da UI
    public PolicyConfiguration getPolicyConfiguration() {
        PolicyConfiguration result = new PolicyConfiguration(allPolicies, sysRules, userRules);
        return result;
    }

    // For da UI
    public void setPolicyConfiguration(PolicyConfiguration pc)
        throws PolicyException
    {
        List confpc = pc.getPolicies();
        List syspc = pc.getSystemPolicyRules();
        List userpc = pc.getUserPolicyRules();

        // Sanity check the policies
        if (confpc == null || confpc.size() < 1)
            throw new PolicyException("List of policies missing or empty");
        List<Policy> newAllPolicies = new ArrayList<Policy>(confpc);

        // Sanity check the system rules
        if (syspc == null || syspc.size() < 1)
            throw new PolicyException("System rules missing or empty");
        // Really need more checking here.  They shouldn't be able to delete or add any rows. XXX
        SystemPolicyRule[] newSysRules = new SystemPolicyRule[syspc.size()];
        int i = 0;
        for (Object o : syspc) {
            SystemPolicyRule spr = (SystemPolicyRule)o;
            newSysRules[i++] = spr;
        }

        // Sanity check the user rules
        if (userpc == null)
            throw new PolicyException("User rules missing");
        // Really need more checking here.  XXX
        List newUserRules = new ArrayList(userpc.size());
        for (Object o : userpc) {
            UserPolicyRule upr = (UserPolicyRule)o;
            newUserRules.add(upr);
        }

        List<Policy> pToAdd = new ArrayList<Policy>();
        List<Policy> pToRemove = new ArrayList<Policy>();

        // what's happening here.
        for (Policy oldp : allPolicies) {
            if (oldp.getId() == null)
                throw new Error("Policy" + oldp.getName() + " has null id");
        }

        // Now do the actual setting
        synchronized(policyRuleLock) {
            Policy newDefaultPolicy = null;
            for (Policy newp : newAllPolicies) {
                boolean foundIt = false;
                if (newp.isDefault()) {
                    if (newDefaultPolicy != null)
                        throw new PolicyException("Cannot have more than one default policy");
                    newDefaultPolicy = newp;
                }
                if (newp.getId() != null) {
                    for (Policy oldp : allPolicies) {
                        if (newp.getId().equals(oldp.getId())) {
                            if (foundIt)
                                throw new PolicyException("Policy duplicated");
                            foundIt = true;
                            setPolicy(oldp, newp.getName(), newp.getNotes());
                        }
                    }
                }
                if (!foundIt)
                    pToAdd.add(newp);
            }
            if (newDefaultPolicy == null)
                throw new PolicyException("Default policy missing");

            for (Policy oldp : allPolicies) {
                boolean foundIt = false;
                for (Policy newp : newAllPolicies) {
                    if (oldp.getId().equals(newp.getId())) {
                        foundIt = true;
                        logger.debug("Not removing policy " + oldp.getId() + ", found in new list");
                        break;
                    }
                }
                if (!foundIt)
                    pToRemove.add(oldp);
            }
            for (Policy newp : pToAdd) {
                addPolicy(newp.getName(), newp.getNotes());
            }
            for (Policy oldp : pToRemove) {
                removePolicy(oldp);
            }
            defaultPolicy = newDefaultPolicy;

            for (SystemPolicyRule newspr : newSysRules) {
                boolean foundIt = false;
                for (SystemPolicyRule oldspr : sysRules) {
                    if (newspr.isSameRow(oldspr)) {
                        if (foundIt)
                            throw new PolicyException("System Policy rule duplicated");
                        foundIt = true;
                        setSystemPolicyRule(oldspr, newspr.getPolicy(), newspr.isInbound());
                    }
                }
                if (!foundIt)
                    throw new PolicyException("System Policy rule to be changed not found");
            }

            setUserPolicyRules(newUserRules);
        }
    }

    // package protected methods ----------------------------------------------

    // MVVM calls in here at boot time and whenever a new interface is
    // added or removed, passing all interfaces.  We automatically add
    // or removeSystemPolicyRules as appropriate.  We also build the
    // in-memory UserPolicyRule list.
    public void reconfigure(byte[] interfaces)
    {
        /* Reconfigure the networking interfaces */
        NetworkingManagerImpl.getInstance().buildIntfEnum();
        
        // For now do nothing
        if (allPolicies.size() == 0)
            // Always
            return;

        synchronized(policyRuleLock) {
            if (logger.isDebugEnabled())
                logger.debug("Setting interfaces to " + interfaces);
            Session s = MvvmContextFactory.context().openSession();
            try {
                Transaction tx = s.beginTransaction();

                Query sysq = s.createQuery("from SystemPolicyRule spr");
                List existingSys = sysq.list();
                Query userq = s.createQuery("from UserPolicyRuleSet uprs");
                UserPolicyRuleSet uprs = (UserPolicyRuleSet) userq.uniqueResult();
                List existingUser = uprs.getRules();

                List goodSys = new ArrayList();
                Set goodUser = new HashSet();

                // For each interface pair
                for (int i = 0; i < interfaces.length - 1; i++) {
                    for (int j = i+1; j < interfaces.length; j++) {
                        byte firstIntf = interfaces[i];
                        byte secondIntf = interfaces[j];

                        // Add in the missing system rules
                        boolean foundForward = false;
                        boolean foundBackward = false;
                        for (Object o : existingSys) {
                            SystemPolicyRule spr = (SystemPolicyRule)o;
                            byte clientIntf = spr.getClientIntf();
                            byte serverIntf = spr.getServerIntf();
                            if (clientIntf == firstIntf && serverIntf == secondIntf) {
                                if (foundForward) {
                                    logger.fatal("Found extra SystemPolicyRule for ci: " + clientIntf + ", si: " + serverIntf);
                                } else {
                                    if (logger.isDebugEnabled())
                                        logger.debug("Found existing SystemPolicyRule for ci: " + clientIntf + ", si: " + serverIntf);
                                    goodSys.add(spr);
                                    foundForward = true;
                                }
                            } else if (clientIntf == secondIntf && serverIntf == firstIntf) {
                                if (foundBackward) {
                                    logger.fatal("Found extra SystemPolicyRule for ci: " + clientIntf + ", si: " + serverIntf);
                                } else {
                                    if (logger.isDebugEnabled())
                                        logger.debug("Found existing SystemPolicyRule for ci: " + clientIntf + ", si: " + serverIntf);
                                    goodSys.add(spr);
                                    foundBackward = true;
                                }
                            }
                        }
                        if (!foundForward) {
                            logger.info("Adding new default inbound SystemPolicyRule for ci: " + firstIntf + ", si: " + secondIntf);
                            SystemPolicyRule newInRule = new SystemPolicyRule(firstIntf, secondIntf, defaultPolicy, true);
                            s.save(newInRule);
                            goodSys.add(newInRule);
                        }
                        if (!foundBackward) {
                            logger.info("Adding new default outbound SystemPolicyRule for ci: " + secondIntf + ", si: " + firstIntf);
                            SystemPolicyRule newOutRule = new SystemPolicyRule(secondIntf, firstIntf, defaultPolicy, false);
                            s.save(newOutRule);
                            goodSys.add(newOutRule);
                        }

                        // Record good user rules.
                        for (Object o : existingUser) {
                            UserPolicyRule upr = (UserPolicyRule)o;
                            byte clientIntf = upr.getClientIntf();
                            byte serverIntf = upr.getServerIntf();
                            if ((clientIntf == firstIntf && serverIntf == secondIntf) ||
                                (clientIntf == secondIntf && serverIntf == firstIntf)) {
                                // Good to go.
                                if (logger.isDebugEnabled())
                                    logger.debug("Found existing UserPolicyRule for ci: " + clientIntf + ", si: " + serverIntf);
                                goodUser.add(upr);
                            }
                        }
                    }
                }

                // Get rid of the extra user rules.
                existingUser.retainAll(goodUser);
                uprs.setRules(existingUser);
                s.saveOrUpdate(uprs);

                // Finally, get rid of the extra system ones.
                existingSys.removeAll(goodSys);
                for (Object o : existingSys) {
                    SystemPolicyRule spr = (SystemPolicyRule)o;
                    logger.info("Removing unused SystemPolicyRule for ci: " + spr.getClientIntf() + ", si: " + spr.getServerIntf());
                    s.delete(spr);
                }

                userRuleSet = uprs;
                userRules = (UserPolicyRule[]) existingUser.toArray(new UserPolicyRule[] { });
                sysRules = (SystemPolicyRule[]) goodSys.toArray(new SystemPolicyRule[] { });

                tx.commit();
            } catch (HibernateException exn) {
                logger.fatal("could not get PolicyRules", exn);
                // Now what? XXX
            } finally {
                try {
                    s.close();
                } catch (HibernateException exn) {
                    logger.warn("could not close session", exn);
                }
            }
        }
    }
}

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

package com.metavize.mvvm.policy;

public interface PolicyManager {

    /**
     * Returns all policies.  Don't count on the return order, it will be the insertion order which
     * isn't very interesting.
     *
     * @return an array of <code>Policy</code>, one for each policy defined in the system
     */
    Policy[] getPolicies();

    /**
     * Returns the default policy (the one used for newly created interfaces).
     *
     * @return the default <code>Policy</code>
     */
    Policy getDefaultPolicy();

    /**
     * Adds a new policy to the system.
     *
     * @param name a <code>String</code> naming the new policy
     * @param notes a <code>String</code> giving a description of the new policy
     * @exception PolicyException if the named policy already exists
     */
    void addPolicy(String name, String notes) throws PolicyException;

    /**
     * Removes a policy from the system.  If the policy is in use (has transforms or is used
     * in the policy selector), it cannot be removed.  Also, the default policy cannot be
     * removed.
     *
     * @param policy the <code>Policy</code> to be removed
     * @exception PolicyException if the policy can not be removed (is in use)
     */
    void removePolicy(Policy policy) throws PolicyException;

    /**
     * Returns all system policies rules. The order isn't important, but for convenience is the
     * suggested display order.  If there are 'n' interfaces, there will be 'n choose 2' * 2
     * of these.
     *
     * @return an array of <code>SystemPolicyRule</code>, one for each system policy rule
     */
    SystemPolicyRule[] getSystemPolicyRules();

    /**
     * Changes the given system policy rule to the given policy and direction.
     *
     * @param rule a <code>SystemPolicyRule</code> to be changed
     * @param p a <code>Policy</code> giving the new policy
     * @param inbound a <code>boolean</code> giving the new direction
     */
    void setSystemPolicy(SystemPolicyRule rule, Policy p, boolean inbound);

    /**
     * Returns all user policies rules.
     *
     * @return a <code>UserPolicyRuleSet</code> containing the ordered list of UserPolicyRules
     */
    UserPolicyRuleSet getUserPolicyRules();

    /**
     * Changes the entire user policy rule set to the given.
     *
     * @param ruleSet an <code>UserPolicyRuleSet</code> value
     */
    void setUserPolicyRules(UserPolicyRuleSet ruleSet);

    /**
     * Helper for the UI -- get all Policy related configuration in a single object
     *
     * @return a <code>PolicyConfiguration</code> containing all policy related settings
     */
    PolicyConfiguration getPolicyConfiguration();

    /**
     * Helper for the UI -- set all Policy related configuration in a single call
     *
     * @param pc a <code>PolicyConfiguration</code> value
     * @exception PolicyException if there is some problem with the settings.
     */
    void setPolicyConfiguration(PolicyConfiguration pc) throws PolicyException;

}

/*
 * $HeadURL$
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

package com.untangle.uvm.node;

import java.util.List;
import java.util.Map;

import com.untangle.uvm.policy.Policy;
import com.untangle.uvm.security.Tid;

/**
 * Remote interface for managing node instances.
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
public interface RemoteNodeManager
{
    /**
     * Get <code>Tid</code>s of nodes in the pipeline.
     *
     * @return list of all node ids.
     */
    List<Tid> nodeInstances();

    /**
     * Node instances by name.
     *
     * @param name name of the node.
     * @return tids of corresponding nodes.
     */
    List<Tid> nodeInstances(String name);

    /**
     * Node instances by policy.
     *
     * @param policy policy of node.
     * @return tids of corresponding nodes.
     */
    List<Tid> nodeInstances(Policy policy);

    /**
     * Node instances by policy, the visible ones only, for the GUI.
     *
     * @param policy policy of node.
     * @return tids of corresponding nodes.
     */
    List<Tid> nodeInstancesVisible(Policy policy);

    /**
     * Node instances by name policy.
     *
     * @param name name of node.
     * @param policy policy of node.
     * @return tids of corresponding nodes.
     */
    List<Tid> nodeInstances(String name, Policy policy);

    /**
     * Create a new node instance under the given policy.  Note
     * that it is an error to specify a non-null policy for a service,
     * or a null policy for a non-service.
     *
     * @param name of the node.
     * @param policy the policy this instance is applied to.
     * @return the <code>tid</code> of the instance.
     * @exception DeployException if the instance cannot be created.
     */
    Tid instantiate(String name, Policy policy) throws DeployException;

    /**
     * Create a new node instance under the given policy.  Note
     * that it is an error to specify a non-null policy for a service,
     * or a null policy for a non-service.
     *
     * @param name of the node.
     * @param policy the policy this instance is applied to.
     * @param args node args.
     * @return the <code>tid</code> of the instance.
     * @exception DeployException if the instance cannot be created.
     */
    Tid instantiate(String name, Policy policy, String[] args)
        throws DeployException;

    /**
     * Create a new node instance under the default policy, or in
     * the null policy if the node is a service.
     *
     * @param name of the node.
     * @param args node args.
     * @return the <code>tid</code> of the instance.
     * @exception DeployException if the instance cannot be created.
     */
    Tid instantiate(String name, String[] args) throws DeployException;

    /**
     * Create a new node instance under the default policy, or in
     * the null policy if the node is a service.
     *
     * @param name of the node.
     * @return the <code>tid</code> of the instance.
     * @exception DeployException if the instance cannot be created.
     */
    Tid instantiate(String name) throws DeployException;

    /**
     * Remove node instance from the pipeline.
     *
     * @param tid <code>Tid</code> of instance to be destroyed.
     * @exception UndeployException if detruction fails.
     */
    void destroy(Tid tid) throws UndeployException;

    /**
     * Get the <code>NodeContext</code> for a node instance.
     *
     * @param tid <code>Tid</code> of the instance.
     * @return the instance's <code>NodeContext</code>.
     */
    NodeContext nodeContext(Tid tid);

    /**
     * Get the statistics and counts for all nodes in one call.
     *
     * @return a <code>Map</code> from Tid to NodeStats for all
     * nodes in RUNNING state.
     */
    Map<Tid, NodeStats> allNodeStats();
    
    /**
     * Get the runtime state for all nodes in one call.
     *
     * @return a <code>Map</code> from Tid to NodeState for all nodes
     */
    Map<Tid, NodeState> allNodeStates();
}

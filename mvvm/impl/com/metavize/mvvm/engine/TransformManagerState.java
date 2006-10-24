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

package com.metavize.mvvm.engine;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import com.metavize.mvvm.policy.Policy;
import com.metavize.mvvm.security.Tid;

/**
 * Internal state for TransformManagerImpl.
 *
 * @author <a href="mailto:amread@untanglenetworks.com">Aaron Read</a>
 * @version 1.0
 */
@Entity
@Table(name="transform_manager_state", schema="settings")
class TransformManagerState
{
    private Long id;
    private Long lastTid = 0L;

    TransformManagerState() { }

    @Id
    @Column(name="id")
    @GeneratedValue
    private Long getId()
    {
        return id;
    }

    private void setId(Long id)
    {
        this.id = id;
    }

    /**
     * Last tid assigned.
     *
     * @return last assigned tid.
     */
    @Column(name="last_tid", nullable=false)
    Long getLastTid()
    {
        return lastTid;
    }

    void setLastTid(Long lastTid)
    {
        this.lastTid = lastTid;
    }

    /**
     * Get the next Tid.
     *
     * @return a <code>Long</code> value
     */
    Tid nextTid(Policy policy, String transformName)
    {
        return new Tid(++lastTid, policy, transformName);
    }
}

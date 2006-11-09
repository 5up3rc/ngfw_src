/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.mvvm.logging;

import java.util.List;

public interface EventRepository<E extends LogEvent>
{
    RepositoryDesc getRepositoryDesc();
    List<E> getEvents();
}

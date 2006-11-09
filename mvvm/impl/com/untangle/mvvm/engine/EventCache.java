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

package com.untangle.mvvm.engine;

import com.untangle.mvvm.logging.EventLogger;
import com.untangle.mvvm.logging.EventRepository;
import com.untangle.mvvm.logging.LogEvent;

abstract class EventCache<E extends LogEvent> implements EventRepository<E>
{
    public abstract void log(E e);

    public abstract void checkCold();

    /**
     * Sets a reference to the EventLogger as soon
     * as this cache is added to an EventLogger
     */
    public abstract void setEventLogger(EventLoggerImpl<E> eventLogger);
}

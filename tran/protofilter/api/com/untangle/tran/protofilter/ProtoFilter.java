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
package com.untangle.tran.protofilter;

import com.untangle.mvvm.logging.EventManager;
import com.untangle.mvvm.tran.Transform;

public interface ProtoFilter extends Transform
{
    ProtoFilterSettings getProtoFilterSettings();
    void setProtoFilterSettings(ProtoFilterSettings settings);
    EventManager<ProtoFilterLogEvent> getEventManager();
}

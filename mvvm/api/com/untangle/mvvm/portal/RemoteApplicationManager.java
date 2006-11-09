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

package com.untangle.mvvm.portal;

import java.util.List;

public interface RemoteApplicationManager
{
    List<Application> getApplications();

    List<String> getApplicationNames();

    Application getApplication(String name);
}

/*
 * Copyright (c) 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.openvpn;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * A site network for a client.  Done this way so the client site
 * networks and the server site networks are in their own tables.
 *
 * @author <a href="mailto:rbscott@metavize.com">Robert Scott</a>
 * @version 1.0
 */
@Entity
@Table(name="tr_openvpn_c_site_network", schema="settings")
public class ClientSiteNetwork extends SiteNetwork
{
    private static final long serialVersionUID = 4617902554944629290L;

    public ClientSiteNetwork() { }
}

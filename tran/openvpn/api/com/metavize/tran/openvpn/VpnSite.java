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

package com.metavize.tran.openvpn;

import java.util.LinkedList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.metavize.mvvm.tran.IPaddr;
import com.metavize.mvvm.tran.ValidateException;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.IndexColumn;

/**
 * A site network for a client.  Done this way so the client site
 * networks and the server site networks are in their own tables.
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 * @version 1.0
 */
@Entity
@Table(name="tr_openvpn_site", schema="settings")
public class VpnSite extends VpnClient
{
    // XXX Fixme
    // private static final long serialVersionUID = -3950973798403822835L;

    /* XXX This should be in one place */
    private static final IPaddr EMPTY_ADDR = new IPaddr( null );

    // List of addresses at this site,
    // initially, may not be supported, just use one address.
    private List    exportedAddressList;

    private boolean isEdgeGuard = false;

    public VpnSite()
    {
        /* XXXXXXXXXXXXXXXXXXXXXXXXXXXXX This should have all of the stuff
         * about exports in it, but for now just keep it in both places */
    }

    /**
     * The list of exported networks for this site.
     * Should rename the column from client_id to site_id.
     *
     * @return the list of exported networks for this site.
     */
    @OneToMany(fetch=FetchType.EAGER)
    @Cascade({ org.hibernate.annotations.CascadeType.ALL,
            org.hibernate.annotations.CascadeType.DELETE_ORPHAN })
    @JoinColumn(name="client_id")
    @IndexColumn(name="position")
    public List<ClientSiteNetwork> getExportedAddressList()
    {
        if ( this.exportedAddressList == null ) this.exportedAddressList = new LinkedList<ClientSiteNetwork>();

        return this.exportedAddressList;
    }

    public void setExportedAddressList( List<ClientSiteNetwork> exportedAddressList )
    {
        this.exportedAddressList = exportedAddressList;
    }

    /**
     * @return whether the other side is an edgeguard.
     */
    @Column(name="is_edgeguard", nullable=false)
    public boolean getIsEdgeGuard()
    {
        return this.isEdgeGuard;
    }

    public void setIsEdgeGuard( boolean isEdgeGuard )
    {
        this.isEdgeGuard = isEdgeGuard;
    }

    /**
     * XXXX This is a convenience method that doesn't scale if there are
     * multiple networks and netmasks
     */
    @Transient
    public ClientSiteNetwork getSiteNetwork()
    {
        List list = getExportedAddressList();

        ClientSiteNetwork site;
        if ( list.size() < 1 ) {
            site = new ClientSiteNetwork();
            site.setNetwork( EMPTY_ADDR );
            site.setNetmask( EMPTY_ADDR );
            site.setLive( true );
            list.add( site );
        } else {
            site = (ClientSiteNetwork)list.get( 0 );
        }

        return site;
    }

    public void setSiteNetwork( IPaddr network, IPaddr netmask )
    {
        List list = getExportedAddressList();

        ClientSiteNetwork site;
        if ( list.size() < 1 ) {
            site = new ClientSiteNetwork();
            list.add( site );
        } else {
            site = (ClientSiteNetwork)list.get( 0 );
        }

        site.setLive( true );
        site.setNetwork( network );
        site.setNetmask( netmask );
    }

    public void validate() throws ValidateException
    {
        super.validate();

        if (( this.exportedAddressList == null ) || ( this.exportedAddressList.size()  == 0 )) {
            throw new ValidateException( "A site must have at least one exported address" );
        }
    }
}



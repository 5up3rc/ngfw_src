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

package com.untangle.tran.openvpn;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.untangle.mvvm.tran.IPaddr;
import com.untangle.mvvm.tran.Rule;
import com.untangle.mvvm.tran.Validatable;
import com.untangle.mvvm.tran.ValidateException;
import org.hibernate.annotations.Type;

/**
 * the configuration for a vpn client.
 *
 * @author <a href="mailto:rbscott@untangle.com">Robert Scott</a>
 * @version 1.0
 */
@MappedSuperclass
public class VpnClientBase extends Rule implements Validatable
{
    private static final long serialVersionUID = -403968809913481068L;

    private static final Pattern NAME_PATTERN;

    private static final int MAX_NAME_LENGTH = 60;

    private IPaddr address;            // may be null.

    // The address group to pull this client address
    private VpnGroup group;

    private String certificateStatus = "Unknown";

    /* A 96-bit random string to be emailed out.  When the user goes
     * to the page and uses this key, they are allowed to download the
     * configuration for one session */
    private String distributionKey;

    /* Most likely unused for early versions but a nice possiblity for
     * the future */
    private String distributionPassword;

    /* Not stored to the database hibernate */

    /* Set to true to tell the transform to distribute the configuration files
     * for this client */
    private boolean distributeClient = false;

    /* Email addresss where to send the client */
    private String distributionEmail = null;

    // constructors -----------------------------------------------------------

    public VpnClientBase() { }

    // accessors --------------------------------------------------------------

    /**
     * @return The address group that this client belongs to.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="group_id")
    public VpnGroup getGroup()
    {
        return this.group;
    }

    public void setGroup( VpnGroup group )
    {
        this.group = group;
    }

    /**
     * Static address for this client, this cannot be set, it is assigned.
     *
     * @return static address of the machine.
     */
    @Type(type="com.untangle.mvvm.type.IPaddrUserType")
    public IPaddr getAddress()
    {
        return this.address;
    }

    public void setAddress( IPaddr address )
    {
        this.address = address;
    }

    /**
     * @return the key required to download the client.
     */
    @Column(name="dist_key")
    public String getDistributionKey()
    {
        return this.distributionKey;
    }

    public void setDistributionKey( String distributionKey )
    {
        this.distributionKey = distributionKey;
    }

    /**
     * @return the key password to download the client.(unused)
     */
    @Column(name="dist_passwd")
    public String getDistributionPassword()
    {
        return this.distributionPassword;
    }

    public void setDistributionPassword( String distributionPassword )
    {
        this.distributionPassword = distributionPassword;
    }

    /* Indicates whether or not the server should distribute a config for this client */
    @Transient
    public boolean getDistributeClient()
    {
        return this.distributeClient;
    }

    public void setDistributeClient( boolean distributeClient )
    {
        this.distributeClient = distributeClient;
    }

    @Transient
    public boolean getDistributeUsb()
    {
        return ( this.distributionEmail == null );
    }

    public void setDistributeUsb( boolean distributeUsb )
    {
        /* In order to distribute over usb, you have to null out the email address  *
         * in order to distribute over email, you have to just set the distribution *
         * email. */
        if ( distributeUsb ) this.distributionEmail = null;
    }

    @Transient
    public String getDistributionEmail()
    {
        return this.distributionEmail;
    }

    public void setDistributionEmail( String email )
    {
        this.distributionEmail = email;
    }

    /* This is the name that is used as the common name in the certificate */
    @Transient
    public String getInternalName()
    {
        return getName().trim().toLowerCase().replace( " ", "_" );
    }

    public void validate() throws ValidateException
    {
        /* XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX */
        String internalName = getInternalName();
        String name = getName();

        validateName( name );

        if ( this.group == null ) throw new ValidateException( "Group cannot be null" );
    }

    @Transient
    public String getCertificateStatus()
    {
        return this.certificateStatus;
    }

    public void setCertificateStatusUnknown( )
    {
        this.certificateStatus = "unknown";
    }

    public void setCertificateStatusValid( )
    {
        this.certificateStatus = "valid";
    }

    @Transient
    public void setCertificateStatusRevoked( )
    {
        this.certificateStatus = "revoked";
    }

    @Transient
    public boolean isUntanglePlatform()
    {
        /* A single client can never be an edgeguard */
        return false;
    }

    /* If the client is alive and the group it is in is enabled, and it has been\
     * assigned an address. */
    @Transient
    public boolean isEnabled()
    {
        return isLive() && ( this.group != null ) && ( this.group.isLive()) && ( this.address != null );
    }

    static void validateName( String name ) throws ValidateException
    {
        if ( name == null ) throw new ValidateException( "Name cannot be null" );

        if ( name.length() == 0 ) {
            throw new ValidateException( "A client cannot have an empty name" );
        }

        if ( name.length() > MAX_NAME_LENGTH ) {
            throw new ValidateException( "A client's name is limited to " +
                                         MAX_NAME_LENGTH + " characters." );
        }

        if ( !NAME_PATTERN.matcher( name ).matches()) {
            throw new ValidateException( "A client name should only contains numbers, letters, " +
                                         "dashes and periods.  Spaces are not allowed. " + name );
        }
    }

    static
    {
        Pattern p;

        try {
            /* Limited to prevent funny shell hacks(the name goes to the shell) */
            p = Pattern.compile( "^[A-Za-z0-9]([-_.0-9A-Za-z]*[0-9A-Za-z])?$" );
        } catch ( PatternSyntaxException e ) {
            System.err.println( "Unable to intialize the host label pattern" );
            p = null;
        }

        NAME_PATTERN = p;
    }
}

/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.uvm.snmp;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * For those not familiar with SNMP, here is a bit of an explanation
 * of the relationship between properties:
 * <br><br>
 *   <ul>
 *   <li>{@link #setEnabled Enabled} turns the entire SNMP support on/off.  If
 *   this property is false, the remainder of the properties have no
 *   significance.
 *   </li>
 *   <li>
 *   {@link #getCommunityString CommunityString} is like a crappy UID/PWD combination.  This
 *   represents the UID/PWD of someone who can read the contents of the MIB on the UVM
 *   machine.  This user has <b>read only access</b>.  There is no read/write access
 *   possible with our implementation (for safety reasons).
 *   </li>
 *   <li>
 *   The property {@link #getPort Port} property is the port on-which the UVM
 *   will listen for UDP SNMP messages.  The default is 161.
 *   </li>
 *   <li>
 *   {@link #getSysContact SysContact} and {@link #getSysLocation SysLocation} are
 *   related in that they are informational properties, useful only if someone is
 *   monitoring the UVM as one of many devices.  These fields are in no way required,
 *   but may make management easier.
 *   </li>
 *   <li>
 *   {@link #setSendTraps SendTraps} controls if SNMP traps are sent from the UVM.  If this
 *   is true, then {@link #setTrapHost TrapHost}, {@link #setTrapPort TrapPort}, and
 *   {@link #setTrapCommunity TrapCommunity} must be set.
 *   </li>
 * </ul>
 * <br><br>
 * If Snmp {@link #isEnabled is enabled}, the {@link #getCommunityString CommunityString}
 * must be set (everything else can be defaulted).  If {@link #isSendTraps traps are enabled},
 * then {@link #setTrapHost TrapHost} and {@link #setTrapCommunity TrapCommunity} must be
 * set.
 */
@Entity
@Table(name="snmp_settings", schema="settings")
public class SnmpSettings implements Serializable {

    private static final long serialVersionUID =
        7597805105233436527L;

    /**
     * The standard port for "normal" agent messages, as
     * per RFC 1157 sect 4.  The value is 161
     */
    public static final int STANDARD_MSG_PORT = 161;

    /**
     * The standard port for trap messages, as per RFC 1157
     * sect 4.  The value is 162
     */
    public static final int STANDARD_TRAP_PORT = 162;

    private Long m_id;
    private boolean m_enabled;
    private int m_port;
    private String m_communityString;
    private String m_sysContact;
    private String m_sysLocation;
    private boolean m_sendTraps;
    private String m_trapHost;
    private String m_trapCommunity;
    private int m_trapPort;

    @Id
    @Column(name="snmp_settings_id")
    @GeneratedValue
    private Long getId() {
        return m_id;
    }

    private void setId(Long id) {
        m_id = id;
    }

    @Column(nullable=false)
    public boolean isEnabled() {
        return m_enabled;
    }

    public void setEnabled(boolean enabled) {
        m_enabled = enabled;
    }

    @Column(nullable=false)
    public int getPort() {
        return m_port;
    }

    public void setPort(int port) {
        m_port = port;
    }

    /**
     * This cannot be blank ("") or null
     *
     * @return the community String
     */
    @Column(name="com_str")
    public String getCommunityString() {
        return m_communityString;
    }

    public void setCommunityString(String s) {
        m_communityString = s;
    }

    /**
     * @return the contact info
     */
    @Column(name="sys_contact", nullable=false)
    public String getSysContact() {
        return m_sysContact;
    }

    public void setSysContact(String s) {
        m_sysContact = s;
    }

    /**
     * @return the system location
     */
    @Column(name="sys_location")
    public String getSysLocation() {
        return m_sysLocation;
    }

    public void setSysLocation(String s) {
        m_sysLocation = s;
    }

    public void setSendTraps(boolean sendTraps) {
        m_sendTraps = sendTraps;
    }

    @Column(name="send_traps", nullable=false)
    public boolean isSendTraps() {
        return m_sendTraps;
    }

    public void setTrapHost(String trapHost) {
        m_trapHost = trapHost;
    }

    /**
     * @return the trap host
     */
    @Column(name="trap_host")
    public String getTrapHost() {
        return m_trapHost;
    }

    public void setTrapCommunity(String tc) {
        m_trapCommunity = tc;
    }

    /**
     * @return the trap community String
     */
    @Column(name="trap_com")
    public String getTrapCommunity() {
        return m_trapCommunity;
    }


    public void setTrapPort(int tp) {
        m_trapPort = tp;
    }

    @Column(name="trap_port", nullable=false)
    public int getTrapPort() {
        return m_trapPort;
    }

}

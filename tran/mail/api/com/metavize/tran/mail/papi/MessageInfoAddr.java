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
package com.metavize.tran.mail.papi;

import java.io.Serializable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import javax.persistence.Entity;
import org.hibernate.annotations.Type;

/**
 * Log e-mail message info.
 *
 * @author <a href="mailto:cng@metavize.com">C Ng</a>
 * @version 1.0
 */
@Entity
@org.hibernate.annotations.Entity(mutable=false)
@Table(name="tr_mail_message_info_addr", schema="events")
public class MessageInfoAddr implements Serializable
{
    /* constants */

    /* columns */
    private Long id; /* msg_id */
    // private MLHandlerInfo handlerInfo; /* hdl_id */

    private MessageInfo messageInfo;
    private int position;
    private AddressKind kind;
    private String addr;
    private String personal;

    /* constructors */
    public MessageInfoAddr() { }

    public MessageInfoAddr(MessageInfo messageInfo, int position,
                           AddressKind kind, String addr, String personal) {
        this.messageInfo = messageInfo;
        this.position = position;
        this.kind = kind;
        if (addr.length() > MessageInfo.DEFAULT_STRING_SIZE) {
            addr = addr.substring(0, MessageInfo.DEFAULT_STRING_SIZE);
        }
        this.addr = addr;
        if (personal != null
            && personal.length() > MessageInfo.DEFAULT_STRING_SIZE) {
            personal = personal.substring(0, MessageInfo.DEFAULT_STRING_SIZE);
        }
        this.personal = personal;
    }

    // accessors --------------------------------------------------------------

    @Id
    @Column(name="id")
    @GeneratedValue
    protected Long getId()
    {
        return id;
    }

    protected void setId(Long id)
    {
        this.id = id;
    }

    /**
     * The MessageInfo object.
     */
    @ManyToOne(cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @JoinColumn(name="msg_id", nullable=false)
    public MessageInfo getMessageInfo()
    {
        return messageInfo;
    }

    public void setMessageInfo(MessageInfo messageInfo)
    {
        this.messageInfo = messageInfo;
    }

    /**
     * The relative position of the field in the set. XXX yes, its a
     * dirty hack, but this enables us to do INSERT without an UPDATE
     * and also helps the reporting.
     *
     * @return the relative position to other MessageInfoAddr
     */
    @Column(nullable=false)
    public int getPosition()
    {
        return position;
    }

    public void setPosition(int position)
    {
        this.position = position;
    }

    /**
     * The email address, in RFC822 format
     *
     * @return email address.
     */
    @Column(nullable=false)
    public String getAddr()
    {
        return addr;
    }

    public void setAddr(String addr)
    {
        if (addr.length() > MessageInfo.DEFAULT_STRING_SIZE) {
            addr = addr.substring(0, MessageInfo.DEFAULT_STRING_SIZE);
        }
        this.addr = addr;
    }

    /**
     * Get a personal for display purposes.
     *
     * @return personal.
     */
    public String getPersonal()
    {
        return personal;
    }

    public void setPersonal(String personal)
    {
        if (personal != null
            && personal.length() > MessageInfo.DEFAULT_STRING_SIZE) {
            personal = personal.substring(0,MessageInfo.DEFAULT_STRING_SIZE);
        }
        this.personal = personal;
    }

    /**
     * The kind of address (To, CC, etc).
     *
     * @return addressKind.
     */
    @Type(type="com.metavize.tran.mail.papi.AddressKindUserType")
    public AddressKind getKind()
    {
        return kind;
    }

    public void setKind(AddressKind kind)
    {
        this.kind = kind;
    }

    public String toString()
    {
        return kind + ": " + addr;
    }
}

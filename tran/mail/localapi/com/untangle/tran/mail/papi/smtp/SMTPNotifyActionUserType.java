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

package com.untangle.tran.mail.papi.smtp;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.HibernateException;
import org.hibernate.usertype.UserType;
import java.io.Serializable;

public class SMTPNotifyActionUserType implements UserType
{
    private static final int[] SQL_TYPES = { Types.CHAR };

    public int[] sqlTypes() { return SQL_TYPES; }
    public Class returnedClass() { return SMTPNotifyAction.class; }
    public boolean equals(Object x, Object y) { return x == y; }
    public Object deepCopy(Object value) { return value; }
    public boolean isMutable() { return false; }

    public Object nullSafeGet(ResultSet rs, String[] names, Object owner)
        throws HibernateException, SQLException
    {
        String s = rs.getString(names[0]);
        if (true == rs.wasNull() || 1 != s.length()) {
            return null;
        } else {
            return SMTPNotifyAction.getInstance(s.charAt(0));
        }
    }

    public void nullSafeSet(PreparedStatement ps, Object v, int i)
        throws HibernateException, SQLException
    {
        if (null == v) {
            // 0 means no value/null
            ps.setString(i, "0");
        } else {
            SMTPNotifyAction a = (SMTPNotifyAction)v;
            ps.setString(i, Character.toString(a.getKey()));
        }

        return;
    }

    public Object replace(Object original, Object target, Object owner)
    {
        return original;
    }

    public Object assemble(Serializable cached, Object owner)
    {
        return deepCopy(cached);
    }

    public Serializable disassemble(Object value)
    {
        return (Serializable)deepCopy(value);
    }

    public int hashCode(Object o)
    {
        return o.hashCode();
    }
}

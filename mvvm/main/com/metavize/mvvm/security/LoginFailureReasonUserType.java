/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id: ReasonUserType.java 194 2005-04-06 19:13:55Z rbscott $
 */

package com.metavize.mvvm.security;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.HibernateException;
import org.hibernate.usertype.UserType;

public class LoginFailureReasonUserType implements UserType
{
    private static final int[] SQL_TYPES = { Types.CHAR };

    public int[] sqlTypes() { return SQL_TYPES; }
    public Class returnedClass() { return LoginFailureReason.class; }
    public boolean equals(Object x, Object y) { return x == y; }
    public Object deepCopy(Object v) { return v; }
    public boolean isMutable() { return false; }

    public Object nullSafeGet(ResultSet rs, String[] names, Object owner)
        throws HibernateException, SQLException
    {
        String s = rs.getString(names[0]);
        if (rs.wasNull() || 1 != s.length()) {
            return null;
        } else {
            return LoginFailureReason.getInstance(s.charAt(0));
        }
    }

    public void nullSafeSet(PreparedStatement ps, Object v, int i)
        throws HibernateException, SQLException
    {
        if (null == v) {
            // 0 means no value/null
            ps.setString(i, "0");
        } else {
            LoginFailureReason r = (LoginFailureReason)v;
            ps.setString(i, Character.toString(r.getKey()));
        }
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

    public int hashCode(Object x)
    {
        return x.hashCode();
    }
}


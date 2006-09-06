/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.type;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.hibernate.HibernateException;
import org.hibernate.usertype.UserType;

public class UriUserType implements UserType
{
    // How big a varchar() do we get for default String fields.  This
    // should be elsewhere. XXX
    public static final int DEFAULT_STRING_SIZE = 255;

    private static final int[] SQL_TYPES = { Types.VARCHAR };
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    public int[] sqlTypes() { return SQL_TYPES; }
    public Class returnedClass() { return URI.class; }
    public boolean equals(Object x, Object y) { return x.equals(y); }
    public Object deepCopy(Object value) { return value; }
    public boolean isMutable() { return false; }

    public Object nullSafeGet(ResultSet rs, String[] names, Object owner)
        throws HibernateException, SQLException
    {
        String name = rs.getString(names[0]);

        if (rs.wasNull()) {
            return null;
        } else {
            // could have been truncated in middle of escape
            if (1 <= name.length() && '%' == name.charAt(name.length() - 1)) {
                name = name.substring(0, name.length() - 1);
            } else if (2 <= name.length() && '%' == name.charAt(name.length() - 2)) {
                name = name.substring(0, name.length() - 2);
            }
            try {
                return new URI(name);
            } catch (URISyntaxException exn) {
                throw new HibernateException(exn);
            }
        }
    }

    public void nullSafeSet(PreparedStatement ps, Object v, int i)
        throws HibernateException, SQLException
    {
        if (null == v) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            // XXX we don't know the column length (it might not be default)
            // XXX should we break uri's into multiple columns? just path?
            String s = v.toString();

            byte[] ba = new byte[DEFAULT_STRING_SIZE];
            ByteBuffer bb = ByteBuffer.wrap(ba);
            CharsetEncoder ce = UTF_8.newEncoder();
            ce.encode(CharBuffer.wrap(s), bb, true);
            ByteArrayInputStream bais = new ByteArrayInputStream(ba, 0, bb.position());
            ps.setBinaryStream(i, bais, bb.position());
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

    public int hashCode(Object o)
    {
        return o.hashCode();
    }
}

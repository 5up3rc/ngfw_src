/**
 * $Id$
 */
package com.untangle.uvm.util;

/**
 * Wraps a byte array to create a <code>CharSequence</code>.
 *
 */
public class AsciiString implements CharSequence
{
    private final byte[] val;

    public AsciiString(byte[] val)
    {
        this.val = new byte[val.length];
        System.arraycopy(val, 0, this.val, 0, val.length);
    }

    // for subsequence
    private AsciiString(byte[] val, int offset, int length)
    {
        this.val = new byte[length];
        System.arraycopy(val, 0, this.val, offset, length);
    }

    public char charAt(int i)
    {
        return (char)val[i];
    }

    public int length()
    {
        return val.length;
    }

    public CharSequence subSequence(int start, int end)
    {
        return new AsciiString(val, start, end - start);
    }

    public String toString()
    {
        return new String(val);
    }
}

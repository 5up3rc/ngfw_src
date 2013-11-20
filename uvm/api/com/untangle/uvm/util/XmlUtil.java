/**
 * $Id$
 */
package com.untangle.uvm.util;

/**
 * XML utility functions.
 *
 */
public class XmlUtil
{
    public static String escapeXml(String in)
    {
        StringBuilder sb = null;
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (null == sb) {
                switch (c) {
                case '<': case '>': case '\'': case '&': case '"':
                    sb = new StringBuilder(in.length() + 32);
                    sb.append(in, 0, i);
                    break;
                }
            }

            if (null != sb) {
                switch (c) {
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                default:
                    sb.append(c);
                    break;
                }
            }
        }

        return null != sb ? sb.toString() : in;
    }
}

/*
 * $HeadURL: svn://chef/branch/prod/web-ui/work/src/uvm-lib/api/com/untangle/uvm/toolbox/DownloadComplete.java $
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module.  An independent
 * module is a module which is not derived from or based on this library.
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

package com.untangle.uvm.toolbox;

import java.io.Serializable;

public class Application implements Comparable<Application>, Serializable
{
    private final MackageDesc libitem;
    private final MackageDesc trial;
    private final MackageDesc node;

    public Application(MackageDesc libitem, MackageDesc trial, MackageDesc node)
    {
        this.libitem = libitem;
        this.trial = trial;
        this.node = node;
    }

    public MackageDesc getLibItem()
    {
        return libitem;
    }

    public MackageDesc getTrial()
    {
        return trial;
    }

    public MackageDesc getNode()
    {
        return node;
    }

    public int getViewPosition()
    {
        if (libitem != null &&
            libitem.getViewPosition() != MackageDesc.UNKNOWN_POSITION) {
            return libitem.getViewPosition();
        } else if (trial != null
                   && trial.getViewPosition() != MackageDesc.UNKNOWN_POSITION) {
            return trial.getViewPosition();
        } else if (node != null
                   && node.getViewPosition() != MackageDesc.UNKNOWN_POSITION) {
            return node.getViewPosition();
        } else {
            return -1;
        }
    }

    // Comparable methods ------------------------------------------------------

    public int compareTo(Application a)
    {
        return new Integer(getViewPosition()).compareTo(a.getViewPosition());
    }

    // Object methods ----------------------------------------------------------

    @Override
    public String toString()
    {
        return "(Application libitem: " + libitem + " trial: " + trial
            + " node: " + node + ")";
    }
}
/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc. 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.node.mail.impl.quarantine.store;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Information about a given Inbox, as maintained
 * outside of the Inbox itself (i.e. for
 * lookup and reporting purposes).
 * <br><br>
 * Be careful.  Changes to the size/count properties
 * should <b>only</b> be made by the StoreSummary
 * which holds it.
 *
 *
 * Assumes all addresses have been lower-cased
 */
final class InboxSummary {

    private RelativeFileName m_inboxDir;
    private final AtomicLong m_totalSz;
    private final AtomicInteger m_totalMails;

    InboxSummary() {
        this(null, 0, 0);
    }

    InboxSummary(RelativeFileName inbox) {
        this(inbox, 0, 0);
    }

    InboxSummary(RelativeFileName inbox,
                 long totalSz,
                 int totalMails) {

        m_totalSz = new AtomicLong(totalSz);
        m_totalMails = new AtomicInteger(totalMails);
        setDir(inbox);
    }

    /**
     * Get the relative file for this user's inbox data
     */
    RelativeFileName getDir() {
        return m_inboxDir;
    }
    void setDir(RelativeFileName dir) {
        if(dir == null) {
            m_inboxDir = null;
        }
        if(dir instanceof RelativeFile) {
            //Don't cache File objects
            dir = new RelativeFileName(dir.relativePath);
        }
        m_inboxDir = dir;
    }

    /**
     * Updates the total size, based on
     * a recalculated value
     *
     * @param newValue the new value
     * @return the <b>old</b> value.
     */
    long updateTotalSz(long newValue) {
        return m_totalSz.getAndSet(newValue);
    }

    void incrementTotalSz(long toAdd) {
        m_totalSz.addAndGet(toAdd);
    }
    void decrementTotalSz(long toSubtract) {
        m_totalSz.addAndGet(-1*toSubtract);
    }

    /**
     * Get the total size (sum of lengths of all files)
     * for this inbox
     */
    long getTotalSz() {
        return m_totalSz.get();
    }

    /**
     * Get the total number of mails in this inbox.
     */
    int getTotalMails() {
        return m_totalMails.get();
    }
    /**
     * Updates the total mails, based on
     * a recalculated value
     *
     * @param newValue the new value
     * @return the <b>old</b> value.
     */
    int updateTotalMails(int newValue) {
        return m_totalMails.getAndSet(newValue);
    }

    void incrementTotalMails(int toAdd) {
        m_totalMails.addAndGet(toAdd);
    }
    void decrementTotalMails(int toSubtract) {
        m_totalMails.addAndGet(-1*toSubtract);
    }

}

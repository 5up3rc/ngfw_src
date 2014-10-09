/**
 * $Id$
 */
package com.untangle.uvm;

import java.util.TimeZone;

/**
 * Describe interface <code>AdminManager</code> here.
 */
public interface AdminManager
{
    AdminSettings getSettings();

    void setSettings(AdminSettings settings);

    /**
     * Returns the time zone that the UVM is currently set to
     */
    TimeZone getTimeZone();

    /**
     * Sets the time zone that the UVM is in.
     */
    void setTimeZone(TimeZone timezone);

    /**
     * Returns the current time that the UVM is set to
     * Returned as a string to avoid any browser interpretation (with its own timezone)
     */
    String getDate();

    String getModificationState();

    String getRebootCount();

    String getFullVersionAndRevision();

    String getKernelVersion();
    
    String getTimeZones();
    
    Integer getTimeZoneOffset();
    
}

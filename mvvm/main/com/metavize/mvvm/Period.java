/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id: Period.java,v 1.7 2005/02/22 23:50:29 amread Exp $
 */

package com.metavize.mvvm;

import java.io.Serializable;
import java.util.Calendar;

/**
 * Period for tasks at a particular time on a set of days.
 *
 * @author <a href="mailto:amread@metavize.com">Aaron Read</a>
 * @version 1.0
 * @hibernate.class
 * table="PERIOD"
 */
public class Period implements Serializable
{
    private static final long serialVersionUID = 2173836337317097459L;

    private Long id;

    private int hour;
    private int minute;

    private boolean sunday = false;
    private boolean monday = false;
    private boolean tuesday = false;
    private boolean wednesday = false;
    private boolean thursday = false;
    private boolean friday = false;
    private boolean saturday = false;

    // constructors -----------------------------------------------------------

    /**
     * Hibernate constructor.
     */
    public Period() { }

    public Period(int hour, int minute, boolean allDays)
    {
        this.hour = hour;
        this.minute = minute;

        if (allDays) {
            monday = tuesday = wednesday = thursday = friday = saturday
                = sunday = true;
        }
    }

    // business methods -------------------------------------------------------

    /**
     * Get the next time for this period.
     *
     * @return next time, null if never.
     */
    public Calendar nextTime()
    {
        Calendar c = Calendar.getInstance();

        if (c.get(Calendar.HOUR_OF_DAY) > hour) {
            c.add(Calendar.DAY_OF_WEEK, 1);
        }

        if (c.get(Calendar.HOUR_OF_DAY) == hour && c.get(Calendar.MINUTE) >= minute) {
            c.add(Calendar.DAY_OF_WEEK, 1);
        }

        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);

        for (int i = 0; i < 7; i++) {
            if (includesDay(c.get(Calendar.DAY_OF_WEEK))) {
                return c;
            } else {
                c.add(Calendar.DAY_OF_WEEK, 1);
            }
        }

        return null;
    }

    /**
     * True if it includes the day, as defined by Calendar.
     *
     * @param day as defined by Calendar statics.
     * @return true if the day is set.
     */
    public boolean includesDay(int day)
    {
        switch (day) {
        case Calendar.SUNDAY:
            return sunday;
        case Calendar.MONDAY:
            return monday;
        case Calendar.TUESDAY:
            return tuesday;
        case Calendar.WEDNESDAY:
            return wednesday;
        case Calendar.THURSDAY:
            return thursday;
        case Calendar.FRIDAY:
            return friday;
        case Calendar.SATURDAY:
            return saturday;
        default:
            return false;
        }
    }

    // accessors --------------------------------------------------------------

    /**
     * @hibernate.id
     * column="PERIOD_ID"
     * generator-class="native"
     */
    private Long getId()
    {
        return id;
    }

    private void setId(Long id)
    {
        this.id = id;
    }

    /**
     * Hour of update.
     *
     * @return the hour.
     * @hibernate.property
     * column="HOUR"
     * not-null="true"
     */
    public int getHour()
    {
        return hour;
    }

    public void setHour(int hour)
    {
        this.hour = hour;
    }

    /**
     * Minute of update.
     *
     * @return the minute.
     * @hibernate.property
     * column="MINUTE"
     * not-null="true"
     */
    public int getMinute()
    {
        return minute;
    }

    public void setMinute(int minute)
    {
        this.minute = minute;
    }

    /**
     * Happen on Sunday.
     *
     * @return true if it happens on Sunday.
     * @hibernate.property
     * column="SUNDAY"
     */
    public boolean getSunday()
    {
        return sunday;
    }

    public void setSunday(boolean sunday)
    {
        this.sunday = sunday;
    }

    /**
     * Happen on Monday.
     *
     * @return true if it happens on Monday.
     * @hibernate.property
     * column="MONDAY"
     */
    public boolean getMonday()
    {
        return monday;
    }

    public void setMonday(boolean monday)
    {
        this.monday = monday;
    }

    /**
     * Happen on Tuesday.
     *
     * @return true if it happens on Tuesday.
     * @hibernate.property
     * column="TUESDAY"
     */
    public boolean getTuesday()
    {
        return tuesday;
    }

    public void setTuesday(boolean tuesday)
    {
        this.tuesday = tuesday;
    }

    /**
     * Happen on Wednesday.
     *
     * @return true if it happens on Wednesday.
     * @hibernate.property
     * column="WEDNESDAY"
     */
    public boolean getWednesday()
    {
        return wednesday;
    }

    public void setWednesday(boolean wednesday)
    {
        this.wednesday = wednesday;
    }

    /**
     * Happen on Thursday.
     *
     * @return true if it happens on Thursday.
     * @hibernate.property
     * column="THURSDAY"
     */
    public boolean getThursday()
    {
        return thursday;
    }

    public void setThursday(boolean thursday)
    {
        this.thursday = thursday;
    }

    /**
     * Happen on Friday.
     *
     * @return true if it happens on Friday.
     * @hibernate.property
     * column="FRIDAY"
     */
    public boolean getFriday()
    {
        return friday;
    }

    public void setFriday(boolean friday)
    {
        this.friday = friday;
    }

    /**
     * Happen on Saturday.
     *
     * @return true if it happens on Saturday.
     * @hibernate.property
     * column="SATURDAY"
     */
    public boolean getSaturday()
    {
        return saturday;
    }

    public void setSaturday(boolean saturday)
    {
        this.saturday = saturday;
    }
}

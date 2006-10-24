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

package com.metavize.tran.reporting;

import java.io.Serializable;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.IndexColumn;

/**
 * Schedule for the Reporting Transform.
 *
 * @author <a href="mailto:amread@metavize.com">Aaron Read</a>
 * @version 1.0
 */
@Entity
@Table(name="tr_reporting_sched", schema="settings")
public class Schedule implements Serializable
{
    private static final long serialVersionUID = 2064742840204258979L;

    // day of week constants
    public static final int NONE = -1; // only used with monthlyNDayOfWk
    public static final int SUNDAY = Calendar.SUNDAY;
    public static final int MONDAY = Calendar.MONDAY;
    public static final int TUESDAY = Calendar.TUESDAY;
    public static final int WEDNESDAY = Calendar.WEDNESDAY;
    public static final int THURSDAY = Calendar.THURSDAY;
    public static final int FRIDAY = Calendar.FRIDAY;
    public static final int SATURDAY = Calendar.SATURDAY;

    private Long id;
    private boolean daily; // false = no, true = yes
    private List<WeeklyScheduleRule> weeklySched;
    // monthlyNDaily, monthlyNDayOfWk, and monthlyNFirst are mutually exclusive
    // - enabling one will disable the other two
    private boolean monthlyNDaily; // false = no, true = yes
    private int monthlyNDayOfWk; // NONE, SUNDAY, ... SATURDAY
    private boolean monthlyNFirst; // false = no, true = yes

    public Schedule()
    {
        // create daily reports every day
        daily = true;

        // create weekly reports every day of week
        weeklySched = new LinkedList<WeeklyScheduleRule>();
        WeeklyScheduleRule weeklySR = new WeeklyScheduleRule(SUNDAY);
        weeklySched.add(weeklySR);
        weeklySR = new WeeklyScheduleRule(MONDAY);
        weeklySched.add(weeklySR);
        weeklySR = new WeeklyScheduleRule(TUESDAY);
        weeklySched.add(weeklySR);
        weeklySR = new WeeklyScheduleRule(WEDNESDAY);
        weeklySched.add(weeklySR);
        weeklySR = new WeeklyScheduleRule(THURSDAY);
        weeklySched.add(weeklySR);
        weeklySR = new WeeklyScheduleRule(FRIDAY);
        weeklySched.add(weeklySR);
        weeklySR = new WeeklyScheduleRule(SATURDAY);
        weeklySched.add(weeklySR);

        // create monthly reports only on 1st of month
        monthlyNDaily = false;
        monthlyNDayOfWk = NONE;
        monthlyNFirst = true;
    }

    @Id
    @Column(name="id")
    @GeneratedValue
    private Long getId()
    {
        return id;
    }

    private void setId(Long id)
    {
        this.id = id;
    }

    /**
     * daily schedule for daily report
     *
     * @return daily schedule
     */
    @Column(nullable=false)
    public boolean getDaily()
    {
        return daily;
    }

    public void setDaily(boolean daily)
    {
        this.daily = daily;
        return;
    }

    /**
     * weekly schedule for weekly report (list of day(s) of week)
     * (if day of week is not present in list,
     *  weekly report is not created on that day)
     * - valid days:
     *   SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY
     *
     * @return the weekly schedule list for reports
     */
    @OneToMany(fetch=FetchType.EAGER)
    @Cascade({ org.hibernate.annotations.CascadeType.ALL,
            org.hibernate.annotations.CascadeType.DELETE_ORPHAN })
    @JoinTable(name="tr_reporting_wk_sched",
               joinColumns=@JoinColumn(name="setting_id"),
               inverseJoinColumns=@JoinColumn(name="rule_id"))
    @IndexColumn(name="position")
    public List<WeeklyScheduleRule> getWeeklySched()
    {
        return weeklySched;
    }

    public void setWeeklySched(List<WeeklyScheduleRule> weeklySched)
    {
        this.weeklySched = weeklySched;
    }

    /* schedule for monthly report is one of the following options */

    /**
     * daily schedule for monthly report
     * (if every day, then not weekly and not on first of month)
     *
     * @return monthly_n_daily schedule
     */
    @Column(name="monthly_n_daily", nullable=false)
    public boolean getMonthlyNDaily()
    {
        return monthlyNDaily;
    }

    public void setMonthlyNDaily(boolean monthlyNDaily)
    {
        this.monthlyNDaily = monthlyNDaily;
        if (true == monthlyNDaily) {
            /* deactivate other monthly schedule options */
            monthlyNDayOfWk = NONE;
            monthlyNFirst = false;
        }
        return;
    }

    /**
     * weekly schedule for monthly report
     * (if once a week, then not daily and not on first of month)
     * - valid days:
     *   NONE,
     *   SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY
     *
     * @return monthly_n_daily schedule
     */
    @Column(name="monthly_n_day_of_wk", nullable=false)
    public int getMonthlyNDayOfWk()
    {
        return monthlyNDayOfWk;
    }

    public void setMonthlyNDayOfWk(int monthlyNDayOfWk)
    {
        this.monthlyNDayOfWk = monthlyNDayOfWk;
        if (NONE != monthlyNDayOfWk) {
            /* deactivate other monthly schedule options */
            monthlyNDaily = false;
            monthlyNFirst = false;
        }
        return;
    }

    /**
     * monthly schedule for monthly report
     * (if first of month, then not daily and not weekly)
     *
     * @return monthly_n_daily schedule
     */
    @Column(name="monthly_n_first", nullable=false)
    public boolean getMonthlyNFirst()
    {
        return monthlyNFirst;
    }

    public void setMonthlyNFirst(boolean monthlyNFirst)
    {
        this.monthlyNFirst = monthlyNFirst;
        if (true == monthlyNFirst) {
            /* deactivate other monthly schedule options */
            monthlyNDaily = false;
            monthlyNDayOfWk = NONE;
        }
        return;
    }
}

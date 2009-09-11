# $HeadURL: svn://chef/work/src/buildtools/rake-util.rb $
# Copyright (c) 2003-2009 Untangle, Inc.
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License, version 2,
# as published by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful, but
# AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
# NONINFRINGEMENT.  See the GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.

import gettext
import logging
import mx
import reports.colors as colors
import reports.i18n_helper
import reports.engine
import reports.sql_helper as sql_helper

from psycopg import DateFromMx
from psycopg import QuotedString
from reports import Chart
from reports import ColumnDesc
from reports import DATE_FORMATTER
from reports import DetailSection
from reports import Graph
from reports import KeyStatistic
from reports import PIE_CHART
from reports import Report
from reports import STACKED_BAR_CHART
from reports import SummarySection
from reports import TIME_OF_DAY_FORMATTER
from reports import TIME_SERIES_CHART
from reports.engine import Column
from reports.engine import EMAIL_DRILLDOWN
from reports.engine import Node
from reports.engine import TOP_LEVEL
from sql_helper import print_timing

_ = reports.i18n_helper.get_translation('untangle-base-spam').lgettext

class SpamBaseNode(Node):
    def __init__(self, node_name, title, short_name, vendor_name, spam_label,
                 ham_label, hourly_spam_rate_title, daily_spam_rate_title,
                 top_spammed_title):
        Node.__init__(self, node_name)

        self.__title = title
        self.__short_name = short_name
        self.__vendor_name = vendor_name
        self.__spam_label = spam_label
        self.__ham_label = ham_label
        self.__hourly_spam_rate_title = hourly_spam_rate_title
        self.__daily_spam_rate_title = daily_spam_rate_title
        self.__top_spammed_title = top_spammed_title

    def parents(self):
        return ['untangle-casing-mail']

    @print_timing
    def setup(self, start_date, end_date):
        self.__update_n_mail_events('events.n_spam_evt', 'reports.n_mail_addrs',
                                    'pop/imap', start_date, end_date)
        self.__update_n_mail_events('events.n_spam_evt_smtp',
                                    'reports.n_mail_addrs', 'smtp',
                                    start_date, end_date)
        self.__update_n_mail_events('events.n_spam_evt', 'reports.n_mail_msgs',
                                    'pop/imap', start_date, end_date)
        self.__update_n_mail_events('events.n_spam_evt_smtp',
                                    'reports.n_mail_msgs', 'smtp', start_date,
                                    end_date)

        column = Column('%s_spam_msgs' % self.__short_name, 'integer',
                        "count(CASE WHEN %s_is_spam THEN 1 ELSE null END)" \
                            % self.__short_name)

        ft = reports.engine.get_fact_table('reports.n_mail_msg_totals')
        ft.measures.append(column)

        ft = reports.engine.get_fact_table('reports.n_mail_addr_totals')
        ft.measures.append(column)

    def get_toc_membership(self):
        return [TOP_LEVEL]

    def get_report(self):
        sections = []

        s = SummarySection('summary', _('Summary Report'),
                           [TotalEmail(self.__short_name, self.__vendor_name,
                                       self.__spam_label, self.__ham_label),
                            HourlySpamRate(self.__short_name,
                                           self.__vendor_name,
                                           self.__spam_label,
                                           self.__ham_label,
                                           self.__hourly_spam_rate_title),
                            DailySpamRate(self.__short_name,
                                          self.__vendor_name,
                                          self.__spam_label,
                                          self.__ham_label,
                                          self.__daily_spam_rate_title),
                            TopSpammedUsers(self.__short_name,
                                            self.__vendor_name,
                                            self.__spam_label,
                                            self.__ham_label,
                                            self.__top_spammed_title)])

        sections.append(s)

        sections.append(SpamDetail(self.__title, self.__short_name))

        return Report(self.name, sections)

    def events_cleanup(self, cutoff):
        pass

    def reports_cleanup(self, cutoff):
        pass

    @print_timing
    def __update_n_mail_events(self, src_table, target_table, protocol,
                               start_date, end_date):
        try:
            sql_helper.run_sql("ALTER TABLE %s ADD COLUMN %s_score real" \
                                   % (target_table, self.__short_name))
        except: pass

        try:
            sql_helper.run_sql("ALTER TABLE %s ADD COLUMN %s_is_spam boolean" \
                                   % (target_table, self.__short_name))
        except: pass

        try:
            sql_helper.run_sql("ALTER TABLE %s ADD COLUMN %s_action char(1)" \
                                   % (target_table, self.__short_name))
        except: pass

        sd = DateFromMx(sql_helper.get_update_info('%s[%s.%s]' % (target_table,
                                                                  self.name,
                                                                  protocol),
                                                   start_date))
        ed = DateFromMx(end_date)

        conn = sql_helper.get_connection()
        try:
            sql_helper.run_sql("""\
UPDATE %s
SET %s_score = score,
  %s_is_spam = is_spam,
  %s_action = action
FROM %s
WHERE %s.time_stamp >= %%s
  AND %s.time_stamp < %%s
  AND %s.vendor_name = %%s
  AND %s.msg_id = %s.msg_id""" % (target_table, self.__short_name,
                                  self.__short_name, self.__short_name,
                                  src_table, target_table,
                                  target_table, src_table, target_table,
                                  src_table),
                               (sd, ed, self.__vendor_name), connection=conn,
                               auto_commit=False)

            sql_helper.set_update_info('%s[%s.%s]' % (target_table, self.name,
                                                      protocol),
                                       ed, connection=conn, auto_commit=False)

            conn.commit()
        except Exception, e:
            conn.rollback()
            raise e

class TotalEmail(Graph):
    def __init__(self, short_name, vendor_name, spam_label, ham_label):
        Graph.__init__(self, 'total-email', _('Total Email'))

        self.__short_name = short_name
        self.__vendor_name = vendor_name
        self.__spam_label = spam_label
        self.__ham_label = ham_label

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if host or user:
            return None

        ed = DateFromMx(end_date)
        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        if email:
            query = """\
SELECT coalesce(sum(msgs), 0)::int,
       coalesce(sum(%s_spam_msgs), 0)::int
FROM reports.n_mail_addr_totals
WHERE addr_kind = 'T' AND addr = %%s AND trunc_time >= %%s AND trunc_time < %%s
""" % self.__short_name
        else:
            query = """\
SELECT coalesce(sum(msgs), 0)::int,
       coalesce(sum(%s_spam_msgs), 0)::int
FROM reports.n_mail_msg_totals
WHERE trunc_time >= %%s AND trunc_time < %%s""" % self.__short_name

        conn = sql_helper.get_connection()
        try:
            lks = []

            curs = conn.cursor()

            if email:
                curs.execute(query, (email, one_week, ed))
            else:
                curs.execute(query, (one_week, ed))
            r = curs.fetchone()

            total = r[0]
            spam = r[1]
            ham = total - spam

            ks = KeyStatistic(_('total'), total, _('messages'))
            lks.append(ks)
            ks = KeyStatistic(self.__spam_label, spam, _('messages'))
            lks.append(ks)
            ks = KeyStatistic(self.__ham_label, ham, _('messages'))
            lks.append(ks)

            plot = Chart(type=PIE_CHART, title=self.title)

            plot.add_pie_dataset({ self.__spam_label: spam,
                                   self.__ham_label: ham },
                                 colors={ self.__ham_label: colors.goodness,
                                          self.__spam_label: colors.badness})
        finally:
            conn.commit()

        return (lks, plot)

class HourlySpamRate(Graph):
    def __init__(self, short_name, vendor_name, spam_label, ham_label, title):
        Graph.__init__(self, 'hourly-%s-rate' % (short_name,), title)

        self.__short_name = short_name
        self.__vendor_name = vendor_name
        self.__spam_label = spam_label
        self.__ham_label = ham_label

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if host or user:
            return None

        ed = DateFromMx(end_date)
        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        conn = sql_helper.get_connection()
        try:
            lks = []

            curs = conn.cursor()

            if email:
                ks_query = """\
SELECT COALESCE(sum(msgs), 0)::float / (%%s * 24) AS email_rate,
       COALESCE(sum(%s_spam_msgs), 0)::float / (%%s * 24) AS spam_rate
FROM reports.n_mail_addr_totals
WHERE addr_kind = 'T' AND addr = %%s AND trunc_time >= %%s AND trunc_time < %%s
""" % (self.__short_name,)
            else:
                ks_query = """\
SELECT COALESCE(sum(msgs), 0)::float / (%%s * 24) AS email_rate,
       COALESCE(sum(%s_spam_msgs), 0)::float / (%%s * 24) AS spam_rate
FROM reports.n_mail_msg_totals
WHERE trunc_time >= %%s AND trunc_time < %%s
""" % (self.__short_name,)

            if email:
                curs.execute(ks_query, (report_days, report_days, email,
                                        one_week, ed))
            else:
                curs.execute(ks_query, (report_days, report_days, one_week, ed))
            r = curs.fetchone()

            email_rate = r[0]
            spam_rate = r[1]
            ham_rate = email_rate - spam_rate

            ks = KeyStatistic(_('mail rate'), email_rate, _('messages/hour'))
            lks.append(ks)
            ks = KeyStatistic(_('%s rate') % self.__spam_label, spam_rate,
                              _('messages/hour'))
            lks.append(ks)
            ks = KeyStatistic(_('%s rate') % self.__ham_label, ham_rate,
                              _('messages/hour'))
            lks.append(ks)

            curs = conn.cursor()

            if email:
                plot_query = """\
SELECT (date_part('hour', trunc_time) || ':00')::time AS time,
       COALESCE(sum(msgs), 0)::float / %%d AS msgs,
       COALESCE(sum(%s_spam_msgs), 0)::float / %%d AS %s_spam_msgs
FROM reports.n_mail_addr_totals
WHERE addr_kind = 'T' AND addr = %%s AND trunc_time >= %%s AND trunc_time < %%s
GROUP BY time
ORDER BY time asc""" % (2 * (self.__short_name,))
            else:
                plot_query = """\
SELECT (date_part('hour', trunc_time) || ':00')::time AS time,
       COALESCE(sum(msgs), 0)::float / %%d AS msgs,
       COALESCE(sum(%s_spam_msgs), 0)::float / %%d AS %s_spam_msgs
FROM reports.n_mail_msg_totals
WHERE trunc_time >= %%s AND trunc_time < %%s
GROUP BY time
ORDER BY time asc""" % (2 * (self.__short_name,))

            dates = []
            ham = []
            spam = []

            curs = conn.cursor()

            if email:
                curs.execute(plot_query, (report_days, report_days, email,
                                          one_week, ed))
            else:
                curs.execute(plot_query, (report_days, report_days, one_week,
                                          ed))

            for r in curs.fetchall():
                dates.append(r[0].seconds)
                m = r[1]
                s = r[2]
                h = m - s
                spam.append(s)
                ham.append(h)
        finally:
            conn.commit()

        plot = Chart(type=TIME_SERIES_CHART,
                     title=self.title,
                     xlabel=_('Hour of Day'),
                     ylabel=_('Emails per Hour'),
                     major_formatter=TIME_OF_DAY_FORMATTER,
                     required_points=sql_helper.HOURLY_REQUIRED_TIME_POINTS)

        plot.add_dataset(dates, spam, gettext.gettext(self.__spam_label),
                         color=colors.badness)
        plot.add_dataset(dates, ham, gettext.gettext(self.__ham_label),
                         color=colors.goodness)

        return (lks, plot)

class DailySpamRate(Graph):
    def __init__(self, short_name, vendor_name, spam_label, ham_label, title):
        Graph.__init__(self, 'daily-%s-rate' % (short_name,), title)

        self.__short_name = short_name
        self.__vendor_name = vendor_name
        self.__spam_label = spam_label
        self.__ham_label = ham_label

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if host or user:
            return None

        ed = DateFromMx(end_date)
        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))
        start_date = end_date - mx.DateTime.DateTimeDelta(report_days)
        one_week = DateFromMx(start_date)

        conn = sql_helper.get_connection()
        try:
            lks = []

            if email:
                ks_query = """\
SELECT COALESCE(sum(msgs), 0)::float / %%s AS email_rate,
       COALESCE(sum(%s_spam_msgs), 0)::float / %%s AS spam_rate
FROM reports.n_mail_addr_totals
WHERE addr_kind = 'T' AND addr = %%s AND trunc_time >= %%s AND trunc_time < %%s
""" % (self.__short_name,)
            else:
                ks_query = """\
SELECT COALESCE(sum(msgs), 0)::float / %%s AS email_rate,
       COALESCE(sum(%s_spam_msgs), 0)::float / %%s AS spam_rate
FROM reports.n_mail_msg_totals
WHERE trunc_time >= %%s AND trunc_time < %%s
""" % (self.__short_name,)

            curs = conn.cursor()
            if email:
                curs.execute(ks_query, (report_days, report_days, email,
                                        one_week, ed))
            else:
                curs.execute(ks_query, (report_days, report_days, one_week, ed))

            r = curs.fetchone()

            email_rate = r[0]
            spam_rate = r[1]
            ham_rate = email_rate - spam_rate

            ks = KeyStatistic(_('mail rate'), email_rate, _('messages/day'))
            lks.append(ks)
            ks = KeyStatistic(_('%s rate') % self.__spam_label, spam_rate,
                              _('messages/day'))
            lks.append(ks)
            ks = KeyStatistic(_('%s rate') % self.__ham_label, ham_rate,
                              _('messages/day'))
            lks.append(ks)

            curs = conn.cursor()

            if email:
                plot_query = """\
SELECT date_trunc('day', trunc_time) AS day,
       COALESCE(sum(msgs), 0)::float AS msgs,
       COALESCE(sum(%s_spam_msgs), 0)::float AS %s_spam_msgs
FROM reports.n_mail_addr_totals
WHERE addr_kind = 'T' AND addr = %%s AND trunc_time >= %%s AND trunc_time < %%s
GROUP BY day
ORDER BY day asc""" % (2 * (self.__short_name,))
            else:
                plot_query = """\
SELECT date_trunc('day', trunc_time) AS day,
       COALESCE(sum(msgs), 0)::float AS msgs,
       COALESCE(sum(%s_spam_msgs), 0)::float AS %s_spam_msgs
FROM reports.n_mail_msg_totals
WHERE trunc_time >= %%s AND trunc_time < %%s
GROUP BY day
ORDER BY day asc""" % (2 * (self.__short_name,))

            dates = []
            ham = []
            spam = []

            curs = conn.cursor()

            if email:
                curs.execute(plot_query, (email, one_week, ed))
            else:
                curs.execute(plot_query, (one_week, ed))

            for r in curs.fetchall():
                dates.append(r[0])
                m = r[1]
                s = r[2]
                h = m - s
                spam.append(s)
                ham.append(h)
        finally:
            conn.commit()

        rp = sql_helper.get_required_points(start_date, end_date,
                                            mx.DateTime.DateTimeDelta(1))

        plot = Chart(type=STACKED_BAR_CHART,
                     title=self.title,
                     xlabel=_('Date'),
                     ylabel=_('Emails per Day'),
                     major_formatter=DATE_FORMATTER,
                     required_points=rp)

        plot.add_dataset(dates, spam, gettext.gettext(self.__spam_label),
                         color=colors.badness)
        plot.add_dataset(dates, ham, gettext.gettext(self.__ham_label),
                         color=colors.goodness)

        return (lks, plot)

class TopSpammedUsers(Graph):
    def __init__(self, short_name, vendor_name, spam_label, ham_label, title):
        Graph.__init__(self, 'top-ten-%s-victims' % (short_name,), title)

        self.__short_name = short_name
        self.__vendor_name = vendor_name
        self.__spam_label = spam_label
        self.__ham_label = ham_label

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if host or user or email:
            return None

        ed = DateFromMx(end_date)
        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        conn = sql_helper.get_connection()
        try:
            query = """\
SELECT addr, COALESCE(sum(%s_spam_msgs), 0)::int AS spam_msgs
FROM reports.n_mail_addr_totals
WHERE addr_kind = 'T' AND trunc_time >= %%s AND trunc_time < %%s
GROUP BY addr
ORDER BY spam_msgs desc
LIMIT 10""" % (self.__short_name,)

            curs = conn.cursor()
            curs.execute(query, (one_week, ed))

            lks = []
            pds = {}

            counted_spam = 0

            for r in curs.fetchall():
                addr = r[0]
                num = r[1]
                counted_spam += num

                lks.append(KeyStatistic(addr, num, self.__spam_label, 'EmailLink'))
                pds[addr] = num
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title)

        plot.add_pie_dataset(pds)

        return (lks, plot)

class SpamDetail(DetailSection):

    def __init__(self, title, short_name):
        self.__title = title.split()[0].lower() # keep 1st word only
        self.__short_name = short_name
        DetailSection.__init__(self, '%s-events' % (self.__title,),
                               _('%s Events' % (self.__title.capitalize(),)))

    def get_columns(self, host=None, user=None, email=None):
        if host or user:
            return None

        rv = [ColumnDesc('time_stamp', _('Time'), 'Date')]

        if email:
            rv.append(ColumnDesc('hname', _('Client'), 'String'))
        else:
            rv.append(ColumnDesc('hname', _('Client'), 'EmailLink'))

        rv += [ColumnDesc('%s_score' % (self.__short_name,), _('Score')),
               ColumnDesc('subject', _('Subject')),
               ColumnDesc('s_server_addr', _('Source IP')),
               ColumnDesc('case', _('Action')),
               ColumnDesc('addr', _('Msg receiver'))]

        return rv

    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if host or user:
            return None

        sql = """\
SELECT time_stamp, hname, %s_score, subject, host(s_server_addr),
       CASE %s_action WHEN 'P' THEN '%s'
                      WHEN 'B' THEN '%s'
                      WHEN 'M' THEN '%s'
                      WHEN 'Q' THEN '%s'
                      WHEN 'S' THEN '%s'
                      WHEN 'Z' THEN '%s'
                      END,
       addr
FROM reports.n_mail_addrs
WHERE time_stamp >= %s AND time_stamp < %s
      AND %s_is_spam AND addr_kind = 'T'
""" % (self.__short_name, self.__short_name,
       _('passed'),
       _('blocked'),
       _('marked'),
       _('quarantined'),
       _('safelisted'),
       _('oversize'),
       DateFromMx(start_date),
       DateFromMx(end_date), self.__short_name)

        if host:
            sql += " AND hname = %s" % QuotedString(host)

        return sql + "ORDER BY time_stamp"

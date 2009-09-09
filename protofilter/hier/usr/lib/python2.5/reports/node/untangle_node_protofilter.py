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
import reports.i18n_helper
import reports.colors as colors
import reports.engine
import reports.sql_helper as sql_helper
import sys

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
from reports.engine import Column
from reports.engine import FactTable
from reports.engine import HOST_DRILLDOWN
from reports.engine import Node
from reports.engine import TOP_LEVEL
from reports.engine import USER_DRILLDOWN
from sql_helper import print_timing

_ = reports.i18n_helper.get_translation('untangle-node-protofilter').lgettext

class Protofilter(Node):
    def __init__(self):
        Node.__init__(self, 'untangle-node-protofilter')

    def setup(self, start_date, end_date):
        self.__update_sessions(start_date, end_date)

        ft = reports.engine.get_fact_table('reports.session_totals')

        ft.measures.append(Column('pf_blocks', 'integer',
                                  "count(CASE WHEN NOT pf_blocked ISNULL THEN 1 ELSE null END)"))

        ft.dimensions.append(Column('pf_protocol', 'text'))

    def get_toc_membership(self):
        return [TOP_LEVEL, HOST_DRILLDOWN, USER_DRILLDOWN]

    def get_report(self):
        sections = []
        s = reports.SummarySection('summary', _('Summary Report'),
                                   [DailyUsage(),
                                    TopTenBlockedProtocolsByHits(),
                                    TopTenDetectedProtocolsByHits(),
                                    TopTenBlockedHostsByHits(),
                                    TopTenLoggedHostsByHits(),
                                    TopTenBlockedUsersByHits(),
                                    TopTenLoggedUsersByHits(),
                                    ])
        sections.append(s)

        sections.append(ProtofilterDetail())

        return reports.Report(self.name, sections)

    def events_cleanup(self, cutoff):
        try:
            sql_helper.run_sql("""\
DELETE FROM events.n_protofilter_evt WHERE time_stamp < %s""", (cutoff,))
        except: pass

    def reports_cleanup(self, cutoff):
        pass

    @print_timing
    def __update_sessions(self, start_date, end_date):
        try:
            sql_helper.run_sql("""
ALTER TABLE reports.sessions ADD COLUMN pf_protocol text""")
        except: pass

        try:
            sql_helper.run_sql("""\
ALTER TABLE reports.sessions ADD COLUMN pf_blocked boolean""")
        except: pass

        sd = DateFromMx(sql_helper.get_update_info('sessions[protofilter]',
                                                   start_date))
        ed = DateFromMx(end_date)

        conn = sql_helper.get_connection()
        try:
            sql_helper.run_sql("""\
UPDATE reports.sessions
SET pf_protocol = protocol,
  pf_blocked = blocked
FROM events.n_protofilter_evt
WHERE reports.sessions.time_stamp >= %s
  AND reports.sessions.time_stamp < %s
  AND reports.sessions.pl_endp_id = events.n_protofilter_evt.pl_endp_id""",
                               (sd, ed), connection=conn, auto_commit=False)

            sql_helper.set_update_info('sessions[protofilter]', ed,
                                       connection=conn, auto_commit=False)

            conn.commit()
        except Exception, e:
            conn.rollback()
            raise e

class DailyUsage(Graph):
    def __init__(self):
        Graph.__init__(self, 'daily-usage', _('Daily Usage'))

    @print_timing
    def get_key_statistics(self, end_date, report_days, host=None, user=None,
                           email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT COALESCE(max(detections), 0)::int,
       COALESCE(sum(detections), 0)::int / %s
FROM (SELECT date_trunc('day', trunc_time) AS day, count(*) AS detections
      FROM reports.session_totals
      WHERE trunc_time >= %s AND trunc_time < %s
      AND pf_protocol != ''"""

        if host:
            query += " AND hname = %s"
        elif user:
            query += " AND uid = %s"

        query += "GROUP BY day) AS foo"

        conn = sql_helper.get_connection()
        try:
            lks = []

            curs = conn.cursor()

            if host:
                curs.execute(query, (report_days, one_week, ed, host))
            elif user:
                curs.execute(query, (report_days, one_week, ed, user))
            else:
                curs.execute(query, (report_days, one_week, ed))

            r = curs.fetchone()
            ks = KeyStatistic(_('max detections (%s-days)') % report_days, r[0],
                              _('detections/day'))
            lks.append(ks)
            ks = KeyStatistic(_('avg detections (%s-days)') % report_days, r[1],
                              _('detections/day'))
            lks.append(ks)
        finally:
            conn.commit()

        return lks

    @print_timing
    def get_plot(self, end_date, report_days, host=None, user=None, email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        start_date = end_date - mx.DateTime.DateTimeDelta(report_days)
        one_day = DateFromMx(start_date)

        conn = sql_helper.get_connection()
        try:
            query = """\
SELECT date_trunc('day', trunc_time) AS day,
       count(*) AS detections
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s
AND pf_protocol != ''"""

            if host:
                query += " AND hname = %s"
            elif user:
                query += " AND uid = %s"

            query += "GROUP BY day ORDER BY day asc"

            curs = conn.cursor()

            if host:
                curs.execute(query, (one_day, ed, host))
            elif user:
                curs.execute(query, (one_day, ed, user))
            else:
                curs.execute(query, (one_day, ed))

            dates = []
            detections = []

            while 1:
                r = curs.fetchone()
                if not r:
                    break
                dates.append(r[0])
                detections.append(r[1])
        finally:
            conn.commit()

        rp = sql_helper.get_required_points(start_date, end_date,
                                            mx.DateTime.DateTimeDelta(1))

        plot = Chart(type=STACKED_BAR_CHART,
                     title=_('Daily Usage'),
                     xlabel=_('Date'),
                     ylabel=_('Detections per Day'),
                     major_formatter=DATE_FORMATTER,
                     required_points=rp)

        plot.add_dataset(dates, detections, label=_('detections'),
                         color=colors.detected)

        return plot

class TopTenBlockedProtocolsByHits(Graph):
    TEN="10"

    def __init__(self):
        Graph.__init__(self, 'top-ten-blocked-protocols-by-hits',
                       _('Top Ten Blocked Protocols By Hits'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))

        query = """\
SELECT pf_protocol, count(*) as hits_sum
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s
AND NOT pf_protocol IS NULL
AND pf_protocol != ''"""

        if host:
            query += " AND hname = %s"
        elif user:
            query += " AND uid = %s"

        query += " GROUP BY pf_protocol ORDER BY hits_sum DESC LIMIT " + self.TEN

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            if host:
                curs.execute(query, (one_day, ed, host))
            elif user:
                curs.execute(query, (one_day, ed, user))
            else:
                curs.execute(query, (one_day, ed))

            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('hits'))
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=_('Top Ten Blocked Protocols (by hits)'),
                     xlabel=_('Protocol'),
                     ylabel=_('Blocks per Day'))

        plot.add_pie_dataset(dataset)

        return (lks, plot)

class TopTenDetectedProtocolsByHits(Graph):
    TEN="10"

    def __init__(self):
        Graph.__init__(self, 'top-ten-detected-protocols-by-hits', _('Top Ten Detected Protocols By Hits'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)

        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))

        query = """\
SELECT pf_protocol, count(*) as hits_sum
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s
AND pf_protocol != ''"""

        if host:
            query += " AND hname = %s"
        elif user:
            query += " AND uid = %s"

        query = query + " GROUP BY pf_protocol ORDER BY hits_sum DESC LIMIT " + self.TEN

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            if host:
                curs.execute(query, (one_day, ed, host))
            elif user:
                curs.execute(query, (one_day, ed, user))
            else:
                curs.execute(query, (one_day, ed))

            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('hits'))
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=_('Top Ten Detected Protocols (by hits)'),
                     xlabel=_('Protocol'),
                     ylabel=_('Blocks per Day'))

        plot.add_pie_dataset(dataset)

        return (lks, plot)

class TopTenBlockedHostsByHits(Graph):
    TEN="10"

    def __init__(self):
        Graph.__init__(self, 'top-ten-blocked-hosts-by-hits', _('Top Ten Blocked Hosts By Hits'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)

        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))

        query = """\
SELECT hname, count(*) as hits_sum
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s
AND NOT pf_protocol IS NULL
AND pf_protocol != ''"""

        if host:
            query += " AND hname = %s"
        elif user:
            query += " AND uid = %s"

        query = query + " GROUP BY hname ORDER BY hits_sum DESC LIMIT " + self.TEN

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            if host:
                curs.execute(query, (one_day, ed, host))
            elif user:
                curs.execute(query, (one_day, ed, user))
            else:
                curs.execute(query, (one_day, ed))

            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('hits'), link_type=reports.HNAME_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=_('Top Ten Blocked Hosts (by hits)'),
                     xlabel=_('Host'),
                     ylabel=_('Blocks per Day'))

        plot.add_pie_dataset(dataset)

        return (lks, plot)

class TopTenLoggedHostsByHits(Graph):
    TEN="10"

    def __init__(self):
        Graph.__init__(self, 'top-ten-logged-hosts-by-hits',
                       _('Top Ten Logged Hosts By Hits'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))

        query = """\
SELECT hname, count(*) as hits_sum
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s
AND pf_protocol != ''"""

        if host:
            query += " AND hname = %s"
        elif user:
            query += " AND uid = %s"

        query +=" GROUP BY hname ORDER BY hits_sum DESC LIMIT " + self.TEN

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            if host:
                curs.execute(query, (one_day, ed, host))
            elif user:
                curs.execute(query, (one_day, ed, user))
            else:
                curs.execute(query, (one_day, ed))

            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('hits'), link_type=reports.HNAME_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=_('Top Ten Logged Hosts (by hits)'),
                     xlabel=_('Host'),
                     ylabel=_('Blocks per Day'))

        plot.add_pie_dataset(dataset)

        return (lks, plot)

class TopTenBlockedUsersByHits(Graph):
    TEN="10"

    def __init__(self):
        Graph.__init__(self, 'top-ten-blocked-users-by-hits', _('Top Ten Blocked Users By Hits'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))

        query = """\
SELECT uid, count(*) as hits_sum
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s
AND uid != ''
AND NOT pf_protocol IS NULL
AND pf_protocol != ''"""

        if host:
            query += " AND hname = %s"
        elif user:
            query += " AND uid = %s"

        query = query + " GROUP BY uid ORDER BY hits_sum DESC LIMIT " + self.TEN

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            if host:
                curs.execute(query, (one_day, ed, host))
            elif user:
                curs.execute(query, (one_day, ed, user))
            else:
                curs.execute(query, (one_day, ed))

            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('hits'), link_type=reports.USER_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=_('Top Ten Blocked Users (by hits)'),
                     xlabel=_('User'),
                     ylabel=_('Blocks per Day'))

        plot.add_pie_dataset(dataset)

        return (lks, plot)

class TopTenLoggedUsersByHits(Graph):
    TEN="10"

    def __init__(self):
        Graph.__init__(self, 'top-ten-logged-users-by-hits', _('Top Ten Logged Users By Hits'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))

        query = """\
SELECT uid, count(*) as hits_sum
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s
AND uid != ''
AND pf_protocol != ''"""

        if host:
            query += " AND hname = %s"
        elif user:
            query += " AND uid = %s"

        query = query + " GROUP BY uid ORDER BY hits_sum DESC LIMIT " + self.TEN

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            if host:
                curs.execute(query, (one_day, ed, host))
            elif user:
                curs.execute(query, (one_day, ed, user))
            else:
                curs.execute(query, (one_day, ed))

            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('hits'), link_type=reports.USER_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=_('Top Ten Logged Users (by hits)'),
                     xlabel=_('User'),
                     ylabel=_('Blocks per Day'))

        plot.add_pie_dataset(dataset)

        return (lks, plot)

class ProtofilterDetail(DetailSection):
    def __init__(self):
        DetailSection.__init__(self, 'detection-events', _('Detection Events'))

    def get_columns(self, host=None, user=None, email=None):
        if email:
            return None

        rv = [ColumnDesc('time_stamp', _('Time'), 'Date')]

        if not host:
            rv.append(ColumnDesc('hname', _('Client'), 'HostLink'))
        if not user:
            rv.append(ColumnDesc('uid', _('User'), 'UserLink'))

        rv = rv + [ColumnDesc('pf_protocol', _('Protocol')),
                   ColumnDesc('pf_blocked', _('Blocked')),
                   ColumnDesc('c_server_addr', _('Server')),
                   ColumnDesc('c_server_port', _('Port'))]

        return rv

    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if email:
            return None

        sql = "SELECT time_stamp,"

        if not host:
            sql = sql + "hname, "
        if not user:
            sql = sql + "uid, "

        sql = sql + ("""pf_protocol, pf_blocked, host(c_server_addr), c_server_port
FROM reports.sessions
WHERE time_stamp >= %s AND time_stamp < %s
AND NOT pf_protocol ISNULL
AND pf_protocol != ''""" % (DateFromMx(start_date),
                            DateFromMx(end_date)))

        if host:
            sql = sql + (" AND host = %s" % QuotedString(host))
        if user:
            sql = sql + (" AND host = %s" % QuotedString(user))

        return sql + "ORDER BY time_stamp"

reports.engine.register_node(Protofilter())

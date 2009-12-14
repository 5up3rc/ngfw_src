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

_ = reports.i18n_helper.get_translation('untangle-node-ips').lgettext

class Ips(Node):
    def __init__(self, node_name, title, vendor_name):
        Node.__init__(self, node_name)

        self.__title = title
        self.__vendor_name = vendor_name

    @print_timing
    def setup(self, start_date, end_date):
        self.__update_sessions(start_date, end_date)

        ft = reports.engine.get_fact_table('reports.session_totals')

        ft.measures.append(Column('ips_blocks', 'integer',
                                  "count(CASE WHEN NOT ips_blocked ISNULL THEN 1 ELSE null END)"))

        ft.dimensions.append(Column('ips_name', 'text'))
        ft.dimensions.append(Column('ips_description', 'text'))

    def get_toc_membership(self):
        return [TOP_LEVEL, HOST_DRILLDOWN, USER_DRILLDOWN]

    def get_report(self):
        sections = []

        s = SummarySection('summary', _('Summary Report'),
                           [DailyUsage(self.__vendor_name),
                            TopTenAttacksByHits(self.__vendor_name)])
        sections.append(s)

        sections.append(IpsDetail())

        return Report(self.name, sections)

    def parents(self):
        return ['untangle-vm']

    def events_cleanup(self, cutoff):
        try:
            sql_helper.run_sql("""\
DELETE FROM events.n_ips_evt
 WHERE time_stamp < %s""", (cutoff,))
        except: pass

        try:
            sql_helper.run_sql("""\
DELETE FROM events.n_ips_statistic_evt
 WHERE time_stamp < %s""", (cutoff,))
        except: pass

    def reports_cleanup(self, cutoff):
        pass

    @print_timing
    def __update_sessions(self, start_date, end_date):
        try:
            sql_helper.run_sql("""\
ALTER TABLE reports.sessions ADD COLUMN ips_blocked boolean""")
        except: pass

        try:
            sql_helper.run_sql("""\
ALTER TABLE reports.sessions ADD COLUMN ips_name text""")
            sql_helper.run_sql("""\
ALTER TABLE reports.sessions ADD COLUMN ips_description text""")
        except: pass

        sd = DateFromMx(sql_helper.get_update_info('sessions[ips]',
                                                   start_date))
        ed = DateFromMx(end_date)

        conn = sql_helper.get_connection()
        try:
            sql_helper.run_sql("""\
UPDATE reports.sessions
SET ips_blocked = blocked, ips_name = name, ips_description = description
FROM events.n_ips_evt join settings.n_ips_rule on rule_sid = sid
WHERE reports.sessions.time_stamp >= %s
  AND reports.sessions.time_stamp < %s
  AND reports.sessions.pl_endp_id = events.n_ips_evt.pl_endp_id""",
                               (sd, ed), connection=conn, auto_commit=False)

            sql_helper.set_update_info('sessions[ips]', ed,
                                       connection=conn, auto_commit=False)

            conn.commit()
        except Exception, e:
            conn.rollback()
            raise e

class TopTenAttacksByHits(Graph):
    def __init__(self, vendor_name):
        Graph.__init__(self, 'top-ten-attacks-by-hits', _('Top Ten Attacks By Hits'))

        self.__vendor_name = vendor_name

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT ips_description, count(*) as hits_sum
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s
AND ips_description != ''"""

        if host:
            query += " AND hname = %s"
        elif user:
            query += " AND uid = %s"

        query += " GROUP BY ips_description ORDER BY hits_sum DESC"

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            if host:
                curs.execute(query, (one_week, ed, host))
            elif user:
                curs.execute(query, (one_week, ed, user))
            else:
                curs.execute(query, (one_week, ed))

            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('hits'))
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title, xlabel=_('Attacks'),
                     ylabel=_('Hits per Day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks[0:10], plot)

class DailyUsage(Graph):
    def __init__(self, vendor_name):
        Graph.__init__(self, 'daily-attacks', _('Daily Attacks'))

        self.__vendor_name = vendor_name

    @print_timing
    def get_key_statistics(self, end_date, report_days, host=None, user=None,
                           email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT COALESCE(max(attacks), 0), COALESCE(avg(attacks), 0)
FROM (SELECT date_trunc('day', trunc_time) AS day, count(*) AS attacks
      FROM reports.session_totals
      WHERE trunc_time >= %s AND trunc_time < %s
      AND ips_description != ''"""

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
                curs.execute(query, (one_day, ed, host))
            elif user:
                curs.execute(query, (one_day, ed, user))
            else:
                curs.execute(query, (one_day, ed))

            r = curs.fetchone()
            ks = KeyStatistic(_('Avg Attacks'), r[1], _('attacks/day'))
            lks.append(ks)
            ks = KeyStatistic(_('Max Attacks'), r[0], _('attacks/day'))
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
       count(*) AS attacks
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s
AND ips_description != ''"""

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
            attacks = []

            while 1:
                r = curs.fetchone()
                if not r:
                    break
                dates.append(r[0])
                attacks.append(r[1])

        finally:
            conn.commit()

        rp = sql_helper.get_required_points(start_date, end_date,
                                            mx.DateTime.DateTimeDelta(1))

        plot = Chart(type=STACKED_BAR_CHART,
                     title=self.title, xlabel=_('Date'),
                     ylabel=_('Attacks per Day'),
                     major_formatter=DATE_FORMATTER,
                     required_points=rp)

        plot.add_dataset(dates, attacks, label=_('attacks'))

        return plot

class IpsDetail(DetailSection):
    def __init__(self):
        DetailSection.__init__(self, 'attack-events', _('Attack Events'))

    def get_columns(self, host=None, user=None, email=None):
        if email:
            return None

        rv = [ColumnDesc('time_stamp', _('Time'), 'Date')]

        if not host:
            rv.append(ColumnDesc('hname', _('Client'), 'HostLink'))
        if not user:
            rv.append(ColumnDesc('uid', _('User'), 'UserLink'))

        rv = rv + [ColumnDesc('ips_description', _('SID:description')),
                   ColumnDesc('ips_blocked', _('Blocked')),
                   ColumnDesc('c_server_addr', _('Server')),
                   ColumnDesc('c_server_port', _('Port'))]

        return rv

    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if email:
            return None

        sql = "SELECT time_stamp, "

        if not host:
            sql = sql + "hname, "
        if not user:
            sql = sql + "uid, "

        sql = sql + ("""ips_description, ips_blocked::text, host(c_server_addr), c_server_port
FROM reports.sessions
WHERE time_stamp >= %s AND time_stamp < %s
AND NOT ips_description ISNULL
AND ips_description != '' """ % (DateFromMx(start_date),
                          DateFromMx(end_date)))

        if host:
            sql = sql + (" AND hname = %s" % QuotedString(host))
        if user:
            sql = sql + (" AND uid = %s" % QuotedString(user))

        return sql + " ORDER BY time_stamp DESC"

reports.engine.register_node(Ips('untangle-node-ips', 'IPS', 'ips'))

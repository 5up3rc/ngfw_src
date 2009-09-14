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
import psycopg
import reports.i18n_helper
import reports.engine
import reports.sql_helper as sql_helper

from mx.DateTime import DateTimeDelta
from psycopg import DateFromMx
from psycopg import QuotedString
from psycopg import TimestampFromMx
from reports import Chart
from reports import ColumnDesc
from reports import DATE_FORMATTER
from reports import DetailSection
from reports import Graph
from reports import KeyStatistic
from reports import PIE_CHART
from reports import Report
from reports import SummarySection
from reports import TIME_OF_DAY_FORMATTER
from reports import TIME_SERIES_CHART
from reports.engine import Column
from reports.engine import FactTable
from reports.engine import Node
from sql_helper import print_timing

EVT_TYPE_REGISTER = 0
EVT_TYPE_RENEW    = 1
EVT_TYPE_EXPIRE   = 2
EVT_TYPE_RELEASE  = 3

_ = reports.i18n_helper.get_translation('untangle-vm').lgettext
def N_(message): return message

class UvmNode(Node):
    def __init__(self):
        Node.__init__(self, 'untangle-vm')

    @print_timing
    def setup(self, start_date, end_date):
        self.__generate_address_map(start_date, end_date)

        self.__create_n_admin_logins(start_date, end_date)

        self.__make_sessions_table(start_date, end_date)
        self.__make_session_counts_table(start_date, end_date)

        ft = FactTable('reports.session_totals',
                       'reports.sessions',
                       'time_stamp',
                       [Column('hname', 'text'),
                        Column('uid', 'text'),
                        Column('client_intf', 'smallint'),
                        Column('c_server_port', 'int4'),
                        Column('c_client_port', 'int4')],
                        [Column('new_sessions', 'bigint', 'count(*)'),
                        Column('s2c_bytes', 'bigint', 'sum(p2c_bytes)'),
                        Column('c2s_bytes', 'bigint', 'sum(p2s_bytes)')])
        reports.engine.register_fact_table(ft)

    @print_timing
    def __create_n_admin_logins(self, start_date, end_date):
        sql_helper.create_partitioned_table("""\
CREATE TABLE reports.n_admin_logins (
    time_stamp timestamp without time zone,
    login text,
    local boolean,
    client_addr inet,
    succeeded boolean,
    reason char(1)
)""",
                                            'time_stamp', start_date, end_date)

        sd = DateFromMx(sql_helper.get_update_info('reports.n_admin_logins',
                                                   start_date))
        ed = DateFromMx(end_date)

        conn = sql_helper.get_connection()
        try:
            sql_helper.run_sql("""\
INSERT INTO reports.n_admin_logins
      (time_stamp, login, local, client_addr, succeeded, reason)
    SELECT time_stamp, login, local, client_addr, succeeded, reason
    FROM events.u_login_evt evt
    WHERE evt.time_stamp >= %s AND evt.time_stamp < %s""",
                               (sd, ed), connection=conn, auto_commit=False)

            sql_helper.set_update_info('reports.n_admin_logins', ed,
                                       connection=conn, auto_commit=False)

            conn.commit()
        except Exception, e:
            conn.rollback()
            raise e

    def post_facttable_setup(self, start_date, end_date):
        self.__make_hnames_table(start_date, end_date)
        self.__make_users_table(start_date, end_date)

    def events_cleanup(self, cutoff):
        sql_helper.run_sql("""\
DELETE FROM events.u_lookup_evt WHERE time_stamp < %s""", (cutoff,))

        sql_helper.run_sql("""\
DELETE FROM events.pl_endp WHERE time_stamp < %s""", (cutoff,))

        sql_helper.run_sql("""\
DELETE FROM events.pl_stats WHERE time_stamp < %s""", (cutoff,))

        sql_helper.run_sql("""\
DELETE FROM events.n_router_evt_dhcp_abs WHERE time_stamp < %s""", (cutoff,))

        sql_helper.run_sql("""\
DELETE FROM events.n_router_evt_dhcp WHERE time_stamp < %s""", (cutoff,))

        sql_helper.run_sql("""\
DELETE FROM events.n_router_dhcp_abs_lease WHERE end_of_lease < %s""", (cutoff,))

        sql_helper.run_sql("""\
DELETE FROM events.n_router_evt_dhcp_abs_leases glue
WHERE NOT EXISTS
  (SELECT *
   FROM events.n_router_evt_dhcp_abs evt
   WHERE evt.event_id = glue.event_id)""")

        sql_helper.run_sql("""\
DELETE FROM events.u_login_evt WHERE time_stamp < %s""", (cutoff,))

    def reports_cleanup(self, cutoff):
        pass

    def get_report(self):
        sections = []

        s = SummarySection('summary', _('Summary Report'),
                           [BandwidthUsage(), ActiveSessions(),
                            DestinationPorts()])

        sections.append(s)
        sections.append(AdministrativeLoginsDetail())

        return Report(self.name, sections)

    @print_timing
    def __make_users_table(self, start_date, end_date):
        sql_helper.create_partitioned_table("""\
CREATE TABLE reports.users (
        date date NOT NULL,
        username text NOT NULL,
        PRIMARY KEY (date, username));
""", 'date', start_date, end_date)

        sd = DateFromMx(sql_helper.get_update_info('reports.users', start_date))
        ed = DateFromMx(end_date)

        conn = sql_helper.get_connection()
        try:
            sql_helper.run_sql("""\
INSERT INTO reports.users (date, username)
    SELECT DISTINCT date_trunc('day', trunc_time)::date AS day, uid
    FROM reports.session_totals
    WHERE trunc_time >= %s AND trunc_time < %s AND NOT uid ISNULL""",
                               (sd, ed), connection=conn, auto_commit=False)

            sql_helper.set_update_info('reports.users', ed, connection=conn,
                                       auto_commit=False)

            conn.commit()
        except Exception, e:
            print e
            conn.rollback()
            raise e

    @print_timing
    def __make_hnames_table(self, start_date, end_date):
        sql_helper.create_partitioned_table("""\
CREATE TABLE reports.hnames (
        date date NOT NULL,
        hname text NOT NULL,
        PRIMARY KEY (date, hname));
""", 'date', start_date, end_date)

        sd = DateFromMx(sql_helper.get_update_info('reports.hnames',
                                                   start_date))
        ed = DateFromMx(end_date)

        conn = sql_helper.get_connection()
        try:
            sql_helper.run_sql("""\
INSERT INTO reports.hnames (date, hname)
    SELECT DISTINCT date_trunc('day', trunc_time)::date, hname
    FROM reports.session_totals
    WHERE trunc_time >= %s AND trunc_time < %s
          AND client_intf = 1 AND NOT hname ISNULL""", (sd, ed),
                               connection=conn, auto_commit=False)

            sql_helper.set_update_info('reports.hnames', ed,
                                       connection=conn, auto_commit=False)

            conn.commit()
        except Exception, e:
            print e
            conn.rollback()
            raise e

    @print_timing
    def __make_sessions_table(self, start_date, end_date):
        sql_helper.create_partitioned_table("""\
CREATE TABLE reports.sessions (
        pl_endp_id int8 NOT NULL,
        time_stamp timestamp NOT NULL,
        end_time timestamp NOT NULL,
        hname text,
        uid text,
        c_client_addr inet,
        c_server_addr inet,
        c_server_port int4,
        c_client_port int4,
        client_intf int2,
        c2p_bytes int8,
        p2c_bytes int8,
        s2p_bytes int8,
        p2s_bytes int8)""", 'time_stamp', start_date, end_date)

        sd = DateFromMx(sql_helper.get_update_info('reports.sessions',
                                                   start_date))
        ed = DateFromMx(end_date)

        conn = sql_helper.get_connection()
        try:
            sql_helper.run_sql("""\
CREATE TEMPORARY TABLE newsessions AS
    SELECT endp.event_id, endp.time_stamp, mam.name,
           endp.c_client_addr, endp.c_server_addr, endp.c_server_port, endp.c_client_port,
           endp.client_intf
    FROM events.pl_endp endp
    LEFT OUTER JOIN reports.merged_address_map mam
      ON (endp.c_client_addr = mam.addr AND endp.time_stamp >= mam.start_time
         AND endp.time_stamp < mam.end_time)
    WHERE endp.time_stamp >= %s AND endp.time_stamp < %s
""", (sd, ed), connection=conn, auto_commit=False)

            sql_helper.run_sql("""\
INSERT INTO reports.sessions
    (pl_endp_id, time_stamp, end_time, hname, uid, c_client_addr,
     c_server_addr, c_server_port, c_client_port, client_intf, c2p_bytes, p2c_bytes,
     s2p_bytes, p2s_bytes)
    SELECT ses.event_id, ses.time_stamp, stats.time_stamp,
         COALESCE(NULLIF(ses.name, ''), HOST(ses.c_client_addr)) AS hname,
         stats.uid, c_client_addr, c_server_addr, c_server_port, c_client_port, client_intf,
         stats.c2p_bytes, stats.p2c_bytes, stats.s2p_bytes, stats.p2s_bytes
    FROM newsessions ses
    JOIN events.pl_stats stats ON (ses.event_id = stats.pl_endp_id)""",
                               connection=conn, auto_commit=False)

            sql_helper.set_update_info('reports.sessions', ed,
                                       connection=conn, auto_commit=False)

            conn.commit()
        except Exception, e:
            conn.rollback()
            raise e

    @print_timing
    def __make_session_counts_table(self, start_date, end_date):
        sql_helper.create_partitioned_table("""\
CREATE TABLE reports.session_counts (
        trunc_time timestamp,
        uid text,
        hname text,
        num_sessions int8)""", 'trunc_time', start_date, end_date)

        sd = DateFromMx(sql_helper.get_update_info('reports.session_counts',
                                                   start_date))
        ed = DateFromMx(end_date)

        conn = sql_helper.get_connection()
        try:
            sql_helper.run_sql("""\
INSERT INTO reports.session_counts
    (trunc_time, uid, hname, num_sessions)
SELECT (date_trunc('minute', time_stamp)
        + (generate_series(0, (extract('epoch' from (end_time - time_stamp))
        / 60)::int) || ' minutes')::interval) AS time, uid, hname, count(*)
FROM reports.sessions
WHERE time_stamp >= %s AND time_stamp < %s
GROUP BY time, uid, hname""", (sd, ed), connection=conn, auto_commit=False)

            sql_helper.set_update_info('reports.session_counts', ed,
                                       connection=conn, auto_commit=False)

            conn.commit()
        except Exception, e:
            conn.rollback()
            raise e

    def teardown(self):
        print "TEARDOWN"

    @print_timing
    def __generate_address_map(self, start_date, end_date):
        self.__do_housekeeping()

        m = {}

        if self.__nat_installed():
            self.__generate_abs_leases(m, start_date, end_date)
            self.__generate_relative_leases(m, start_date, end_date)
            self.__generate_static_leases(m, start_date, end_date)

        self.__generate_manual_map(m, start_date, end_date)

        self.__write_leases(m)

    @print_timing
    def __do_housekeeping(self):
        sql_helper.run_sql("""\
DELETE FROM settings.n_reporting_settings WHERE tid NOT IN
(SELECT tid FROM settings.u_node_persistent_state
WHERE NOT target_state = 'destroyed')""")

        sql_helper.run_sql("""\
DELETE FROM settings.u_ipmaddr_dir_entries WHERE ipmaddr_dir_id NOT IN
(SELECT id FROM settings.u_ipmaddr_dir WHERE id IN
(SELECT network_directory FROM settings.n_reporting_settings))""")

        sql_helper.run_sql("""\
DELETE FROM settings.u_ipmaddr_dir WHERE id NOT IN
(SELECT network_directory FROM settings.n_reporting_settings)""")

        if sql_helper.table_exists('reports', 'merged_address_map'):
            sql_helper.run_sql("DROP TABLE reports.merged_address_map");

        sql_helper.run_sql("""\
CREATE TABLE reports.merged_address_map (
    id         SERIAL8 NOT NULL,
    addr       INET NOT NULL,
    name       VARCHAR(255),
    start_time TIMESTAMP NOT NULL,
    end_time   TIMESTAMP,
    PRIMARY KEY (id))""")

    @print_timing
    def __write_leases(self, m):
        values = []

        for v in m.values():
            for l in v:
                if l.hostname:
                    values.append(l.values())

        conn = sql_helper.get_connection()
        try:
            curs = conn.cursor()

            curs.executemany("""\
INSERT INTO reports.merged_address_map (addr, name, start_time, end_time)
VALUES (%s, %s, %s, %s)""", values)

        finally:
            conn.commit()

    def __nat_installed(self):
        return sql_helper.table_exists('events',
                                       'n_router_evt_dhcp_abs_leases')

    @print_timing
    def __generate_abs_leases(self, m, start_date, end_date):
        self.__generate_leases(m, """\
SELECT evt.time_stamp, lease.end_of_lease, lease.ip, lease.hostname,
       CASE WHEN (lease.event_type = 0) THEN 0 ELSE 3 END AS event_type
FROM events.n_router_evt_dhcp_abs_leases AS glue,
     events.n_router_evt_dhcp_abs AS evt,
     events.n_router_dhcp_abs_lease AS lease
WHERE lease.hostname != '' AND glue.event_id = evt.event_id
      AND glue.lease_id = lease.event_id
      AND ((%s <= evt.time_stamp and evt.time_stamp <= %s)
      OR ((%s <= lease.end_of_lease and lease.end_of_lease <= %s)))
ORDER BY evt.time_stamp""", start_date, end_date)

    @print_timing
    def __generate_relative_leases(self, m, start_date, end_date):
        self.__generate_leases(m, """\
SELECT evt.time_stamp, evt.end_of_lease, evt.ip, evt.hostname, evt.event_type
FROM events.n_router_evt_dhcp AS evt
WHERE hostname != '' AND ((%s <= evt.time_stamp AND evt.time_stamp <= %s)
    OR (%s <= evt.end_of_lease AND evt.end_of_lease <= %s))
ORDER BY evt.time_stamp""", start_date, end_date)

    @print_timing
    def __generate_static_leases(self, m, start_date, end_date):
        conn = sql_helper.get_connection()
        try:
            curs = conn.cursor()

            curs.execute("""\
SELECT hostname_list, static_address
FROM settings.u_dns_static_host_rule AS rule,
     settings.n_router_dns_hosts AS list,
     settings.n_router_settings AS settings
WHERE rule.rule_id = list.rule_id
      AND settings.settings_id = list.setting_id""")

            while 1:
                r = curs.fetchone()
                if not r:
                    break

                (hostname, ip) = r

                hostname = hostname.split(" ")[0]

                m[ip] = [Lease((start_date, end_date, ip, hostname, None))]
        finally:
            conn.commit()

    @print_timing
    def __generate_manual_map(self, m, start_date, end_date):
        conn = sql_helper.get_connection()
        try:
            curs = conn.cursor()

            curs.execute("""\
SELECT addr, name
FROM (SELECT addr, min(position) AS min_idx
      FROM (SELECT c_client_addr AS addr
            FROM events.pl_endp WHERE pl_endp.client_intf = 1
            UNION
            SELECT c_server_addr AS addr
            FROM events.pl_endp WHERE pl_endp.server_intf = 1
            UNION
            SELECT client_addr AS addr
            FROM events.u_login_evt) AS addrs
      JOIN settings.u_ipmaddr_dir_entries entry
      JOIN settings.u_ipmaddr_rule rule USING (rule_id)
      ON rule.ipmaddr >>= addr
      WHERE NOT addr ISNULL
      GROUP BY addr) AS pos_idxs
LEFT OUTER JOIN settings.u_ipmaddr_dir_entries entry
JOIN settings.u_ipmaddr_rule rule USING (rule_id)
ON min_idx = position""")

            while 1:
                r = curs.fetchone()
                if not r:
                    break

                (ip, hostname) = r

                m[ip] = [Lease((start_date, end_date, ip, hostname, None))]
        finally:
            conn.commit()

    def __generate_leases(self, m, q, start_date, end_date):
        st = DateFromMx(start_date)
        et = DateFromMx(end_date)

        conn = sql_helper.get_connection()
        try:
            curs = conn.cursor()

            curs.execute(q, (st, et, st, et))

            while 1:
                r = curs.fetchone()
                if not r:
                    break

                self.__insert_lease(m, Lease(r))
        finally:
            conn.commit()

    def __insert_lease(self, m, event):
        et = event.event_type

        if et == EVT_TYPE_REGISTER or et == EVT_TYPE_RENEW:
            self.__merge_event(m, event)
        elif et == EVT_TYPE_RELEASE or et == EVT_TYPE_EXPIRE:
            self.__truncate_event(m, event)
        else:
            logging.warn('do not know type: %d' % et)

    def __merge_event(self, m, event):
        l = m.get(event.ip, None)

        if not l:
            m[event.ip] = [event]
        else:
            for (index, lease) in enumerate(l):
                same_hostname = lease.hostname = event.hostname

                if lease.after(event):
                    l.insert(index, lease)
                    return
                elif lease.intersects_before(event):
                    if same_hostname:
                        lease.start = event.start
                        return
                    else:
                        event.end_of_lease = lease.start
                        l.insert(index, lease)
                        return
                elif lease.encompass(event):
                    if same_hostname:
                        return
                    else:
                        lease.end_of_lease = event.start
                        l.insert(index + 1, lease)
                        return
                elif lease.intersects_after(event):
                    if same_hostname:
                        lease.end_of_lease = event.end_of_lease
                    else:
                        lease.end_of_lease = event.start
                        index = index + 1
                        l.insert(index, event)

                    if index + 1 < len(l):
                        index = index + 1
                        next_lease = l[index]

                        if (next_lease.start > lease.start
                            and next_lease.start < lease.end_of_lease):
                            if next_lease.hostname == lease.hostname:
                                del(l[index])
                                lease.end_of_lease = next_lease.end_of_lease
                            else:
                                lease.end_of_lease = next_lease.start
                    return
                elif lease.encompassed(event):
                    lease.start = event.start
                    return

            l.append(event)

    def __truncate_event(self, m, event):
        l = m.get(event.ip, None)

        if l:
            for (index, lease) in enumerate(l):
                if (lease.start < event.start
                    and lease.end_of_lease > event.start):
                    lease.end_of_lease = event.start
                    return

class BandwidthUsage(Graph):
    def __init__(self):
        Graph.__init__(self, 'bandwidth-usage', _('Bandwidth Usage'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        conn = sql_helper.get_connection()
        try:
            lks = []

            ks_query = """\
SELECT coalesce(sum(s2c_bytes + c2s_bytes), 0) / (24 * 60 * 60) AS avg_rate,
       coalesce(max(s2c_bytes + c2s_bytes), 0) / 60 AS peak_rate
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s"""

            if user:
                ks_query += "AND uid = %s"
            elif host:
                ks_query += "AND hname = %s"

            curs = conn.cursor()

            if user:
                curs.execute(ks_query, (one_day, ed, user))
            elif host:
                curs.execute(ks_query, (one_day, ed, host))
            else:
                curs.execute(ks_query, (one_day, ed))

            r = curs.fetchone()

            avg_rate = r[0]
            peak_rate = r[1]

            ks = KeyStatistic(_('Avg data rate (1-day)'),
                              avg_rate, N_('bytes/s'))
            lks.append(ks)
            ks = KeyStatistic(_('Peak data rate (1-day)'),
                              peak_rate, N_('bytes/s'))
            lks.append(ks)

            ks_query = """\
SELECT coalesce(sum(s2c_bytes + c2s_bytes), 0) / %s AS avg_rate,
       coalesce(sum(s2c_bytes + c2s_bytes), 0) AS total
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s
"""

            if user:
                ks_query += "AND uid = %s"
            elif host:
                ks_query += "AND hname = %s"

            curs = conn.cursor()

            if user:
                curs.execute(ks_query, (report_days, one_week, ed, user))
            elif host:
                curs.execute(ks_query, (report_days, one_week, ed, host))
            else:
                curs.execute(ks_query, (report_days, one_week, ed))

            r = curs.fetchone()

            avg_rate = r[0]
            total = r[1]

            ks = KeyStatistic(_('Avg data rate (%s-day)') % report_days,
                              avg_rate, N_('bytes/day'))
            lks.append(ks)
            ks = KeyStatistic(_('Data Transfered (%s-day)') % report_days,
                              total, N_('bytes'))
            lks.append(ks)

            curs = conn.cursor()

            plot_query = """\
SELECT (date_part('hour', trunc_time) || ':'
        || (date_part('minute', trunc_time)::int / 10 * 10))::time AS time,
       sum(s2c_bytes + c2s_bytes) / (1000 * 10 * 60) AS throughput
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s"""

            if user:
                plot_query += " AND uid = %s"
            elif host:
                plot_query += " AND hname = %s"

            plot_query += """\
GROUP BY time
ORDER BY time asc"""

            dates = []
            throughput = []

            curs = conn.cursor()

            if user:
                curs.execute(plot_query, (one_week, ed, user))
            elif host:
                curs.execute(plot_query, (one_week, ed, host))
            else:
                curs.execute(plot_query, (one_week, ed))

            for r in curs.fetchall():
                dates.append(r[0].seconds)
                throughput.append(r[1])
        finally:
            conn.commit()

        plot = Chart(type=TIME_SERIES_CHART, title=self.title,
                     xlabel=_('Hour of Day'), ylabel=_('Throughput (KB/s)'),
                     major_formatter=TIME_OF_DAY_FORMATTER,
                     required_points=sql_helper.REQUIRED_TIME_POINTS)

        plot.add_dataset(dates, throughput, _('Usage'))

        return (lks, plot)

class ActiveSessions(Graph):
    def __init__(self):
        Graph.__init__(self, 'active-sessions', _('Active Sessions'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_day = DateFromMx(end_date - mx.DateTime.DateTimeDelta(1))
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        conn = sql_helper.get_connection()
        try:
            lks = []

            ks_query = """\
SELECT coalesce(sum(num_sessions), 0) / (24 * 60) AS avg_sessions,
       coalesce(max(num_sessions), 0)::int AS max_sessions
FROM reports.session_counts
WHERE trunc_time >= %s AND trunc_time < %s"""

            if user:
                ks_query += "AND uid = %s"
            elif host:
                ks_query += "AND hname = %s"

            curs = conn.cursor()

            if user:
                curs.execute(ks_query, (one_day, ed, user))
            elif host:
                curs.execute(ks_query, (one_day, ed, host))
            else:
                curs.execute(ks_query, (one_day, ed))

            r = curs.fetchone()

            avg_sessions = r[0]
            max_sessions = r[1]

            ks = KeyStatistic(_('Avg active sessions (1-day)'),
                              avg_sessions, _('sessions'))
            lks.append(ks)
            ks = KeyStatistic(_('Max active sessions (1-day)'),
                              max_sessions, _('sessions'))
            lks.append(ks)

            ks_query = """\
SELECT sum(num_sessions)::int AS total_sessions
FROM reports.session_counts
WHERE trunc_time >= %s AND trunc_time < %s"""

            if user:
                ks_query += "AND uid = %s"
            elif host:
                ks_query += "AND hname = %s"

            curs = conn.cursor()

            if user:
                curs.execute(ks_query, (one_week, ed, user))
            elif host:
                curs.execute(ks_query, (one_week, ed, host))
            else:
                curs.execute(ks_query, (one_week, ed))

            r = curs.fetchone()

            total_sessions = r[0]

            ks = KeyStatistic(_('Total Sessions'), total_sessions,
                              _('sessions'))
            lks.append(ks)

            curs = conn.cursor()

            plot_query = """\
SELECT (date_part('hour', trunc_time) || ':'
        || date_part('minute', trunc_time))::time AS time,
       sum(num_sessions) / %s AS sessions
FROM reports.session_counts
WHERE trunc_time >= %s AND trunc_time < %s"""

            if user:
                plot_query += " AND uid = %s"
            elif host:
                plot_query += " AND hname = %s"

            plot_query += """\
GROUP BY time
ORDER BY time asc"""

            dates = []
            num_sessions = []

            curs = conn.cursor()

            if user:
                curs.execute(plot_query, (report_days, one_week, ed, user))
            elif host:
                curs.execute(plot_query, (report_days, one_week, ed, host))
            else:
                curs.execute(plot_query, (report_days, one_week, ed))

            for r in curs.fetchall():
                dates.append(r[0].seconds)
                num_sessions.append(r[1])
        finally:
            conn.commit()

        plot = Chart(type=TIME_SERIES_CHART, title=self.title,
                     xlabel=_('Hour of Day'), ylabel=_('Number of Sessions'),
                     major_formatter=TIME_OF_DAY_FORMATTER,
                     required_points=sql_helper.REQUIRED_TIME_POINTS)

        plot.add_dataset(dates, num_sessions, _('Sessions'))

        return (lks, plot)

class DestinationPorts(Graph):
    def __init__(self):
        Graph.__init__(self, 'top-dest-ports', _('Top Destination Ports'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT c_server_port, sum(new_sessions)::int as sessions
FROM reports.session_totals
WHERE trunc_time >= %s AND trunc_time < %s"""

        if host:
            query += " AND hname = %s"
        elif user:
            query += " AND uid = %s"

        query += """\
GROUP BY c_server_port
ORDER BY sessions DESC
LIMIT 10"""

        conn = sql_helper.get_connection()
        try:
            curs = conn.cursor()

            if host:
                curs.execute(query, (one_week, ed, host))
            elif user:
                curs.execute(query, (one_week, ed, user))
            else:
                curs.execute(query, (one_week, ed))


            lks = []
            pds = {}

            for r in curs.fetchall():
                port = r[0]
                sessions = r[1]
                ks = KeyStatistic(str(port), sessions, _('sessions'))
                lks.append(ks)
                pds[port] = sessions
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART, title=self.title, xlabel=_('Port'),
                     ylabel=_('Sessions'))

        plot.add_pie_dataset(pds)

        return (lks, plot)

class Lease:
    def __init__(self, row):
        self.start = row[0]
        self.end_of_lease = row[1]
        self.ip = row[2]
        self.hostname = row[3]
        self.event_type = row[4]

    def after(self, event):
        return self.start > event.end_of_lease

    def intersects_before(self, event):
        return ((self.start > event.start
                 and self.start < event.end_of_lease)
                and (self.end_of_lease > event.end_of_lease
                     or self.end_of_lease == event.end_of_lease))

    def intersects_after(self, event):
        return ((self.start < event.start
                 and self.end_of_lease > event.start)
                and (self.end_of_lease == event.end_of_lease
                     or self.end_of_lease < event.end_of_lease))

    def encompass(self, event):
        return ((self.start == event.start or self.start < event.start)
                and (self.end_of_lease == event.end_of_lease
                     or self.end_of_lease > event.end_of_lease))

    def encompassed(self, event):
        return ((self.start == event.start or self.start > event.start)
                and ( self.end_of_lease == event.end_of_lease
                      or self.end_of_lease < event.end_of_lease))

    def values(self):
        return (self.ip, self.hostname, TimestampFromMx(self.start),
                TimestampFromMx(self.end_of_lease))

class AdministrativeLoginsDetail(DetailSection):
    def __init__(self):
        DetailSection.__init__(self, 'admin-logins-events', _('Administrative Logins Events'))

    def get_columns(self, host=None, user=None, email=None):
        if host or user or email:
            return None

        rv = [ColumnDesc('time_stamp', _('Time'), 'Date')]

        rv += [ColumnDesc('client_addr', _('Client IP')),
               ColumnDesc('succeeded', _('Success'))]

        return rv

    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if email:
            return None

        sql = """\
SELECT time_stamp, host(client_addr), succeeded::text
FROM reports.n_admin_logins
WHERE time_stamp >= %s AND time_stamp < %s AND not local
""" % (DateFromMx(start_date), DateFromMx(end_date))

        return sql + "ORDER BY time_stamp"


reports.engine.register_node(UvmNode())

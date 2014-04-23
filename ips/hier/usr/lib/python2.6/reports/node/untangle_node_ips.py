import gettext
import logging
import mx
import reports.i18n_helper
import reports.engine
import reports.sql_helper as sql_helper
import sys

from psycopg2.extensions import DateFromMx
from psycopg2.extensions import QuotedString
from psycopg2.extensions import TimestampFromMx
from reports import Chart
from reports import ColumnDesc
from reports import DATE_FORMATTER
from reports import DetailSection
from reports import Graph
from reports import Highlight
from reports import HOUR_FORMATTER
from reports import KeyStatistic
from reports import PIE_CHART
from reports import Report
from reports import STACKED_BAR_CHART
from reports import SummarySection
from reports import TIME_OF_DAY_FORMATTER
from reports import TIMESTAMP_FORMATTER
from reports.engine import Column
from reports.engine import HOST_DRILLDOWN
from reports.engine import Node
from reports.engine import TOP_LEVEL
from reports.engine import USER_DRILLDOWN
from reports.sql_helper import print_timing

_ = reports.i18n_helper.get_translation('untangle-node-ips').lgettext

class Ips(Node):
    def __init__(self, node_name, title, vendor_name):
        Node.__init__(self, node_name,'Intrusion Prevention')

        self.__title = title
        self.__vendor_name = vendor_name

    @print_timing
    def setup(self):
        ft = reports.engine.get_fact_table('reports.session_totals')
        ft.measures.append(Column('ips_blocks', 'integer', "count(CASE WHEN NOT ips_blocked ISNULL THEN 1 ELSE null END)"))
        ft.dimensions.append(Column('ips_description', 'text'))

    def get_toc_membership(self):
        return [TOP_LEVEL, HOST_DRILLDOWN, USER_DRILLDOWN]

    def get_report(self):
        sections = []

        s = SummarySection('summary', _('Summary Report'),
                           [IpsHighlight(self.name),
                            DailyUsage(self.__vendor_name),
                            TopTenAttacksByHits(self.__vendor_name)])
        sections.append(s)

        sections.append(IpsDetail())

        return Report(self, sections)

    def parents(self):
        return ['untangle-vm']

    def reports_cleanup(self, cutoff):
        pass

class IpsHighlight(Highlight):
    def __init__(self, name):
        Highlight.__init__(self, name,
                           _(name) + " " +
                           _("scanned") + " " + "%(sessions)s" + " " +
                           _("sessions and detected") + " " +
                           "%(attacks)s" + " " + _("attacks of which") +
                           " " + "%(blocks)s" + " " + _("were blocked"))

    @print_timing
    def get_highlights(self, end_date, report_days,
                       host=None, user=None, email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """
SELECT COALESCE(SUM(new_sessions),0)::int AS sessions,
       COALESCE(sum(CASE WHEN NULLIF(ips_description,'') IS NULL THEN 0 ELSE 1 END), 0) AS attacks,
       COALESCE(sum(ips_blocks), 0) AS blocks
FROM reports.session_totals
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone"""

        if host:
            query = query + " AND hostname = %s"
        elif user:
            query = query + " AND username = %s"

        conn = sql_helper.get_connection()
        curs = conn.cursor()

        h = {}
        try:
            if host:
                curs.execute(query, (one_week, ed, host))
            elif user:
                curs.execute(query, (one_week, ed, user))
            else:
                curs.execute(query, (one_week, ed))

            h = sql_helper.get_result_dictionary(curs)
            
        finally:
            conn.commit()

        return h

class TopTenAttacksByHits(Graph):
    def __init__(self, vendor_name):
        Graph.__init__(self, 'top-attacks-by-hits', _('Top Attacks (by Hits)'))

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
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone
AND ips_description != ''"""

        if host:
            query += " AND hostname = %s"
        elif user:
            query += " AND username = %s"

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
                ks = KeyStatistic(r[0], r[1], _('Hits'))
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title, xlabel=_('Attacks'),
                     ylabel=_('Hits Per Day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class DailyUsage(Graph):
    def __init__(self, vendor_name):
        Graph.__init__(self, 'attacks', _('Attacks'))

        self.__vendor_name = vendor_name

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None, email=None):
        if email:
            return None

        start_date = end_date - mx.DateTime.DateTimeDelta(report_days)

        lks = []
        conn = sql_helper.get_connection()
        curs = conn.cursor()
        try:
            if report_days == 1:
                time_interval = 60 * 60
                unit = "Hour"
                formatter = HOUR_FORMATTER
            else:
                time_interval = 24 * 60 * 60
                unit = "Day"
                formatter = DATE_FORMATTER

            sums = ["COUNT(*)"]

            extra_where = [("ips_description != ''", {})]
            if host:
                extra_where.append(("hostname = %(host)s", { 'host' : host }))
            elif user:
                extra_where.append(("username = %(user)s" , { 'user' : user }))

            q, h = sql_helper.get_averaged_query(sums, "reports.session_totals",
                                                 start_date,
                                                 end_date,
                                                 extra_where = extra_where,
                                                 time_interval = time_interval)
            curs.execute(q, h)

            dates = []
            attacks = []
            
            for r in curs.fetchall():
                dates.append(r[0])
                attacks.append(r[1])

            if not attacks:
                attacks = [0,]

            rp = sql_helper.get_required_points(start_date, end_date,
                                                mx.DateTime.DateTimeDeltaFromSeconds(time_interval))

            ks = KeyStatistic(_('Avg Attacks Blocked'),
                              sum(attacks) / len(rp),
                              _('Blocks')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max Attacks Blocked'), max(attacks),
                              _('Blocks')+'/'+_(unit))
            lks.append(ks)

            plot = Chart(type=STACKED_BAR_CHART,
                         title=self.title, xlabel=_(unit),
                         ylabel=_('Attacks'),
                         major_formatter=TIMESTAMP_FORMATTER,
                         required_points=rp)

            plot.add_dataset(dates, attacks, label=_('Attacks'))
        finally:
            conn.commit()

        return (lks, plot)

class IpsDetail(DetailSection):
    def __init__(self):
        DetailSection.__init__(self, 'attack-events', _('Intrusion Events'))

    def get_columns(self, host=None, user=None, email=None):
        if email:
            return None

        rv = [ColumnDesc('time_stamp', _('Time'), 'Date')]

        if not host:
            rv.append(ColumnDesc('hostname', _('Client'), 'HostLink'))
        if not user:
            rv.append(ColumnDesc('username', _('User'), 'UserLink'))

        rv = rv + [ColumnDesc('ips_ruleid', _('Rule ID')),
                   ColumnDesc('ips_description', _('Description')),
                   ColumnDesc('ips_blocked', _('Blocked')),
                   ColumnDesc('s_server_addr', _('Server')),
                   ColumnDesc('s_server_port', _('Port'))]

        return rv

    def get_all_columns(self, host=None, user=None, email=None):
        return self.get_session_columns(host, user, email)
    
    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if email:
            return None

        sql = "SELECT * "
        sql = sql + ("""FROM reports.sessions
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone
AND NOT ips_description ISNULL
AND ips_description != '' """ % (DateFromMx(start_date),
                                 DateFromMx(end_date)))

        if host:
            sql = sql + (" AND hostname = %s" % QuotedString(host))
        if user:
            sql = sql + (" AND username = %s" % QuotedString(user))

        return sql + " ORDER BY time_stamp DESC"

reports.engine.register_node(Ips('untangle-node-ips', 'IPS', 'ips'))

import gettext
import logging
import mx
import reports.i18n_helper
import reports.engine
import reports.sql_helper as sql_helper

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
from reports import SummarySection
from reports import TIMESTAMP_FORMATTER
from reports import TIME_OF_DAY_FORMATTER
from reports import TIME_SERIES_CHART
from reports.engine import Column
from reports.engine import FactTable
from reports.engine import Node
from reports.engine import TOP_LEVEL
from reports.sql_helper import print_timing

_ = reports.i18n_helper.get_translation('untangle-node-openvpn').lgettext
def N_(message): return message

class OpenVpn(Node):
    def __init__(self):
        Node.__init__(self, 'untangle-node-openvpn','OpenVPN')

    def setup(self):
        self.__create_openvpn_stats( )

        ft = FactTable('reports.openvpn_connect_totals',
                       'reports.openvpn_stats',
                       'time_stamp',
                       [Column('client_name', 'text'),
                        Column('remote_address', 'inet'),
                        Column('remote_port', 'integer')],
                       [Column('rx_bytes', 'bigint', 'sum(rx_bytes)'),
                        Column('tx_bytes', 'bigint', 'sum(tx_bytes)')])
        reports.engine.register_fact_table(ft)

    def get_toc_membership(self):
        return [TOP_LEVEL]

    def parents(self):
        return ['untangle-vm']

    def get_report(self):
        sections = []

        s = SummarySection('summary', _('Summary Report'),
                           [OpenvpnHighlight(self.name),
                            BandwidthUsage(), TopUsers()])
        sections.append(s)

        sections.append(OpenVpnDetail())

        return Report(self, sections)

    @print_timing
    def reports_cleanup(self, cutoff):
        sql_helper.drop_fact_table("openvpn_stats", cutoff)
        sql_helper.drop_fact_table("openvpn_connect_totals", cutoff)

    @print_timing
    def __create_openvpn_stats( self ):
        sql_helper.create_fact_table("""\
CREATE TABLE reports.openvpn_stats (
    time_stamp timestamp without time zone,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    rx_bytes bigint,
    tx_bytes bigint,
    remote_address inet,
    pool_address inet,
    remote_port integer,
    client_name text,
    event_id bigserial
)""")

        sql_helper.add_column('reports', 'openvpn_stats', 'pool_address', 'inet')
        
        sql_helper.create_index("reports","openvpn_stats","event_id");
        sql_helper.create_index("reports","openvpn_stats","time_stamp");

class OpenvpnHighlight(Highlight):
    def __init__(self, name):
        Highlight.__init__(self, name,
                           _(name) + " " +
                           _("securely passed") + " " + "%(traffic)s" + " " +
                           _("MB") + " " + _("of traffic and processed") +
                           " " + "%(logins)s" + " " + _("remote logins"))

    @print_timing
    def get_highlights(self, end_date, report_days,
                       host=None, user=None, email=None):
        if host or user or email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """
SELECT (COALESCE(sum(rx_bytes + tx_bytes), 0) / 1000000)::int AS traffic,
       count(*) AS logins
FROM reports.openvpn_connect_totals
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone"""

        conn = sql_helper.get_connection()
        curs = conn.cursor()

        h = {}
        try:
            curs.execute(query, (one_week, ed))

            h = sql_helper.get_result_dictionary(curs)
                
        finally:
            conn.commit()

        return h

class BandwidthUsage(Graph):
    def __init__(self):
        Graph.__init__(self, 'bandwidth-usage', _('Bandwidth Usage'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if host or user or email:
            return None

        start_date = end_date - mx.DateTime.DateTimeDelta(report_days)

        lks = []

        conn = sql_helper.get_connection()
        curs = conn.cursor()
        try:
            # kB
            sums = ["ROUND(COALESCE(SUM(rx_bytes + tx_bytes) / 1000, 0)::numeric, 2)"]

            extra_where = []
            if host:
                extra_where.append(("hostname = %(host)s", { 'host' : host }))
            elif user:
                extra_where.append(("username = %(user)s" , { 'user' : user }))

            time_interval = 3600
            q, h = sql_helper.get_averaged_query(sums, "reports.openvpn_stats",
                                                 start_date,
                                                 end_date,
                                                 extra_where = extra_where,
                                                 time_field = "time_stamp",
                                                 time_interval = time_interval)
            curs.execute(q, h)

            dates = []
            throughput = []

            for r in curs.fetchall():
                dates.append(r[0])
                throughput.append(float(r[1]) / time_interval)

            if not throughput:
                throughput = [0,]
                
            ks = KeyStatistic(_('Avg Data Rate'),
                              "%.2f" % (sum(throughput)/len(throughput)),
                              N_('kB/s'))
            lks.append(ks)
            ks = KeyStatistic(_('Max Data Rate'), max(throughput), 
                              N_('kB/s'))
            lks.append(ks)
            ks = KeyStatistic(_('Data Transferred'), sum(throughput) * time_interval, N_('kB'))
            lks.append(ks)

            plot = Chart(type=TIME_SERIES_CHART,
                         title=_('Bandwidth Usage'),
                         xlabel=_('Date'),
                         ylabel=_('Throughput (kB/s)'),
                         major_formatter=TIMESTAMP_FORMATTER)

            plot.add_dataset(dates, throughput, _('Usage (kB/sec)'))
        finally:
            conn.commit()

        return (lks, plot)

class TopUsers(Graph):
    def __init__(self):
        Graph.__init__(self, 'top-users', _('Top Users'))

    @print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if host or user or email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT client_name, (sum(rx_bytes + tx_bytes)/1000000)::int AS throughput
FROM reports.openvpn_connect_totals
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone
GROUP BY client_name
ORDER BY throughput desc"""

        conn = sql_helper.get_connection()
        try:
            curs = conn.cursor()

            curs.execute(query, (one_week, ed))

            lks = []
            pds = {}

            for r in curs.fetchall():
                client_name = r[0]
                num = r[1]

                lks.append(KeyStatistic(client_name, num, _('MB')))
                pds[client_name] = num
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART, title=_('Top Users'))

        plot.add_pie_dataset(pds, display_limit=10)

        return (lks[0:10], plot)

class OpenVpnDetail(DetailSection):
    def __init__(self):
        DetailSection.__init__(self, 'login-events', _('Login Events'))

    def get_columns(self, host=None, user=None, email=None):
        if host or user or email:
            return None

        rv = [ColumnDesc('time_stamp', _('Time'), 'Date')]

        rv = rv + [ColumnDesc('client_name', _('Client'))]
        rv = rv + [ColumnDesc('remote_address', _('Address'))]
        rv = rv + [ColumnDesc('remote_port', _('Port'))]

        return rv
    
    def get_all_columns(self, host=None, user=None, email=None):
        return self.get_columns(host, user, email)

    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if host or user or email:
            return None

        sql = "SELECT distinct(time_stamp), client_name, host(remote_address) as remote_address, remote_port"

        sql = sql + ("""
FROM reports.openvpn_connect_totals
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone""" % (DateFromMx(start_date), DateFromMx(end_date)))

        return sql + " ORDER BY time_stamp DESC"

reports.engine.register_node(OpenVpn())


import gettext
import logging
import mx
import reports.colors as colors
import reports.engine
import reports.sql_helper as sql_helper
import uvm.i18n_helper

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
from reports import TIME_SERIES_CHART
from reports.engine import Column
from reports.engine import HOST_DRILLDOWN
from reports.engine import Node
from reports.engine import TOP_LEVEL
from reports.engine import USER_DRILLDOWN
from reports.sql_helper import print_timing

from reports.log import *
logger = getLogger(__name__)

_ = uvm.i18n_helper.get_translation('untangle-base-web-filter').lgettext

def N_(message): return message

class WebFilterBaseNode(Node):
    def __init__(self, node_name, title, short_name):
        Node.__init__(self, node_name, title)

        self.__short_name = short_name

    def parents(self):
        return ['untangle-casing-http']

    @sql_helper.print_timing
    def setup(self):
        ft = reports.engine.get_fact_table('reports.http_totals')
        ft.measures.append(Column('%s_blocks' % self.__short_name, 'integer',
                                  "count(CASE WHEN %s_blocked THEN 1 ELSE null END)"
                                  % self.__short_name))
        ft.measures.append(Column('%s_violations' % self.__short_name, 'integer',
                                  "count(CASE WHEN %s_flagged THEN 1 ELSE null END)"
                                  % self.__short_name))
        ft.dimensions.append(Column('%s_category' % self.__short_name, 'text'))
        ft.dimensions.append(Column('%s_reason' % self.__short_name, 'text'))

    def get_toc_membership(self):
        return [TOP_LEVEL, HOST_DRILLDOWN, USER_DRILLDOWN]

    def get_report(self, summariesAppend = None, detailsAppend = None ):
        if summariesAppend is None:
            summariesAppend = []
        if detailsAppend is None:
            detailsAppend = []

        sections = []

        summaryReports = [
            WebHighlight(self.name, self.__short_name),
            DailyWebUsage(self.__short_name),
            TotalWebUsage(self.__short_name),
            TopTenWebBrowsingHostsByHits(self.__short_name),
            TopTenWebBrowsingHostsBySize(self.__short_name),
            TopTenWebBrowsingUsersByHits(self.__short_name),
            TopTenWebBrowsingUsersBySize(self.__short_name),
            TopTenWebPolicyViolationsByHits(self.__short_name),
            TopTenWebBlockedPolicyViolationsByHits(self.__short_name),
            TopTenWebsitesByHits(self.__short_name),
            TopTenWebsitesBySize(self.__short_name),
            TopTenWebPolicyViolatorsByHits(self.__short_name),
            TopTenWebPolicyViolatorsADByHits(self.__short_name),
            TopTenPolicyViolations(self.__short_name),
            TopTenBlockedPolicyViolations(self.__short_name)
            ];
        summaryReports.extend( summariesAppend )

        s = SummarySection(
                'summary', 
                _('Summary Report'),
                summaryReports 
            );

        sections.append(s)

        sections.append(WebFilterDetail(self.__short_name))
        sections.append(WebFilterDetailAll(self.__short_name))
        sections.append(WebFilterDetailDomains(self.__short_name))

        if len( detailsAppend ):
            for detail in detailsAppend:
                sections.append( detail )

        return Report(self, sections)

    def reports_cleanup(self, cutoff):
        pass

class WebHighlight(Highlight):
    def __init__(self, name, node_name):
        Highlight.__init__(self, name,
                           _(name) + " " +
                           _("scanned") + " " + "%(hits)s" + " " +
                           _("web hits and detected") + " " +
                           "%(violations)s" + " " + _("violations of which") +
                           " " + "%(blocks)s" + " " + _("were blocked"))
        self.__short_name = node_name

    @sql_helper.print_timing
    def get_highlights(self, end_date, report_days,
                       host=None, user=None, email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT COALESCE(sum(hits), 0)::int AS hits,
       COALESCE(sum(%s_violations), 0)::int AS violations,
       COALESCE(sum(%s_blocks), 0)::int AS blocks
FROM reports.http_totals
WHERE time_stamp >= %%s AND time_stamp < %%s
""" % (self.__short_name, self.__short_name)

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

class DailyWebUsage(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'web-usage', _('Web Usage'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None, email=None):
        if email:
            return None

        start_date = end_date - mx.DateTime.DateTimeDelta(report_days)

        lks = []
        
        conn = sql_helper.get_connection()
        curs = conn.cursor()

        try:
            sums = ["COALESCE(SUM(hits), 0)::float",
                    "COALESCE(SUM(%s_blocks), 0)::float" % (self.__short_name,),
                    "COALESCE(SUM(%s_violations), 0)::float" % (self.__short_name,)]

            extra_where = []
            if host:
                extra_where.append(("hostname = %(host)s", { 'host' : host }))
            elif user:
                extra_where.append(("username = %(user)s" , { 'user' : user }))

            if report_days == 1:
                time_interval = 60 * 60
                unit = "Hour"
                formatter = HOUR_FORMATTER
            else:
                time_interval = 24 * 60 * 60
                unit = "Day"
                formatter = DATE_FORMATTER
                
            q, h = sql_helper.get_averaged_query(sums, "reports.http_totals",
                                                 start_date,
                                                 end_date,
                                                 extra_where = extra_where,
                                                 time_interval = time_interval)
            curs.execute(q, h)

            dates = []
            hits = []
            blocks = []
            violations = []

            while 1:
                r = curs.fetchone()
                if not r:
                    break
                dates.append(r[0])
                hits.append(r[1]-r[2])
                blocks.append(r[2])
                violations.append(r[3]-r[2])

            rp = sql_helper.get_required_points(start_date, end_date,
                                            mx.DateTime.DateTimeDeltaFromSeconds(time_interval))

            if not hits:
                hits = [0,]
            if not blocks:
                blocks = [0,]
            if not violations:
                violations = [0,]

            ks = KeyStatistic(_('Avg Hits'), sum(hits) / len(rp), _('Hits')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max Hits'), max(hits), _('Hits')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Avg Violations'), sum(violations) / len(rp), _('Violations')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max Violations'), max(violations), _('Violations')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Avg Blocks'), sum(blocks) / len(rp), _('Blocks')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max Blocks'), max(blocks), _('Blocks')+'/'+_(unit))
            lks.append(ks)

        finally:
            conn.commit()

        plot = Chart(type=STACKED_BAR_CHART,
                     title=self.title,
                     xlabel=_(unit),
                     ylabel=_('Hits'),
                     major_formatter=formatter,
                     required_points=rp)

        plot.add_dataset(dates, hits, label=_('Clean Hits'), color=colors.goodness)
        plot.add_dataset(dates, violations, label=_('Violations'), color=colors.detected)
        plot.add_dataset(dates, blocks, label=_('Blocks'), color=colors.badness)

        return (lks, plot)

class TotalWebUsage(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'total-web-usage', _('Total Web Usage'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT COALESCE(sum(hits)::int, 0),
       COALESCE(sum(%s_violations), 0)::int AS violations,
       COALESCE(sum(%s_blocks), 0)::int AS blocks
FROM reports.http_totals
WHERE time_stamp >= %%s AND time_stamp < %%s""" % (self.__short_name,
                                                   self.__short_name)
        if host:
            query = query + " AND hostname = %s"
        elif user:
            query = query + " AND username = %s"

        conn = sql_helper.get_connection()
        try:
            lks = []

            curs = conn.cursor()

            if host:
                curs.execute(query, (one_week, ed, host))
            elif user:
                curs.execute(query, (one_week, ed, user))
            else:
                curs.execute(query, (one_week, ed))
            r = curs.fetchone()

            hits = r[0]
            blocks = r[2]
            violations = r[1] - blocks
            
            ks = KeyStatistic(_('Total Clean Hits'), hits-violations-blocks, 'Hits')
            lks.append(ks)
            ks = KeyStatistic(_('Total Violations'), violations, 'Violations')
            lks.append(ks)
            ks = KeyStatistic(_('Total Blocked Violations'), blocks, 'Blocks')
            lks.append(ks)
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART, title=self.title, xlabel=_('Date'),
                     ylabel=_('Hits Per Day'))

        plot.add_pie_dataset({_('Total Clean Hits'): hits-violations-blocks,
                              _('Total Violations'): violations,
                              _('Total Blocked Violations'): blocks},
                             colors={_('Total Clean Hits'): colors.goodness,
                                     _('Total Violations'): colors.detected,
                                     _('Total Blocked Violations'): colors.badness})

        return (lks, plot)

class TopTenWebPolicyViolationsByHits(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-categories-of-violations-by-hits',
                       _('Top Categories Of Violations (by Hits)'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT %s_category, count(*)::int AS blocks_sum
FROM reports.http_totals
WHERE time_stamp >= %%s AND time_stamp < %%s
AND %s_violations > 0
""" % (2 * (self.__short_name,))
        if host:
            query = query + " AND hostname = %s"
        elif user:
            query = query + " AND username = %s"
        query += """
GROUP BY %s_category ORDER BY blocks_sum DESC
""" % (self.__short_name,)

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
                cat = r[0]
                if not cat or cat == '' or cat is None:
                    cat = _('Uncategorized')
                ks = KeyStatistic(cat, r[1], _('Hits'))
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('Policy'),
                     ylabel=_('Hits Per Day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class TopTenWebBlockedPolicyViolationsByHits(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-web-categories-of-blocked-violations-by-hits',
                       _('Top Categories Of Blocked Violations (by Hits)'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT %s_category, sum(%s_blocks)::int AS blocks_sum
FROM reports.http_totals
WHERE time_stamp >= %%s AND time_stamp < %%s
AND %s_blocks > 0
""" % (3 * (self.__short_name,))
        if host:
            query = query + " AND hostname = %s"
        elif user:
            query = query + " AND username = %s"
        query += """
GROUP BY %s_category ORDER BY blocks_sum DESC""" \
            % self.__short_name

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
                name = r[0]
                if name is None:
                    name = "None"
                ks = KeyStatistic(name, r[1], _('Hits'))
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('Policy'),
                     ylabel=_('Blocks Per Day'))
        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class TopTenWebBrowsingHostsByHits(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-web-browsing-hosts-by-hits',
                       _('Top Web Browsing Hosts (by Hits)'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if host or user or email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT hostname, sum(hits)::int as hits_sum
FROM reports.http_totals
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone
GROUP BY hostname ORDER BY hits_sum DESC"""

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            curs.execute(query, (one_week, ed))
            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('Hits'),
                                  link_type=reports.HNAME_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]

        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('Host'),
                     ylabel=_('Hits Per Day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class TopTenWebBrowsingUsersByHits(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-web-browsing-users-by-hits',
                       _('Top Web Browsing Users (by Hits)'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if host or user or email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT username, sum(hits)::int as hits_sum
FROM reports.http_totals
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone AND NOT username IS NULL AND username != ''
GROUP BY username ORDER BY hits_sum DESC"""

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            curs.execute(query, (one_week, ed))
            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('Hits'),
                                  link_type=reports.USER_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]

        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('User'),
                     ylabel=_('Hits Per Day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class TopTenWebBrowsingUsersBySize(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-web-browsing-users-by-size',
                       _('Top Web Browsing Users (by Size)'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if host or user or email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT username, COALESCE(sum(s2c_content_length)/1000000, 0)::bigint as size_sum
FROM reports.http_totals
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone AND NOT username IS NULL AND username != ''
GROUP BY username ORDER BY size_sum DESC"""

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            curs.execute(query, (one_week, ed))
            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('MB'),
                                  link_type=reports.USER_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]

        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('User'),
                     ylabel=_('MB/day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class TopTenWebPolicyViolatorsByHits(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-host-violators-by-hits',
                       _('Top Host Violators (by Hits)'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if host or user or email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT hostname, COALESCE(sum(%s_blocks), 0)::int as blocks_sum
FROM reports.http_totals
WHERE time_stamp >= %%s AND time_stamp < %%s
AND %s_blocks > 0
GROUP BY hostname
ORDER BY blocks_sum DESC""" % ((self.__short_name,)*2)

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            curs.execute(query, (one_week, ed))
            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('Hits'),
                                  link_type=reports.HNAME_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]

        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('Host'),
                     ylabel=_('Hits Per Day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class TopTenWebPolicyViolatorsADByHits(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-violators-by-hits',
                       _('Top User Violators (by Hits)'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                           email=None):
        if host or user or email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT username, sum(%s_blocks)::int as blocks_sum
FROM reports.http_totals
WHERE time_stamp >= %%s AND time_stamp < %%s
AND %s_blocks > 0
AND username != ''
GROUP BY username ORDER BY blocks_sum DESC""" \
            % (2 * (self.__short_name,))

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            curs.execute(query, (one_week, ed))
            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], _('Hits'),
                                  link_type=reports.USER_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('Uid'),
                     ylabel=_('Hits Per Day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class TopTenWebBrowsingHostsBySize(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-web-browsing-hosts-by-size',
                       _('Top Web Browsing Hosts (by Size)'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if host or user or email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT hostname, COALESCE(sum(s2c_content_length)/1000000, 0)::bigint as size_sum
FROM reports.http_totals
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone"""
        query += " GROUP BY hostname ORDER BY size_sum DESC"

        conn = sql_helper.get_connection()
        try:
            lks = []
            dataset = {}

            curs = conn.cursor()

            curs.execute(query, (one_week, ed))
            for r in curs.fetchall():
                ks = KeyStatistic(r[0], r[1], N_('MB'),
                                  link_type=reports.HNAME_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]

        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('Host'),
                     ylabel=_('MB/day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class TopTenWebsitesByHits(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-websites-by-hits',
                       _('Top Websites (by Hits)'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT host, sum(hits)::int as hits_sum
FROM reports.http_totals
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone"""
        if host:
            query += " AND hostname = %s"
        elif user:
            query += " AND username = %s"
        query += " GROUP BY host ORDER BY hits_sum DESC"

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
                name = r[0] if r[0] is not None else "none"
                ks = KeyStatistic(name, r[1], _('Hits'), link_type=reports.URL_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('Hosts'),
                     ylabel=_('Hits Per Day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class TopTenWebsitesBySize(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-websites-by-size',
                       _('Top Websites (by Size)'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT host, coalesce(sum(s2c_content_length)/1000000, 0)::bigint as size_sum
FROM reports.http_totals
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone"""
        if host:
            query += " AND hostname = %s"
        elif user:
            query += " AND username = %s"
        query += """
GROUP BY host ORDER BY size_sum DESC"""

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
                name = r[0] if r[0] is not None else "none"
                ks = KeyStatistic(name, r[1], N_('MB'), link_type=reports.URL_LINK)
                lks.append(ks)
                dataset[r[0]] = r[1]

        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('Hosts'),
                     ylabel=_('MB/day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class TopTenPolicyViolations(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-violations',
                       _('Top Violations'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT host, sum(hits)::int as hits_sum
FROM reports.http_totals
WHERE time_stamp >= %%s AND time_stamp < %%s
AND %s_violations > 0
""" % (self.__short_name,)
        if host:
            query += " AND hostname = %s"
        elif user:
            query += " AND username = %s"
        query += " GROUP BY host ORDER BY hits_sum DESC"

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
                host = r[0]
                if host and len(host) > 25:
                    host = host[:25] + "..."
                ks = KeyStatistic(host, r[1], _('Hits'))
                lks.append(ks)
                dataset[host] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('Hosts'),
                     ylabel=_('Hits Per Day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class TopTenBlockedPolicyViolations(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'top-blocked-violations',
                       _('Top Blocked Violations'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT host, COALESCE(sum(hits), 0)::int as hits_sum
FROM reports.http_totals
WHERE time_stamp >= %%s AND time_stamp < %%s
AND %s_blocks > 0
""" % (self.__short_name,)
        if host:
            query += " AND hostname = %s"
        elif user:
            query += " AND username = %s"
        query += " GROUP BY host ORDER BY hits_sum DESC"

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
                     title=self.title,
                     xlabel=_('Hosts'),
                     ylabel=_('Hits Per Day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks, plot, 10)

class WebFilterDetail(DetailSection):
    def __init__(self, node_name):
        DetailSection.__init__(self, 'violations', _('Violation Events'))

        self.__short_name = node_name

    def get_columns(self, host=None, user=None, email=None):
        if email:
            return None

        rv = [ColumnDesc('time_stamp', _('Time'), 'Date')]

        if host:
            rv.append(ColumnDesc('hostname', _('Client')))
        else:
            rv.append(ColumnDesc('hostname', _('Client'), 'HostLink'))

        if user:
            rv.append(ColumnDesc('username', _('User')))
        else:
            rv.append(ColumnDesc('username', _('User'), 'UserLink'))

        rv += [ColumnDesc('%s_category' % self.__short_name, _('Category')),
               ColumnDesc('%s_flagged' % self.__short_name, _('Flagged')),
               ColumnDesc('%s_blocked' % self.__short_name, _('Blocked')),
               ColumnDesc('url', _('Url'), 'URL'),
               ColumnDesc('s_server_addr', _('Server Ip')),
               ColumnDesc('c_client_addr', _('Client Ip'))]

        return rv
    
    def get_all_columns(self, host=None, user=None, email=None):
        return self.get_http_columns(host, user, email)

    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if email:
            return None

        sql = """\
SELECT *,
       CASE s_server_port WHEN 443 THEN 'https://' ELSE 'http://' END || host || uri AS url
FROM reports.http_events
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone
AND (%s_flagged OR %s_blocked)
""" % (DateFromMx(start_date), DateFromMx(end_date),
       self.__short_name, self.__short_name)

        if host:
            sql += " AND hostname = %s" % QuotedString(host)
        if user:
            sql += " AND username = %s" % QuotedString(user)

        return sql + " ORDER BY time_stamp DESC"

class WebFilterDetailAll(DetailSection):
    def __init__(self, node_name):
        DetailSection.__init__(self, 'events', _('All Events'))

        self.__short_name = node_name

    def get_columns(self, host=None, user=None, email=None):
        if email:
            return None

        rv = [ColumnDesc('time_stamp', _('Time'), 'Date')]

        if host:
            rv.append(ColumnDesc('hostname', _('Client')))
        else:
            rv.append(ColumnDesc('hostname', _('Client'), 'HostLink'))

        if user:
            rv.append(ColumnDesc('username', _('User')))
        else:
            rv.append(ColumnDesc('username', _('User'), 'UserLink'))

        rv += [ColumnDesc('%s_category' % self.__short_name, _('Category')),
               ColumnDesc('%s_flagged' % self.__short_name, _('Flagged')),
               ColumnDesc('%s_blocked' % self.__short_name, _('Blocked')),
               ColumnDesc('url', _('Url'), 'URL'),
               ColumnDesc('s_server_addr', _('Server Ip')),
               ColumnDesc('c_client_addr', _('Client Ip'))]

        return rv

    def get_all_columns(self, host=None, user=None, email=None):
        return self.get_http_columns(host, user, email)
    
    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if email:
            return None

        sql = """\
SELECT *,
       CASE s_server_port WHEN 443 THEN 'https://' ELSE 'http://' END || host || uri AS url
FROM reports.http_events
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone""" % (
                                                 DateFromMx(start_date),
                                                 DateFromMx(end_date))

        if host:
            sql += " AND hostname = %s" % QuotedString(host)
        if user:
            sql += " AND username = %s" % QuotedString(user)

        return sql + " ORDER BY time_stamp DESC"

class WebFilterDetailDomains(DetailSection):
    def __init__(self, node_name):
        DetailSection.__init__(self, 'domains', _('Site Events'))

        self.__short_name = node_name

    def get_columns(self, host=None, user=None, email=None):
        if email:
            return None

        rv = [ColumnDesc('host', _('Site')),
              ColumnDesc('hits', _('Hits')),
              ColumnDesc('size', _('Size (MB)'))]

        return rv
    
    def get_all_columns(self, host=None, user=None, email=None):
        return self.get_columns(host, user, email)

    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if email:
            return None

        sql = """\
SELECT host,
       count(*) AS hits, round(COALESCE(sum(s2c_content_length) / 10^6, 0)::numeric, 2)::float as size
FROM reports.http_events
WHERE time_stamp >= %s::timestamp without time zone AND time_stamp < %s::timestamp without time zone
"""  % (DateFromMx(start_date),
        DateFromMx(end_date))

        if host:
            sql += " AND hostname = %s" % QuotedString(host)
        if user:
            sql += " AND username = %s" % QuotedString(user)

        sql += " GROUP BY host"

        return sql + " ORDER BY hits DESC"

# Unused reports --------------------------------------------------------------

class WebUsageByCategory(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'web-usage-by-category',
                       _('Web Usage By Category'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT %s_category, count(*) AS count_events
FROM reports.http_events
WHERE time_stamp >= %%s AND time_stamp < %%s""" % self.__short_name
        if host:
            query += " AND hostname = %s"
        elif user:
            query += " AND username = %s"
        query += """\
GROUP BY %s_category
ORDER BY count_events DESC""" % self.__short_name

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
                stat_key = r[0]
                if stat_key is None:
                    stat_key = _('Uncategorized')
                ks = KeyStatistic(stat_key, r[1], _('Hits'))
                lks.append(ks)
                dataset[stat_key] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('Category'),
                     ylabel=_('Hits Per Day'))
        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks[0:10], plot)

class ViolationsByCategory(Graph):
    def __init__(self, node_name):
        Graph.__init__(self, 'violations-by-category',
                       _('Violations By Category'))

        self.__short_name = node_name

    @sql_helper.print_timing
    def get_graph(self, end_date, report_days, host=None, user=None,
                  email=None):
        if email:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT %s_category, count(*) as blocks_sum
FROM reports.http_events
WHERE time_stamp >= %%s AND time_stamp < %%s
AND %s_flagged """ % (2 * (self.__short_name,))
        if host:
            query += " AND hostname = %s"
        elif user:
            query += " AND username = %s"
        query += """\
GROUP BY %s_category
ORDER BY blocks_sum DESC""" % self.__short_name

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
                stat_key = r[0]
                if stat_key is None:
                    stat_key = _('Uncategorized')
                ks = KeyStatistic(stat_key, r[1], _('Hits'))
                lks.append(ks)
                dataset[stat_key] = r[1]
        finally:
            conn.commit()

        plot = Chart(type=PIE_CHART,
                     title=self.title,
                     xlabel=_('Category'),
                     ylabel=_('Hits Per Day'))

        plot.add_pie_dataset(dataset, display_limit=10)

        return (lks[0:10], plot)

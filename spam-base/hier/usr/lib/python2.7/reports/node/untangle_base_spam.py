import gettext
import logging
import mx
import reports.colors as colors
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
from reports import STACKED_BAR_CHART
from reports import SummarySection
from reports import TIMESTAMP_FORMATTER
from reports import TIME_OF_DAY_FORMATTER
from reports import TIME_SERIES_CHART
from reports.engine import Column
from reports.engine import EMAIL_DRILLDOWN
from reports.engine import Node
from reports.engine import TOP_LEVEL
from reports.sql_helper import print_timing

_ = reports.i18n_helper.get_translation('untangle-base-spam').lgettext

class SpamBaseNode(Node):
    def __init__(self, node_name, title, short_name, vendor_name, spam_label,
                 ham_label, hourly_spam_rate_title, daily_spam_rate_title,
                 top_spammed_title):
        Node.__init__(self, node_name, title)

        self.__title = title
        self.__short_name = short_name
        self.__vendor_name = vendor_name
        self.__spam_label = spam_label
        self.__ham_label = ham_label
        self.__hourly_spam_rate_title = hourly_spam_rate_title
        self.__daily_spam_rate_title = daily_spam_rate_title
        self.__top_spammed_title = top_spammed_title

    def parents(self):
        return ['untangle-casing-smtp']

    @print_timing
    def setup(self):
        self.__create_spam_smtp_tarpit_events(  )

        column = Column('%s_spam_msgs' % self.__short_name, 'integer',
                        "count(CASE WHEN %s_is_spam THEN 1 ELSE null END)" \
                            % self.__short_name)

        ft = reports.engine.get_fact_table('reports.mail_msg_totals')
        ft.measures.append(column)

        ft = reports.engine.get_fact_table('reports.mail_addr_totals')
        ft.measures.append(column)

    def get_toc_membership(self):
        return [TOP_LEVEL, EMAIL_DRILLDOWN]

    def get_report(self):
        sections = []

        s = SummarySection('summary', _('Summary Report'),
                           [SpamHighlight(self.name, self.__short_name, self.__spam_label),
                            TotalEmail(self.__short_name, self.__vendor_name,
                                       self.__spam_label, self.__ham_label),
#                             HourlySpamRate(self.__short_name,
#                                            self.__vendor_name,
#                                            self.__spam_label,
#                                            self.__ham_label,
#                                            self.__hourly_spam_rate_title),
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
        sections.append(SpamDetailAll(self.__title, self.__short_name))

        return Report(self, sections)

    def reports_cleanup(self, cutoff):
        sql_helper.drop_fact_table('spam_smtp_tarpit_events', cutoff)

    @print_timing
    def __create_spam_smtp_tarpit_events(self):
        sql_helper.create_fact_table("""\
CREATE TABLE reports.spam_smtp_tarpit_events (
    time_stamp timestamp without time zone,
    ipaddr inet,
    hostname text,
    policy_id int8,
    vendor_name varchar(255),
    event_id bigserial)""")

        sql_helper.create_index("reports","spam_smtp_tarpit_events","event_id");
        sql_helper.create_index("reports","spam_smtp_tarpit_events","time_stamp");

class SpamHighlight(Highlight):
    def __init__(self, name, short_name, spam_label):
        Highlight.__init__(self, name,
                           _(name) + " " +
                           _("scanned") + " " +
                           "%(messages)s" + " " +
                           _("messages and detected and processed") +
                           " " +
                           "%(spam)s" + " " + _(spam_label.lower()) + _(" messages"))
        self.__short_name = short_name

    @print_timing
    def get_highlights(self, end_date, report_days,
                       host=None, user=None, email=None):
        if host or user:
            return None

        ed = DateFromMx(end_date)
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """\
SELECT coalesce(sum(msgs), 0)::int AS messages,
       coalesce(sum(%s_spam_msgs), 0)::int AS spam
FROM reports.mail_addr_totals
WHERE time_stamp >= %%s AND time_stamp < %%s
AND addr_kind = 'T'""" % (self.__short_name,)

        if email:
            query += " AND addr = %s"
        else:
            query += " AND addr_pos = '1'"

        conn = sql_helper.get_connection()
        curs = conn.cursor()

        h = {}
        try:
            if email:
                curs.execute(query, (one_week, ed, email))
            else:
                curs.execute(query, (one_week, ed))

            h = sql_helper.get_result_dictionary(curs)
                
        finally:
            conn.commit()

        return h

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
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        query = """
SELECT coalesce(sum(msgs), 0)::int,
       coalesce(sum(%s_spam_msgs), 0)::int
FROM reports.mail_addr_totals
WHERE time_stamp >= %%s AND time_stamp < %%s
AND addr_kind = 'T'""" % (self.__short_name,)

        if email:
            query += " AND addr = %s"
        else:
            query += " AND addr_pos = '1'"

        conn = sql_helper.get_connection()
        try:
            lks = []

            curs = conn.cursor()

            if email:
                curs.execute(query, (one_week, ed, email))
            else:
                curs.execute(query, (one_week, ed))
            r = curs.fetchone()

            total = r[0]
            spam = r[1]
            ham = total - spam

            ks = KeyStatistic(_('Total'), total, _('Messages'))
            lks.append(ks)
            ks = KeyStatistic(self.__spam_label, spam, _('Messages'))
            lks.append(ks)
            ks = KeyStatistic(self.__ham_label, ham, _('Messages'))
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

            ks_query = """\
SELECT COALESCE(sum(msgs), 0)::float / (%%s * 24) AS email_rate,
       COALESCE(sum(%s_spam_msgs), 0)::float / (%%s * 24) AS spam_rate
FROM reports.mail_addr_totals
WHERE time_stamp >= %%s AND time_stamp < %%s
AND addr_kind = 'T'""" % (self.__short_name,)

            if email:
                ks_query += " AND addr = %s"
            else:
                ks_query += " AND addr_pos = '1'"
                
            if email:
                curs.execute(ks_query, (report_days, report_days, email,
                                        one_week, ed))
            else:
                curs.execute(ks_query, (report_days, report_days, one_week, ed))
            r = curs.fetchone()

            email_rate = r[0]
            spam_rate = r[1]
            ham_rate = email_rate - spam_rate

            ks = KeyStatistic(_('Mail Rate'), email_rate, _('Messages/hour'))
            lks.append(ks)
            ks = KeyStatistic(_('%s Rate') % self.__spam_label, spam_rate,
                              _('Messages/hour'))
            lks.append(ks)
            ks = KeyStatistic(_('%s Rate') % self.__ham_label, ham_rate,
                              _('Messages/hour'))
            lks.append(ks)

            # per hour
            sums = ["coalesce(sum(msgs), 0)::float * 60 * 60",
                    "coalesce(sum(%s_spam_msgs), 0)::float * 60 * 60 " % (self.__short_name)]

            if email:
                extra_where = (("addr_kind = 'T' AND addr = %(email)s", { 'email' : email }),)
            else:
                extra_where = (("addr_kind = 'T' AND addr_pos = '1'", {}),)
                
            q, h = sql_helper.get_averaged_query(sums, "reports.mail_addr_totals",
                                                 end_date - mx.DateTime.DateTimeDelta(report_days),
                                                 end_date,
                                                 extra_where = extra_where)
            curs.execute(q, h)

            dates = []
            ham = []
            spam = []

            for r in curs.fetchall():
                dates.append(r[0])
                m = r[1]
                s = r[2]
                h = m - s
                spam.append(s)
                ham.append(h)
        finally:
            conn.commit()

        plot = Chart(type=TIME_SERIES_CHART,
                     title=self.title,
                     xlabel=_('Hour Of Day'),
                     ylabel=_('Emails Per Hour'),
                     major_formatter=TIMESTAMP_FORMATTER)

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

        start_date = end_date - mx.DateTime.DateTimeDelta(report_days)

        conn = sql_helper.get_connection()
        try:
            lks = []

            sums = ["COALESCE(SUM(msgs)-SUM(%s_spam_msgs), 0)::float" % (self.__short_name,),
                    "COALESCE(SUM(%s_spam_msgs), 0)::float" % (self.__short_name,)]
            
            extra_where = [("addr_kind = 'T'", {})]
            if email:
                extra_where.append(("addr = %(email)s", { 'email' : email }))
            else:
                extra_where.append(("addr_pos = '1'" , {}))

            if report_days == 1:
                time_interval = 60 * 60
                unit = "Hour"
                formatter = HOUR_FORMATTER
            else:
                time_interval = 24 * 60 * 60
                unit = "Day"
                formatter = DATE_FORMATTER
                
            q, h = sql_helper.get_averaged_query(sums, "reports.mail_addr_totals",
                                                 start_date,
                                                 end_date,
                                                 extra_where = extra_where,
                                                 time_interval = time_interval)

            curs = conn.cursor()
            curs.execute(q, h)

            dates = []
            totals = []
            spams = []
            hams = []
            
            for r in curs.fetchall():
                dates.append(r[0])
                hams.append(float(r[1]))
                spams.append(float(r[2]))
                totals.append(float(r[1]+r[2]))                

            rp = sql_helper.get_required_points(start_date, end_date,
                                            mx.DateTime.DateTimeDeltaFromSeconds(time_interval))

            if not totals:
                totals = [0,]
                spams = [0,]
                hams = [0,]

            ks = KeyStatistic(_('Avg Mail Rate'), sum(totals)/len(rp), _('Messages')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max Mail Rate'), max(totals), _('Messages')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Avg %s Rate') % self.__spam_label, sum(spams)/len(rp),
                              _('Messages')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max %s Rate') % self.__spam_label, max(spams),
                              _('Messages')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Avg %s Rate') % self.__ham_label, sum(hams)/len(rp),
                              _('Messages')+'/'+_(unit))
            lks.append(ks)
            ks = KeyStatistic(_('Max %s Rate') % self.__ham_label, max(hams),
                              _('Messages')+'/'+_(unit))
            lks.append(ks)

            curs = conn.cursor()

        finally:
            conn.commit()

        plot = Chart(type=STACKED_BAR_CHART,
                     title=self.title,
                     xlabel=_(unit),
                     ylabel=_('Emails per ' + unit),
                     major_formatter=formatter,
                     required_points=rp)

        plot.add_dataset(dates, spams, gettext.gettext(self.__spam_label),
                         color=colors.badness)
        plot.add_dataset(dates, hams, gettext.gettext(self.__ham_label),
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
        one_week = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

        conn = sql_helper.get_connection()
        try:
            query = """\
SELECT foo.addr, foo.spam_msgs
FROM (SELECT addr, sum(%s_spam_msgs)::int AS spam_msgs
      FROM reports.mail_addr_totals
      WHERE addr_kind = 'T'
      AND addr_pos = '1'
      AND time_stamp >= %%s AND time_stamp < %%s
      GROUP BY addr) AS foo
WHERE foo.spam_msgs > 0
ORDER BY spam_msgs desc""" % (self.__short_name,)

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

        plot.add_pie_dataset(pds, display_limit=10)

        return lks, plot, 10

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

        rv.append(ColumnDesc('hostname', _('Client'), 'String'))

        rv += [ColumnDesc('%s_score' % (self.__short_name,), _('Score')),
               ColumnDesc('from_addrs', _('Msg Sender')),
               ColumnDesc('subject', _('Subject')),
               ColumnDesc('s_server_addr', _('Destination Ip')),
               ColumnDesc('action', _('Action')),
               ColumnDesc('to_addrs', _('Msg Receiver'), 'EmailLink')]

        return rv
    
    def get_all_columns(self, host=None, user=None, email=None):
        return self.get_columns(host, user, email)

    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if host or user:
            return None

        sql = """\
SELECT m1.time_stamp, m1.hostname, m1.%s_score, m2.addr AS from_addrs, m1.subject, host(m1.s_server_addr) as s_server_addr,
       CASE m1.%s_action WHEN 'P' THEN '%s'
                      WHEN 'D' THEN '%s'
                      WHEN 'B' THEN '%s'
                      WHEN 'M' THEN '%s'
                      WHEN 'Q' THEN '%s'
                      WHEN 'S' THEN '%s'
                      WHEN 'Z' THEN '%s'
                      END AS action,
       m1.addr AS to_addrs
FROM reports.mail_addrs AS m1, reports.mail_addrs AS m2
WHERE m1.time_stamp >= %s AND m1.time_stamp < %s
AND m2.time_stamp >= %s AND m2.time_stamp < %s
AND m1.%s_is_spam AND m1.addr_kind = 'T'
AND m2.addr_kind = 'F'
AND m1.msg_id = m2.msg_id
""" % (self.__short_name, self.__short_name,
       _('Passed'),
       _('Dropped'),
       _('Blocked'),
       _('Marked'),
       _('Quarantined'),
       _('Safelisted'),
       _('Oversize'),
       DateFromMx(start_date), DateFromMx(end_date),
       DateFromMx(start_date), DateFromMx(end_date),
       self.__short_name)

        if email:
            sql += " AND m1.addr = %s" % QuotedString(email)

        return sql + " ORDER BY m1.time_stamp DESC"

class SpamDetailAll(DetailSection):

    def __init__(self, title, short_name):
        self.__title = title.split()[0].lower() # keep 1st word only
        self.__short_name = short_name
        DetailSection.__init__(self, '%s-events-all' % (self.__title,),
                               _('%s Events (all)' % (self.__title.capitalize(),)))

    def get_columns(self, host=None, user=None, email=None):
        if host or user:
            return None

        rv = [ColumnDesc('time_stamp', _('Time'), 'Date')]

        rv.append(ColumnDesc('hostname', _('Client'), 'String'))

        rv += [ColumnDesc('%s_score' % (self.__short_name,), _('Score')),
               ColumnDesc('from_addrs', _('Msg Sender')),
               ColumnDesc('subject', _('Subject')),
               ColumnDesc('s_server_addr', _('Destination Ip')),
               ColumnDesc('action', _('Action')),
               ColumnDesc('to_addrs', _('Msg Receiver'), 'EmailLink')]

        return rv

    def get_all_columns(self, host=None, user=None, email=None):
        return self.get_columns(host, user, email)
    
    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        if host or user:
            return None

        sql = """\
SELECT m1.time_stamp, m1.hostname, m1.%s_score, m2.addr AS from_addrs, m1.subject, host(m1.s_server_addr) AS s_server_addr,
       CASE m1.%s_action WHEN 'P' THEN '%s'
                      WHEN 'D' THEN '%s'
                      WHEN 'B' THEN '%s'
                      WHEN 'M' THEN '%s'
                      WHEN 'Q' THEN '%s'
                      WHEN 'S' THEN '%s'
                      WHEN 'Z' THEN '%s'
                      END AS action,
       m1.addr AS to_addrs
FROM reports.mail_addrs AS m1, reports.mail_addrs AS m2
WHERE m1.time_stamp >= %s AND m1.time_stamp < %s
AND m2.time_stamp >= %s AND m2.time_stamp < %s
AND m1.addr_kind = 'T'
AND m2.addr_kind = 'F'
AND m1.msg_id = m2.msg_id
""" % (self.__short_name, self.__short_name,
       _('Passed'),
       _('Dropped'),
       _('Blocked'),
       _('Marked'),
       _('Quarantined'),
       _('Safelisted'),
       _('Oversize'),
       DateFromMx(start_date), DateFromMx(end_date),
       DateFromMx(start_date), DateFromMx(end_date))

        if email:
            sql += " AND m1.addr = %s" % QuotedString(email)

        return sql + " ORDER BY m1.time_stamp DESC"

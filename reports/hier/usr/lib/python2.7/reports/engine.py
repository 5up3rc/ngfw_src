# $Id$
import commands
import logging
import mx
import os
import re
import simplejson as json
import shutil
import reports.sql_helper as sql_helper
import string
import sys
import time
import traceback

from mx.DateTime import DateTimeDelta
from psycopg2.extensions import DateFromMx
from psycopg2.extensions import TimestampFromMx
from reports.sql_helper import print_timing
from reports.log import *
logger = getLogger(__name__)

UVM_JAR_DIR = '@PREFIX@/usr/share/java/uvm/'

TOP_LEVEL = 'top-level'
USER_DRILLDOWN = 'user-drilldown'
HOST_DRILLDOWN = 'host-drilldown'
EMAIL_DRILLDOWN = 'email-drilldown'
MAIL_REPORT_BLACKLIST = ('untangle-node-configuration-backup',)

def get_number_wan_interfaces():
    return len(get_wan_clause().split(','))

def get_wan_clause():
    netSettingsJsonObj = json.loads(open('@PREFIX@/usr/share/untangle/settings/untangle-vm/network.js', 'r').read())
    wans = []
    for intf in netSettingsJsonObj['interfaces']['list']:
        if intf['configType'] == 'DISABLED':
            continue
        if intf.get('isWan'):
            wans.append(str(intf['interfaceId']))
    return "(" + ','.join(wans) + ")"

def get_wan_names_map():
    netSettingsJsonObj = json.loads(open('@PREFIX@/usr/share/untangle/settings/untangle-vm/network.js', 'r').read())
    map = {}
    for intf in netSettingsJsonObj['interfaces']['list']:
        if intf.get('isWan'):
            map[int(intf['interfaceId'])] = intf['name']

    return map

def get_wan_ip():
    netSettingsJsonObj = json.loads(open('@PREFIX@/usr/share/untangle/settings/untangle-vm/network.js', 'r').read())
    wans = []
    for intf in netSettingsJsonObj['interfaces']['list']:
        if intf.get('isWan'):
            try:
                STATUS_JSON = json.loads(open('/var/lib/untangle-netd/interface-' + str(intf.get('interfaceId')) + '-status.js').read())
                addr = STATUS_JSON.get('v4Address')
                if addr != None:
                    return addr
                else:
                    return "unknown.ip"
            except Exception,e:
                traceback.print_exc(e)
                return "unknown.ip"
    return "no.wan.found"

class Node:
    def __init__(self, name, title):
        self.__name = name
        self.__display_title = title
        self.__view_position = self.get_view_position()
        
    def get_report(self):
        return None

    def get_toc_membership(self):
        return []

    def setup(self):
        pass

    def create_tables(self):
        pass

    def post_facttable_setup(self, start_date, end_date):
        pass

    def reports_cleanup(self, cutoff):
        pass

    @property
    def name(self):
        return self.__name

    @property
    def display_title(self):
        return self.__display_title

    @property
    def view_position(self):
        return self.__view_position

    def parents(self):
        return []

    def get_view_position(self):
        view_position = None

        try:
            filename = '@PREFIX@/usr/share/untangle/lib/%s/nodeProperties.js'%self.__name
            if not os.path.isfile(filename):
                return 1

            json_obj = json.loads(open(filename, 'r').read())

            if json_obj.get('viewPosition') != None:
                return int(json_obj.get('viewPosition'))

        except Exception,e:
            logger.error('could not find node properties for: %s' % self.__name, exc_info=True)

        return 1
            
class FactTable:
    def __init__(self, name, detail_table, time_column, dimensions, measures):
        self.__name = name
        self.__detail_table = detail_table
        self.__time_column = time_column
        self.__dimensions = dimensions
        self.__measures = measures

    @property
    def name(self):
        return self.__name

    @property
    def measures(self):
        return self.__measures

    @property
    def dimensions(self):
        return self.__dimensions

    def process(self, start_date, end_date):
        tables = sql_helper.create_table(self.__ddl())

        for c in (self.measures + self.dimensions):
            schema, tablename = self.__name.split(".")
            sql_helper.add_column( tablename, c.name, c.type )

        sd = TimestampFromMx(sql_helper.get_update_info(self.__name, start_date))

        conn = sql_helper.get_connection()

        try:
            sql_helper.run_sql(self.__insert_stmt(), (sd, end_date), connection=conn, auto_commit=False)
            sql_helper.set_update_info(self.__name, end_date, connection=conn, auto_commit=False, origin_table=self.__detail_table)
            conn.commit()
        except Exception, e:
            conn.rollback()
            raise e

    def __ddl(self):
        ddl = 'CREATE TABLE %s (time_stamp timestamp without time zone' \
            % self.__name
        for c in (self.__dimensions + self.__measures):
            ddl += ", %s %s" % (c.name, c.type)
        ddl += ')'
        return ddl

    def __insert_stmt(self):
        insert_strs = ['time_stamp']
        select_strs = ["date_trunc('minute', %s)" % self.__time_column]
        group_strs = ["date_trunc('minute', %s)" % self.__time_column]

        for c in self.__dimensions:
            insert_strs.append(c.name)
            select_strs.append(c.value_expression)
            group_strs.append(c.name)

        for c in self.__measures:
            insert_strs.append(c.name)
            select_strs.append(c.value_expression)

        return """\
INSERT INTO %s (%s)
    SELECT %s FROM %s
    WHERE %s >= %%s AND %s < %%s
    GROUP BY %s""" % (self.__name, string.join(insert_strs, ','),
                      string.join(select_strs, ','), self.__detail_table,
                      self.__time_column, self.__time_column,
                      string.join(group_strs, ','))

class Column:
    def __init__(self, name, type, value_expression=None):
        self.__name = name
        self.__type = type
        self.__value_expression = value_expression or name

    @property
    def name(self):
        return self.__name

    @property
    def type(self):
        return self.__type

    @property
    def value_expression(self):
        return self.__value_expression

__nodes = {}
__fact_tables = {}

def register_node(node):
    global __nodes

    __nodes[node.name] = node

def limit_nodes(trial_report_node):
    global __nodes

    toRemove = []

    for node_name, node in __nodes.iteritems():
        if node != trial_report_node and node_name not in trial_report_node.parents():
            toRemove.append(node_name)

    for node_name in toRemove:
        del __nodes[node_name]
    
def register_fact_table(fact_table):
    global __fact_tables

    logger.debug("registering fact table: '%s'" % (fact_table.name))
    __fact_tables[fact_table.name] = fact_table

def get_fact_table(name):
    global __fact_tables

    return __fact_tables.get(name, None)

@sql_helper.print_timing
def process_fact_tables(start_date, end_date):
    global __fact_tables

    for ft in __fact_tables.values():
        ft.process(start_date, end_date)

@sql_helper.print_timing
def generate_reports(report_base, end_date, report_days):
    global __nodes

    date_base = 'data/%d-%02d-%02d' % (end_date.year, end_date.month,
                                       end_date.day)

    mail_reports = []

    top_level = []
    user_drilldown = []
    host_drilldown = []
    email_drilldown = []

    for node_name in __get_node_partial_order():
        try:
            node = __nodes.get(node_name, None)
            if not node:
                logger.warn('could not get node %s' % node_name)
            else:
                tocs = node.get_toc_membership()
                if TOP_LEVEL in tocs:
                    top_level.append(node_name)
                if USER_DRILLDOWN in tocs:
                    user_drilldown.append(node_name)
                if HOST_DRILLDOWN in tocs:
                    host_drilldown.append(node_name)
                if EMAIL_DRILLDOWN in tocs:
                    email_drilldown.append(node_name)

                report = node.get_report()
                if report:
                    report.generate(report_base, date_base, end_date,
                                    report_days=report_days)
                    if node_name in MAIL_REPORT_BLACKLIST:
                        logger.info('Not including report for %s in emailed reports, since it is blacklisted' % (node_name,))
                    else:
                        logger.debug('Including report for %s in emailed reports' % (node_name,))
                        mail_reports.append(report)

        except:
            logger.error('could not generate reports for: %s' % node_name, exc_info=True)

    __write_toc(report_base, date_base, 'top-level', top_level)
    __write_toc(report_base, date_base, 'user-drilldown', user_drilldown)
    __write_toc(report_base, date_base, 'host-drilldown', host_drilldown)
    __write_toc(report_base, date_base, 'email-drilldown', email_drilldown)

    return mail_reports

@sql_helper.print_timing
def generate_sub_report(report_base, node_name, end_date, report_days=1,
                        host=None, user=None, email=None):
    date_base = 'data/%d-%02d-%02d' % (end_date.year, end_date.month,
                                           end_date.day)

    node = __nodes.get(node_name, None)

    logger.info("About to generate sub-report for %s: days='%s', host='%s', user='%s', email='%s'"
                % (node_name, report_days, host, user, email))

    if not node:
        msg = 'UNKNOWN_NODE: %s' % node_name
        logger.warn(msg)
        return msg

    report = node.get_report()

    if user:
        report.generate(report_base, date_base, end_date, user=user, report_days=report_days)
    if host:
        report.generate(report_base, date_base, end_date, host=host, report_days=report_days)
    if email:
        report.generate(report_base, date_base, end_date, email=email, report_days=report_days)

    dir = get_node_base(node_name, date_base, report_days=report_days,
                        host=host, user=user, email=email)

    logger.info("** sub-report generation done: report_base='%s', dir=%s'" % (report_base, dir))

    __generate_plots(report_base, dir)

    return 'DONE'

@sql_helper.print_timing
def generate_plots(report_base, end_date, report_days=1):
    date_base = 'data/%d-%02d-%02d/%s' % (end_date.year, end_date.month,
                                          end_date.day,
                                          report_days_dir(report_days))
    __generate_plots(report_base, date_base)

@sql_helper.print_timing
def reports_cleanup(cutoff):
    logger.info("Cleaning reports data for all dates < %s" % (cutoff,))

    for name in __get_node_partial_order():
        try:
            node = __nodes.get(name, None)
            logger.info("Cleaning data for %s" % (name,))
            node.reports_cleanup(cutoff)
        except:
            logger.warn('could not cleanup reports for: %s' % name,
                         exc_info=True)

@sql_helper.print_timing
def delete_old_reports(dir, cutoff):
    logger.info("Cleaning reports files for all dates < %s" % (cutoff,))    
    for f in os.listdir(dir):
        if re.match('^\d+-\d+-\d+$', f):
            d = mx.DateTime.DateFrom(f)
            if d < cutoff:
                shutil.rmtree('%s/%s' % (dir, f));

@sql_helper.print_timing
def init_engine(node_module_dir):
    __get_nodes(node_module_dir)

@sql_helper.print_timing
def create_tables():
    global __nodes

    logger.info('create_tables()')
    for name in __get_available_nodes():
        try:
            logger.info('create_tables() for: %s' % (name))
            node = __nodes.get(name, None)

            if not node:
                logger.warn('could not get node %s' % name)
            else:
                node.create_tables()
        except:
            logger.warn('could not setup for: %s' % name, exc_info=True)

@sql_helper.print_timing
def setup():
    global __nodes

    logger.info('setup(): %s ' % __get_available_nodes())
    for name in __get_available_nodes():
        try:
            logger.info('setup() for: %s' % (name))
            node = __nodes.get(name, None)

            if not node:
                logger.warn('could not get node %s' % name)
            else:
                node.setup()
        except:
            logger.warn('could not setup for: %s' % name, exc_info=True)

@sql_helper.print_timing
def post_facttable_setup(start_date, end_date):
    global __nodes

    for name in __get_node_partial_order():
        try:
            logger.debug('doing post_facttable_setup for: %s' % name)
            node = __nodes.get(name, None)
            if not node:
                logger.warn('could not get node %s' % name)
            else:
                node.post_facttable_setup(start_date, end_date)
        except:
            logger.warn('could not do post factable setup for: %s' % name, exc_info=True)

@sql_helper.print_timing
def fix_hierarchy(output_base):
    base_dir = '%s/data' % output_base

    if not os.path.isdir(base_dir):
        os.makedirs(base_dir);

    for date_dir in os.listdir(base_dir):
        if re.match('^\d+-\d+-\d+$', date_dir):
            one_day_dir = '%s/%s/1-day' % (base_dir, date_dir)
            if not os.path.isdir(one_day_dir):
                os.mkdir(one_day_dir)
                for node_dir in os.listdir('%s/%s' % (base_dir, date_dir)):
                    node_path = '%s/%s/%s' % (base_dir, date_dir, node_dir)
                    if os.path.isdir(node_path) and not re.match('[0-9]+-days?$', node_dir):
                        newDir = '%s/%s' % (one_day_dir, node_dir)
                        os.rename(node_path, newDir)
                        xmlFile = os.path.join(newDir, "report.xml")
                        if os.path.isfile(xmlFile):
                            f = open(xmlFile)
                            xml = f.read()
                            f.close()
                            f = open(xmlFile, 'w')
                            f.write(re.sub(r'/(\d\d\d\d-\d\d-\d\d)/', r'/\1/1-day/', xml))
                            f.close()

def get_node_base(name, date_base, report_days=1, host=None, user=None,
                  email=None):
    days_dir = report_days_dir(report_days)

    if host:
        return '%s/%s/host/%s/%s' % (date_base, days_dir, host, name)
    elif user:
        return '%s/%s/user/%s/%s' % (date_base, days_dir, user, name)
    elif email:
        return '%s/%s/email/%s/%s' % (date_base, days_dir, email, name)
    else:
        return '%s/%s/%s' % (date_base, days_dir, name)

def report_days_dir(report_days):
    if report_days < 2:
        return '%s-day' % report_days
    else:
        return '%s-days' % report_days

def get_node(name):
    return __nodes[name]

def _first_page(canvas, doc):
    canvas.saveState()
    canvas.setFont('Times-Bold',16)
    canvas.drawCentredString(PAGE_WIDTH/2.0, PAGE_HEIGHT-108, "Reports")
    canvas.setFont('Times-Roman',9)
    canvas.drawString(inch, 0.75 * inch, "First Page / foo")
    canvas.restoreState()

def _later_pages(canvas, doc):
    canvas.saveState()
    canvas.setFont('Times-Roman',9)
    canvas.drawString(inch, 0.75 * inch, "Page %d foo" % doc.page)
    canvas.restoreState()

def __generate_plots(report_base, dir):
    path = []

    path.append('@PREFIX@/usr/share/untangle/lib/untangle-libuvm-bootstrap/')
    path.append('@PREFIX@/usr/share/untangle/lib/untangle-libuvm-api/')
    path.append('@PREFIX@/usr/share/untangle/conf/')
    path.append('@PREFIX@/usr/share/untangle/lib/untangle-node-reports/')

    for f in os.listdir(UVM_JAR_DIR):
        if f.endswith('.jar'):
            path.append('%s/%s' % (UVM_JAR_DIR, f))

    logger.debug("About to call GraphGenerator with report_base='%s', dir='%s'" % (report_base, dir))

    command = "java -Djava.awt.headless=true -cp %s com.untangle.node.reports.items.GraphGenerator '%s' '%s'" % (string.join(path, ':'), report_base, dir)

    os.system(command)

def export_static_data(reports_output_base, end_date, report_days):
    directory = '%s/data/%d-%02d-%02d/%s' % (reports_output_base,
                                             end_date.year,
                                             end_date.month,
                                             end_date.day,
                                             report_days_dir(report_days))
    if not os.path.isdir(directory):
        os.makedirs(directory)

    start_date = DateFromMx(end_date - mx.DateTime.DateTimeDelta(report_days))

    for category in ('hosts', 'users', 'emails'):
        methodName = "__get_%s" % category
        result = globals()[methodName](start_date, end_date)
        result = [ e for e in result if e ]
        fp = os.path.join(directory, '%s.txt' % category)
        open(fp, 'w').write('\n'.join(result))

def __get_users(start_date, end_date):
    conn = sql_helper.get_connection()

    try:
        curs = conn.cursor()
        # select all distinct UIDs from that time period
        curs.execute("SELECT DISTINCT username from reports.session_totals WHERE time_stamp >= %s and time_stamp < %s",
                     (start_date, end_date))
        rows = curs.fetchall()
        rv = [row[0] for row in rows if row[0]]
    except:
        logger.warn('could not generate users list', exc_info=True)
        rv = []
    finally:
        conn.commit()

    logger.debug("Users between %s and %s: %s" % (start_date, end_date, rv))

    return rv

def __get_hosts(start_date, end_date):
    conn = sql_helper.get_connection()

    try:
        curs = conn.cursor()
        # select all distinct hostname where the client was on a non-WAN interface from that time period
        curs.execute("""SELECT DISTINCT hostname from reports.session_totals 
                        WHERE time_stamp >= %s and time_stamp < %s
                        AND client_intf NOT IN """ + get_wan_clause() + 
                     " AND server_intf IN " + get_wan_clause(),
                     (start_date, end_date))
        rows = curs.fetchall()
        rv = [row[0] for row in rows if row[0]]
    except:
        logger.warn('could not generate hosts list', exc_info=True)
        rv = []
    finally:
        conn.commit()

    logger.debug("Hosts between %s and %s: %s" % (start_date, end_date, rv))

    return rv

def __get_emails(start_date, end_date):
    conn = sql_helper.get_connection()

    try:
        if not sql_helper.table_exists('mail_addr_totals'):
            return [];
        curs = conn.cursor()
        # select all distinct email addresses from that time period
        curs.execute("""SELECT DISTINCT addr FROM reports.mail_addr_totals
                      WHERE time_stamp >=%s AND time_stamp < %s
                      AND addr_kind IN ('T','C')""",
                     (start_date, end_date))
        rows = curs.fetchall()
        rv = [row[0] for row in rows if row[0]]
    except:
        logger.warn('could not generate email list', exc_info=True)
        rv = []
    finally:
        conn.commit()

    logger.debug("Emails between %s and %s: %s" % (start_date, end_date, rv))

    return rv

def __get_available_nodes():
    global __nodes
    available = set(__nodes.keys());
    list = []

    while len(available):
        name = available.pop()
        __add_node(name, list, available)

    return list;

# do care about uninstalled nodes
def __get_node_partial_order(exclude_uninstalled=True):
    global __nodes

    installed = __get_installed_nodes()

    available = set(__nodes.keys());
    list = []

    while len(available):
        name = available.pop()
        if name in installed or name == 'untangle-vm' or not exclude_uninstalled:
                __add_node(name, list, available)

    return list

def __get_installed_nodes():
    list = []

    nodes_settings = json.loads(open('@PREFIX@/usr/share/untangle/settings/untangle-vm/nodes.js', 'r').read())
    for node in nodes_settings["nodes"]["list"]:
        list.append(node["nodeName"])

    return list

def __add_node(name, list, available):
    global __nodes

    node = __nodes.get(name, None)
    if not node:
        logger.warn('node not found %s' % name)
    else:
        for p in node.parents():
            if p in available:
                available.remove(p)
                __add_node(p, list, available)
        list.append(name)

def __get_nodes(node_module_dir):
    for f in os.listdir(node_module_dir):
        if f.endswith('py'):
            (m, e) = os.path.splitext(f)
            __import__('reports.node.%s' % m)

def __write_toc(report_base, date_base, type, list):
    d = '%s/%s' % (report_base, date_base)
    if not os.path.exists(d):
        os.makedirs(d)

    f = open('%s/%s' % (d, type), 'w')
    try:
        for l in list:
            f.write(l)
            f.write("\n")
    finally:
        f.close()


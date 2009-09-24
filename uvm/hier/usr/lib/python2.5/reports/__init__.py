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
#
# Aaron Read <amread@untangle.com>

import csv
import gettext
import logging
import mx
import os
import popen2
import re
import reportlab.lib.colors
import reports.colors
import reports.sql_helper as sql_helper
import reports.i18n_helper
import string

from lxml.etree import CDATA
from lxml.etree import Element
from lxml.etree import ElementTree
from mx.DateTime import DateTimeDeltaFromSeconds

from reportlab.lib.colors import HexColor
from reportlab.lib.colors import Color
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.platypus import Paragraph
from reportlab.platypus import Spacer
from reportlab.platypus.flowables import PageBreak
from reportlab.platypus.flowables import Image
from reportlab.platypus.flowables import KeepTogether
from reportlab.platypus.tables import Table
from reportlab.platypus.tables import TableStyle
from reports.engine import ReportDocTemplate
from reports.engine import get_node_base
from reports.pdf import STYLESHEET
from reports.pdf import SectionHeader

from reportlab.graphics.shapes import Rect

HNAME_LINK = 'HostLink'
USER_LINK = 'UserLink'
EMAIL_LINK = 'EmailLink'
URL_LINK = 'URLLink'

_ = reports.i18n_helper.get_translation('untangle-vm').lgettext

def __time_of_day_formatter(x, pos):
    t = DateTimeDeltaFromSeconds(x)
    return "%02d:%02d" % (t.hour, t.minute)

def __date_formatter(x, pos):
    return x.strftime("%b-%d")

def __identity_formatter(x, pos):
    return x

class Formatter:
    def __init__(self, name, function):
        self.__name = name
        self.__function = function

    @property
    def name(self):
        return self.__name

    @property
    def function(self):
        return self.__function

TIME_OF_DAY_FORMATTER = Formatter('time-of-day', __time_of_day_formatter)
DATE_FORMATTER = Formatter('date', __date_formatter)
IDENTITY_FORMATTER = Formatter('identity', __identity_formatter)

TIME_SERIES_CHART = 'time-series-chart'
STACKED_BAR_CHART = 'stacked-bar-chart'
PIE_CHART = 'pie-chart'

class Report:
    def __init__(self, name, sections):
        self.__name = name
        self.__title, self.__view_position = self.__get_node_info(self.__name)
        self.__sections = sections

    @property
    def title(self):
        return self.__title

    @property
    def view_position(self):
        return self.__view_position

    def generate(self, report_base, date_base, end_date, host=None, user=None,
                 email=None):
        node_base = get_node_base(self.__name, date_base, host, user, email)

        element = Element('report')
        element.set('name', self.__name)
        element.set('title', self.__title)
        if host:
            element.set('host', host)
        if user:
            element.set('user', user)
        if email:
            element.set('email', email)

        for s in self.__sections:
            section_element = s.generate(report_base, node_base, end_date, host,
                                         user, email)

            if section_element is not None:
                element.append(section_element)

        if len(element.getchildren()):
            tree = ElementTree(element)

            if not os.path.exists('%s/%s' % (report_base, node_base)):
                os.makedirs('%s/%s' % (report_base, node_base))

            report_file = "%s/%s/report.xml" % (report_base, node_base)

            logging.info('writing %s' % report_file)
            tree.write(report_file, encoding='utf-8', pretty_print=True,
                       xml_declaration=True)

    def get_flowables(self, report_base, date_base, end_date):
        node_base = get_node_base(self.__name, date_base)

        sh = SectionHeader(self.__title)

        story = [sh, Spacer(1, 0.25 * inch)]

        for s in self.__sections:
            story += s.get_flowables(report_base, node_base, end_date)

        return story

    def __get_node_info(self, name):
        title = None
        view_position = None

        (stdout, stdin) = popen2.popen2(['apt-cache', 'show', name])
        try:
            for l in stdout:
                m = re.search('Display-Name: (.*)', l)
                if m:
                    title = m.group(1)
                m = re.search('View-Position: ([0-9]*)', l)
                if m:
                    view_position = int(m.group(1))
        finally:
            stdout.close()
            stdin.close()

        return (title, view_position)

class Section:
    def __init__(self, name, title):
        self.__name = name
        self.__title = title

    @property
    def name(self):
        return self.__name

    @property
    def title(self):
        return self.__title

    def generate(self, report_base, node_base, end_date, host=None, user=None,
                 email=None):
        pass

    def get_flowables(self, report_base, date_base, end_date):
        return []

class SummarySection(Section):
    def __init__(self, name, title, summary_items=[]):
        Section.__init__(self, name, title)

        self.__summary_items = summary_items

    def generate(self, report_base, node_base, end_date, host=None, user=None,
                 email=None):
        section_base = "%s/%s" % (node_base, self.name)

        element = Element('summary-section')
        element.set('name', self.name)
        element.set('title', self.title)

        for summary_item in self.__summary_items:
            report_element = summary_item.generate(report_base, section_base,
                                                   end_date, host, user, email)
            if report_element is not None:
                element.append(report_element)

        if len(element.getchildren()):
            return element
        else:
            return None

    def get_flowables(self, report_base, node_base, end_date):
        section_base = "%s/%s" % (node_base, self.name)

        story = []

        for si in self.__summary_items:
            story += si.get_flowables(report_base, section_base, end_date)
            story.append(PageBreak())

        return story

class DetailSection(Section):
    def __init__(self, name, title):
        Section.__init__(self, name, title)

    def get_columns(self, host=None, user=None, email=None):
        pass

    def get_sql(self, start_date, end_date, host=None, user=None, email=None):
        pass

    def generate(self, report_base, node_base, end_date, host=None, user=None,
                 email=None):
        element = Element('detail-section')
        element.set('name', self.name)
        element.set('title', self.title)

        start_date = end_date - mx.DateTime.DateTimeDelta(7) # XXX report_days
        sql = self.get_sql(start_date, end_date, host, user, email)

        if not sql:
            logging.warn('no sql for DetailSection: %s' % self.name)
            sql = ''

        sql_element = Element('sql')
        sql_element.text = CDATA(sql)
        element.append(sql_element)

        columns = self.get_columns(host, user, email)
        if not columns:
            logging.warn('no columns for DetailSection: %s' % self.name)
            columns = []

        for c in columns:
            element.append(c.get_dom())

        return element

    def get_flowables(self, report_base, date_base, end_date):
        return []

class ColumnDesc():
    def __init__(self, name, title, type=None):
        self.__name = name
        self.__title = title
        self.__type = type

    @property
    def title(self):
        return self.__title

    def get_dom(self):
        element = Element('column')
        element.set('name', self.__name)
        element.set('title', self.__title)
        if self.__type:
            element.set('type', self.__type)

        return element

class Graph:
    def __init__(self, name, title):
        self.__name = name
        self.__title = title

    def get_graph(self, end_date, report_days, host=None, user=None, email=None):
        return (self.get_key_statistics(end_date, report_days, host, user, email),
                self.get_plot(end_date, report_days, host, user, email))

    def get_key_statistics(self, report_days, end_date, host=None, user=None,
                           email=None):
        return []

    def get_plot(self, end_date, report_days, host=None, user=None, email=None):
        return None

    @property
    def name(self):
        return self.__name

    @property
    def title(self):
        return self.__title

    def generate(self, report_base, section_base, end_date, host=None,
                 user=None, email=None):
        graph_data = self.get_graph(end_date, 7, host, user, email)

        if not graph_data:
            return None

        self.__key_statistics, self.__plot = graph_data

        if not self.__plot:
            return None

        if not self.__key_statistics:
            self.__key_statistics = []

        filename_base = '%s-%s' % (section_base, self.__name)

        dir = os.path.dirname('%s/%s' % (report_base, filename_base))
        if not os.path.exists(dir):
            os.makedirs(dir)

        self.__plot.generate_csv('%s/%s.csv' % (report_base, filename_base))

        element = Element('graph')
        element.set('name', self.__name)
        element.set('title', self.__title)
        element.set('type', self.__plot.type)
        element.set('image', filename_base + '.png')
        element.set('csv', filename_base + '.csv')

        for ks in self.__key_statistics:
            ks_element = Element('key-statistic')
            name = ks.name
            if not ks.name:
                name = ''
            ks_element.set('name', str(name))
            if type(ks.value) == float:
                ks.value = '%.2f' % ks.value
            ks_element.set('value', str(ks.value))
            if ks.unit:
                ks_element.set('unit', ks.unit)
            if ks.link_type:
                ks_element.set('link-type', ks.link_type)
            element.append(ks_element)

        element.append(self.__plot.get_dom())

        return element

    def get_flowables(self, report_base, section_base, end_date):
        img_file = '%s/%s-%s.png' % (report_base, section_base, self.__name)
        if not os.path.exists(img_file):
            logging.warn('skipping summary for missing png: %s' % img_file)
            return []
        image = Image(img_file)

        zebra_colors = [HexColor(0xE0E0E0), None]

        if self.__plot.type == PIE_CHART:
            colors = self.__plot.colors
            background_colors = [None]
            data = [[Paragraph(_('Key Statistics'), STYLESHEET['TableTitle']),
                     '', '']]
        else:
            colors = None
            background_colors = zebra_colors
            data = [[Paragraph(_('Key Statistics'), STYLESHEET['TableTitle']),
                     '']]

        for i, ks in enumerate(self.__key_statistics):
            n = ks.name
            if colors:
                val, unit = ks.scaled_value
                if unit:
                    data.append(['', n, "%s %s" % (val, unit)])
                else:
                    data.append(['', n, "%s" % val])
                c = colors.get(n, None)
                if c:
                    background_colors.append(c)
                else:
                    background_colors.append(zebra_colors[(i + 1) % 2])
            else:
                data.append([n, "%s %s" % ks.scaled_value])

        style = []

        if colors:
            style.append(['ROWBACKGROUNDS', (0, 0), (0, -1), background_colors])
            style.append(['ROWBACKGROUNDS', (1, 0), (-1, -1), zebra_colors])
            style.append(['SPAN', (0, 0), (2, 0)])
        else:
            style.append(['SPAN', (0, 0), (1, 0)])
            style.append(['ROWBACKGROUNDS', (0, 1), (-1, -1), zebra_colors])

        style += [['BACKGROUND', (0, 0), (-1, 0), reportlab.lib.colors.grey],
                  ['BOX', (0, 0), (-1, -1), 1, reportlab.lib.colors.grey]]

        ks_table = Table(data, style=style)

        return [image, Spacer(1, 0.125 * inch), ks_table]

class Chart:
    def __init__(self, type=TIME_SERIES_CHART, title=None, xlabel=None,
                 ylabel=None, major_formatter=IDENTITY_FORMATTER,
                 required_points=[]):
        self.__type = type
        self.__title = title
        self.__xlabel = xlabel
        self.__ylabel = ylabel
        self.__major_formatter = major_formatter

        self.__datasets = []

        self.__header = [xlabel]

        self.__colors = {}
        self.__color_num = 0

        self.__required_points = required_points

    @property
    def type(self):
        return self.__type

    @property
    def colors(self):
        return self.__colors

    def add_dataset(self, xdata, ydata, label=None, color=None, linestyle='-'):
        if self.__type == PIE_CHART:
            raise ValueError('using 2D dataset for pie chart')

        if not color:
            color = reports.colors.color_palette[self.__color_num]
            self.__color_num += 1

        label = str(label)

        m = {'xdata': xdata, 'ydata': ydata, 'label': label,
             'linestyle': linestyle, 'color': color}
        self.__datasets.append(m)

        self.__header.append(label)

        self.__colors[label] = color

    def add_pie_dataset(self, data, colors={}):
        if self.__type != PIE_CHART:
            raise ValueError('using pie dataset for non-pie chart')

        for k, v in colors.iteritems():
            self.__colors[str(k)] = v

        self.__datasets = {}
        for k, v in data.iteritems():
            k = str(k)
            self.__datasets[k] = v
            if not self.__colors.has_key(k):
                self.__colors[k] = reports.colors.color_palette[self.__color_num]
                self.__color_num += 1

    def generate_csv(self, filename, host=None, user=None, email=None):
        if self.__type == PIE_CHART:
            self.__generate_pie_csv(filename, host=host, user=user, email=email)
        else:
            self.__generate_2d_csv(filename, host=host, user=user, email=email)

    def get_dom(self):
        element = Element('plot')
        if self.__type:
            element.set('type', self.__type)
        if self.__title:
            element.set('title', self.__title)
        if self.__xlabel:
            element.set('x-label', self.__xlabel)
        if self.__ylabel:
            element.set('y-label', self.__ylabel)
        if self.__major_formatter:
            element.set('major-formatter', self.__major_formatter.name)

        for t, c in self.__colors.iteritems():
            ce = Element('color')
            ce.set('title', str(t))
            ce.set('value', "%02x%02x%02x" % c.bitmap_rgb())
            element.append(ce)

        return element

    def __generate_2d_csv(self, filename, host=None, user=None, email=None):
        data = {}
        z = 0

        for ds in self.__datasets:
            for x, y in zip(ds['xdata'], ds['ydata']):
                a = data.get(x, None)
                if a:
                    a[z] = y
                else:
                    a = [0 for i in range(len(self.__datasets))]
                    a[z] = y
                    data[x] = a
            z = z + 1

        for x in self.__required_points:
            a = data.get(x, None)
            if not a:
                a = [0 for i in range(len(self.__datasets))]
                data[x] = a

        rows = [[k] + v for (k, v) in data.iteritems()]
        rows.sort()

        w = csv.writer(open(filename, 'w'))
        w.writerow(self.__header)
        for r in rows:
            if self.__major_formatter:
                r[0] = self.__major_formatter.function(r[0], None)
            for i, e in enumerate(r):
                if e is None:
                    r[i] = 0
            w.writerow(r)

    def __generate_pie_csv(self, filename, host=None, user=None, email=None):
        items = []

        for k, v in self.__datasets.iteritems():
            items.append([k, v])

        items.sort(cmp=self.__pie_sort, reverse=True)

        w = csv.writer(open(filename, 'w'))
        w.writerow([_('slice'), _('value')])
        for e in items:
            k = e[0]
            v = e[1]
            if k is None:
                k = 'None'
            if v is None:
                v = 0
            w.writerow([k, v])

    def __pie_sort(self, a, b):
        return cmp(a[1], b[1])

class KeyStatistic:
    def __init__(self, name, value, unit=None, link_type=None):
        if name is None:
            logging.warn('KeyStatistic name is None')
            name = _("Unknown")
        if value is None:
            logging.warn('KeyStatistic for %s value is None' % name)
            value = 0

        self.__name = name
        self.__value = value
        self.__unit = unit
        self.__link_type = link_type

    @property
    def scaled_value(self):
        if self.__unit is None:
            return (self.__value, self.__unit)
        if self.__unit.startswith('bytes'):
            if self.__value < 1000000:
                s = string.split(self.__unit, '/')
                s[0] = _('KB')
                return ('%.2f' % (self.__value / 1000.0), string.join(s, '/'))
            elif self.__value < 1000000000:
                s = string.split(self.__unit, '/')
                s[0] = _('MB')
                return ('%.2f' % (self.__value / 1000000.0), string.join(s, '/'))
            else:
                s = string.split(self.__unit, '/')
                s[0] = _('GB')
                return ('%.2f' % (self.__value / 1000000000.0), string.join(s, '/'))
        elif type(self.__value) is float:
            return ('%.2f' % self.__value, self.__unit)
        else:
            return (self.__value, self.__unit)

    @property
    def name(self):
        return self.__name

    @property
    def value(self):
        return self.__value

    @property
    def unit(self):
        return self.__unit

    @property
    def link_type(self):
        return self.__link_type

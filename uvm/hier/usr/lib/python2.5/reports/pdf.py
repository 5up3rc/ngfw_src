import gettext
import reportlab.lib.colors as colors

from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.enums import TA_RIGHT
from reportlab.lib.sequencer import getSequencer
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.styles import StyleSheet1
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.units import inch, cm
from reportlab.platypus import NextPageTemplate
from reportlab.platypus import Paragraph
from reportlab.platypus import Spacer
from reportlab.platypus.doctemplate import BaseDocTemplate
from reportlab.platypus.doctemplate import PageTemplate
from reportlab.platypus.flowables import Flowable
from reportlab.platypus.flowables import Image
from reportlab.platypus.flowables import PageBreak
from reportlab.platypus.frames import Frame
from reportlab.platypus.tables import Table
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.rl_config import defaultPageSize
from sql_helper import print_timing

PAGE_HEIGHT = defaultPageSize[1]
PAGE_WIDTH = defaultPageSize[0]

_ = gettext.gettext

def __getStyleSheet():
    """Returns a stylesheet object"""
    stylesheet = StyleSheet1()

    stylesheet.add(ParagraphStyle(name='Normal',
                                  fontName='Helvetica',
                                  fontSize=10,
                                  leading=12))

    stylesheet.add(ParagraphStyle(name='Heading1',
                                  parent=stylesheet['Normal'],
                                  fontName = 'Helvetica-Bold',
                                  fontSize=18))

    stylesheet.add(ParagraphStyle(name='Title',
                                  parent=stylesheet['Normal'],
                                  fontName = 'Helvetica-Bold',
                                  fontSize=18,
                                  leading=22,
                                  spaceAfter=6))

    stylesheet.add(ParagraphStyle(name='SubTitle',
                                  parent=stylesheet['Normal'],
                                  fontName = 'Helvetica-Bold',
                                  fontSize=14,
                                  textColor='0x009933',
                                  leading=22,
                                  spaceAfter=6))

    stylesheet.add(ParagraphStyle(name='TableTitle',
                                  parent=stylesheet['Normal'],
                                  fontName = 'Helvetica-Bold'))

    stylesheet.add(ParagraphStyle(name='HeaderLink',
                                  parent=stylesheet['Normal'],
                                  alignment=TA_RIGHT))

    stylesheet.add(ParagraphStyle(name='TocHeading1',
                                  parent=stylesheet['Normal'],
                                  textColor='blue',
                                  leftIndent=20,
                                  firstLineIndent=-20, spaceBefore=5,
                                  leading=5))

    return stylesheet

STYLESHEET = __getStyleSheet()

class SectionHeader(Flowable):
    def __init__(self, title):
        self.__title = title

        self.__table = Table([[Paragraph(title, STYLESHEET['Heading1']),
                               Paragraph("<a href='#TOP' color='blue'>Back to Top</a>",
                                         STYLESHEET['HeaderLink'])]],
                             style=[('LINEBELOW', (0, 0), (1, 0), 1,
                                     HexColor(0xCCCCCC))])

    @property
    def title(self):
        return self.__title

    def wrap(self, availWidth, availHeight):
        return self.__table.wrap(availWidth, availHeight)

    def split(self, availWidth, availHeight):
        return self.__table.split(availWidth, availHeight)

    def draw(self):
        self.__table.draw()

    def drawOn(self, canvas, x, y, _sW=0):
        self.__table.drawOn(canvas, x, y, _sW=_sW)

class ReportDocTemplate(BaseDocTemplate):
    def __init__(self, filename, **kw):
        apply(BaseDocTemplate.__init__, (self, filename), kw)

        self.allowSplitting = 0
        self.seq = getSequencer()
        self.chapter = ""

    def afterInit(self):
        self.addPageTemplates(TocTemplate('TOC', self.pagesize))
        self.addPageTemplates(BodyTemplate('Body', self.pagesize))

    def afterFlowable(self, flowable):
        if flowable.__class__.__name__ == 'SectionHeader':
            title = flowable.title
            key = 'h1-%s' % self.seq.nextf('SectionHeader')
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(title, key)
            self.notify('TOCEntry', (0, title, self.page, key))
            self.chapter = title
        elif flowable.__class__.__name__ == 'TableOfContents':
            key = 'toc-%s' % self.seq.nextf('TOC')
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(_('Table of Contents'), key)

class TocTemplate(PageTemplate):
    def __init__(self, id, pageSize=defaultPageSize):
        self.pageWidth = pageSize[0]
        self.pageHeight = pageSize[1]
        frame1 = Frame(inch,
                       inch,
                       self.pageWidth - 2*inch,
                       self.pageHeight - 2*inch,
                       id='normal')
        PageTemplate.__init__(self, id, [frame1])

    def afterDrawPage(self, canvas, doc):
        y = self.pageHeight - 50
        canvas.saveState()
        canvas.setStrokeColor(HexColor(0xCCCCCC))
        canvas.setFillColor(HexColor(0xCCCCCC))
        canvas.setFont('Helvetica', 10)
        canvas.drawString(inch, y+8, doc.title)
        canvas.drawRightString(self.pageWidth - inch, y+8,
                               _('Table of Contents'))
        canvas.line(inch, y, self.pageWidth - inch, y)
        canvas.drawCentredString(doc.pagesize[0] / 2, 0.75*inch,
                                 'Page %d' % canvas.getPageNumber())
        canvas.restoreState()

class BodyTemplate(PageTemplate):
    def __init__(self, id, pageSize=defaultPageSize):
        self.pageWidth = pageSize[0]
        self.pageHeight = pageSize[1]
        frame1 = Frame(inch,
                       inch,
                       self.pageWidth - 2*inch,
                       self.pageHeight - 2*inch,
                       id='normal')
        PageTemplate.__init__(self, id, [frame1])

    def afterDrawPage(self, canvas, doc):
        y = self.pageHeight - 50
        canvas.saveState()
        canvas.setStrokeColor(HexColor(0xCCCCCC))
        canvas.setFillColor(HexColor(0xCCCCCC))
        canvas.setFont('Helvetica', 10)
        canvas.drawString(inch, y+8, doc.title)
        canvas.drawRightString(self.pageWidth - inch, y+8, doc.chapter)
        canvas.line(inch, y, self.pageWidth - inch, y)
        canvas.drawCentredString(doc.pagesize[0] / 2, 0.75*inch,
                                 _('Page %d') % canvas.getPageNumber())
        canvas.restoreState()

@print_timing
def generate_pdf(report_base, end_date, mail_reports):
    file = "/home/amread/report.pdf";

    date_base = 'data/%d-%02d-%02d' % (end_date.year, end_date.month, end_date.day)

    story = []

    date_str = end_date.strftime("%A %d %B %Y")
    doc = ReportDocTemplate(file, title=_('Report for %s') % date_str)


    t = Table([[Paragraph('<a name="TOP"/>' + _('Report'),
                          STYLESHEET['Title'])],
               [Paragraph(date_str, STYLESHEET['SubTitle'])]])

    story.append(Table([[Image('/var/www/images/BrandingLogo.gif'), t]],
                       style=[('VALIGN', (1, 0), (1, 0), 'TOP')]))

    toc = TableOfContents()
    toc.levelStyles = [STYLESHEET['TocHeading1']]

    story.append(toc);
    story.append(NextPageTemplate('Body'))
    story.append(PageBreak())

    mail_reports.sort(__node_cmp)

    for r in mail_reports:
        story += r.get_flowables(report_base, date_base, end_date)
        story.append(PageBreak())

    doc.multiBuild(story)

    return file

def __node_cmp(x, y):
    return cmp(x.view_position, y.view_position)

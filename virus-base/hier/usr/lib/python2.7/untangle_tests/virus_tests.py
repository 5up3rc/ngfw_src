import unittest2
import time
import subprocess
from datetime import datetime
import sys
import os
import subprocess
import socket
from jsonrpc import ServiceProxy
from jsonrpc import JSONRPCException
from uvm import Manager
from uvm import Uvm
from untangle_tests import ClientControl

ftp_server = "test.untangle.com"
ftp_virus_file_name = "FedEx-Shipment-Notification-Jan23-2012-100100.zip"

uvmContext = Uvm().getUvmContext()
defaultRackId = 1
clientControl = ClientControl()
node = None

def flushEvents():
    reports = uvmContext.nodeManager().node("untangle-node-reporting")
    if (reports != None):
        reports.flushEvents()

def addPassSite(site, enabled=True, description="description"):
    newRule =  { "enabled": enabled, "description": description, "javaClass": "com.untangle.uvm.node.GenericRule", "string": site }
    rules = node.getPassSites()
    rules["list"].append(newRule)
    node.setPassSites(rules)

def nukePassSites():
    rules = node.getPassSites()
    rules["list"] = []
    node.setPassSites(rules)

class VirusTests(unittest2.TestCase):

    @staticmethod
    def nodeName():
        return "untangle-base-virus"

    @staticmethod
    def shortName():
        return "untangle"

    def setUp(self):
        global node,md5StdNum
        if node == None:
            # download eicar and trojan files before installing virus blocker
            clientControl.runCommand("rm /tmp/eicar /tmp/std_022_ftpVirusBlocked_file /tmp/temp_022_ftpVirusPassSite_file >/dev/null 2>&1")
            result = clientControl.runCommand("wget http://test.untangle.com/virus/00_eicar.com -O /tmp/eicar -o /dev/null 2>&1")
            assert (result == 0)
            result = clientControl.runCommand("wget -q -O /tmp/std_022_ftpVirusBlocked_file ftp://" + ftp_server + "/" + ftp_virus_file_name)
            assert (result == 0)
            md5StdNum = clientControl.runCommand("\"md5sum /tmp/std_022_ftpVirusBlocked_file | awk '{print $1}'\"", True)
            print "md5StdNum <%s>" % md5StdNum
            assert (result == 0)
            if (uvmContext.nodeManager().isInstantiated(self.nodeName())):
                print "ERROR: Node %s already installed" % self.nodeName();
                raise unittest2.SkipTest('node %s already instantiated' % self.nodeName())
            node = uvmContext.nodeManager().instantiate(self.nodeName(), defaultRackId)
            flushEvents()

    # verify client is online
    def test_010_clientIsOnline(self):
        result = clientControl.isOnline()
        assert (result == 0)

    # test that client can http download zip
    def test_011_httpNonVirusNotBlocked(self):
        result = clientControl.runCommand("wget -q -O - http://test.untangle.com/test/test.zip 2>&1 | grep -q text123")
        assert (result == 0)

    # test that client can block virus http download zip
    def test_012_httpVirusBlocked(self):
        result = clientControl.runCommand("wget -q -O - http://test.untangle.com/test/eicar.zip 2>&1 | grep -q blocked")
        assert (result == 0)

    # test that client can block virus http download zip
    def test_013_httpVirusPassSite(self):
        addPassSite("test.untangle.com")
        result = clientControl.runCommand("wget -q -O - http://test.untangle.com/test/eicar.zip 2>&1 | grep -q blocked")
        nukePassSites()
        assert (result == 1)

    # test that client can ftp download zip
    def test_021_ftpNonVirusNotBlocked(self):
        adResult = subprocess.call(["ping","-c","1",ftp_server],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        if (adResult != 0):
            raise unittest2.SkipTest("FTP server not available")
        result = clientControl.runCommand("wget -q -O /dev/null ftp://" + ftp_server + "/test.zip")
        assert (result == 0)

    # test that client can block virus ftp download zip
    def test_022_ftpVirusBlocked(self):
        clientControl.runCommand("rm /tmp/temp_022_ftpVirusBlocked_file  >/dev/null 2>&1") 
        result = clientControl.runCommand("wget -q -O /tmp/temp_022_ftpVirusBlocked_file ftp://" + ftp_server + "/" + ftp_virus_file_name)
        assert (result == 0)
        md5TestNum = clientControl.runCommand("\"md5sum /tmp/temp_022_ftpVirusBlocked_file | awk '{print $1}'\"", True)
        print "md5StdNum <%s> vs md5TestNum <%s>" % (md5StdNum, md5TestNum)
        assert (md5StdNum != md5TestNum)
        flushEvents()
        query = None;
        for q in node.getFtpEventQueries():
            if q['name'] == 'Infected Ftp Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'])  # pass if event list is not empty
        assert(len(events['list']) > 0)
        print "Event:" + str(events['list'][0])
        ftp_server_IP = socket.gethostbyname(ftp_server)
        assert(events['list'][0]['s_server_addr'] == ftp_server_IP)  # IP address of ftp server
        assert(events['list'][0]['c_client_addr'] == ClientControl.hostIP)
        assert(events['list'][0]['uri'] == ftp_virus_file_name)
        assert(events['list'][0][ self.shortName() + '_name'] != None)
        assert(events['list'][0][ self.shortName() + '_clean'] == False)

    # test that client can block virus ftp download zip
    def test_023_ftpVirusPassSite(self):
        ftp_server_IP = socket.gethostbyname(ftp_server)
        addPassSite(ftp_server_IP)
        clientControl.runCommand("rm /tmp/temp_022_ftpVirusBlocked_file  >/dev/null 2>&1") 
        result = clientControl.runCommand("wget -q -O /tmp/temp_022_ftpVirusPassSite_file ftp://" + ftp_server + "/" + ftp_virus_file_name)
        assert (result == 0)
        md5TestNum = clientControl.runCommand("\"md5sum /tmp/temp_022_ftpVirusPassSite_file | awk '{print $1}'\"", True)
        print "md5StdNum <%s> vs md5TestNum <%s>" % (md5StdNum, md5TestNum)
        assert (md5StdNum == md5TestNum)
        flushEvents()
        query = None;
        for q in node.getFtpEventQueries():
            if q['name'] == 'Infected Ftp Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'])  # pass if event list is not empty
        assert(len(events['list']) > 0)
        print "Event:" + str(events['list'][0])
        ftp_server_IP = socket.gethostbyname(ftp_server)
        assert(events['list'][0]['s_server_addr'] == ftp_server_IP)  # IP address of ftp server
        assert(events['list'][0]['c_client_addr'] == ClientControl.hostIP)
        assert(events['list'][0]['uri'] == ftp_virus_file_name)
        assert(events['list'][0][ self.shortName() + '_name'] != None)
        assert(events['list'][0][ self.shortName() + '_clean'] == False)
        nukePassSites()

    def test_100_eventlog_httpVirus(self):
        fname = sys._getframe().f_code.co_name
        result = clientControl.runCommand("wget -q -O - http://test.untangle.com/test/eicar.zip?arg=%s 2>&1 | grep -q blocked" % fname)
        assert (result == 0)
        flushEvents()
        query = None;
        for q in node.getWebEventQueries():
            if q['name'] == 'Infected Web Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'])  # pass if event list is not empty
        assert(len(events['list']) > 0)
        print "Event:" + str(events['list'][0])
        assert(events['list'][0]['host'] == "test.untangle.com")
        assert(events['list'][0]['uri'] == ("/test/eicar.zip?arg=%s" % fname))
        assert(events['list'][0][ self.shortName() + '_name'] != None)
        assert(events['list'][0][ self.shortName() + '_clean'] == False)

    def test_101_eventlog_httpNonVirus(self):
        fname = sys._getframe().f_code.co_name
        result = clientControl.runCommand("wget -q -O - http://test.untangle.com/test/test.zip?arg=%s 2>&1 | grep -q text123" % fname)
        assert (result == 0)
        flushEvents()
        query = None;
        for q in node.getWebEventQueries():
            if q['name'] == 'Clean Web Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'] != None)
        assert(len(events['list']) > 0)
        print "Event:" + str(events['list'][0])
        assert(events['list'][0]['host'] == "test.untangle.com")
        assert(events['list'][0]['uri'] == ("/test/test.zip?arg=%s" % fname))
        assert(events['list'][0][self.shortName() + '_clean'] == True)
        
    def test_102_eventlog_ftpVirus(self):
        fname = sys._getframe().f_code.co_name
        result = clientControl.runCommand("wget -q -O /tmp/temp_022_ftpVirusBlocked_file ftp://" + ftp_server + "/" + ftp_virus_file_name)
        assert (result == 0)
        flushEvents()
        query = None;
        for q in node.getFtpEventQueries():
            if q['name'] == 'Infected Ftp Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'])  # pass if event list is not empty
        assert(len(events['list']) > 0)
        print "Event:" + str(events['list'][0])
        assert(events['list'][0]['uri'] == ftp_virus_file_name)
        assert(events['list'][0][ self.shortName() + '_name'] != None)
        assert(events['list'][0][ self.shortName() + '_clean'] == False)

    def test_103_eventlog_ftpNonVirus(self):
        fname = sys._getframe().f_code.co_name
        result = clientControl.runCommand("wget -q -O /dev/null ftp://" + ftp_server + "/test.zip")
        assert (result == 0)
        flushEvents()
        query = None;
        for q in node.getFtpEventQueries():
            if q['name'] == 'Clean Ftp Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'] != None)
        assert(len(events['list']) > 0)
        print "Event:" + str(events['list'][0])
        assert(events['list'][0]['uri'] == "test.zip")
        assert(events['list'][0][self.shortName() + '_clean'] == True)

    port25Test = subprocess.call(["netcat","-z","-w","1","test.untangle.com","25"],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    @unittest2.skipIf(port25Test != 0,  "Port 25 blocked")
    def test_104_eventlog_smtpVirus(self):
        startTime = datetime.now()
        fname = sys._getframe().f_code.co_name
        # download the email script
        result = clientControl.runCommand("wget -O /tmp/email_script.py http://test.untangle.com/test/email_script.py 1>/dev/null 2>&1")
        assert (result == 0)
        result = clientControl.runCommand("chmod 775 /tmp/email_script.py")
        assert (result == 0)
        # email the file
        result = clientControl.runCommand("/tmp/email_script.py --server=74.123.29.140 --from=junk@test.untangle.com --to=junk@test.untangle.com --subject='%s' --body='body' --file=/tmp/eicar" % (fname))
        assert (result == 0)
        flushEvents()
        query = None;
        for q in node.getMailEventQueries():
            if q['name'] == 'Infected Email Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'] != None)
        print "startTime: " + str(startTime)
        assert(len(events['list']) > 0)
        print "Event:" + str(events['list'][0])
        assert(events['list'][0]['addr'] == "junk@test.untangle.com")
        assert(events['list'][0]['subject'] == str(fname))
        assert(events['list'][0][self.shortName() + '_clean'] == False)
        assert(datetime.fromtimestamp((events['list'][0]['time_stamp']['time'])/1000) > startTime)

    port25Test = subprocess.call(["netcat","-z","-w","1","test.untangle.com","25"],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    @unittest2.skipIf(port25Test != 0,  "Port 25 blocked")
    def test_105_eventlog_smtpNonVirus(self):
        startTime = datetime.now()
        fname = sys._getframe().f_code.co_name
        result = clientControl.runCommand("echo '%s' > /tmp/attachment-%s" % (fname, fname))
        assert (result == 0)
        # download the email script
        result = clientControl.runCommand("wget -O /tmp/email_script.py http://test.untangle.com/test/email_script.py 1>/dev/null 2>&1")
        assert (result == 0)
        result = clientControl.runCommand("chmod 775 /tmp/email_script.py")
        assert (result == 0)
        # email the file
        result = clientControl.runCommand("/tmp/email_script.py --server=74.123.29.140 --from=junk@test.untangle.com --to=junk@test.untangle.com --subject='%s' --body='body' --file=/tmp/attachment-%s" % (fname, fname))
        assert (result == 0)
        flushEvents()
        query = None;
        for q in node.getMailEventQueries():
            if q['name'] == 'Clean Email Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'] != None)
        print "startTime: " + str(startTime)
        assert(len(events['list']) > 0)
        print "Event:" + str(events['list'][0])
        assert(events['list'][0]['addr'] == "junk@test.untangle.com")
        assert(events['list'][0]['subject'] == str(fname))
        assert(events['list'][0][ self.shortName() + '_clean'] == True)
        assert(datetime.fromtimestamp((events['list'][0]['time_stamp']['time'])/1000) > startTime)

    port25Test = subprocess.call(["netcat","-z","-w","1","test.untangle.com","25"],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    @unittest2.skipIf(port25Test != 0,  "Port 25 blocked")
    def test_106_eventlog_smtpVirusPassList(self):
        ftp_server_IP = socket.gethostbyname(ftp_server)
        addPassSite(ftp_server_IP)
        startTime = datetime.now()
        fname = sys._getframe().f_code.co_name
        result = clientControl.runCommand("echo '%s' > /tmp/attachment-%s" % (fname, fname))
        assert (result == 0)
        # download the email script
        result = clientControl.runCommand("wget -O /tmp/email_script.py http://test.untangle.com/test/email_script.py 1>/dev/null 2>&1")
        assert (result == 0)
        result = clientControl.runCommand("chmod 775 /tmp/email_script.py")
        assert (result == 0)
        # email the file
        result = clientControl.runCommand("/tmp/email_script.py --server=74.123.29.140 --from=junk@test.untangle.com --to=junk@test.untangle.com --subject='%s' --body='body' --file=/tmp/eicar" % (fname))
        assert (result == 0)
        flushEvents()
        query = None;
        for q in node.getMailEventQueries():
            if q['name'] == 'Clean Email Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'] != None)
        print "startTime: " + str(startTime)
        assert(len(events['list']) > 0)
        print "Event:" + str(events['list'][0])
        assert(events['list'][0]['addr'] == "junk@test.untangle.com")
        assert(events['list'][0]['subject'] == str(fname))
        assert(events['list'][0][ self.shortName() + '_clean'] == True)
        assert(datetime.fromtimestamp((events['list'][0]['time_stamp']['time'])/1000) > startTime)
        nukePassSites()

    @staticmethod
    def finalTearDown(self):
        global node
        if node != None:
            uvmContext.nodeManager().destroy( node.getNodeSettings()["id"] )
            node = None
        

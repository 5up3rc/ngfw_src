import unittest2
import time
import sys
import pdb
import os

from jsonrpc import ServiceProxy
from jsonrpc import JSONRPCException
from uvm import Manager
from uvm import Uvm
import remote_control
import test_registry
import global_functions

uvmContext = Uvm().getUvmContext()
defaultRackId = 1
nodeSettings = None
node = None

#pdb.set_trace()

def touchProtoRule( protoGusername, flag = True, block =True ):
    global nodeSettings,node
    for rec in nodeSettings['protoRules']['list']:
        # print "nodeSettings: " + str(rec)
        if (rec['name'] == protoGusername):
            rec['flag'] = flag
            rec['block'] = block
    node.setSettings(nodeSettings)

def create2MatcherRule( matcher1Type, matcher1Value, matcher2Type, matcher2Value, blocked=True ):
    matcher1TypeStr = str(matcher1Type)
    matcher1ValueStr = str(matcher1Value)
    matcher2TypeStr = str(matcher2Type)
    matcher2ValueStr = str(matcher2Value)
    return {
        "javaClass": "com.untangle.node.application_control.ApplicationControlLogicRule",
        "description": "2-MatcherRule: " + matcher1TypeStr + " = " + matcher1ValueStr + " && " + matcher2TypeStr + " = " + matcher2ValueStr,
        "live": True,
        "id": 1,
        "action": {
            "javaClass": "com.untangle.node.application_control.ApplicationControlLogicRuleAction",
            "actionType": "BLOCK",
            "flag": True
            },
        "matchers": {
            "javaClass": "java.util.LinkedList",
            "list": [
                {
                    "invert": False,
                    "javaClass": "com.untangle.node.application_control.ApplicationControlLogicRuleMatcher",
                    "matcherType": matcher1TypeStr,
                    "value": matcher1ValueStr
                    },
                {
                    "invert": False,
                    "javaClass": "com.untangle.node.application_control.ApplicationControlLogicRuleMatcher",
                    "matcherType": matcher2TypeStr,
                    "value": matcher2ValueStr
                    }
                ]
            }
        };

def nukeLogicRules():
    global node, nodeSettings
    nodeSettings['logicRules']['list'] = []
    node.setSettings(nodeSettings)

def appendLogicRule(newRule):
    global node, nodeSettings
    nodeSettings['logicRules']['list'].append(newRule)
    node.setSettings(nodeSettings)

class ApplicationControlTests(unittest2.TestCase):

    @staticmethod
    def nodeName():
        return "untangle-node-application-control"

    @staticmethod
    def vendorName():
        return "Untangle"

    def setUp(self):
        global nodeSettings, node
        if node == None:
            if (uvmContext.nodeManager().isInstantiated(self.nodeName())):
                print "ERROR: Node %s already installed" % self.nodeName();
                raise Exception('node %s already instantiated' % self.nodeName())
            node = uvmContext.nodeManager().instantiate(self.nodeName(), defaultRackId)
            nodeSettings = node.getSettings()
            # run a few sessions so that the classd daemon starts classifying
            for i in range(2): remote_control.isOnline()
            
    def test_010_clientIsOnline(self):
        result = remote_control.isOnline()
        assert (result == 0)

    def test_011_classdIsRunning(self):
        result = os.system("ps aux | grep classd | grep -v grep >/dev/null 2>&1")
        assert (result == 0)

    def test_020_protoRule_Default_Google(self):
        result = remote_control.runCommand("wget -q -O /dev/null -4 -t 2 --timeout=5 http://www.google.com/")
        assert (result == 0)

    def test_021_protoRule_Block_Google(self):
        touchProtoRule("Google",True,True)
        result = remote_control.runCommand("wget -q -O /dev/null -4 -t 2 --timeout=5 http://www.google.com/")
        assert (result != 0)

    def test_022_protoRule_Allow_Google(self):
        touchProtoRule("Google",False,False)
        result = remote_control.runCommand("wget -4 -q -O /dev/null -t 2 --timeout=5 http://www.google.com/")
        assert (result == 0)

    def test_023_protoRule_Facebook(self):
        touchProtoRule("Facebook",False,False)
        result1 = remote_control.runCommand("wget --no-check-certificate -q -O /dev/null -4 -t 2 --timeout=5 https://facebook.com/")
        touchProtoRule("Facebook",True,True)
        result2 = remote_control.runCommand("wget --no-check-certificate -4 -q -O /dev/null -t 2 --timeout=5 https://facebook.com/")
        touchProtoRule("Facebook",False,False)
        assert (result1 == 0)
        assert (result2 != 0)

    def test_024_protoRule_Dns(self):
        raise unittest2.SkipTest("Test not consistent, disabling.")
        touchProtoRule("DNS",False,False)
        result1 = remote_control.runCommand("host -4 -W3 test.untangle.com 8.8.8.8")
        touchProtoRule("DNS",True,True)
        result2 = remote_control.runCommand("host -4 -W3 test.untangle.com 8.8.8.8")
        touchProtoRule("DNS",False,False)
        assert (result1 == 0)
        assert (result2 != 0)

    def test_025_protoRule_Ftp(self):
        touchProtoRule("FTP",False,False)
        result1 = remote_control.runCommand("wget -q -O /dev/null -4 -t 2 -o /dev/null ftp://test.untangle.com/")
        touchProtoRule("FTP",True,True)
        result2 = remote_control.runCommand("wget -q -O /dev/null -4 -t 2 -o /dev/null ftp://test.untangle.com")
        touchProtoRule("FTP",False,False)
        assert (result1 == 0)
        assert (result2 != 0)

    def test_026_protoRule_Pandora(self):
        touchProtoRule("Pandora",False,False)
        result1 = remote_control.runCommand("wget --no-check-certificate -q -O /dev/null -4 -t 2 --timeout=5 https://pandora.com/")
        touchProtoRule("Pandora",True,True)
        result2 = remote_control.runCommand("wget --no-check-certificate -q -O /dev/null -4 -t 2 --timeout=5 https://pandora.com/")
        touchProtoRule("Pandora",False,False)
        assert (result1 == 0)
        assert (result2 != 0)

    def test_030_logicRule_Allow_Default(self):
        result = remote_control.runCommand("wget --no-check-certificate -q -O /dev/null -4 -t 2 --timeout=5 https://mail.google.com/")
        assert (result == 0)
        
    def test_031_logicRule_Block_Secure(self):
        nukeLogicRules()
        appendLogicRule(create2MatcherRule("PROTOCOL", "TCP", "APPLICATION_CONTROL_PROTOCHAIN", "*/SSL*"))
        result = remote_control.runCommand("wget --no-check-certificate -q -O /dev/null -4 -t 2 --timeout=5 https://mail.google.com/")
        assert (result != 0)

    def test_032_logicRule_Allow_Secure(self):
        nukeLogicRules()
        result = remote_control.runCommand("wget --no-check-certificate -q -O /dev/null -4 -t 2 --timeout=5 https://mail.google.com/")
        assert (result == 0)

    def test_100_eventlog_Block_Google(self):
        touchProtoRule("Google",True,True)
        result = remote_control.runCommand("wget -O /dev/null -4 -t 2 --timeout=5 http://www.google.com/")
        assert (result != 0)
        time.sleep(1)

        events = global_functions.get_events('Application Control','Blocked Sessions',None,1)
        assert(events != None)
        found = global_functions.check_events( events.get('list'), 5, 
                                            "application_control_application", "GOOGLE", 
                                            "application_control_blocked", True,
                                            "application_control_flagged", True)
        assert( found )

    def test_500_classdDaemonReconnect(self):
        for i in range(10):
            print "Test %i" % i
            result = os.system("/etc/init.d/untangle-classd restart >/dev/null 2>&1")
            assert (result == 0)
            result = remote_control.isOnline()
            assert (result == 0)
        # give it some time to recover for future tests
        for i in range(5):
            result = remote_control.isOnline()
            time.sleep(1)

    @staticmethod
    def finalTearDown(self):
        global node
        if node != None:
            uvmContext.nodeManager().destroy( node.getNodeSettings()["id"] )
            node = None

test_registry.registerNode("application-control", ApplicationControlTests)


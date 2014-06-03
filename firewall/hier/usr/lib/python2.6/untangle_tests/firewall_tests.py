import unittest2
import time
import sys
import traceback
from jsonrpc import ServiceProxy
from jsonrpc import JSONRPCException
from uvm import Manager
from uvm import Uvm
from untangle_tests import ClientControl
from untangle_tests import TestDict

uvmContext = Uvm().getUvmContext()
defaultRackId = 1
clientControl = ClientControl()
node = None
testsiteIP = "74.123.29.140"
testsiteIPRange = "74.123.29.139-74.123.29.141"
testsiteIPRange2 = "74.123.27.139-74.123.30.141"

def createSingleMatcherRule( matcherType, value, blocked=True, flagged=True, invert=False ):
    matcherTypeStr = str(matcherType)
    valueStr = str(value)
    return {
        "javaClass": "com.untangle.node.firewall.FirewallRule", 
        "id": 1, 
        "enabled": True, 
        "description": "Single Matcher: " + matcherTypeStr + " = " + valueStr, 
        "flag": flagged, 
        "block": blocked, 
        "matchers": {
            "javaClass": "java.util.LinkedList", 
            "list": [
                {
                    "invert": invert, 
                    "javaClass": "com.untangle.node.firewall.FirewallRuleMatcher", 
                    "matcherType": matcherTypeStr, 
                    "value": valueStr
                    }
                ]
            }
        };

def createDualMatcherRule( matcherType, value, matcherType2, value2, blocked=True, flagged=True, invert=False ):
    matcherTypeStr = str(matcherType)
    valueStr = str(value)
    matcherTypeStr2 = str(matcherType2)
    valueStr2 = str(value2)
    return {
        "javaClass": "com.untangle.node.firewall.FirewallRule", 
        "id": 1, 
        "enabled": True, 
        "description": "Dual Matcher: " + matcherTypeStr + " = " + valueStr + " && " + matcherTypeStr2 + " = " + valueStr2, 
        "flag": flagged, 
        "block": blocked, 
        "matchers": {
            "javaClass": "java.util.LinkedList", 
            "list": [
                {
                    "invert": invert, 
                    "javaClass": "com.untangle.node.firewall.FirewallRuleMatcher", 
                    "matcherType": matcherTypeStr, 
                    "value": valueStr
                    },
                {
                    "invert": invert, 
                    "javaClass": "com.untangle.node.firewall.FirewallRuleMatcher", 
                    "matcherType": matcherTypeStr2, 
                    "value": valueStr2
                    }

                ]
            }
        };

def flushEvents():
    reports = uvmContext.nodeManager().node("untangle-node-reporting")
    if (reports != None):
        reports.flushEvents()

def nukeRules():
    rules = node.getRules()
    rules["list"] = [];
    node.setRules(rules);

def appendRule(newRule):
    rules = node.getRules()
    rules["list"].append(newRule);
    node.setRules(rules);

class FirewallTests(unittest2.TestCase):

    @staticmethod
    def nodeName():
        return "untangle-node-firewall"

    @staticmethod
    def vendorName():
        return "Untangle"

    def setUp(self):
        global node
        if node == None:
            if (uvmContext.nodeManager().isInstantiated(self.nodeName())):
                print "ERROR: Node %s already installed" % self.nodeName();
                raise Exception('node %s already instantiated' % self.nodeName())
            node = uvmContext.nodeManager().instantiate(self.nodeName(), defaultRackId)

    # verify client is online
    def test_010_clientIsOnline(self):
        result = clientControl.runCommand("wget -4 -t 2 --timeout=5 -o /dev/null http://test.untangle.com/")
        assert (result == 0)

    # verify client is online
    def test_011_defaultIsPass(self):
        result = clientControl.runCommand("wget -o /dev/null http://test.untangle.com/")
        assert (result == 0)

    # verify a block port 80 rule works
    def test_020_blockDstPort80(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","80"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify a block port 79-81 rule works
    def test_021_blockDstPort79to81(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","79-81"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify a block port 79,80,81 rule works
    def test_022_blockDstPort79comma80comma81(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","79,80,81"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify a block port 79,81 rule doesnt match 80
    def test_023_blockDstPort79comma81(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","79,81"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify a block port 79,80,81 rule works
    def test_024_blockDstPortList(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","1- 5,80, 90-100"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify a block port any rule works
    def test_025_blockDstPortAny(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","any"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify a block port >79 rule blocks 80
    def test_026_blockDstPortGreaterThan(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT",">79"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify a block port <81 rule blocks 80
    def test_027_blockDstPortLessThan(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","<81"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify a block port <1 rule doesnt block 80
    def test_028_blockDstPortLessThanInverse(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","<1"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify a block udp rule
    def test_028_blockUdpPort53(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","53"));
        result = clientControl.runCommand("host test.untangle.com 4.2.2.1 >/dev/null 2>&1")
        assert (result != 0)

    # verify src addr rule with any works
    def test_030_blockSrcAddrAny(self):
        nukeRules();
        appendRule(createSingleMatcherRule("SRC_ADDR","any"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify src addr rule with IP works
    def test_031_blockSrcAddrIP(self):
        nukeRules();
        appendRule(createSingleMatcherRule("SRC_ADDR",ClientControl.hostIP));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify src addr rule with CIDR works
    def test_032_blockSrcAddrCIDR(self):
        nukeRules();
        appendRule(createSingleMatcherRule("SRC_ADDR",ClientControl.hostIP+"/24"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify src addr rule with commas works
    def test_033_blockSrcAddrComma(self):
        nukeRules();
        appendRule(createSingleMatcherRule("SRC_ADDR","4.3.2.1, "+ ClientControl.hostIP + ",  1.2.3.4/31"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify dst addr rule with any works
    def test_040_blockDstAddrAny(self):
        nukeRules();
        appendRule( createSingleMatcherRule("DST_ADDR","Any", blocked=True) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify dst addr rule with IP works
    def test_041_blockDstAddr(self):
        nukeRules();
        appendRule( createSingleMatcherRule("DST_ADDR",testsiteIP, blocked=True) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify dst addr rule with CIDR works
    def test_042_blockDstAddrCIDR(self):
        nukeRules();
        appendRule( createSingleMatcherRule("DST_ADDR",testsiteIP+"/31", blocked=True) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify dst addr rule with commas works
    def test_043_blockDstAddrComma(self):
        nukeRules();
        appendRule( createSingleMatcherRule("DST_ADDR","1.2.3.4/31," + testsiteIP+",5.6.7.8", blocked=True) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify dst addr rule with commas works
    def test_044_blockDstAddrRange(self):
        nukeRules();
        appendRule( createSingleMatcherRule("DST_ADDR",testsiteIPRange, blocked=True) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify dst addr rule with commas works
    def test_045_blockDstAddrRange2(self):
        nukeRules();
        appendRule( createSingleMatcherRule("DST_ADDR",testsiteIPRange2, blocked=True) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify protocol rule works
    def test_046_blockProtocolTCP(self):
        nukeRules();
        appendRule( createSingleMatcherRule("PROTOCOL","TCP", blocked=True) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify protocol UDP not TCP block rule works
    def test_047_blockProtocolUDPnotTCP(self):
        nukeRules();
        appendRule( createDualMatcherRule("PROTOCOL","UDP", "DST_PORT", 53) );
        result = clientControl.runCommand("host test.untangle.com 4.2.2.1 >/dev/null 2>&1")
        assert (result != 0)
        # Use TCP version of DNS lookup.
        result = clientControl.runCommand("host -T test.untangle.com 4.2.2.1 >/dev/null 2>&1")
        assert (result == 0)

    # verify protocol TCP not UDP block rule works
    def test_048_blockProtocolTCPnotUDP(self):
        nukeRules();
        appendRule( createDualMatcherRule("PROTOCOL","TCP", "DST_PORT", 53) );
        result = clientControl.runCommand("host test.untangle.com 4.2.2.1 >/dev/null 2>&1")
        assert (result == 0)
        # Use TCP version of DNS lookup.
        result = clientControl.runCommand("host -T test.untangle.com 4.2.2.1 >/dev/null 2>&1")
        assert (result != 0)

    # verify src intf any rule works
    def test_050_blockDstIntfAny(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "DST_INTF", "any" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify dst intf number rule works
    def test_051_blockDstIntf(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "DST_INTF", ClientControl.interfaceExternal ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify dst intf number rule doesnt match everythin
    def test_052_blockDstIntfWrongIntf(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "DST_INTF", ClientControl.interfaceExternal + 1 ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify dst intf with commas blocks
    def test_053_blockDstIntfCommas(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "DST_INTF", "99," + str(ClientControl.interfaceExternal) +  ", 100" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify dst intf wan is blockde
    def test_054_blockDstIntfWan(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "DST_INTF", "wan" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify dst intf non_wan not blocked
    def test_055_blockDstIntfNonWan(self):
        nukeRules();
        # specify TCP so the DNS UDP session doesn't get blocked (if it happens to be inbound)
        appendRule( createDualMatcherRule( "DST_INTF", "non_wan", "PROTOCOL", "tcp") );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify src intf any rule works
    def test_060_blockSrcIntfAny(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "SRC_INTF", "any" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify src intf number rule works
    def test_061_blockSrcIntf(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "SRC_INTF", ClientControl.interface ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify src intf number rule doesnt match everythin
    def test_062_blockSrcIntfWrongIntf(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "SRC_INTF", ClientControl.interface + 1 ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify src intf with commas blocks
    def test_063_blockSrcIntfCommas(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "SRC_INTF", "99," + str(ClientControl.interface) +  ", 100" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify src intf non_wan is blocked
    def test_064_blockSrcIntfNonWan(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "SRC_INTF", "non_wan" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify src intf wan not blocked
    def test_065_blockSrcIntfWan(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "SRC_INTF", "wan" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify src penalty box wan not blocked
    def test_066_blockSrcPenaltyBox(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "CLIENT_IN_PENALTY_BOX", None ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify src penalty box wan is blocked when client in penalty box
    def test_067_blockSrcPenaltyBox2(self):
        fname = sys._getframe().f_code.co_name
        nukeRules();
        uvmContext.hostTable().addHostToPenaltyBox( ClientControl.hostIP, 60, fname );
        appendRule( createSingleMatcherRule( "CLIENT_IN_PENALTY_BOX", None ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 1)
        uvmContext.hostTable().releaseHostFromPenaltyBox( ClientControl.hostIP );

    # verify src penalty box wan not blocked
    def test_068_blockDstPenaltyBox(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "SERVER_IN_PENALTY_BOX", None ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify bogus user agent match not blocked
    def test_070_blockUserAgent(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "HTTP_USER_AGENT", "*testtesttesttesttesttesttest*" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify bogus user agent match is blocked after setting agent
    def test_071_blockUserAgent2(self):
        nukeRules();

        entry = uvmContext.hostTable().getHostTableEntry( ClientControl.hostIP )
        entry['httpUserAgent'] = "Mozilla foo bar";
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

        appendRule( createSingleMatcherRule( "HTTP_USER_AGENT", "*Mozilla*" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 1)

        entry['httpUserAgent'] = None;
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

    # verify bogus user agent OS match not blocked
    def test_072_blockUserAgentOs(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "HTTP_USER_AGENT_OS", "*testtesttesttesttesttesttest*" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify bogus user agent OS match blocked after setting OS
    def test_073_blockUserAgentOs2(self):
        nukeRules();

        entry = uvmContext.hostTable().getHostTableEntry( ClientControl.hostIP )
        entry['httpUserAgentOs'] = "foobar Linux barbaz" ;
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

        appendRule( createSingleMatcherRule( "HTTP_USER_AGENT_OS", "*Linux*" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 1)

        entry['httpUserAgentOs'] = None ;
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

    # verify bogus hostname match not blocked
    def test_074_blockClientHostname(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "CLIENT_HOSTNAME", "*testtesttesttesttesttesttest*" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify bogus hostname match blocked after setting hostname
    def test_075_blockClientHostname2(self):
        nukeRules();

        hostname = clientControl.runCommand("hostname -s", stdout=True)
        entry = uvmContext.hostTable().getHostTableEntry( ClientControl.hostIP )
        entry['hostname'] = hostname
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

        appendRule( createSingleMatcherRule( "CLIENT_HOSTNAME", hostname ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 1)

        entry['hostname'] = None ;
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )


    # verify bogus username match not blocked
    def test_076_blockClientUsername(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "USERNAME", "*testtesttesttesttesttesttest*" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify bogus username match not blocked
    def test_077_blockClientUsernameUnauthenticated(self):
        nukeRules();

        # make sure no username is known for this IP
        username = clientControl.runCommand("hostname -s", stdout=True)
        entry = uvmContext.hostTable().getHostTableEntry( ClientControl.hostIP )
        entry['usernameAdConnector'] = None
        entry['usernameCaptive'] = None
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

        appendRule( createSingleMatcherRule( "USERNAME", "[unauthenticated]" ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 1)

    # verify username matcher works
    def test_078_blockClientUsernameManual(self):
        nukeRules();

        username = clientControl.runCommand("hostname -s", stdout=True)
        entry = uvmContext.hostTable().getHostTableEntry( ClientControl.hostIP )
        entry['usernameAdConnector'] = username
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

        appendRule( createSingleMatcherRule( "USERNAME", username ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 1)

        entry['usernameAdConnector'] = None ;
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

    # verify "[authenticated]" matches any username
    def test_079_blockClientUsernameAuthenticated(self):
        nukeRules();

        username = clientControl.runCommand("hostname -s", stdout=True)
        entry = uvmContext.hostTable().getHostTableEntry( ClientControl.hostIP )
        entry['usernameAdConnector'] = username
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

        appendRule( createSingleMatcherRule( "USERNAME", '[authenticated]' ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 1)

        entry['usernameAdConnector'] = None ;
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

    # verify "*" matches any username but not null
    def test_080_blockClientUsernameStar(self):
        nukeRules();

        username = clientControl.runCommand("hostname -s", stdout=True)
        entry = uvmContext.hostTable().getHostTableEntry( ClientControl.hostIP )
        entry['usernameAdConnector'] = username
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

        appendRule( createSingleMatcherRule( "USERNAME", '*' ) );

        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 1)

        entry = uvmContext.hostTable().getHostTableEntry( ClientControl.hostIP )
        entry['usernameAdConnector'] = None
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

        entry['usernameAdConnector'] = None ;
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

    # verify '' username matches null username (but not all usernames)
    def test_081_blockClientUsernameBlank(self):
        nukeRules();

        username = clientControl.runCommand("hostname -s", stdout=True)
        entry = uvmContext.hostTable().getHostTableEntry( ClientControl.hostIP )
        entry['usernameAdConnector'] = username
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

        appendRule( createSingleMatcherRule( "USERNAME", '' ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

        entry = uvmContext.hostTable().getHostTableEntry( ClientControl.hostIP )
        entry['usernameAdConnector'] = None
        uvmContext.hostTable().setHostTableEntry( ClientControl.hostIP, entry )

        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 1)

    # verify username is NOT '*' matches null username
    def test_082_blockClientUsernameBlank2(self):
        nukeRules();
        appendRule( createSingleMatcherRule( "USERNAME", '*', invert=True ) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 1)

    # verify rules that a rule with two matching matchers works
    def test_085_dualMatcherRule(self):
        nukeRules();
        appendRule( createDualMatcherRule("SRC_ADDR", ClientControl.hostIP, "DST_PORT", 80) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify rules that both MUST match for the session to be blocked
    def test_086_dualMatcherRuleAnd(self):
        nukeRules();
        appendRule( createDualMatcherRule("SRC_ADDR", ClientControl.hostIP, "DST_PORT", 79) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify rules evaluated in order
    def test_090_ruleOrder(self):
        nukeRules();
        appendRule( createSingleMatcherRule("SRC_ADDR", ClientControl.hostIP, blocked=False) );
        appendRule( createSingleMatcherRule("DST_PORT", "80") );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)

    # verify rules evaluated in order
    def test_091_ruleOrderReverse(self):
        nukeRules();
        appendRule( createSingleMatcherRule("DST_PORT", "80") );
        appendRule( createSingleMatcherRule("SRC_ADDR", ClientControl.hostIP, blocked=False) );
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)

    # verify a block port 80 rule works
    def test_100_blockDstPort80EventLog(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","80"));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result != 0)
        flushEvents()
        query = None;
        for q in node.getEventQueries():
            if q['name'] == 'Blocked Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'] != None)
        assert(len(events['list']) > 0)
        assert(events['list'][0]['c_client_addr'] == ClientControl.hostIP)
        assert(events['list'][0]['s_server_port'] == 80)
        assert(events['list'][0]['firewall_rule_index'] != 0 and events['list'][0]['firewall_rule_index'] != None)
        assert(events['list'][0]['firewall_blocked'] == True)
        assert(events['list'][0]['firewall_flagged'] == True)

    # verify a flag port 80 rule works
    def test_101_flagDstPort80EventLog(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","80",blocked=False,flagged=True));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)
        flushEvents()
        query = None;
        for q in node.getEventQueries():
            if q['name'] == 'Flagged Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'] != None)
        assert(len(events['list']) > 0)
        assert(events['list'][0]['c_client_addr'] == ClientControl.hostIP)
        assert(events['list'][0]['s_server_port'] == 80)
        assert(events['list'][0]['firewall_rule_index'] != 0 and events['list'][0]['firewall_rule_index'] != None)
        assert(events['list'][0]['firewall_flagged'] == True)
        assert(events['list'][0]['firewall_blocked'] == False)

    # verify a port 80 rule log
    def test_102_logDstPort80EventLog(self):
        nukeRules();
        appendRule(createSingleMatcherRule("DST_PORT","80",blocked=False,flagged=False));
        result = clientControl.runCommand("wget -o /dev/null -t 1 --timeout=3 http://test.untangle.com/")
        assert (result == 0)
        flushEvents()
        query = None;
        for q in node.getEventQueries():
            if q['name'] == 'All Events': query = q;
        assert(query != None)
        events = uvmContext.getEvents(query['query'],defaultRackId,1)
        assert(events != None)
        assert(events['list'] != None)
        assert(len(events['list']) > 0)
        assert(events['list'][0]['c_client_addr'] == ClientControl.hostIP)
        assert(events['list'][0]['s_server_port'] == 80)
        assert(events['list'][0]['firewall_rule_index'] != 0 and events['list'][0]['firewall_rule_index'] != None)
        assert(events['list'][0]['firewall_blocked'] == False)
        assert(events['list'][0]['firewall_flagged'] == False)

    def test_999_finalTearDown(self):
        global node
        uvmContext.nodeManager().destroy( node.getNodeSettings()["id"] )
        node = None
        
TestDict.registerNode("firewall", FirewallTests)








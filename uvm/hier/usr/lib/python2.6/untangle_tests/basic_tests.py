import unittest2
from jsonrpc import ServiceProxy
from jsonrpc import JSONRPCException
from uvm import Manager
from uvm import Uvm
from untangle_tests import ClientControl

uvmContext = Uvm().getUvmContext()
clientControl = ClientControl()

class TestEnvironmentTests(unittest2.TestCase):

    # verify connectivity to untangle-vm
    def test_01_uvmConnectivity(self):
        global uvmContext
        try:
            version = uvmContext.version()
        except JSONRPCException, e:
            raise AssertionError("Failed to connect to untangle-vm")

    # verify reports is installed (needed for event log tests)
    def test_02_reportsIsInstalled(self):
        global uvmContext
        assert (uvmContext.nodeManager().isInstantiated('untangle-node-reporting'))

    # verify reports flush events works
    def test_03_reportsFlushEvents(self):
        reports = uvmContext.nodeManager().node("untangle-node-reporting")
        assert (reports != None)
        reports.flushEvents()

    # verify connectivity to client
    def test_10_clientConnectivity(self):
        result = clientControl.runCommand("/bin/true")
        assert (result == 0)

    # verify client can exec commands and return code
    def test_11_clientShellReturnCode(self):
        result = clientControl.runCommand("/bin/true")
        assert (result == 0)
        result = clientControl.runCommand("/bin/false")
        assert (result == 1)

    # verify client can exec commands and return code
    def test_12_clientShellOutput(self):
        result = clientControl.runCommand("echo yay", stdout=True)
        assert (result == "yay")

    # verify client has necessary tools
    def test_13_clientHasNecessaryTools(self):
        result = clientControl.runCommand("which wget >/dev/null")
        assert (result == 0)
        result = clientControl.runCommand("which curl >/dev/null")
        assert (result == 0)
        result = clientControl.runCommand("which netcat >/dev/null")
        assert (result == 0)
        result = clientControl.runCommand("which nmap >/dev/null")
        assert (result == 0)
        result = clientControl.runCommand("which python >/dev/null")
        assert (result == 0)
        result = clientControl.runCommand("which mime-construct >/dev/null")
        assert (result == 0)
        result = clientControl.runCommand("which pidof >/dev/null")
        assert (result == 0)

    # verify client is online
    def test_14_clientIsOnline(self):
        result = clientControl.runCommand("wget -4 -t 2 --timeout=5 -o /dev/null http://test.untangle.com/")
        assert (result == 0)
        result = clientControl.runCommand("wget -4 -t 2 --timeout=5 -o /dev/null http://google.com/")
        assert (result == 0)

    # verify client can pass UDP
    def test_20_clientCanPassUDP(self):
        result = clientControl.runCommand("host cnn.com 8.8.8.8 > /dev/null")
        assert (result == 0)
        result = clientControl.runCommand("host google.com 8.8.8.8 > /dev/null")
        assert (result == 0)

    # verify client is online
    def test_30_clientNotRunningOpenvpn(self):
        result = clientControl.runCommand("pidof openvpn >/dev/null")
        assert (result != 0)

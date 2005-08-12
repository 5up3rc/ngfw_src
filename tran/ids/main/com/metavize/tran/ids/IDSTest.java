package com.metavize.tran.ids;

import java.util.*;
import java.nio.ByteBuffer;

import java.net.InetAddress;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import com.metavize.mvvm.tapi.Protocol;
import com.metavize.mvvm.tapi.IPSession;
import com.metavize.mvvm.tapi.event.*;
import com.metavize.mvvm.tran.ParseException;

public class IDSTest {

	private class TestDataEvent implements IPDataEvent {
		ByteBuffer buffer;
		public TestDataEvent() {
			buffer = ByteBuffer.allocate(512);
		}

		public TestDataEvent(String str) {
			setData(str.getBytes());
		}

		public void setData(byte[] data) {
			buffer.clear();
			buffer.put(data);
			buffer.flip();
		}
		public ByteBuffer data() {
			return buffer;
		}
	}
	
	private static final Logger log = Logger.getLogger(IDSTest.class);
	private IDSRules rules = new IDSRules();
	
	static {
		log.setLevel(Level.ALL);
	}
	public IDSTest() {}

	public boolean runTest() {
		log.warn("\n************************Running Test******************************");
		generateRuleTest();
		runHeaderTest();
		runSignatureTest();
		generateRandomRuleHeaders(1000);
		runTimeTest(1);
		return true;
	}

	private boolean generateRuleTest() {
		String testValidStrings[] 	= { 
			"alert tcp any any -> any any (msg:\"Rule Zero\"; content:\"|00|bob|00||00|bob\")",
			"alert tcp 10.0.0.40-10.0.0.101 any -> 66.35.250.0/24 80 (content:\"bob\"; msg:\"Rule One\"; flow: to_server;)",
			"alert tcp 10.0.0.101 !5000: -> 10.0.0.1/16 !80 (content: \"BOB\"; offset: 3; nocase; msg:\"Rule tW0\"; flow: from_server;)",
			"alert TCP 10.0.0.101 4000:5000 <> 10.0.0.1/24 :6000 (content:\"bob\"; content:\"BOB\"; nocase; msg:  Rule 3; dsize: < 5;)",
			"alert tcp [10.0.0.101,192.168.1.1,10.0.0.44] !:80 -> any 80 (msg: Rule x4x; dsize: 3<> 10; )",
			"alert tcp any any -> any any (msg:\"Rule 5\"; dsize:  > 4 ;)",
			"alert tcp any any -> any any (msg:\"Rule 6\"; content:|DE AD BE EF|BOB; nocase;)", 
			"alert tcp any any -> any any (msg:\"Rule 7\"; pcre:\"/r(a|u)wr/smi\" ;)",
			"alert tcp 66.35.250.0/24 any -> 10.0.0.1/24 any (msg:\"Rule 8, Server as client test\")" };
		
		for(int i=0; i < testValidStrings.length; i++) {
			try { 
				rules.addRule(testValidStrings[i]);
			} catch (ParseException e)  { log.error(e.getMessage()); }
		}
		return true;
	}

	private void runSignatureTest() {
		/**These test ignore header matching, and thus can deal with
		 * all the test signatures*/
		
		/**Setup*/
		List<IDSRuleHeader> ruleList = rules.getHeaders();
		List<IDSRuleSignature> signatures = new LinkedList<IDSRuleSignature>();
		Iterator<IDSRuleHeader> it = ruleList.iterator();
		while(it.hasNext())
			signatures.add(it.next().getSignature());
		IDSSessionInfo info = new IDSSessionInfo();
		info.setSignatures(signatures);

		/**Run Tests*/
		TestDataEvent test = new TestDataEvent();
		checkSessionData(info, test, true, 0, false);
		checkSessionData(info, test, false, 4, false);
		
		byte[] basicNoCase = {'b','o','b'};
		test.setData(basicNoCase);
		checkSessionData(info, test, false, 1, true);
		checkSessionData(info, test, true, 1, false);
		checkSessionData(info, test, true, 2, false);
		checkSessionData(info, test, true, 3, true);
		checkSessionData(info, test, false, 5, false);

		byte[] basicNoCase1 = {'1','2','3','B','O','B'};
		test.setData(basicNoCase1);
		checkSessionData(info, test, false, 1, false);
        checkSessionData(info, test, true, 2, true);
		checkSessionData(info, test, false, 6, false);

		byte[] dSizeTest = { 'c', 'c', 'c', 'c', 'c', 'b', 'o', 'b' };
		test.setData(dSizeTest);
		checkSessionData(info, test, false, 1, true);
		checkSessionData(info, test, true, 2, true);
		checkSessionData(info, test, true, 3, false);
		checkSessionData(info, test, false, 4, true);
		checkSessionData(info, test, false, 5, true);

		byte[] complexContentStuff = { '4','2',(byte)0xDE,(byte)0xAD,(byte)0xBE,(byte)0xEF,'b','o','b',(byte)0x1F,(byte)0x12 };
		test.setData(complexContentStuff);
		checkSessionData(info, test, false,4, false);
		checkSessionData(info, test, true, 5, true);
		checkSessionData(info, test, false, 6, true);
	}
				
	private void checkSessionData(IDSSessionInfo info, IPDataEvent event, boolean isServer,int ruleNum,  boolean answer) {
		info.setEvent(event);
		info.setFlow(isServer);
		if(!checkAnswer(info.testSignature(ruleNum), answer)) {
			log.warn("\tOption Test Failed on rule:\n " + ruleNum);
			log.warn("\tSignature contents::\n " + info.getSignature(ruleNum));
			log.warn("Data: " + new String(event.data().array()));
		}
	}
					

	private void runHeaderTest() {
		
		List<IDSRuleHeader> ruleList = rules.getHeaders();
		
		matchTest(ruleList.get(1), Protocol.TCP, "10.0.0.101", 33242, "66.35.250.8", 80, true);
		matchTest(ruleList.get(0), Protocol.TCP, "192.168.1.1", 33065, "66.33.22.111", 80, true);
		matchTest(ruleList.get(0), Protocol.UDP, "192.168.1.1", 33065, "66.33.22.111", 80, false);
		matchTest(ruleList.get(1), Protocol.TCP, "192.168.1.1", 33065, "66.33.22.111", 80, false);
		matchTest(ruleList.get(2), Protocol.TCP, "192.168.1.1", 33065, "66.33.22.111", 80, false);
		matchTest(ruleList.get(4), Protocol.TCP, "192.168.1.1", 33065, "66.33.22.111", 80, true);
		matchTest(ruleList.get(8), Protocol.TCP, "10.0.0.44", 33065, "66.35.250.8", 80, false);
		matchTest(ruleList.get(8), Protocol.TCP, "66.35.250.8", 33065, "10.0.0.44", 80, true);
		matchTest(ruleList.get(1), Protocol.TCP, "10.0.0.43", 1024, "10.0.0.101", 4747, false);
		matchTest(ruleList.get(4), Protocol.TCP, "10.0.0.43", 1024, "10.0.0.101", 4747, false);
		matchTest(ruleList.get(1), Protocol.TCP, "10.0.0.101",3232,"10.0.0.31",4999, false);
		matchTest(ruleList.get(2), Protocol.TCP, "10.0.0.101",3232,"10.0.0.31",4999, true);
		matchTest(ruleList.get(3), Protocol.TCP, "10.0.0.101",3232,"10.0.0.31",4999, false);
	}

	private void matchTest(IDSRuleHeader header, Protocol protocol, String clientAddr, int clientPort, String serverAddr, int serverPort, boolean answer) {
		InetAddress clientAddress = null;
		InetAddress serverAddress = null;
		try {
			clientAddress = InetAddress.getByName(clientAddr);
			serverAddress = InetAddress.getByName(serverAddr);
		} catch( Exception e ) { log.error(e); }

		if(!checkAnswer(header.matches(protocol, clientAddress, clientPort, serverAddress, serverPort),answer))
			log.warn("Match Test Failed:\n"  + 
					"Client:" +clientAddress+":"+clientPort + 
					"\nServer:" +serverAddress+":"+serverPort +
					"\ndoes not match rule:\n" + header +"\n");
	}
	
	private boolean checkAnswer(boolean eval, boolean correct) {
		if(eval != correct) 
			log.warn("Evaluated: "+ eval+ " Should be: " + correct);
		return eval == correct;
	}

	private void runTimeTest(int seconds) {
		long stopTime = System.currentTimeMillis() + seconds*1000;
		int counter = 0;
		//rules = IDSDetectionEngine.instance().getRulesForTesting();

		Random rand = new Random();
		while(stopTime > System.currentTimeMillis()) {
			try {
				InetAddress addr1 = InetAddress.getByName(rand.nextInt(256)+"."+rand.nextInt(256)+"."+rand.nextInt(256)+"."+rand.nextInt(256));
				InetAddress addr2 = InetAddress.getByName(rand.nextInt(256)+"."+rand.nextInt(256)+"."+rand.nextInt(256)+"."+rand.nextInt(256));
				rules.matchesHeader(Protocol.TCP, addr1, rand.nextInt(65536), addr2, rand.nextInt(65536));
			} catch (Exception e) { log.error("er");}
			counter++;
		}
		double timePerMatch = (double)seconds*1000/(double)counter;
		log.info("Completed " + counter+ " matches in " + seconds + " seconds "+" ("+timePerMatch+" ms/match"+").");
	}

	private void generateRandomRuleHeaders(int num) {
		long startTime = System.currentTimeMillis();
		Random rand = new Random();
		rules.clear();
		for(int i=0;i<num;i++) {

			String dir;
			String prot;
			String clientIP, serverIP;
			String clientPort, serverPort;
			if(rand.nextInt(2) == 0)
				dir = " ->";
			else
				dir = " <>";
			
			if(rand.nextInt(2) == 0)
				prot = " udp";
			else
				prot = " TCP";
			clientIP = getRandomIPAddress();
			serverIP = getRandomIPAddress();

			clientPort = getRandomPort();
			serverPort = getRandomPort();

			try {
				rules.addRule("alert"+prot+clientIP+clientPort+dir+serverIP+serverPort+" ( content: \"I like spoons\"; msg: \"This is just a test\";)");
			} catch(ParseException e) { log.error("Could not parse rule; " + e.getMessage()); }
		}
		long endTime = System.currentTimeMillis() - startTime;
		log.info("Time it took to parse " + num +" rules: " + endTime + " milliseconds");
	}

	private String getRandomPort() {
		String str;
		Random rand = new Random();
		switch(rand.nextInt(3)) {
			case 0:
				str = "any";
				break;
			case 1:
				str = rand.nextInt(65536)+"";
				break;
			case 2:
				int port1 = rand.nextInt(65536);
				int port2 = rand.nextInt(65536);
				str = port1+":"+port2;
				break;
			default:
				str = "any";
		}
		return " "+str;
	}
	
	private String getRandomIPAddress() {
		String str;
		Random rand = new Random();
		switch(rand.nextInt(4))  {
			case 0:
				str = "any";
				break;
			case 1:
				str ="10.0.0.1/24";
				break;
			case 2:
				str = "[192.168.0.1,192.168.0.30,192.168.0.101]";						
				break;
			case 3:
				str = rand.nextInt(256)+"."+rand.nextInt(256)+"."+rand.nextInt(256)+"."+rand.nextInt(256);
				break;
			default:
				str = "any";
		}
		return " "+str;
	}
}

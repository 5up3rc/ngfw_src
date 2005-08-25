package com.metavize.tran.ids;

import java.util.regex.*;
import java.util.List;
import java.util.LinkedList;
import java.util.Vector;

import com.metavize.tran.ids.options.*;
import com.metavize.mvvm.tapi.Protocol;
import com.metavize.mvvm.tran.firewall.IPMatcher;
import com.metavize.mvvm.tran.PortRange;
import com.metavize.mvvm.tran.ParseException;

public class IDSStringParser {

	public static final String HOME_IP = "Home"+0xBEEF;
	public static final String EXTERNAL_IP = "EXTERNAL"+0xBEEF;
	private static Pattern maskPattern = Pattern.compile("\\d\\d");
	private static Pattern semicolonMask = Pattern.compile("\\;");
	
	private static boolean clientIPFlag;
	private static boolean clientPortFlag;
	private static boolean serverIPFlag;
	private static boolean serverPortFlag;
	
	public static String[] parseRuleSplit(String rule) throws ParseException {
		int first = rule.indexOf("(");
		int last = rule.lastIndexOf(")");
		if(first < 0 || last < 0)
			throw new ParseException("Could not split rule: ");
		String parts[] = { rule.substring(0,first).trim(), rule.substring(first+1,last).trim() };
		
		return parts;
	}
	
	public static IDSRuleSignature parseSignature(String signatureString, int action) throws ParseException {
		IDSRuleSignature returnSignature = new IDSRuleSignature(action);
		
		
		String replaceChar = ""+0xff42;
		signatureString = signatureString.replaceAll("\\\\;",replaceChar);
		String options[] = signatureString.trim().split(";");
		for(int i = 0; i < options.length; i++) {
			options[i].trim();	
			options[i] = options[i].replaceAll(replaceChar,"\\\\;");
			int delim = options[i].indexOf(':');
			if(delim < 0)
				returnSignature.addOption(options[i].trim(),"No Params");
			else {
				String opt = options[i].substring(0,delim).trim();
				returnSignature.addOption(opt, options[i].substring(delim+1).trim());
			}
		} 
		return returnSignature;
	}	
	public static IDSRuleHeader parseHeader(String header) throws ParseException {
		
		int action;
		List<IPMatcher> ipMatcher, portMatcher;
		clientIPFlag = clientPortFlag = serverIPFlag = serverPortFlag = false;
		
		/* Header should match: action prot sourceIP sourcePort -> destIP destPort */
		String tokens[] = header.split(" ");
		if(tokens.length != 7) {
			throw new ParseException("Not a valid String Header" + header);
		}
		
		/*Objects needed for a IDSRuleHeader constructor*/
		Protocol protocol;
		List<IPMatcher> clientIPList, serverIPList;
		PortRange clientPortRange, serverPortRange;
		boolean	direction = parseDirection(tokens[4]);
		
		action = parseAction(tokens[0]);
		/*Parse Protocol*/
		protocol = parseProtocol(tokens[1]);
		/*Parse server and client IP data - this will throw exceptions*/
		clientIPFlag	= parseNegation(tokens[2]);
		tokens[2]		= stripNegation(tokens[2]);
		clientIPList 	= parseIPToken(tokens[2]);
		
		serverIPFlag 	= parseNegation(tokens[5]);
		tokens[5]		= stripNegation(tokens[5]);
		serverIPList 	= parseIPToken(tokens[5]);
		
		/*Parse server and client port data - this will not throw exceptions*/
		clientPortFlag	= parseNegation(tokens[3]);
		tokens[3]		= stripNegation(tokens[3]);
		clientPortRange = parsePortToken(tokens[3]);
		
		serverPortFlag 	= parseNegation(tokens[6]);
		tokens[6]		= stripNegation(tokens[6]);
		serverPortRange = parsePortToken(tokens[6]);

		/*So we throw them ourselves*/
		if(clientPortRange == null)
			throw new ParseException("Invalid source port: " + tokens[3]);
		if(serverPortRange == null) {
			throw new ParseException("Invalid destination port: " +tokens[6]);
		}
		
		/*Build and return the rule header*/
		IDSRuleHeader ruleHeader = new IDSRuleHeader(action, direction, protocol, clientIPList, clientPortRange, serverIPList, serverPortRange);
		ruleHeader.setNegationFlags(clientIPFlag, clientPortFlag, serverIPFlag, serverPortFlag);
		return ruleHeader;
	}

	private static int parseAction(String action) throws ParseException {
		String validActions[] = IDSRuleManager.ACTIONS;
		for(int i=0; i < validActions.length;i++) {
			if(validActions[i].equalsIgnoreCase(action))
				return i;
		}
		throw new ParseException("Not a valid action: " + action);
	}

	private static Protocol parseProtocol(String protoString) throws ParseException {
		if(protoString.equalsIgnoreCase("tcp"))
			return Protocol.TCP;
		else if(protoString.equalsIgnoreCase("udp"))
			return Protocol.UDP;
		else
			throw new ParseException("Invalid Protocol string: " + protoString);
	}

	private static boolean parseDirection(String direction) throws ParseException  {
		if(direction.equals("<>"))
			return IDSRuleHeader.IS_BIDIRECTIONAL;
		else if(direction.equals("->"))
			return !IDSRuleHeader.IS_BIDIRECTIONAL;
		else
			throw new ParseException("Invalid direction opperator: " + direction);
	}

	private static String stripNegation(String str) {
		return str.replaceAll("!","");
	}
	private static boolean parseNegation(String negationString) {
		 if(negationString.contains("!"))
			 return true;
		 return false;
	}

	private static List<IPMatcher> parseIPToken(String ipString) throws ParseException {
		
		List<IPMatcher> ipList = new Vector<IPMatcher>();
		if(ipString.equalsIgnoreCase("any"))
			ipList.add(IPMatcher.MATCHER_ALL);
		else if(ipString.equalsIgnoreCase(EXTERNAL_IP))
				ipList.add(IPMatcher.MATCHER_EXTERNAL);
		else if(ipString.equalsIgnoreCase(HOME_IP))
				ipList.add(IPMatcher.MATCHER_INTERNAL);
		else {
			ipString = ipString.replaceAll("\\[","");
			ipString = ipString.replaceAll("\\]","");
			
			String allAddrs[] = ipString.split(",");			
			for(int i=0; i < allAddrs.length; i++)
				ipList.add(IPMatcher.parse(validateMask(allAddrs[i])));
		}
		return ipList;
	}
	
	private static PortRange parsePortToken(String portString) {
		if(portString.equalsIgnoreCase("any"))
			return PortRange.ANY;
		else {
			int port  = -1;
			int port2 = -1;
			int index = portString.indexOf(":");
			/**
			 * Matches port string style xxxx (no range)
			 * */
			if(index == -1) {
				port = Integer.parseInt(portString);
				if(port >= 0)
					return new PortRange(port, port);
			}
			/**
			 * Matches port string style :xxxx (0 to xxxx)
			 * */
			else if(index == 0) {
				port = Integer.parseInt(portString.substring(1,portString.length()));
				if(port >= 0 && port <= 65535)
					return new PortRange(0,port);
			}
			/**
			 * Matches port string style xxxx: (xxxx to 65535)
			 * */
			else if(index == portString.length() - 1) {
				port = Integer.parseInt(portString.substring(0,index));
				if(port >= 0 && port <= 65535)
					return new PortRange(port,65535);
			}
			/**
			 * Matches port string style xxxx:yyyy (xxxx to yyyy)
			 * */
			else {
				port = Integer.parseInt(portString.substring(0,index));
				port2 = Integer.parseInt(portString.substring(index+1,portString.length()));
				if( port >= 0 && port2 >= 0 && port <= 65535 && port2 <= 65535)
					return new PortRange(port, port2);
			}
			return null;
		}
	}
		
	/**
	 * This function converts an ip mask in the form of x.x.x.x/24
	 * to an ip type mask, eg x.x.x.x/255.255.255.0 (24 ones followed by 8 zeros)
	 * Useful becuase IPMatcher cannot parse the first, but can parse the second.
	 **/
	private static String validateMask(String ipAddr) {
		String mask[] = ipAddr.split("/");
		if(mask.length != 2)
			return ipAddr;
		Matcher m = maskPattern.matcher(mask[1]);
		if(m.matches()) {
			int maskNum = Integer.parseInt(mask[1]);
			if(maskNum > 32 || maskNum < 0)
				validateMask(mask[0]+"/32");
			
			long tmp = 0xFFFFFFFF;
			tmp = tmp << (32-maskNum);
			return mask[0]+"/"+longToIPv4String(tmp);
		}
		return mask[0];
	}

	private static String longToIPv4String(long addr) {
		String addrString = "";
		
		for ( int c = 4 ; --c >= 0  ; ) {
			addrString += (int)((addr >> ( 8 * c )) & 0xFF);
			if ( c > 0 )
				addrString += ".";
		}	
		return addrString;
	}
}

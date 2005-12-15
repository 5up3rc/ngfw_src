 /*
  * Copyright (c) 2005 Metavize Inc.
  * All rights reserved.
  *
  * This software is the confidential and proprietary information of
  * Metavize Inc. ("Confidential Information").  You shall
  * not disclose such Confidential Information.
  *
  * $Id:$
  */
package com.metavize.tran.mail.papi.smtp;

import static com.metavize.tran.util.BufferUtil.*;
import static com.metavize.tran.util.ASCIIUtil.*;
import static com.metavize.tran.util.Ascii.*;

import java.util.*;
import java.nio.*;

//TODO bscott unbounded nature of buffering here
//     concerns me, but who would connect to a
//     roughe SMTP server?!?

//TODO bscott I've decided to use the "last" code
//     as the code, in case the lines have different
//     codes (!?!)

/**
 * Stateful class which accumulates
 * and parser server responses (possibly
 * multi-line).
 * <br>
 * Because of classloader issues this class is public.  However,
 * it should really not be used other than in the casing.
 */
public class ResponseParser {

  private List<ResponseLine> m_lines;

  public ResponseParser() {
  }

  /**
   * Cause this Parser to forget its state,
   * such that the next call to "parse" will
   * begin a new response.
   */
  public void reset() {
    m_lines = null;
  }



  /**
   * Parse method.
   * If null is returned, bytes may be trapped in the
   * ByteBuffer.  These should be handed-back later.
   *
   * @param buf a buffer with response candidate bytes
   * @return the response, or null if more bytes are
   *         required.
   */
  public Response parse(ByteBuffer buf)
    throws NotAnSMTPResponseLineException {
    
    while(buf.hasRemaining()) {
      int endOfLine = findCrLf(buf);
      if(endOfLine == -1) {
        return null;
      }
      ByteBuffer line = buf.duplicate();
      line.limit(endOfLine);
      if(line.remaining() < 3) {
        throw new NotAnSMTPResponseLineException("Not a response line (too short) \"" +
          bbToString(line) + "\"");
      }
      int replyCode = readReplyCode(line);
      if(replyCode == -1) {
        //I guess we could check for > 600 or <0, but this
        //class doesn't enforce semantics, just format.
        throw new NotAnSMTPResponseLineException("Not a response line (no reply code) \"" +
          bbToString(line) + "\"");
      }

      boolean isLast = true;
      String arg = null;
      
      if(line.hasRemaining()) {
        byte b = line.get();
        if(!(b == SP || b == DASH)) {
          ByteBuffer dup = buf.duplicate();
          dup.limit(endOfLine);
          throw new NotAnSMTPResponseLineException("Not a response line NNNX where " +
            "NNN is number and X is not space or dash \"" +
            bbToString(line) + "\"");          
        }
        isLast = (b == SP);
        if(line.hasRemaining()) {
          arg = bbToString(line);
        }
      }
      else {
        //No space, which we'll interpret as last
        isLast = true;//Redundant
        arg = null;//Redundant
      }

      //Consume the line from the buffer
      buf.position(endOfLine + 2);
      
      ResponseLine respLine = new ResponseLine(replyCode, arg, isLast);
      if(!respLine.isLast) {
        if(m_lines == null) {
          m_lines = new ArrayList<ResponseLine>();
        }
        m_lines.add(respLine);
        continue;
      }
      else {
        if(m_lines != null) {
          m_lines.add(respLine);
          Response ret = new Response(replyCode, getLineArgs(m_lines));
          reset();
          return ret;
        }
        else {
          reset();
          return new Response(replyCode, arg);
        }
      }
    }
    return null;
  }

  private static String[] getLineArgs(List<ResponseLine> lines) {
    String[] ret = new String[lines.size()];
    for(int i = 0; i<ret.length; i++) {
      ret[i] = lines.get(i).arg;
      if(ret[i] == null) {
        ret[i] = "";
      }
    }
    return ret;
  }

  //PRE: At least 3 bytes for reading.
  //If not a reply line, buffer is rewound
  //Copied from Aaron.  Thanks! - wrs
  private static int readReplyCode(ByteBuffer buf) {
    int i = 0;

    byte c = buf.get();
    if(48 <= c && 57 >= c) {
        i = (c - 48) * 100;
    } else {
        buf.position(buf.position()-1);
        return -1;
    }

    c = buf.get();
    if (48 <= c && 57 >= c) {
        i += (c - 48) * 10;
    } else {
        buf.position(buf.position()-2);
        return -1;
    }

    c = buf.get();
    if (48 <= c && 57 >= c) {
        i += (c - 48);
        return i;
    } else {
        buf.position(buf.position()-3);
        return -1;
    }
  }

  private class ResponseLine {
    final int code;
    final String arg;
    final boolean isLast;

    ResponseLine(int code,
      String arg,
      boolean isLast) {
      this.code = code;
      this.arg = arg;
      this.isLast = isLast;
    }

    public String toString() {
      return (code + (isLast?" ":"-") + (arg == null?"":arg));
    }
  }

//====================== Testing =========================

/*

  public static void main(String[] args) throws Exception {
    String crlf = new String(CRLF_BA);
    String dash = new String(new byte[] {DASH});
    String sp = new String(new byte[] {SP});

    //
    String inputStr;
    String outputStr;

    
    inputStr = "123 " + crlf;
    outputStr = inputStr;
    doTest("Normal Conforming", inputStr.getBytes(), outputStr.getBytes(), false);

    inputStr = "123" + crlf;
    outputStr = "123 " + crlf;
    doTest("Normal w/o space", inputStr.getBytes(), outputStr.getBytes(), false);

    inputStr = "12x" + crlf;
    outputStr = inputStr;
    doTest("12x (bad)", inputStr.getBytes(), outputStr.getBytes(), true);

    inputStr = "x23" + crlf;
    outputStr = inputStr;
    doTest("x23 (bad)", inputStr.getBytes(), outputStr.getBytes(), true);

    inputStr = "123-" + crlf + "123 " + crlf;
    outputStr = inputStr;
    doTest("123-CRLF123CRLF", inputStr.getBytes(), outputStr.getBytes(), false);
    
    inputStr = "123-" + crlf + "123" + crlf;
    outputStr = "123-" + crlf + "123 " + crlf;
    doTest("123-CRLF123CRLF (fix trailing space)", inputStr.getBytes(), outputStr.getBytes(), false);

    inputStr = "123-" + crlf + "12x" + crlf;
    outputStr = inputStr;
    doTest("123-CRLF12xCRLF (bad)", inputStr.getBytes(), outputStr.getBytes(), true);

    inputStr = "123-" + crlf + "x23" + crlf;
    outputStr = inputStr;
    doTest("123-CRLFx23CRLF (bad)", inputStr.getBytes(), outputStr.getBytes(), true);

    inputStr = "123-foo" + crlf + "123 moo" + crlf;
    outputStr = inputStr;
    doTest("text on each line", inputStr.getBytes(), outputStr.getBytes(), false);

    inputStr = "123-foo" + crlf +
      "123-moo" + crlf +
      "123-doo" + crlf +
      "123 goo" + crlf;
    outputStr = inputStr;
    doTest("text on each of 4 lines", inputStr.getBytes(), outputStr.getBytes(), false);    
  }




  private static void doTest(String name,
    byte[] input,
    byte[] expectedOut,
    boolean expectException) throws Exception {
    int len = Math.min(input.length, 5);
    for(int i = 1; i<len; i++) {
      test(name, input, expectedOut, i, expectException);
    }
  }

  //Performs test on the given input/output,
  //by trying all combinations of array sizes
  //for the given number of buffers.
  private static void test(String name,
    byte[] input,
    byte[] expectedOut,
    int numBuffers,
    boolean expectException) throws Exception  {

    CaseHolder ch = new CaseHolder(numBuffers, input.length);

    while(ch.hasNext()) {
      int[] arraySizes = ch.next();
      String subName = name + arraySizesToString(arraySizes);      
      List<ByteBuffer> bufs = new ArrayList<ByteBuffer>();
      int lenSoFar = 0;
      for(int i = 0; i<arraySizes.length; i++) {
        bufs.add(ByteBuffer.wrap(input, lenSoFar, arraySizes[i]));
        lenSoFar+=arraySizes[i];
      }
      if(expectException) {
        try {
          test(name, bufs);
          System.out.println("\n=======================================");
          System.out.println("Failure on " + subName + " (no exception)");
          System.err.println("Failure on " + subName + " (no exception)");
          System.out.println("=======================================\n");
        }
        catch(Exception ex) {
          System.out.println("\n=======================================");      
          System.out.println(subName + " passed (Got exception)");
          System.out.println("=======================================\n");           
        }        
      }
      else {
        byte[] ret = test(subName, bufs);
        if(!arrayCompare(ret, expectedOut)) {
          System.out.println("\n=======================================");
          System.out.println("Failure on " + subName);
          System.err.println("Failure on " + subName);
          printArraysSBS(expectedOut, ret);
          System.out.println("=======================================\n");
        }
        else {
          System.out.println("\n=======================================");      
          System.out.println(subName + " passed");
          System.out.println("=======================================\n");        
        }
      }
    }
  }


  //Performs the test.  Returns the output
  //from the byte stuffer (including terminator)
  private static byte[] test(String name,
    List<ByteBuffer> bufs) throws Exception {

    List<ByteBuffer> outputBufs = new ArrayList<ByteBuffer>();

    ResponseParser respParser = new ResponseParser();
    Response resp = null;
    for(int i = 0; i<bufs.size(); i++) {
      ByteBuffer buf = bufs.get(i);
      resp = respParser.parse(buf);
      if(resp == null) {
        if(i+1 == bufs.size()) {
          System.out.println("\n=======================================");
          System.out.println("Failure on " + name + " not enough bytes and we're done");
          System.err.println("Failure on " + name + " not enough bytes and we're done");
          System.out.println("=======================================\n");
          return new byte[0];
        }
        if(buf.hasRemaining()) {
          System.out.println(name + " Move bytes " + buf.remaining() + " forward to next buffer");
          ByteBuffer newBuf = ByteBuffer.allocate(buf.remaining() + bufs.get(i+1).remaining());
          newBuf.put(buf);
          newBuf.put(bufs.get(i+1));
          newBuf.flip();
          bufs.set(i+1, newBuf);
        }
      }
      else {
        break;
      }
    }
    ByteBuffer retBuf = resp.getBytes();
    byte[] ret = new byte[retBuf.remaining()];
    retBuf.get(ret);
    return ret;
  }

  //Pretty-prints the size of the array
  private static String arraySizesToString(int[] a) {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    for(int i : a) {
      sb.append(" " + i + " ");
    }
    sb.append(")");
    return sb.toString();
  }

  //Prints to arrays side-by-side for comparison
  private static void printArraysSBS(byte[] expected, byte[] found) {
    int len = Math.max(expected.length, found.length);

    System.out.println(" expected     output");
    
    for(int i = 0; i<len; i++) {
      String str = null;
      if(i < expected.length) {
        byte b = expected[i];
        if(b > 31 && b < 127) {
          str = new String(new byte[] {b, SP, SP});
        }
        else {
          str = "-?-";
        }
        System.out.print(btoiPad(expected[i]) + " (" + str + ")   ");
      }
      else {
        System.out.print("  <EOF>     ");
      }
      if(i < found.length) {
        byte b = found[i];
        if(b > 31 && b < 127) {
          str = new String(new byte[] {b, SP, SP});
        }
        else {
          str = "-?-";
        }
        System.out.print(btoiPad(found[i]) + " (" + str + ") ");
      }
      else {
        System.out.print("  <EOF>  ");
      }      
      System.out.println();
    }
  }
  //Pads a byte to 4 characters
  private static String btoiPad(byte b) {
    String ret = "" + (int) b;
    while(ret.length() < 4) {
      ret+=" ";
    }
    return ret;
  }


  private static boolean arrayCompare(byte[] a, byte[] b) {
    if(a.length != b.length) {
      return false;
    }
    for(int i = 0; i<a.length; i++) {
      if(a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }  
  
  //Stateful class which acts as a factory
  //for all combinations of array sizes.
  //Works by ensuring that any given
  //value in the array is always greater
  //than zero, and the sum of the array
  //is constant.
  private static class CaseHolder {
    private int m_ptr;
    private int[] m_vals;
    private boolean m_hasNext;

    CaseHolder(int len, int sum) {
      m_vals = new int[len];
      for(int i = 0; i<len; i++) {
        m_vals[i] = 1;
      }
      m_vals[len-1] = sum - len + 1;
      m_hasNext = true;
    }

    boolean hasNext() {
      return m_hasNext;
    }

    int[] next() {
      int[] ret = new int[m_vals.length];
      System.arraycopy(m_vals, 0, ret, 0, ret.length);
      m_hasNext = makeNext(0);
      return ret;
    }
    boolean makeNext(int ptr) {
      if(ptr+1 == m_vals.length) {
        return false;
      }
      if(m_vals[ptr+1] > 1) {
        //Pivot the value down
        m_vals[ptr]++;
        m_vals[ptr+1]--;
        return true;
      }
      else {
        //Move current quantity up
        //one and attempt to pivot again
        //on the next slot
        m_vals[ptr+1]+=(m_vals[ptr] - 1);
        m_vals[ptr]=1;
        return makeNext(ptr+1);
      }
    }
    
  }
*/

}
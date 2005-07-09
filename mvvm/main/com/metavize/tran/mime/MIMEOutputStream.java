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
package com.metavize.tran.mime;

import static com.metavize.tran.util.Ascii.*;
import java.nio.*;
import java.io.*;

/**
 * OutputStream which adds some convienences
 * for doing MIME stuff.
 * <br>
 * This class is single-threaded.
 */
public class MIMEOutputStream
  extends FilterOutputStream {
  
  private byte[] m_transferBuf;
  
  public MIMEOutputStream(OutputStream target) {
    super(target);
  }
  
  public long write(Line[] lines) 
    throws IOException {
    long ret = 0;
    for(Line line : lines) {
      ret+=write(line);
    }
    return ret;
  }
  
  public int write(Line line) 
    throws IOException {
    return write(line.getBuffer(true));
  }
  
  /**
   * The buffer is not reset to its original 
   * position after this method concludes.
   */
  public int write(ByteBuffer buf)
    throws IOException {

    //Technically sloppy, but assume we will write this
    //much
    int ret = buf.remaining();    
    
    if(buf.hasArray()) {
      write(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
    }
    else {
      ensureTransferBuf();
      while(buf.hasRemaining()) {
        int transfer = buf.remaining() > m_transferBuf.length?
          m_transferBuf.length:
          buf.remaining();
        buf.get(m_transferBuf, 0, transfer);
        write(m_transferBuf, 0, transfer);
      }
    }
    return ret;
  }
  
  /**
   * Pipes the contents of the input stream
   * to this output stream, until EOF is
   * reached.
   */
  public long pipe(InputStream in) 
    throws IOException {
    
    return pipe(in, Long.MAX_VALUE);
  }
  
  public long pipe(final InputStream in,
    final long maxTransfer) 
    throws IOException {
    System.out.println("***DEBUG*** BEGIN Pipe: " + maxTransfer);
    long total = 0;
    int reqAmt = 0;
    int read = 0;

    ensureTransferBuf();    
        
    while(total < maxTransfer) {
/*    
      int reqAmt = m_transferBuf.length;
      
      if((maxTransfer - total) > m_transferBuf.length) {
        //Ok.  We'll ask for a whole buffer
        reqAmt = m_transferBuf.length;
      }
      else if( ((int) (maxTransfer - total)) < m_transferBuf.length  ){
        reqAmt = ((int) (maxTransfer - total)); 
      }
*/    
      //Figure out how much to ask for in our read.  
      reqAmt = (maxTransfer - total) > m_transferBuf.length?
        m_transferBuf.length:
        (int) (maxTransfer - total);
      
      System.out.println("***DEBUG*** Request: " + reqAmt + ", total: " + total);
      
      //Perform the read
      read = in.read(m_transferBuf, 0, reqAmt);
      if(read == -1) {
        break;
      }
      //Perform the write
      write(m_transferBuf, 0, read);
      total+=read;
    }
    System.out.println("***DEBUG*** ENDOF Pipe: " + maxTransfer);
    return total;
  }
   
  /**
   * Note that this String should be in the US-ASCII charset!
   */
  public void write(String aString) 
    throws IOException {
    write(aString.getBytes());
  } 
  
  /**
   * Writes the given line, terminating with a CRLF
   */
  public void writeLine(String line) 
    throws IOException {
    write(line);
    writeLine();
  }
  
  /**
   * Writes a proper line terminator (CRLF).
   */
  public void writeLine() 
    throws IOException {
    write(CRLF_BA);
  }
  
  public void write(MIMESourceRecord record) 
    throws IOException {
    //TODO bscott There has to be a better way to handle this
    MIMEParsingInputStream inStream = record.source.getInputStream(record.start);
    pipe(inStream, record.len);
    inStream.close();
  }
  
  
  private void ensureTransferBuf() {
    if(m_transferBuf == null) {
      m_transferBuf = new byte[8192];
    }
  }
  
}
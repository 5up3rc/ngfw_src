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
import java.util.*;
import org.apache.log4j.Logger;
import java.io.*;
import static com.metavize.tran.util.ASCIIUtil.*;
import java.nio.*;
import com.metavize.tran.util.TemplateValues;


/**
 * Class representing a collection of RFC 822 Headers (also MIME-conformant).
 * <br>
 * Manipulating headers is a bit tricky.  In some cases, we're talking about
 * all HeaderFields with a given {@link HeaderField#getNameLC name}.  In other 
 * cases, we're dealing with individual HeaderField entries.  Where things are
 * ambigious, the docs for the method will specify.
 * <br>
 * <br>
 * This class also implements {@link com.metavize.tran.util.TemplateValues TemplateValues}.
 * Valid token names for headers are <code>MIMEHeader:&lt;headerName></code> where <code>headerName</code>
 * is the case insensitive name of the header.  This variable will be replaced by the variable
 * of that name, or null if not found.  If there are multiple HeaderFields for the given header
 * name, a comma (",") will be used to separate values.
 * <br>
 * <br> 
 * <b>Not threadsafe</b>
 */
public class Headers
  implements TemplateValues,
    Iterable<HeaderField> {

  public static String MIME_HEADER_VAR_PREFIX = "MIMEHeader:".toLowerCase();
  
  private final Logger m_logger = Logger.getLogger(Headers.class);

  private HeaderFieldFactory m_factory;
  private List<HeaderField> m_headersInOrder;
  private Map<LCString, List<HeaderField>> m_headersByName;
  
  private MIMESourceRecord m_sourceRecord;
  
  private boolean m_changed;
  
  private HeadersObserver m_observer;
  private MyHeaderFieldObserver m_hfCallbackHandler = 
    new MyHeaderFieldObserver();
    
  public Headers(HeaderFieldFactory factory) {
    m_factory = factory;
    m_headersInOrder = new ArrayList<HeaderField>();
    m_headersByName = new HashMap<LCString, List<HeaderField>>();
  }
  
  /**
   * The source <b>must</b> contain the CRLFCRLF (blank line)
   */
  public Headers(HeaderFieldFactory factory,
    MIMESource source,
    int sourceStart,
    int sourceLen,
    List<HeaderField> headersInOrder,
    Map<LCString, List<HeaderField>> headersByName) {
    
    for(HeaderField header : headersInOrder) {
      header.setObserver(m_hfCallbackHandler);
    }
    
    m_factory = factory;
    m_sourceRecord = new MIMESourceRecord(
      source,
      sourceStart,
      sourceLen,
      true);

    m_headersInOrder = headersInOrder;
    m_headersByName = headersByName;
    m_changed = false;
  }

  public Iterator<HeaderField> iterator() {
    return m_headersInOrder.iterator();
  }

  /**
   * For use in Templates (see JavaDoc at the top of this class
   * for explanation of vairable format}.
   */
  public String getTemplateValue(String key) {
    if(key.toLowerCase().startsWith(MIME_HEADER_VAR_PREFIX)) {
      String headerName = key.substring(MIME_HEADER_VAR_PREFIX.length());
      List<HeaderField> headers = getHeaderFields(headerName);
      if(headers == null) {
        return null;
      }
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      for(HeaderField header : headers) {
        if(first) {
          first = false;
        }
        else {
          sb.append(", ");
        }
        sb.append(header.getValueAsString());
      }
      return sb.toString();
    }
    return null;
  }
  
  public void setObserver(HeadersObserver observer) {
    m_observer = observer;
  }
  public HeadersObserver getObserver() {
    return m_observer;
  }
  
  /**
   * Returns the count of header fields.
   */
  public int getNumHeaderFields() {
    return m_headersInOrder.size();
  }
  
  /**
   * Access all HeaderFields with the given name (e.g. "RECEIVED").
   * <br>
   * Adding or removing from the returned list will <b>not</b>
   * affect the contents of this Headers object
   *
   * @param headerFieldName the name of the header field
   * @return a List of the header fields, or null
   */
  public List<HeaderField> getHeaderFields(LCString headerFieldName) {
    return m_headersByName.get(headerFieldName);
  }
  public List<HeaderField> getHeaderFields(String headerFieldName) {
    return getHeaderFields(new LCString(headerFieldName));
  } 
  
  /**
   * Remove all occurances of the names HeaderField from this
   * Headers
   *
   * @return true if the Field was present, and one or more
   *         entries were removed
   */
  public boolean removeHeaderFields(LCString headerFieldName) {
    int removed = 0;
    
    //Remove from the ordered list
    ListIterator<HeaderField> it = m_headersInOrder.listIterator();
    while(it.hasNext()) {
      HeaderField field = it.next();
      if(field.getNameLC().equals(headerFieldName)) {
        field.setObserver(null);
        it.remove();
        removed++;
      }
    }
    
    //Remove from the map
    m_headersByName.remove(headerFieldName);

    if(removed > 0) {
      changed();
      if(m_observer != null) {
        m_observer.headerFieldsRemoved(headerFieldName);
      }
    }
    return removed > 0;
  } 
  
  public HeaderField addHeaderField(String headerName,
    String valueString)
    throws HeaderParseException {
    
    HeaderField newField = m_factory.createHeaderField(headerName);
    newField.assignFromString(valueString, false);
    
    addHeaderFieldImpl(newField);
    return newField;
  }
  
  protected HeaderField addHeaderField(String headerName) {
    HeaderField newField = m_factory.createHeaderField(headerName);
    addHeaderFieldImpl(newField);
    return newField;    
  }
  
  
  /**
   * Add the HeaderField to the Headers.
   */
  private void addHeaderFieldImpl(HeaderField newHeader) {
    List<HeaderField> existingList = m_headersByName.get(newHeader);
    if(existingList == null) {
      existingList = new ArrayList<HeaderField>();
      m_headersByName.put(newHeader.getNameLC(), existingList);
    }
    existingList.add(newHeader);
    m_headersInOrder.add(newHeader);
    newHeader.setObserver(m_hfCallbackHandler);
    changed();
    if(m_observer != null) {
      m_observer.headerFieldAdded(newHeader);
    }
  }

  
  private void changed() {
    m_changed = true;
    m_sourceRecord = null;
  }

  protected void clearChanged(MIMESourceRecord sourceRecord) {
    m_changed = false;
    m_sourceRecord = sourceRecord;
  }
  
  /**
   * Terminates with a blank line, even if the headers
   * are blank.
   */
  public final void writeTo(MIMEOutputStream out)
    throws IOException {
    if(!m_changed && m_sourceRecord != null) {
      out.write(m_sourceRecord);
    }
    else {
      for(HeaderField field : m_headersInOrder) {
        field.writeTo(out);
      }
      out.writeLine();
    }
  }

  /**
   * Get the contents of this Headers in a ByeBuffer.
   * Returned buffer ready to read.
   */
  public final ByteBuffer toByteBuffer()
    throws IOException {
    //TODO bscott Should we be more inteligent about the size of the buffer?
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    MIMEOutputStream mimeOut = new MIMEOutputStream(baos);
    writeTo(mimeOut);
    mimeOut.flush();
    return ByteBuffer.wrap(baos.toByteArray());
  }
  
  /**
   * Really only for debugging, not to produce output suitable
   * for transmission.
   */  
  public String toString() {
    StringBuilder sb = new StringBuilder();
    
    for(HeaderField header : m_headersInOrder) {
      sb.append(header.toString());
      sb.append(System.getProperty("line.separator"));
    }
    
    return sb.toString();
  }

  /**
   * Helper method 
   */
  public static Headers parseHeaders(MIMEParsingInputStream stream,
    MIMESource streamSource,
    HeaderFieldFactory fieldFactory)
    throws IOException, 
      InvalidHeaderDataException, 
      HeaderParseException {

    return parseHeaders(stream, streamSource, fieldFactory, new MIMEPolicy());
  }
  
  
  /**
   * Helper method 
   */
  public static Headers parseHeaders(MIMEParsingInputStream stream,
    MIMESource streamSource,
    HeaderFieldFactory fieldFactory,
    MIMEPolicy policy)
    throws IOException, 
      InvalidHeaderDataException, 
      HeaderParseException {
      HeadersParser hp = new HeadersParser();
      return hp.parseHeaders(stream, streamSource, fieldFactory, policy);
   }
  
  private class MyHeaderFieldObserver
    implements HeaderFieldObserver {
   /** 
    * Callback from child (contained) HeaderField objects that 
    * their content has changed.
    */
    public void headerFieldChanged(HeaderField changedField) {
      changed();
      if(m_observer != null) {
        m_observer.headerFieldChanged(changedField);
      }
    }    
  }
}

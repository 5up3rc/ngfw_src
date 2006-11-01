/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.mime;

import org.apache.log4j.Logger;
import static com.untangle.tran.util.Ascii.*;


/**
 * Object representing a "Content-Type" Header as found in an
 * RFC 821/RFC 2045 document.
 * <br>
 * So this Object is usefull (without a lot of conditional code), if
 * the type is mal-formed it will return {@link #TEXT_PRIM_TYPE_STR text}/
 * {@link #PLAIN_SUB_TYPE_STR plain} as the
 * {@link #getPrimaryType primary} and {@link getSubType sub} types
 * respectivly.
 */
public class ContentTypeHeaderField
  extends HeaderFieldWithParams {

  private static final String BOUNDARY_PARAM_NAME = "boundary";
  private static final String CHARSET_PARAM_NAME = "charset";
  private static final String NAME_NAME = "name";
  private static final String FORMAT_NAME = "format";
  private static final String DELSP_NAME = "delsp";

  private static final LCString BOUNDARY_PARAM_KEY = new LCString(BOUNDARY_PARAM_NAME);
  private static final LCString CHARSET_PARAM_KEY = new LCString(CHARSET_PARAM_NAME);
  private static final LCString NAME_KEY = new LCString(NAME_NAME);
  //Next two from RFC 3676
  private static final LCString FORMAT_KEY = new LCString(FORMAT_NAME);
  private static final LCString DELSP_KEY = new LCString(DELSP_NAME);

  //Composite primary types
  public static final String MULTIPART_PRIM_TYPE_STR = "multipart";
  public static final String MESSAGE_PRIM_TYPE_STR = "message";

  //discrete primary types
  public static final String TEXT_PRIM_TYPE_STR = "text";
  public static final String IMAGE_PRIM_TYPE_STR = "image";
  public static final String AUDIO_PRIM_TYPE_STR = "audio";
  public static final String VIDEO_PRIM_TYPE_STR = "video";
  public static final String APPLICATION_PRIM_TYPE_STR = "application";

  //popular subtypes
  public static final String PLAIN_SUB_TYPE_STR = "plain";
  public static final String HTML_SUB_TYPE_STR = "html";
  public static final String RFC222_SUB_TYPE_STR = "rfc822";
  public static final String MIXED_SUB_TYPE_STR = "mixed";
  public static final String ALTERNATIVE_SUB_TYPE_STR = "alternative";

  public static final String MULTIPART_MIXED = MULTIPART_PRIM_TYPE_STR + "/" + MIXED_SUB_TYPE_STR;
  public static final String MESSAGE_RFC822 = MESSAGE_PRIM_TYPE_STR + "/" + RFC222_SUB_TYPE_STR;
  public static final String TEXT_PLAIN = TEXT_PRIM_TYPE_STR + "/" + PLAIN_SUB_TYPE_STR;
  public static final String TEXT_HTML = TEXT_PRIM_TYPE_STR + "/" + HTML_SUB_TYPE_STR;
  public static final String MULTIPART_ALTERNATIVE = MULTIPART_PRIM_TYPE_STR + "/" + ALTERNATIVE_SUB_TYPE_STR;


  private final Logger m_logger = Logger.getLogger(ContentTypeHeaderField.class);

  private String m_primaryType;
  private String m_subtype;

  public ContentTypeHeaderField() {
    super(HeaderNames.CONTENT_TYPE, HeaderNames.CONTENT_TYPE_LC);
  }
  public ContentTypeHeaderField(String name) {
    super(name, HeaderNames.CONTENT_TYPE_LC);
  }


  /**
   * Get the primary type defined by this header,
   * or {@link #TEXT_PRIM_TYPE_STR text} if either the
   * parsed primary type or {@link #getSubType sub type}
   * were null.
   */
  public String getPrimaryType() {

    return (m_primaryType==null || m_subtype == null)?
      TEXT_PRIM_TYPE_STR:
      m_primaryType;
  }

  /**
   * Set the primary type defined by this header
   */
  public void setPrimaryType(String type) {
    m_primaryType = type;
    changed();
  }

  /**
   * Get the sub type defined by this header,
   * or {@link PLAIN_SUB_TYPE_STR text} if either the
   * parsed {@link #getPrimaryType primary type} or sub type
   * were null.
   */
  public String getSubType() {
    return (m_primaryType==null || m_subtype == null)?
      PLAIN_SUB_TYPE_STR:
      m_subtype;
  }


  /**
   * Set the subtype defined by this header
   */
  public void setSubType(String subtype) {
    m_subtype = subtype;
    changed();
  }

  /**
   * Equivilant to:
   * <pre>
   * String s = getPrimaryType() + "/" + getSubType()
   * </pre>
   */
  public String getContentType() {
    StringBuilder sb = new StringBuilder();
    sb.append(getPrimaryType());
    sb.append('/');
    sb.append(getSubType());
    return sb.toString();
  }

  /**
   * Get the Boundary defined by this header.  This value
   * only applies to multipart sections, and may be null.
   */
  public String getBoundary() {
    return getParam(BOUNDARY_PARAM_KEY);
  }

  /**
   * Set the Boundary defined by this header.  Note that this
   * method does <b>not</b> implicitly change the type
   * to a composite type just because you set this value
   * (i.e. you must make {@link #setPrimaryType the primary type}
   * {@link #MULTIPART_PRIM_TYPE_STR multipart})
   * <br><br>
   * Note that the boundary may start with "--", but when used as
   * a boundary two more dashes ("--") will be prepended to each occurance
   * within the content and two more appended to the terminating
   * boundary (see RFC 2045).
   * <br><br>
   * Setting this to null causes the Boundary attribute to be
   * removed.
   * <br><br>
   * Note that
   * {@link com.untangle.tran.mime.MIMEUtil#makeBoundary MIMEUtil has a helper method}
   * for creating boundaries
   *
   * @param boundary the boundary.  If null, this "unsets"
   *        the boundary attribute.
   */
  public void setBoundary(String boundary) {
    setParam(BOUNDARY_PARAM_KEY.str, boundary);
  }

  public String getCharset() {
    return getParam(CHARSET_PARAM_KEY);
  }
  public void setCharset(String charset) {
    setParam(CHARSET_PARAM_KEY.str, charset);
  }

  /**
   * Convienence method which determines if the {@link #getPrimaryType primary type}
   * is multipart.
   *
   */
  public boolean isMultipart() {
    return getPrimaryType() != null &&
      MULTIPART_PRIM_TYPE_STR.equalsIgnoreCase(getPrimaryType());
  }

  /**
   * Convienence method which determines if this is "message/rfc822"
   * type
   */
  public boolean isMessageRFC822() {
    return getPrimaryType().equalsIgnoreCase(MESSAGE_PRIM_TYPE_STR) &&
      getSubType().equalsIgnoreCase(RFC222_SUB_TYPE_STR);
  }

  @Override
  protected void parsePrimaryValue(HeaderFieldTokenizer t)
    throws HeaderParseException {

    try {
      HeaderFieldTokenizer.Token token1 = t.nextTokenIgnoreComments();
      if(token1 == null ||
        (token1.getType() != HeaderFieldTokenizer.TokenType.ATOM &&
          token1.getType() != HeaderFieldTokenizer.TokenType.QTEXT)) {
        throw new HeaderParseException("Invalid ContentType header \"" +
          t.getOriginal() + "\".  First token null or not ATOM or QTEXT");
      }

      HeaderFieldTokenizer.Token token2 = t.nextTokenIgnoreComments();
      if(token2 == null || token2.getDelim() != (byte) FWD_SLASH) {
        throw new HeaderParseException("Invalid ContentType header \"" +
          t.getOriginal() + "\".  Second token null or not /");
      }

      HeaderFieldTokenizer.Token token3 = t.nextTokenIgnoreComments();
      if(token3 == null ||
        (token3.getType() != HeaderFieldTokenizer.TokenType.ATOM &&
          token3.getType() != HeaderFieldTokenizer.TokenType.QTEXT)) {
        throw new HeaderParseException("Invalid ContentType header \"" +
          t.getOriginal() + "\".  Third token null or not ATOM or QTEXT");
      }

      m_primaryType = token1.toString();
      m_subtype = token3.toString();
    }
    catch(Exception ex) {
      m_logger.warn("Exception parsing content type.  Assume text/plain", ex);
      m_primaryType = TEXT_PRIM_TYPE_STR;
      m_subtype = PLAIN_SUB_TYPE_STR;
    }
  }

  @Override
  protected void writePrimaryValue(StringBuilder sb) {
    sb.append(getPrimaryType());
    sb.append(FWD_SLASH);
    sb.append(getSubType());
//    sb.append(COLON);
  }

  @Override
  protected ParamParsePolicy getParamParsePolicy(String paramName) {
    paramName = paramName.toLowerCase();
    if(BOUNDARY_PARAM_KEY.str.equals(paramName)) {
      return ParamParsePolicy.QTEXT_OR_ALL;
    }
    if(
      CHARSET_PARAM_KEY.str.equals(paramName) ||
      FORMAT_KEY.str.equals(paramName) ||
      DELSP_KEY.str.equals(paramName)
      ) {
      return ParamParsePolicy.ATOM_OR_QTEXT;
    }
    if(NAME_KEY.str.equals(paramName)) {
      return ParamParsePolicy.LOOSE;
    }
    return super.getParamParsePolicy(paramName);
  }

  public static void main(String[] args) throws Exception {
    String crlf = "\r\n";
    test("text/plain");
    test(
      "multipart/mixed; " + "boundary=\"--abc\"xRND_CRAP");
    test(
      "multipart/mixed; " + "boundary=\"--abc\"xRND_CRAP;name=a b c;charset=US-ASCII");
    test(
      "multipart/mixed; " + "boundary=\"--abc\"xRND_CRAP;name=a b c; charset=US-ASCII");
    test(
      "multipart/mixed; " + "boundary=\"--abc\"xRND_CRAP;name=a b c; charset=US-ASCII (plain text) goo=doo");
    test(
      "multipart/mixed; " + "boundary=\"--abc\"xRND_CRAP; foo=\"moo\"");
    test(
      "multipart/mixed; a b=\"doo\"; " +
      "boundary=------------070407010503030002060104 xRND_CRAP; foo=\"moo\"");
  }
  public static void test(String s) throws Exception {
    System.out.println("\n\n========================\n" + s + "\n\n");
    ContentTypeHeaderField hf = new ContentTypeHeaderField();
    hf.assignFromString(s, true);
    hf.parseLines();
    System.out.println("-------------------------");
    System.out.println(hf.toString());
    System.out.println("-------------------------");
  }

}
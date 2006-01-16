/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id$
 */
 
package com.metavize.mvvm.security;
import com.metavize.tran.util.Pair;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.naming.InvalidNameException;
import java.util.ArrayList;
import java.util.List;


//=============================================
// DEVELOPER NOTES:
//
// I got lazy, and wrapped the Java APIs.  If
// this is ever used in a performance-critical
// app it should be re-written
//
//  - wrs 1/12/05
//=============================================

/**
 * Convience class for dealing with LDAP-style (RFC2253) Distinguished names.  For
 * now, just wraps the Java APIs (which are a bit cryptic, but take care of encoding
 * issues).  It also treats everything as Strings, ignoring some of the details
 * of naming.
 * <br>
 * Note that instances are not threadsafe.
 */
public class RFC2253Name implements java.io.Serializable {

  private List<Pair<String, String>> m_members;

  private RFC2253Name(List<Pair<String, String>> members) {
    m_members = members;
  }

  /**
   * Copy constructor
   */
  public RFC2253Name(RFC2253Name copy) {
    m_members = new ArrayList<Pair<String, String>>();
    for(Pair<String, String> pair : copy.m_members) {
      m_members.add(new Pair<String, String>(pair.a, pair.b));
    }
  }

  /**
   * Get the value for the given type
   *
   * @param type the type
   *
   * @return the value, or null if there is no
   *         such type in this name
   */
  public String getValue(String type) {
    int index = indexOf(type);
    return index==-1?
      null:
      m_members.get(index).b;
  }

  /**
   * Add the given RDN
   *
   * @param type the type (e.g. "CN")
   * @param value the value (e.g. "www.yahoo.com").
   *
   * 
   * @exception InvalidNameException if the type or value
   *            are in an unparsable format.
   */  
  public void add(String type, String value)
    throws InvalidNameException {
    new Rdn(type, value);//Will throw an exception
    m_members.add(new Pair<String, String>(type, value));
  }

  /**
   * Add the given RDN at the given index
   *
   * @param type the type (e.g. "CN")
   * @param value the value (e.g. "www.yahoo.com").
   * @param index the index
   *
   * 
   * @exception InvalidNameException if the type or value
   *            are in an unparsable format.
   */
  public void add(String type, String value, int index)
    throws InvalidNameException {
    new Rdn(type, value);//Will throw an exception
    m_members.add(index, new Pair<String, String>(type, value));
  }

  /**
   * Get the index of the given type (case insensitive).
   *
   * @param type the type String (e.g. "CN").
   *
   * @return the index, or -1 if not found
   */
  public int indexOf(String type) {
    int index = 0;
    for(Pair<String, String> entry : m_members) {
      if(entry.a.equalsIgnoreCase(type)) {
        return index;
      }
      index++;
    }
    return -1;
  }

  /**
   * Remove the name/value pair at the given index.  You
   * may also want to use {@link indexOf indexOf}.
   *
   * @param index the index
   */
  public void remove(int index) {
    m_members.remove(index);
  }

  /**
   * Convert the internal name/value pair members into
   * a proper DN string.  Note that the Java classes seem
   * to reorder some things (either because they are broken,
   * of they are doing us a service and enforcing some
   * ordering rules from the spec???).
   *
   * @return the String
   */
  public String toRFC2253String() {
    try {
      List<Rdn> rdns = new ArrayList<Rdn>();

      int index = 0;
      for(Pair<String, String> entry : m_members) {
        rdns.add(new Rdn(entry.a, entry.b));
      }
      return new LdapName(rdns).toString();
    }
    catch(InvalidNameException ex) {
      //This shouldn't happen but...
      throw new RuntimeException(ex);
    }
  }

  @Override
  public String toString() {
    return toRFC2253String();
  }

  /**
   * Parse a String in XX=yyy,ZZ=acbd... format
   *
   * @param str the RFC 2253 string
   *
   * @return the parsed RFC2253Name
   *
   * @exception InvalidNameException if the string is an illegal Distinguished Name
   */
  public static RFC2253Name parse(String str)
    throws InvalidNameException {
    
    List<Pair<String, String>> ret = new ArrayList<Pair<String, String>>();

    LdapName ldapName = new LdapName(str);

    List<Rdn> rdns = ldapName.getRdns();

    for(Rdn rdn : rdns) {
      ret.add(new Pair<String, String>(rdn.getType().toString(), rdn.getValue().toString()));
    }
    return new RFC2253Name(ret);
  }

  /**
   * Create a new (empty) RFC2253 name)
   *
   * @return the new name
   */
  public static RFC2253Name create() {
    return new RFC2253Name(new ArrayList<Pair<String, String>>());
  }


  public static void main(String[] args) throws Exception {

    //Create
    RFC2253Name newName = RFC2253Name.create();
    newName.add("L", "San, Mateo");
    newName.add("CN", "foo", 0);
    newName.add("ST", "Cali", 0);
    newName.add("OU", "Metavize");    
    newName.add("X", "blaaa", 0);

    System.out.println(newName.toRFC2253String());

    //Parse
    RFC2253Name parseName = RFC2253Name.parse("L=San\\, Mateo, OU=Metavize, CN=foo, ST=Cali, X=blaaa");
    System.out.println("Second one's L = " + parseName.getValue("L"));
    System.out.println(parseName.toRFC2253String());
    
  
  }

}


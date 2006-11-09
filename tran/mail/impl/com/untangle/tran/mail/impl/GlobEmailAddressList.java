/*
 * Copyright (c) 2003-2006 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.tran.mail.impl;

import java.util.List;
import java.util.ArrayList;

import com.untangle.tran.util.Pair;



/**
 * List of email addresses, understanding a glob (wildcard)
 * syntax
 * <br>
 * Threadsafe
 */
public class GlobEmailAddressList {

  //Lazy, simple way to implement this class
  private final GlobEmailAddressMapper m_mapper;


  /**
   * Construct an GlobEmailAddressList based on the provided
   * addresses
   *
   * @param list of email addresses (which may be in
   *        glob format).
   */
  public GlobEmailAddressList(List<String> list) {

    List<Pair<String, String>> pairing =
      new ArrayList<Pair<String, String>>();

    for(String s : list) {
      pairing.add(new Pair<String, String>(s, "x"));
    }

    m_mapper = new GlobEmailAddressMapper(pairing);
  }


  /**
   * Test if this list contains the address.  Note that
   * the address must be in exact format (i.e. no wildcards).
   *
   * @param address the address;
   */
  public boolean contains(String address) {
    return m_mapper.getAddressMapping(address) != null;
  }
}

/*
 * Copyright (c) 2003, 2004, 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id: $
 */

package com.metavize.mvvm.engine.addrbook;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.ServiceUnavailableException;
import javax.naming.NamingException;
import javax.naming.AuthenticationException;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.Attribute;
import javax.naming.directory.SearchControls;
import javax.naming.directory.BasicAttributes;

import org.apache.log4j.Logger;

import java.util.Hashtable;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import com.metavize.mvvm.addrbook.RepositoryType;
import com.metavize.mvvm.addrbook.AddressBookSettings;
import com.metavize.mvvm.addrbook.UserEntry;
import com.metavize.mvvm.addrbook.NoSuchEmailException;

import com.metavize.tran.util.Pair;


/**
 * Abstract base class for "Adapters" which call LDAP
 * to perform various operations.
 *
 */
abstract class LdapAdapter {

  private final Logger m_logger =
    Logger.getLogger(LdapAdapter.class);


  /**
   * For subclasses to define the type of repository
   *
   * @return the repository type
   */
  protected abstract RepositoryType getRepositoryType();

  /**
   * For subclasses to hold the settings.
   *
   * @return the settings
   */
  protected abstract AddressBookSettings getSettings();

  /**
   * Authenticate the given uid/pwd combination.
   *
   * @param uid the userid
   * @param pwd the password
   *
   * @return true if authentication successful, false otherwise.
   *
   * @exception ServiceUnavailableException if the back-end communication
   *            with the repository is somehow hosed.
   */
  public abstract boolean authenticate(String uid, String pwd)
    throws ServiceUnavailableException;


  /**
   * Authenticate the user who owns the given email addres
   * with the provided password.
   *
   * <b>Warning.  It is a big security no-no to tell bad-guys
   * if an account exists or not (it helps them guess stuff).
   * Therefore, special care should be used in translating the
   * "NoSuchEmailException" into a "wrong email" message in
   * any UI</b>   
   *
   * @param email the email address
   * @param pwd the password
   *
   * @return true if authentication successful, false otherwise.
   *
   * @exception ServiceUnavailableException if the back-end communication
   *            with the repository is somehow hosed.
   */
  public boolean authenticateByEmail(String email, String pwd)
    throws ServiceUnavailableException, NoSuchEmailException {
    
    UserEntry entry = getEntryByEmail(email);

    if(entry == null) {
      throw new NoSuchEmailException(email);
    }

    return authenticate(entry.getUID(), pwd);
  }

  /**
   * List all user entries.  Returns an empty list if
   * there are no entries.
   *
   * @return all entries in this repository.
   *
   * @exception ServiceUnavailableException if the back-end communication
   *            with the repository is somehow hosed.
   */
  public List<UserEntry> listAll()
    throws ServiceUnavailableException {

    try {
      List<Map<String, String[]>> list =
        queryAsSuperuser(getSettings().getSearchBase(),
          getListAllUsersSearchString(),
          getUserEntrySearchControls());

      if(list == null || list.size() == 0) {
        return null;
      }

      List<UserEntry> ret = new ArrayList<UserEntry>();

      for(Map<String, String[]> map : list) {
        UserEntry entry = toUserEntry(map);
        if(entry != null) {
          ret.add(entry);
        }
      }
      return ret;
    }
    catch(NamingException ex) {
      m_logger.warn("Exception listing entries", ex);
      throw new ServiceUnavailableException(ex.toString());
    }
  }  

  /**
   * Tests if the given userid exists in this repository
   *
   * @param uid the userid
   *
   * @return true if it exists
   *
   * @exception ServiceUnavailableException if the back-end communication
   *            with the repository is somehow hosed.
   */
  public boolean containsUser(String uid)
    throws ServiceUnavailableException {
    return getEntry(uid) != null;
  }


  /**
   * Get a UserEntry by email address.  If nore than one user share emails,
   * this returns the <b>first</b> user.
   *
   * @param email the email address
   *
   * @return the UserEntry, or null if no entry corresponding to that
   *         email was found
   *
   * @exception ServiceUnavailableException if the back-end communication
   *            with the repository is somehow hosed.
   */
  public UserEntry getEntryByEmail(String email)
    throws ServiceUnavailableException {

    StringBuilder sb = new StringBuilder();
    sb.append("(&");
    sb.append("(").append("objectClass=").append(getSettings().getUserClass()).append(")");
    sb.append("(").append(getSettings().getMailAttributeAN()).append("=").append(email).append(")");
    sb.append(")");

    return getEntryWithSearchString(sb.toString());    
    
  }

  /**
   * Get a UserEntry by userid.  If nore than one entry is found (!),
   * this returns the <b>first</b> user.
   *
   * @param uid the username
   * @return the UserEntry, <b>or null if no entry corresponding to that
   *         uid was found</b>
   *
   * @exception ServiceUnavailableException if the back-end communication
   *            with the repository is somehow hosed.
   */
  public UserEntry getEntry(String uid)
    throws ServiceUnavailableException {
    
    StringBuilder sb = new StringBuilder();
    sb.append("(&");
    sb.append("(").append("objectClass=").append(getSettings().getUserClass()).append(")");
    sb.append("(").append(getSettings().getUIDAN()).append("=").append(uid).append(")");
    sb.append(")");

    return getEntryWithSearchString(sb.toString());
  }


  /**
   * Tests the settings via creating a superuser context.
   *
   * @exception CommunicationException if the server could not be found
   * @exception AuthenticationException if the server did not like
   *            our superuser credentials
   * @exception NamingException catch-all for "bummer, something else is wrong".
   */
  public void test()
    throws CommunicationException, AuthenticationException, NamingException {

    try {
      closeContext(createSuperuserContext());
    }
    catch(NamingException ex) {
      throw ex;
    }
    
  }



  
//========================================
// Superuser Context Pool Methods
//========================================  

  
  /**
   * Access for the (maybe someday) pool of
   * superuser connections
   *
   *
   * @return null if no connection can be established.
   */
  protected final DirContext checkoutSuperuserContext() {
  
    AddressBookSettings settings = getSettings();

    try {
      return createSuperuserContext();
    }
    catch(Exception ex) {
      m_logger.error("Unable to create superuser context with settings: " +
        "Host: \"" + settings.getLDAPHost() + "\", " +
        "Port: \"" + settings.getLDAPPort() + "\", " +
        "Superuser DN: \"" + settings.getSuperuserDN() + "\", " +
        "Pass: " + (settings.getSuperuserPass()==null?"<null>":"<not null>"),
        ex);
      return null;
    }
  }

  /**
   * Return the previously checked-out superuser context
   *
   * @param ctx the context
   * @param mayBeBad if true, the context should be considered
   *        suspect.
   */
  protected final void returnSuperuserContext(DirContext ctx,
    boolean mayBeBad) {
    closeContext(ctx);
  }


  
//========================================
// Other Methods Subclasses may use
//========================================  





  /**
   * Convienence methods to convert the communication and authentication excpetions
   * to the more generic "ServiceUnavailableException".
   */
  protected final NamingException convertToServiceUnavailableException(NamingException ex) {
    if(ex instanceof CommunicationException) {
      return new ServiceUnavailableException("Communication Failure: " +
        ex.toString());
    }
    if(ex instanceof AuthenticationException) {
      return new ServiceUnavailableException("Authentication Failure: " +
        ex.toString());
    }
    return ex;  
  }

  /**
   * Convienence method which creates SearchControls with the given
   * attributes to be returned and subtree scope
   */
  protected SearchControls createSimpleSearchControls(String...filter) {
    SearchControls searchCtls = new SearchControls();
    searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    searchCtls.setReturningAttributes(filter);
    return searchCtls;
  }




  /**
   * Name says it all.  Will never throw an
   * exception
   */
  protected final void closeContext(DirContext ctx) {
    try {
      ctx.close();
    }
    catch(Exception ignore) {}
  }


  /**
   * Create a context based on the given parameters
   *
   * @param host the host
   * @param port the port
   * @param dn the DistinguishedName
   * @param pass the password 
   */
  protected final DirContext createContext(String host,
    int port,
    String dn,
    String pass) throws NamingException {
    
    Hashtable<String, String> ldapEnv = new Hashtable<String, String>(5);
    ldapEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    ldapEnv.put(Context.PROVIDER_URL,  "ldap://" + host + ":" + Integer.toString(port));
    ldapEnv.put(Context.SECURITY_AUTHENTICATION, "simple");
    ldapEnv.put(Context.SECURITY_PRINCIPAL, dn);
    ldapEnv.put(Context.SECURITY_CREDENTIALS, pass);
    return new InitialDirContext(ldapEnv);
  }

  
  /**
   * Create a DirContext based on the current superuser
   * parameters
   *
   *
   * @return a DirContext as the superuser.
   */
  protected final DirContext createSuperuserContext()
    throws CommunicationException, AuthenticationException, NamingException {
    AddressBookSettings settings = getSettings();    
    return createContext(settings.getLDAPHost(),
      settings.getLDAPPort(),
      settings.getSuperuserDN(),
      settings.getSuperuserPass());
  }

  /**
   * Intended to be overidden by Active Directory
   */
  protected String getListAllUsersSearchString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(").append("objectClass=").append(getSettings().getUserClass()).append(")");
    return sb.toString();
  }

  
  /**
   * Perform the given query as a superuser.  This
   * method will try "twice" to make sure it is not
   * trying to reuse a dead connection.
   *
   * @param searchBase the base of the search
   * @param searchFilter the query string
   * @param ctls the search controls
   *
   * @return all returned data 
   */
  protected final List<Map<String, String[]>> queryAsSuperuser(
    String searchBase,
    String searchFilter,
    SearchControls ctls) throws NamingException, ServiceUnavailableException {

    return queryAsSuperuserImpl(searchBase, searchFilter, ctls, true);
    
  }

  /**
   * Perform the given query (only attempts once as
   * the context was passed-in).
   *
   * @param searchBase the base of the search
   * @param searchFilter the query string
   * @param ctls the search controls
   * @param ctx the context to use for the query.
   *
   * @return all returned data 
   */  
  protected final List<Map<String, String[]>> query(
    String searchBase,
    String searchFilter,
    SearchControls ctls,
    DirContext ctx) throws ServiceUnavailableException, NamingException {

    try {
      NamingEnumeration answer = ctx.search(searchBase, searchFilter, ctls);
      List<Map<String, String[]>> ret = new ArrayList<Map<String, String[]>>();
      while (answer.hasMoreElements()) {
        SearchResult sr = (SearchResult)answer.next();
        Attributes attrs = sr.getAttributes();
        if (attrs != null) {
          Map<String, String[]> map = new HashMap<String, String[]>();
          for (NamingEnumeration ae = attrs.getAll();ae.hasMore();) {
            Attribute attr = (Attribute)ae.next();
            String attrName = attr.getID();
            ArrayList<String> values = new ArrayList<String>();
            NamingEnumeration e = attr.getAll();
            while(e.hasMore()) {
              values.add(e.next().toString());
            }
            map.put(attrName, (String[]) values.toArray(new String[values.size()]));
          }
          ret.add(map);
        }
      }
      return ret;
    }
    catch(NamingException ex) {
      throw convertToServiceUnavailableException(ex);
    }
  }


  /**
   * ...name says it all.  Note that this tries "twice" in case
   * the superuser connection was dead
   */
  protected final void createSubcontextAsSuperuser(String dn,
    BasicAttributes attribs)
      throws NamingException,
        NameAlreadyBoundException,
        ServiceUnavailableException {
    createSubcontextAsSuperuserImpl(dn, attribs, true);
  }
  


  
//========================================
// Helper Methods
//========================================

  

  /**
   * Returns null if not found.  If more than one found (!),
   * first is returned.
   */
  private UserEntry getEntryWithSearchString(String searchStr)
    throws ServiceUnavailableException {

    try {

      List<Map<String, String[]>> list =
        queryAsSuperuser(getSettings().getSearchBase(),
          searchStr,
          getUserEntrySearchControls());

      
      if(list == null || list.size() == 0) {
        return null;
      }

      return toUserEntry(list.get(0));
    }
    catch(NamingException ex) {
      throw new ServiceUnavailableException(ex.toString());
    }
  }


  /**
   * Perform the superuser query, repeating again in case a dead
   * connection was used.
   */
  private List<Map<String, String[]>> queryAsSuperuserImpl(
    String searchBase,
    String searchFilter,
    SearchControls ctls,
    boolean tryAgain) throws NamingException, ServiceUnavailableException {

    DirContext ctx = checkoutSuperuserContext();    

    if(ctx == null) {
      throw new ServiceUnavailableException("Unable to obtain context");
    }
    
  
    try {
      List<Map<String, String[]>> ret = query(searchBase, searchFilter, ctls, ctx);
      returnSuperuserContext(ctx, false);
      return ret;
    }
    catch(NamingException ex) {
      returnSuperuserContext(ctx, true);
      if(tryAgain) {
        return queryAsSuperuserImpl(searchBase, searchFilter, ctls, false);
      }
      else {
        throw convertToServiceUnavailableException(ex);
      }
    }    
  }

  /**
   * Used to repeat the creating subcontext thing twice
   * should a connection be stale
   */
  private void createSubcontextAsSuperuserImpl(String dn,
    BasicAttributes attribs,
    boolean tryAgain)
      throws NameAlreadyBoundException,
        ServiceUnavailableException,
        NamingException {

    DirContext ctx = checkoutSuperuserContext();

    if(ctx == null) {
      throw new ServiceUnavailableException("Unable to obtain context");
    }


    try {
      ctx.createSubcontext(dn, attribs);
      returnSuperuserContext(ctx, false);
      return;
    }
    catch(NameAlreadyBoundException nabe) {
      returnSuperuserContext(ctx, false);
      throw nabe;
    }
    catch(NamingException ex) {
      returnSuperuserContext(ctx, true);
      if(tryAgain) {
        createSubcontextAsSuperuserImpl(dn, attribs, false);
      }
      else {
        throw convertToServiceUnavailableException(ex);
      }
    }
  }  
  



  /**
   * Helper to convert (based on our "standard" returned controls)
   * the USerEntry.
   */
  private UserEntry toUserEntry(Map<String, String[]> map) {
    Pair<String, String> parsedName =
      parseFullName(getFirstEntryOrNull(map.get(getSettings().getFullNameAN())));

    return new UserEntry(
      getFirstEntryOrNull(map.get(getSettings().getUIDAN())),
      parsedName.a,
      parsedName.b,
      getFirstEntryOrNull(map.get(getSettings().getMailAttributeAN())),
      getRepositoryType());
  }   

  
  /**
   * Gets the first entry in the String[], or null
   */
  protected String getFirstEntryOrNull(String[] str) {
    return (str==null || str.length ==0)?
      null:str[0];
  }


  /**
   * Helper to create the search controls used when fetching a UserEntry
   */
  private SearchControls getUserEntrySearchControls() {
    return createSimpleSearchControls(
      getSettings().getUIDAN(),
      getSettings().getMailAttributeAN(),
      getSettings().getFullNameAN());
  }


  /**
   * If input is null, members of pair are null.  If no space,
   * then only firrst name is returned (e.g. "Bono" or "Sting")
   * and last name is "".  If entire String is "", then
   * the pair is "", ""
   */
  private final Pair<String, String> parseFullName(String str) {
    if(str == null) {
      return new Pair<String, String>(null, null);
    }
    str = str.trim();

    int index = str.indexOf(' ');

    if(index == -1) {
      return new Pair<String, String>(str, "");
    }
    return new Pair<String, String>(str.substring(0, index).trim(), str.substring(index).trim());
  }  
  

}



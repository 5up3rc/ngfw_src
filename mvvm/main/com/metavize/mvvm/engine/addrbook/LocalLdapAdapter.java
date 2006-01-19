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
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.Attribute;
import javax.naming.directory.SearchControls;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.BasicAttribute;

import org.apache.log4j.Logger;

import java.util.Hashtable;
import java.util.List;
import java.util.ArrayList;

import com.metavize.mvvm.addrbook.RepositoryType;
import com.metavize.mvvm.addrbook.RepositorySettings;
import com.metavize.mvvm.addrbook.UserEntry;


/**
 * LDAP adapter "hardcoded" for our local OpenLDAP repository.  Unlike the
 * ActiveDirectory version, this may modify entries.
 */
public class LocalLdapAdapter
  extends LdapAdapter {

  private final Logger m_logger =
    Logger.getLogger(LocalLdapAdapter.class);

  private RepositorySettings m_settings;

  /**
   * Create a Local adapter with custom settings
   */
  public LocalLdapAdapter(RepositorySettings settings) {
    m_settings = settings;
  }

  /**
   * Create a local adapter with all defaults
   */
  public LocalLdapAdapter() {
    this(new RepositorySettings(
      "cn=admin,dc=mydomain",
      "passwd",
      "inetOrgPerson",
      "mail",
      "cn",
      "uid",
      "DC=mydomain",
      "localhost",
      389)
      );
  }  

  @Override
  protected final RepositorySettings getSettings() {
    return m_settings;
  }

  
  @Override
  protected final RepositoryType getRepositoryType() {
    return RepositoryType.LOCAL_DIRECTORY;
  }


  @Override
  public boolean authenticate(String uid, String pwd)
    throws ServiceUnavailableException {

    try {
      DirContext ctx = createContext(getSettings().getLDAPHost(),
      getSettings().getLDAPPort(),
      "uid=" + uid + "," + getSettings().getSearchBase(),
      pwd);
      boolean ret = ctx != null;
      closeContext(ctx);
      return ret;
    }
    catch(AuthenticationException ex) {
      return false;
    }
    catch(NamingException ex) {
      m_logger.warn("Exception authenticating user \"" + uid + "\"", ex);
      throw new ServiceUnavailableException(ex.toString());
    }
  }   

  /**
   * Change the given user's password
   *
   * @param uid the userid
   * @param password the new password
   *
   * @exception ServiceUnavailableException if the back-end communication
   *            with the repository is somehow hosed.
   *
   * @exception NameNotFoundException if the username could not be found
   *
   * @exception IllegalArgumentException if the uid or password are null
   */
  public void changePassword(String uid,
    String password)
    throws ServiceUnavailableException,
      NameNotFoundException,
      IllegalArgumentException {

    if(password == null) {
      throw new IllegalArgumentException("password cannot be null");
    }

    try {
      modifyUserImpl(uid,
        null, password, true);
    }
    catch(NameNotFoundException ex) {throw ex;}
    catch(ServiceUnavailableException ex) {throw ex;}
    catch(NamingException ex) {
      m_logger.warn("changing password for \"" + uid + "\"", ex);
      throw new ServiceUnavailableException(ex.toString());
    }
  }    

  /**
   * Modify the user's stuff in one call (password and
   * settings).  If password is null, it won't be changed.
   * <br>
   * Note that the unique ID cannot be changed
   *
   * @param changedEntry the entry to change
   * @param password the new password (may be null)
   *
   * @return the same entry passed-in with all changes
   *         "confirmed".
   *
   *
   * @exception ServiceUnavailableException if the back-end communication
   *            with the repository is somehow hosed.
   *
   * @exception NameNotFoundException if the username could not be found
   *
   * @exception IllegalArgumentException if the changedEntry is null
   */
  public UserEntry modifyUserEntry(UserEntry changedEntry,
    String password)
    throws ServiceUnavailableException,
      NameNotFoundException,
      IllegalArgumentException {

    if(changedEntry.getUID() == null) {
      throw new IllegalArgumentException("uid cannot be null");
    }
    try {
      return modifyUserImpl(changedEntry.getUID(),
        changedEntry, password, true);
    }
    catch(NameNotFoundException ex) {throw ex;}
    catch(ServiceUnavailableException ex) {throw ex;}
    catch(NamingException ex) {
      m_logger.warn("Exception Modifying user \"" + changedEntry.getUID() + "\"", ex);
      throw new ServiceUnavailableException(ex.toString());
    }    
  }  


  /**
   * Delete the given user entry
   *
   * @param uid the userid
   *
   * @return true if found and deleted, false if not found or not deleted
   *
   * @exception ServiceUnavailableException if the back-end communication
   *            with the repository is somehow hosed.   
   */
  public boolean deleteUserEntry(String uid)
    throws ServiceUnavailableException  {
    
    try {
      return deleteUserEntryImpl(uid, true);
    }
    catch(ServiceUnavailableException ex) {
      m_logger.warn("Exception deleting user \"" + uid + "\"", ex);
      throw ex;
    }
  }


  /**
   * Create a new user entry.  Obviously, the "storedIn" property
   * of the UserEntry is ignored for this call
   *
   * @param newEntry the new entry
   * @param password the password for the new user
   *
   * @return the new UserEntry
   *
   * @exception ServiceUnavailableException if the back-end communication
   *            with the repository is somehow hosed.
   *
   * @exception NameAlreadyBoundException the name already exists
   *
   * @exception IllegalArgumentException if the newEntry or password is null
   */
  public UserEntry createUserEntry(UserEntry newEntry,
    String password)
    throws NameAlreadyBoundException,
      ServiceUnavailableException,
      IllegalArgumentException {

    //Idiot proofing
    if(password == null) {
      throw new IllegalArgumentException("password cannot be null");
    }
    if(newEntry.getUID() == null) {
      throw new IllegalArgumentException("uid cannot be null");
    }

    //Fixup bad entries
    newEntry.setLastName(replaceNull(newEntry.getLastName(), "surname"));
    newEntry.setFirstName(replaceNull(newEntry.getFirstName(), "user"));
    newEntry.setEmail(replaceNull(newEntry.getEmail(), "user@localhost"));


    //Create the LDAP crap    
    BasicAttributes attrs = new BasicAttributes();
    BasicAttribute attr = new BasicAttribute("objectclass");
    attr.add(getSettings().getUserClass());
    attrs.put(attr);

    attrs.put(new BasicAttribute("uid", newEntry.getUID()));
    attrs.put(new BasicAttribute("sn", newEntry.getLastName()));
    attrs.put(new BasicAttribute("cn",
      newEntry.getFirstName() + " " + newEntry.getLastName()));
    attrs.put(new BasicAttribute("mail", newEntry.getEmail()));
    attrs.put(new BasicAttribute("userPassword", password));//TODO MD5

    try {
      createSubcontextAsSuperuser("uid=" + newEntry.getUID() + ",dc=mydomain", attrs);
    }
    catch(NameAlreadyBoundException ex) {
      throw ex;
    }
    catch(NamingException ex) {
      m_logger.warn("Exception creating new user\"" + newEntry.getUID() + "\"", ex);
      throw new ServiceUnavailableException(ex.toString());
    }

    newEntry.setStoredIn(getRepositoryType());
    return newEntry;
  }

  private String replaceNull(String str, String replacement) {
    return (str==null || "".equals(str.trim()))?
      replacement:str;
  }


  /**
   * Tries twice
   */
  private boolean deleteUserEntryImpl(String uid, boolean tryAgain)
    throws ServiceUnavailableException {

    DirContext ctx = checkoutSuperuserContext();
          
    if(ctx == null) {
      throw new ServiceUnavailableException("Unable to obtain superuser context");
    }

    try {
      ctx.destroySubcontext("uid=" + uid + ",dc=mydomain");
      returnSuperuserContext(ctx, false);
      return true;
    }
    catch(NameNotFoundException ex) {
      returnSuperuserContext(ctx, false);
      return false;
    }
    catch(NamingException ex) {
      returnSuperuserContext(ctx, true);
      if(tryAgain) {
        return deleteUserEntryImpl(uid, false);
      }
      else {
        m_logger.warn("Exception deleting userid \"" + uid + "\"", ex);
        throw new ServiceUnavailableException(ex.toString());
      }
    }
  }    


  /**
   * If password is null, it won't be changed.  Note also
   * that the UserEntry can be null (hence the bare "uid"
   * property).
   */
  private UserEntry modifyUserImpl(String uid,
    UserEntry changedEntry,
    String password,
    boolean tryAgain)
    throws ServiceUnavailableException,
      NameNotFoundException,
      NamingException,
      IllegalArgumentException {


    DirContext ctx = checkoutSuperuserContext();
    
    if(ctx == null) {
      throw new ServiceUnavailableException("Unable to obtain superuser context");
    }    

    BasicAttributes attrs = new BasicAttributes();
    
    if(changedEntry != null) {
      //Fixup trash
      changedEntry.setLastName(replaceNull(changedEntry.getLastName(), "surname"));
      changedEntry.setFirstName(replaceNull(changedEntry.getFirstName(), "user"));
      changedEntry.setEmail(replaceNull(changedEntry.getEmail(), "user@localhost"));

      attrs.put(new BasicAttribute("sn", changedEntry.getLastName()));
      attrs.put(new BasicAttribute("cn",
        changedEntry.getFirstName() + " " + changedEntry.getLastName()));
      attrs.put(new BasicAttribute("mail", changedEntry.getEmail()));
    }
    if(password != null) {
      attrs.put(new BasicAttribute("userPassword", password));//TODO MD5
    }

    

    try {
      ctx.modifyAttributes("uid=" + uid + ",dc=mydomain",
        DirContext.REPLACE_ATTRIBUTE,
        attrs);
      returnSuperuserContext(ctx, false);
      if(changedEntry != null) {
        changedEntry.setStoredIn(getRepositoryType());
      }
      return changedEntry;
    }
    catch(NamingException ex) {
      returnSuperuserContext(ctx, true);
      if(tryAgain) {
        return modifyUserImpl(uid, changedEntry, password, false);
      }
      else {
        throw convertToServiceUnavailableException(ex);
      }
    }      
      
  }
    

  public static void main(String[] args) throws Exception {
    System.out.println("Hello");
    LocalLdapAdapter adapter = new LocalLdapAdapter();

    System.out.println("==================================");
    System.out.println("Do test");
    adapter.test();
    System.out.println("Test OK");

    System.out.println("==================================");
    String rnd = Long.toString(System.currentTimeMillis());
    String uid = "jdoe" + rnd;
    System.out.println("Create a user \"" + uid + "\"");
    UserEntry ue = new UserEntry(uid, "John", "Doe" + rnd, uid + "@foo.com");
    System.out.println("User (PRE)");
    System.out.println(ue);
    ue = adapter.createUserEntry(ue, "passwd");
    System.out.println("User (POST)");
    System.out.println(ue);
    System.out.println("Created entry.  Go check it out and hit return to continue");
    System.in.read();

    System.out.println("===================================");
    System.out.println("Check if user exists by ID");
    System.out.println("User \"" + uid + "\" exists? " + adapter.containsUser(uid));

    System.out.println("===================================");
    System.out.println("Get full entry for good user");
    ue = adapter.getEntry(uid);
    System.out.println("Returned: ");
    System.out.println(ue);


    System.out.println("===================================");
    System.out.println("Get entry by email");
    ue = adapter.getEntryByEmail(ue.getEmail());
    System.out.println("Returned: ");
    System.out.println(ue);

    System.out.println("===================================");
    System.out.println("Try to change the surname and email");
    ue.setLastName("NEW_LAST_NAME");
    ue.setEmail("new@new.com");
    ue = adapter.modifyUserEntry(ue, null);
    System.out.println("Returned: ");
    System.out.println(ue);

    System.out.println("===================================");
    System.out.println("Try to authenticate");
    System.out.println(adapter.authenticate(uid, "passwd"));

    System.out.println("===================================");
    System.out.println("Try to authenticate (bad password)");
    System.out.println(adapter.authenticate(uid, "xpasswd"));

    System.out.println("===================================");
    System.out.println("Try to authenticate (bad user)");
    System.out.println(adapter.authenticate("doesnotexist", "xpasswd"));

    
    
    System.out.println("===================================");
    System.out.println("Try to change password");
    adapter.changePassword(ue.getUID(), "newpassword");

    
    System.out.println("===================================");
    System.out.println("Try to authenticate");
    System.out.println(adapter.authenticate(uid, "newpassword"));

    System.out.println("===================================");
    System.out.println("Try to authenticate (bad password)");
    System.out.println(adapter.authenticate(uid, "passwd"));

    System.out.println("==================================");
    System.out.println("Delete \"" + uid + "\"");    
    System.out.println("Delete success: " + adapter.deleteUserEntry(uid));
    System.out.println("Go check it out and hit return to continue");
    System.in.read();

    System.out.println("===================================");
    System.out.println("Check if user exists by ID");
    System.out.println("User \"" + uid + "\" exists? " + adapter.containsUser(uid));



    System.out.println("===================================");
    System.out.println("Get full entry for user after delete");
    ue = adapter.getEntry(uid);
    System.out.println("Returned: ");
    System.out.println(ue);

    System.out.println("===================================");
    System.out.println("List All");
    List<UserEntry> list = adapter.listAll();
    for(UserEntry entry : list) {
      System.out.println("----------------");
      System.out.println(entry);
    }     

  }
}



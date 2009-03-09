/*
 * Copyright (c) 2003-2009 Untangle, Inc. 
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module.  An independent
 * module is a module which is not derived from or based on this library.
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 *
 * $Id$
 */

package com.untangle.uvm.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.ServiceUnavailableException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import com.untangle.node.util.Pair;
import com.untangle.uvm.addrbook.NoSuchEmailException;
import com.untangle.uvm.addrbook.RepositorySettings;
import com.untangle.uvm.addrbook.RepositoryType;
import com.untangle.uvm.addrbook.UserEntry;
import com.untangle.uvm.addrbook.GroupEntry;
import org.apache.log4j.Logger;
import com.untangle.node.util.SimpleExec;
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
    protected abstract RepositorySettings getSettings();


    /**
     * Get the type of object used to describe "people"
     * in this directory (e.g. "user" or "inetOrgPerson").
     */
    protected abstract String[] getUserClassType();

    protected abstract String[] getGroupClassType();


    /**
     * Get the attribute used to hold the mail (e.g. "mail").
     */
    protected abstract String getMailAttributeName();

    /**
     * Get the name of the attribute used to hold
     * the full name (i.e. "CN")
     */
    protected abstract String getFullNameAttributeName();


    /**
     * Get the name of the attribute describing the unique id
     * (i.e. "uid" or "sAMAccountName").
     */
    protected abstract String getUIDAttributeName();

    // Gets search base from repository settings in dependent way.
    protected abstract String getSearchBase();

    protected abstract String getSuperuserDN();

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
     * Join a domain.  Creates a computer account for the Untangle server in the ADs
     * domain, or the equivalent in the local LDAP directory.
     *
     * @param smbWorkgroup The Netbios Domain name (usually first word of realm) to join
     */
    public abstract void joinDomain(String smbWorkgroup)
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
                queryAsSuperuser(getSearchBase(),
                                 getListAllUsersSearchString(),
                                 getUserEntrySearchControls());

            List<UserEntry> ret = new ArrayList<UserEntry>();

            if(list == null || list.size() == 0) {
                return ret;
            }

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

    public List<GroupEntry> listAllGroups()
        throws ServiceUnavailableException {
        List<GroupEntry> ret = new ArrayList<GroupEntry>();
        try {
            SimpleExec.SimpleExecResult result = SimpleExec.exec(
                                                                 "getent",//cmd
                                                                 new String[] {//args
                                                                     "group"
                                                                 },
                                                                 null,//env
                                                                 null,//rootDir
                                                                 true,//stdout
                                                                 true,//stderr
                                                                 1000*20);

            if(result.exitCode==0) {
                String output = new String(result.stdOut);
                String[] lines = output.split("\n");
                for (String line : lines) {
                    String[] groupPieces = line.split(":");
                    if (Long.valueOf(groupPieces[2]).longValue() < 500l) {
                        continue;
                    }
                    GroupEntry ge = new GroupEntry(
                              groupPieces[0],
                              Long.valueOf(groupPieces[2]).longValue(),
                              groupPieces[0],
                              "",
                              "",
                              null,
                              getRepositoryType());
                    ret.add(ge);

                }
            }
        } catch (IOException ioe) {
            m_logger.warn("Exception group listing entries", ioe);
            throw new ServiceUnavailableException(ioe.toString());
         
        }
            //List<Map<String, String[]>> list =
                //queryAsSuperuser(getSearchBase(),
                                 //getListAllGroupsSearchString(),
                                 //getGroupEntrySearchControls());
//
            //List<GroupEntry> ret = new ArrayList<GroupEntry>();
//
            //if(list == null || list.size() == 0) {
                //return ret;
            //}
//
            //for(Map<String, String[]> map : list) {
                //GroupEntry entry = toGroupEntry(map);
                //if(entry != null) {
                    //ret.add(entry);
                //}
            //}
            //return ret;
            //}
            //catch(NamingException ex) {
            // m_logger.warn("Exception group listing entries", ex);
            // throw new ServiceUnavailableException(ex.toString());
            //}
        return ret;
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
        sb.append(orStrings("objectClass=", getUserClassType()));
        sb.append("(").append(getMailAttributeName()).append("=").append(email).append(")");
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
        sb.append(orStrings("objectClass=", getUserClassType()));
        sb.append("(").append(getUIDAttributeName()).append("=").append(uid).append(")");
        sb.append(")");

        return getEntryWithSearchString(sb.toString());
    }

    public static String orStrings(String prefix, String[] values) {
        return queryStrings(prefix, values, "|", "(", ")");
    }

    public static String queryStrings(String prefix, String[] values, String operator, String open, String close) {
        StringBuilder results = new StringBuilder();
        queryStringsRecursive(prefix, values, operator, open, close, results);
        return results.toString();
    }
    
    protected static void queryStringsRecursive(String prefix, String[] values, String operator, String open, String close, StringBuilder results) {
        if (values.length == 1) {
            results.append(open + prefix + values[0] + close);
        } else {
            results.append(open + operator + open + prefix + values[0] + close);
            String[] valuesDequed = new String[values.length-1];
            System.arraycopy(values, 1, valuesDequed, 0, values.length-1);
            queryStringsRecursive(prefix, valuesDequed, operator, open, close, results);
            results.append(close);
        }
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


    /**
     * Method telling the adapter that is is no longer required.  However,
     * for threading reasons there may be a few more outstanding requests.
     */
    protected void close() {
        //TODO When we create the pool, this will put the pool into
        //a mode where each existing connection is shut-down, and any
        //new connect requests require a new context.  Returning contexts
        //always results in their being closed.
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

        RepositorySettings settings = getSettings();

        try {
            return createSuperuserContext();
        }
        catch(AuthenticationException ex) {
            m_logger.warn("Unable to create superuser context with settings: " +
                          "Host: \"" + settings.getLDAPHost() + "\", " +
                          "Port: \"" + settings.getLDAPPort() + "\", " +
                          "Superuser DN: \"" + getSuperuserDN() + "\", " +
                          "Pass: " + (settings.getSuperuserPass()==null?"<null>":"<not null>"),
                          ex);
            return null;
        }
        catch(Exception ex) {
            m_logger.error("Unable to create superuser context with settings: " +
                           "Host: \"" + settings.getLDAPHost() + "\", " +
                           "Port: \"" + settings.getLDAPPort() + "\", " +
                           "Superuser DN: \"" + getSuperuserDN() + "\", " +
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
        RepositorySettings settings = getSettings();
        return createContext(settings.getLDAPHost(),
                             settings.getLDAPPort(),
                             getSuperuserDN(),
                             settings.getSuperuserPass());
    }


    /**
     * Intended to be overidden by Active Directory
     */
    protected String getListAllUsersSearchString() {
        return orStrings("objectClass=", getUserClassType());
    }

    protected String getListAllGroupsSearchString() {
        return orStrings("objectClass=", getGroupClassType());
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
     * the context was passed-in), and return the specified attributes.
     *
     * @param searchBase the base of the search
     * @param searchFilter the query string
     * @param ctls the search controls specifying which attributes to return
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
                queryAsSuperuser(getSearchBase(),
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
            parseFullName(getFirstEntryOrNull(map.get(getFullNameAttributeName())));

        return new UserEntry(
                             getFirstEntryOrNull(map.get(getUIDAttributeName())),
                             parsedName.a,
                             parsedName.b,
                             getFirstEntryOrNull(map.get(getMailAttributeName())),
                             getRepositoryType());
    }

    /**
     * Helper to convert (based on our "standard" returned controls)
     * the GroupEntry.
     */
    private GroupEntry toGroupEntry(Map<String, String[]> map) {
        return new GroupEntry(
                              getFirstEntryOrNull(map.get(getCNName())),
                              Long.valueOf("0").longValue(), //TODO fix this later
                              getFirstEntryOrNull(map.get(getGroupName())),
                              getFirstEntryOrNull(map.get(getGroupTypeName())),
                              getFirstEntryOrNull(map.get(getGroupDescriptionName())),
                              map.get(getGroupMembersName()),
                              getRepositoryType());
    }

    protected String getCNName() {
        return "cn";
    }

    protected String getGroupName() {
        return "samaccountname";
    }

    protected String getGroupTypeName() {
        return "samaccounttype";
    }

    protected String getGroupDescriptionName() {
        return "description";
    }

    protected String getGroupMembersName() {
        return "members";
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
                                          getUIDAttributeName(),
                                          getMailAttributeName(),
                                          getFullNameAttributeName());
     }


    /**
     * Helper to create the search controls used when fetching a GroupEntry
     */
    private SearchControls getGroupEntrySearchControls() {
        return createSimpleSearchControls(
                                          "cn",
                                          "description",
                                          "sAMAccountName",
                                          "sAMAccountType",
                                          "member");
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

    // Helper
    protected String domainComponents(String dom)
    {
        if (dom.toUpperCase().startsWith("DC="))
            return dom;
        while (dom.endsWith("."))
            dom = dom.substring(0, dom.length() - 1);
        return "DC=" + dom.replace(".", ",DC=");
    }

}



/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.metavize.tran.mail.impl.safelist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;

import com.metavize.mvvm.util.TransactionWork;
import com.metavize.tran.mail.MailTransformImpl;
import com.metavize.tran.mail.papi.MailTransformSettings;
import com.metavize.tran.mail.papi.safelist.NoSuchSafelistException;
import com.metavize.tran.mail.papi.safelist.SafelistActionFailedException;
import com.metavize.tran.mail.papi.safelist.SafelistAdminView;
import com.metavize.tran.mail.papi.safelist.SafelistEndUserView;
import com.metavize.tran.mail.papi.safelist.SafelistManipulation;
import com.metavize.tran.mail.papi.safelist.SafelistRecipient;
import com.metavize.tran.mail.papi.safelist.SafelistSender;
import com.metavize.tran.mail.papi.safelist.SafelistSettings;
import com.metavize.tran.mail.papi.safelist.SafelistTransformView;
import com.metavize.tran.mime.EmailAddress;

/**
 * Implementation of the safelist stuff
 */
public class SafelistManager
  implements SafelistAdminView, SafelistEndUserView, SafelistTransformView
{
    private final Logger m_logger = Logger.getLogger(SafelistManager.class);

    private MailTransformImpl mlImpl;
    private MailTransformSettings mlSettings;

    // caches of values held by persistent hibernate mapping objects
    private HashMap<String, ArrayList<String>> m_sndrsByRcpnt = new HashMap<String, ArrayList<String>>();
    private HashSet<String> m_allSndrs = new HashSet<String>();

    public SafelistManager() {}

    /**
     * The Safelist manager "cheats" and lets the MailTranformImpl
     * maintain the persistence for settings
     */
    public void setSettings(MailTransformImpl mlImpl, MailTransformSettings mlSettings)
    {
        this.mlImpl = mlImpl;
        this.mlSettings = mlSettings;
        // cast list because xdoclet does not support java 1.5
        renew((List<SafelistSettings>) mlSettings.getSafelistSettings());
        return;
    }

    //-------------------- SafelistTransformView ------------------------

    //See doc on SafelistTransformView.java
    public boolean isSafelisted(EmailAddress envelopeSender,
      EmailAddress mimeFrom, List<EmailAddress> recipients)
    {
        EmailAddress eAddr = (null != envelopeSender ? envelopeSender : (null != mimeFrom ? mimeFrom : null));

        boolean bReturn = (null == eAddr) ? bReturn = false : m_allSndrs.contains(eAddr.getAddress().toLowerCase());
        m_logger.debug("sender ( " + envelopeSender + ", " + mimeFrom + "): is safelisted: " + bReturn);
        return bReturn;
    }

    //--------------------- SafelistManipulation -----------------------

    //See doc on SafelistManipulation.java
    public String[] addToSafelist(String rcpnt, String newSndr)
      throws NoSuchSafelistException, SafelistActionFailedException
    {
        m_logger.debug("recipient: " + rcpnt + ", added: " + newSndr + " to safelist");
        ArrayList<String> sndrs = getSndrs(rcpnt);
        if (null == sndrs) {
            sndrs = createSL(rcpnt);
            //throw new NoSuchSafelistException(rcpnt + " has no safelist; cannot add " + newSndr + " to safelist");
        }
        sndrs.add(newSndr.toLowerCase());

        setSndrs(rcpnt, sndrs);
        return toStringArray(sndrs);
    }

    //See doc on SafelistManipulation.java
    public String[] removeFromSafelist(String rcpnt, String obsSndr)
      throws NoSuchSafelistException, SafelistActionFailedException
    {
        m_logger.debug("recipient: " + rcpnt + ", removed: " + obsSndr + " from safelist");
        ArrayList<String> sndrs = getSndrs(rcpnt);
        if (null == sndrs) {
            return null;
            //throw new NoSuchSafelistException(rcpnt + " has no safelist; cannot remove " + obsSndr + " from safelist");
        }
        obsSndr = obsSndr.toLowerCase();
        sndrs.remove(obsSndr);

        setSndrs(rcpnt, sndrs);
        return toStringArray(sndrs);
    }

    //See doc on SafelistManipulation.java
    public String[] replaceSafelist(String rcpnt, String...newSndrs)
      throws NoSuchSafelistException, SafelistActionFailedException
    {
        m_logger.debug("recipient: " + rcpnt + ", replacing safelist");
        ArrayList<String> sndrs = getSndrs(rcpnt);
        if (null == sndrs) {
            sndrs = createSL(rcpnt);
            //throw new NoSuchSafelistException(rcpnt + " has no safelist to replace");
        } else {
            sndrs.clear();
        }

        for (String sndr : newSndrs) {
            sndrs.add(sndr.toLowerCase());
        }

        setSndrs(rcpnt, sndrs);
        return toStringArray(sndrs);
    }

    //See doc on SafelistManipulation.java
    public String[] getSafelistContents(String rcpnt)
      throws NoSuchSafelistException, SafelistActionFailedException
    {
        m_logger.debug("recipient: " + rcpnt + ", getting safelist");
        ArrayList<String> sndrs = getSndrs(rcpnt);
        if (null == sndrs) {
            sndrs = createSL(rcpnt);
            //throw new NoSuchSafelistException(rcpnt + " has no safelist; cannot get safelist");
        }

        return toStringArray(sndrs);
    }

    //See doc on SafelistManipulation.java
    public boolean hasOrCanHaveSafelist(String rcpnt)
    {
        m_logger.debug("recipient: " + rcpnt + ", has or can have safelist");
        return true;
    }

    //See doc on SafelistManipulation.java
    public void test()
    {
        return;
    }

    //--------------------- SafelistAdminView -----------------------

    //See doc on SafelistAdminView.java
    public List<String> listSafelists() throws SafelistActionFailedException
    {
        m_logger.debug("returning all safelists");
        return new ArrayList<String>(m_sndrsByRcpnt.keySet());
    }

    //See doc on SafelistAdminView.java
    public void deleteSafelist(String rcpnt)
      throws SafelistActionFailedException
    {
        m_logger.debug("recipient: " + rcpnt + ", deleted safelist");
        m_sndrsByRcpnt.remove(rcpnt.toLowerCase());

        setSndrs(null, null);
        return;
    }

    //See doc on SafelistAdminView.java
    public void createSafelist(String rcpnt)
      throws SafelistActionFailedException
    {
        createSL(rcpnt);

        return;
    }

    //See doc on SafelistAdminView.java
    public boolean safelistExists(String rcpnt)
      throws SafelistActionFailedException
    {
        boolean bReturn = m_sndrsByRcpnt.containsKey(rcpnt.toLowerCase());
        m_logger.debug("recipient: " + rcpnt + ", has safelist: " + bReturn);
        return bReturn;
    }

    //--------------------- SafelistEndUserView -----------------------

    // refresh (add/delete/update) safelists for this recipient and
    // make these changes visible as persistent hibernate mapping objects
    private synchronized void setSndrs(String rcpnt, ArrayList<String> sndrs)
    {
        if (null == rcpnt) {
            m_logger.debug("refreshing all safelists");
        } else {
            m_logger.debug("recipient: " + rcpnt + ", updating safelist: " + sndrs);
            rcpnt = rcpnt.toLowerCase();
        }

        HashMap<String, ArrayList<SafelistSettings>> allHMSafelistsByRcpnt = new HashMap<String, ArrayList<SafelistSettings>>();
        HashMap<String, SafelistSender> allHMSndrs = new HashMap<String, SafelistSender>();
        // cast list because xdoclet does not support java 1.5
        List<SafelistSettings> safelists = (List<SafelistSettings>) mlSettings.getSafelistSettings();

        ArrayList<SafelistSettings> rcpntHMSafelists;
        SafelistRecipient slRcpnt;
        SafelistSender slSndr;
        String tmpRcpnt;
        String tmpSndr;

        // create temporary caches of safelists
        for (SafelistSettings safelist : safelists) {
            // recipient may exist in multiple safelists
            // but we only use one copy of recipient
            slRcpnt = safelist.getRecipient();
            tmpRcpnt = slRcpnt.getAddr();

            // sender may exist in multiple safelists
            // but we only use one copy of sender
            slSndr = safelist.getSender();
            tmpSndr = slSndr.getAddr();
            allHMSndrs.put(tmpSndr, slSndr); // cache sender

            rcpntHMSafelists = allHMSafelistsByRcpnt.get(tmpRcpnt);
            if (null == rcpntHMSafelists) {
                // create safelist cache for recipient
                rcpntHMSafelists = new ArrayList<SafelistSettings>();
                allHMSafelistsByRcpnt.put(tmpRcpnt, rcpntHMSafelists);
            }
            rcpntHMSafelists.add(safelist); // cache safelist for recipient
        }

        ArrayList<SafelistSettings> newSafelists = new ArrayList<SafelistSettings>(safelists.size());
        ArrayList<String> curRcpnts = new ArrayList<String>(m_sndrsByRcpnt.keySet());

        SafelistSettings newSafelist;
        ArrayList<String> curSndrs;

        // update/add/remove safelists for current recipients
        for (String curRcpnt : curRcpnts) {
            if (null != rcpnt && true == rcpnt.equals(curRcpnt)) {
                if (null == sndrs) {
                    // we drop obsolete safelists for this recipient
                    // (by not reusing this recipient's safelists)
                    continue;
                } else {
                    // we have new safelists for this recipient
                    curSndrs = sndrs;
                }
            } else {
                // we reuse old safelists for this recipient
                // - list always exists in this context (but it may be empty)
                curSndrs = m_sndrsByRcpnt.get(curRcpnt);
            }

            rcpntHMSafelists = allHMSafelistsByRcpnt.get(curRcpnt);
            if (null == rcpntHMSafelists) {
                // create new recipient
                slRcpnt = new SafelistRecipient(curRcpnt);
                m_logger.debug("adding recipient: " + curRcpnt);

                // create safelist cache for new recipient
                rcpntHMSafelists = new ArrayList<SafelistSettings>();
                allHMSafelistsByRcpnt.put(curRcpnt, rcpntHMSafelists);
            } else {
                // use recipient from any safelist; recipients are all same
                slRcpnt = rcpntHMSafelists.get(0).getRecipient();
                m_logger.debug("reusing recipient: " + curRcpnt);
            }

            // if recipient has no safelist, skip recipient (do not save)
            for (String curSndr : curSndrs) {
                newSafelist = getSndrSafelist(curSndr, rcpntHMSafelists);
                if (null != newSafelist) {
                    // safelist already exists for this recipient
                    // so reuse safelist
                    newSafelists.add(newSafelist);
                    m_logger.debug("reusing sender: " + curSndr);
                    m_logger.debug("reusing safelist: " + newSafelist);
                    continue;
                }
                // else safelist doesn't exist for this recipient
                // so create new safelist

                newSafelist = new SafelistSettings();
                newSafelist.setRecipient(slRcpnt);

                // if another recipient uses this sender,
                // this recipient can use this sender too
                slSndr = allHMSndrs.get(curSndr);
                if (null == slSndr) {
                    // create new sender
                    slSndr = new SafelistSender(curSndr);
                    // cache new sender
                    allHMSndrs.put(curSndr, slSndr);
                    m_logger.debug("adding sender: " + curSndr);
                } else {
                    // else reuse sender
                    m_logger.debug("reusing sender: " + curSndr);
                }

                newSafelist.setSender(slSndr);

                newSafelists.add(newSafelist);
                m_logger.debug("adding safelist: " + newSafelist);

                if (null != rcpntHMSafelists) {
                    // cache new safelist for new recipient
                    rcpntHMSafelists.add(newSafelist);
                }
            }
        }

        // refresh all safelists
        // - delete unused safelists, add new safelists, reuse old safelists
        safelists.clear(); // clear old cache
        safelists.addAll(newSafelists); // add new cache

        // clear all caches of safelist references
        ArrayList<String> clrRcpnts = new ArrayList<String>(m_sndrsByRcpnt.keySet());
        for (String clrRcpnt : clrRcpnts) {
            allHMSafelistsByRcpnt.get(clrRcpnt).clear();
        }
        allHMSafelistsByRcpnt.clear();
        allHMSndrs.clear();
        newSafelists.clear();

        setHMSafelists(safelists); // set new cache
        return;
    }

    private SafelistSettings getSndrSafelist(String sndr, List<SafelistSettings> safelists)
    {
        if (null == safelists) {
            return null;
        }

        for (SafelistSettings safelist : safelists) {
            if (true == sndr.equals(safelist.getSender().getAddr())) {
                return safelist;
            }
        }

        return null;
    }

    private void setHMSafelists(List<SafelistSettings> safelists)
    {
        m_logger.debug("setting safelists, size: " + safelists.size());

        mlSettings.setSafelistSettings(safelists);

        TransactionWork tw = new TransactionWork()
        {
            public boolean doWork(Session s)
            {
                s.saveOrUpdate(mlSettings);

                return true;
            }

            public Object getResult() { return null; }
        };
        mlImpl.getTransformContext().runTransaction(tw);

        return;
    }

    // get (refresh) safelists for this recipient
    private ArrayList<String> getSndrs(String rcpnt)
    {
        return getSndrs(getHMSafelists(), rcpnt);
    }

    private ArrayList<String> getSndrs(List<SafelistSettings> safelists, String rcpnt)
    {
        return renewGetSndrs(safelists, rcpnt);
    }

    private List getHMSafelists()
    {
        TransactionWork tw = new TransactionWork()
        {
            public boolean doWork(Session s)
            {
                Query q = s.createQuery
                    ("from MailTransformSettings");
                mlSettings = (MailTransformSettings)q.uniqueResult();

                return true;
            }

            public Object getResult() { return null; }
        };
        mlImpl.getTransformContext().runTransaction(tw);

        // cast list because xdoclet does not support java 1.5
        return (List<SafelistSettings>) mlSettings.getSafelistSettings();
    }

    private void renew(List<SafelistSettings> safelists)
    {
        m_logger.debug("reading all safelists");

        HashMap<String, ArrayList<String>> sndrsByRcpnt = new HashMap<String, ArrayList<String>>();
        HashSet<String> allSndrs = new HashSet<String>();

        SafelistRecipient slRcpnt;
        SafelistSender slSndr;
        ArrayList<String> sndrs;
        String rcpnt;
        String sndr;

        for (SafelistSettings safelist : safelists) {

            // we implicitly ignore duplicates w/ HashMap
            slRcpnt = safelist.getRecipient();
            rcpnt = slRcpnt.getAddr().toLowerCase();

            slSndr = safelist.getSender();
            sndr = slSndr.getAddr().toLowerCase();

            m_logger.debug("using safelist: " + safelist + ", recipient: " + rcpnt + ", sender: " + sndr);

            // we implicitly ignore duplicates w/ HashSet
            allSndrs.add(sndr);

            sndrs = sndrsByRcpnt.get(rcpnt);
            if (null == sndrs) {
                // create new safelists for this recipient
                sndrs = new ArrayList<String>();
                sndrs.add(sndr);
                sndrsByRcpnt.put(rcpnt, sndrs);
                continue;
            }
            // else if necessary, add sender to this recipient's safelists

            if (true == sndrs.contains(sndr)) {
                // we explicitly ignore duplicates for ArrayList
                continue; 
            }
            // else add sender to this recipient's safelist

            sndrs.add(sndr);
        }

        m_sndrsByRcpnt = sndrsByRcpnt;
        m_allSndrs = allSndrs;

        return;
    }

    private ArrayList<String> renewGetSndrs(List<SafelistSettings> safelists, String rcpnt)
    {
        renew(safelists);
        // if null, recipient has no safelist
        return (m_sndrsByRcpnt.get(rcpnt.toLowerCase()));
    }

    private ArrayList<String> createSL(String rcpnt)
      throws SafelistActionFailedException
    {
        m_logger.debug("recipient: " + rcpnt + ", created safelist");
        ArrayList<String> sndrs = getSndrs(rcpnt);
        if (null == sndrs) {
            sndrs = new ArrayList<String>();
            m_sndrsByRcpnt.put(rcpnt, sndrs);
        } else {
            sndrs.clear();
        }

        return sndrs;
    }

    // return references to list contents for private use
    private String[] toStringArray(ArrayList<String> strs)
    {
        return (String[]) strs.toArray(new String[strs.size()]);
    }
}

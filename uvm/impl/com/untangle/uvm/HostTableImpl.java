/**
 * $Id$
 */
package com.untangle.uvm;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Date;
import java.util.Iterator;
import java.net.InetAddress;

import org.apache.log4j.Logger;

import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.HostTable;
import com.untangle.uvm.HostTableEntry;
import com.untangle.uvm.util.I18nUtil;
import com.untangle.uvm.node.EventLogQuery;
import com.untangle.uvm.node.PenaltyBoxEvent;
import com.untangle.uvm.node.QuotaEvent;

/**
 * HostTable stores a global table of all "local" IPs that have recently been seen.
 * This table is useful for storing information know about the various hosts.
 *
 * Different applications can add known information about various hosts by attaching objects with keys
 * Other applications can check what is known about various hosts by looking up objected stored for the various keys
 *
 * Other Documentation in HostTable.java
 */
public class HostTableImpl implements HostTable
{
    private static final int CLEANER_SLEEP_TIME_MILLI = 60 * 1000; /* 60 seconds */
    private static final int CLEANER_LAST_ACCESS_MAX_TIME = 30 * 60 * 1000; /* 30 minutes */

    private final Logger logger = Logger.getLogger(getClass());

    private ConcurrentHashMap<InetAddress, HostTableEntry> hostTable;

    private Set<HostTable.HostTableListener> listeners = new HashSet<HostTableListener>();

    private EventLogQuery penaltyBoxEventQuery;
    private EventLogQuery hostTableEventQuery;
    private EventLogQuery quotaEventQuery;

    private volatile Thread cleanerThread;
    private HostTableCleaner cleaner = new HostTableCleaner();

    private volatile Thread reverseLookupThread;
    private HostTableReverseHostnameLookup reverseLookup = new HostTableReverseHostnameLookup();
    private HashMap<String,String> macVendorTable = new HashMap<String,String>();
    
    private int maxLicensedSize = 0;
    
    protected HostTableImpl()
    {
        this.hostTable = new ConcurrentHashMap<InetAddress, HostTableEntry>();

        this.penaltyBoxEventQuery = new EventLogQuery(I18nUtil.marktr("PenaltyBox Events"), "SELECT * FROM reports.penaltybox ORDER BY time_stamp DESC");
        this.hostTableEventQuery = new EventLogQuery(I18nUtil.marktr("Host Table Events"), "SELECT * FROM reports.host_table_updates ORDER BY time_stamp DESC");
        this.quotaEventQuery = new EventLogQuery(I18nUtil.marktr("Quota Events"), "SELECT * FROM reports.quotas ORDER BY time_stamp DESC");
        initializeMacVendorTable();
        
        UvmContextFactory.context().newThread(this.cleaner).start();
        UvmContextFactory.context().newThread(this.reverseLookup).start();
    }
    
    public HostTableEntry getHostTableEntry( InetAddress addr )
    {
        return getHostTableEntry( addr, false );
    }

    public HostTableEntry getHostTableEntry( InetAddress addr, boolean createIfNecessary )
    {
        HostTableEntry entry = hostTable.get( addr );

        if ( entry == null && createIfNecessary ) {
            entry = createNewHostTableEntry( addr );
            hostTable.put( addr, entry );
            this.reverseLookupThread.interrupt(); /* wake it up to force hostname lookup */
        }

        return entry;
    }
    
    public void setHostTableEntry( InetAddress addr, HostTableEntry entry )
    {
        hostTable.put( addr, entry );
    }

    public LinkedList<HostTableEntry> getHosts()
    {
        return new LinkedList<HostTableEntry>(hostTable.values());
    }
    
    public synchronized void addHostToPenaltyBox( InetAddress address, int time_sec, String reason )
    {
        HostTableEntry entry = getHostTableEntry( address, true );
        long entryTime = System.currentTimeMillis();
        long exitTime  = entryTime + (time_sec * 1000L);

        logger.info("Adding " + address.getHostAddress() + " to Penalty box for " + time_sec + " seconds");

        /**
         * Set Penalty Boxed flag
         */
        boolean currentFlag = entry.getPenaltyBoxed();
        entry.setPenaltyBoxed(true);

        /**
         * If the entry time is null, set it.
         * If it is not null, the host was probably already in the penalty box so don't update it
         */
        long currentEntryTime = entry.getPenaltyBoxEntryTime();
        if (currentEntryTime == 0)
            entry.setPenaltyBoxEntryTime( entryTime );
        currentEntryTime = entryTime;

        /**
         * Update the exit time, if the proposed value is after the current value
         */
        long currentExitTime = entry.getPenaltyBoxExitTime();
        if (currentExitTime == 0 || exitTime > currentExitTime)
            entry.setPenaltyBoxExitTime( exitTime );
        currentExitTime = exitTime;
            
        int action;
        if ( currentFlag ) {
            action = PenaltyBoxEvent.ACTION_REENTER; /* was already there */
        } else {
            action = PenaltyBoxEvent.ACTION_ENTER; /* new entry */
        }

        PenaltyBoxEvent evt = new PenaltyBoxEvent( action, address, new Date(currentEntryTime), new Date(currentExitTime), reason ) ;
        UvmContextFactory.context().logEvent(evt);

        /**
         * Call listeners
         */
        if (action == PenaltyBoxEvent.ACTION_ENTER) {
            for ( HostTableListener listener : this.listeners ) {
                try {
                    listener.enteringPenaltyBox( address );
                } catch ( Exception e ) {
                    logger.error( "Exception calling listener", e );
                }
            }
        }
        
        return;
    }

    public synchronized void releaseHostFromPenaltyBox( InetAddress address )
    {
        HostTableEntry entry = getHostTableEntry( address );
        if (entry == null) /* host not in penalty box */
            return;
        
        long now = System.currentTimeMillis();

        /**
         * Save previous values and remove from penalty box
         */
        boolean currentFlag = entry.getPenaltyBoxed();
        long currentEntryTime = entry.getPenaltyBoxEntryTime();
        long currentExitTime  = entry.getPenaltyBoxExitTime();
        entry.setPenaltyBoxed( false );
        entry.setPenaltyBoxEntryTime( 0 );
        entry.setPenaltyBoxExitTime( 0 );
        
        /**
         * If the host was in the penalty box, we must log the event and call the listeners
         */

        if ( !currentFlag ) {
            return;
        }
        if ( currentEntryTime == 0 ) {
            logger.warn("Entry time not set for penalty boxed host");
            return;
        }
        if ( currentExitTime == 0 ) {
            logger.warn("Exit time not set for penalty boxed host");
            return;
        }
        
        /**
         * If current date is before planned exit time, use it instead, otherwise just log the exit time
         */
        if ( now > currentExitTime ) {
            logger.info("Removing " + address.getHostAddress() + " from Penalty box. (expired)");
        } else {
            logger.info("Removing " + address.getHostAddress() + " from Penalty box. (admin requested)");
            currentExitTime = now; /* set exitTime to now, because the host was release prematurely */
        }
            
        UvmContextFactory.context().logEvent( new PenaltyBoxEvent( PenaltyBoxEvent.ACTION_EXIT, address, new Date(currentEntryTime), new Date(currentExitTime), null ) );

        /**
         * Call listeners
         */
        for ( HostTableListener listener : this.listeners ) {
            try {
                listener.exitingPenaltyBox( address );
            } catch ( Exception e ) {
                logger.error( "Exception calling listener", e );
            }
        }
        
        return;
    }

    public synchronized void giveHostQuota( InetAddress address, long quotaBytes, int time_sec, String reason )
    {
        if (address == null) {
            logger.warn("Invalid argument: address is null");
            return;
        }
        HostTableEntry entry = getHostTableEntry( address, true );
        long now = System.currentTimeMillis();

        /* If there already is a quota and it will be reset */
        entry.setQuotaSize( quotaBytes );
        entry.setQuotaRemaining( quotaBytes );
        entry.setQuotaIssueTime( now );
        entry.setQuotaExpirationTime( now + (time_sec*1000) );

        /**
         * Call listeners
         */
        for ( HostTableListener listener : this.listeners ) {
            try {
                listener.quotaGiven( address );
            } catch ( Exception e ) {
                logger.error( "Exception calling listener", e );
            }
        }

        UvmContextFactory.context().logEvent( new QuotaEvent( QuotaEvent.ACTION_GIVEN, address, reason, quotaBytes ) );
        
        return;
    }

    public synchronized void removeQuota( InetAddress address )
    {
        if (address == null) {
            logger.warn("Invalid argument: address is null");
            return;
        }
        HostTableEntry entry = getHostTableEntry( address );
        if (entry == null)
            return;

        entry.setQuotaSize( 0 );
        entry.setQuotaRemaining( 0 );
        entry.setQuotaIssueTime( 0 );
        entry.setQuotaExpirationTime( 0 );

        /**
         * Call listeners
         */
        for ( HostTableListener listener : this.listeners ) {
            try {
                listener.quotaRemoved( address );
            } catch ( Exception e ) {
                logger.error( "Exception calling listener", e );
            }
        }
    }

    public boolean hostQuotaExceeded( InetAddress address )
    {
        if (address == null) {
            logger.warn("Invalid argument: address is null");
            return false;
        }
        HostTableEntry entry = getHostTableEntry( address );
        if ( entry == null )
            return false;
        if (entry.getQuotaSize() <= 0)
            return false;

        /**
         * Check if its expired, if it is - remove the quota
         */
        long now = System.currentTimeMillis();
        if (now > entry.getQuotaExpirationTime()) {
            removeQuota( address );
            return false;
        }

        if (entry.getQuotaRemaining() <= 0)
            return true;
        return false;
    }

    public synchronized void refillQuota(InetAddress address)
    {
        if (address == null) {
            logger.warn("Invalid argument: address is null");
            return;
        }
        HostTableEntry entry = getHostTableEntry( address );
        if ( entry == null )
            return;
        if ( entry.getQuotaSize() <= 0 )
            return;

        entry.setQuotaRemaining( entry.getQuotaSize() );
    }

    public synchronized boolean decrementQuota( InetAddress address, long bytes )
    {
        if (address == null) {
            logger.warn("Invalid argument: address is null");
            return false;
        }
        HostTableEntry entry = getHostTableEntry( address );
        if ( entry == null )
            return false;
        if ( entry.getQuotaSize() <= 0 )
            return false;

        /**
         * Decrement
         */
        long remaining = entry.getQuotaRemaining();
        long newRemaning = remaining - bytes;
        entry.setQuotaRemaining( newRemaning );


        if ( remaining > 0 && newRemaning <= 0 ) {
            logger.info("Host " + address.getHostAddress() + " exceeded quota.");
            UvmContextFactory.context().logEvent( new QuotaEvent( QuotaEvent.ACTION_EXCEEDED, address, null, entry.getQuotaSize()) );
            return true;
        }

        return false;
    }
    
    public boolean hostInPenaltyBox( InetAddress address )
    {
        if (address == null) {
            logger.warn("Invalid argument: address is null");
            return false;
        }
        HostTableEntry entry = getHostTableEntry( address );
        if ( entry == null )
            return false;
        if ( !entry.getPenaltyBoxed() )
            return false;

        /**
         * If the exit time has already passed the host is no longer penalty boxed
         * As such, release the host from the penalty box immediately and return false
         */
        long exitTime = entry.getPenaltyBoxExitTime();
        long now = System.currentTimeMillis();
        if ( exitTime != 0 && now > exitTime ) {
            releaseHostFromPenaltyBox( address );
            return false;
        }
                
        return true;
    }

    public LinkedList<HostTableEntry> getPenaltyBoxedHosts()
    {
        LinkedList<HostTableEntry> list = new LinkedList<HostTableEntry>(UvmContextFactory.context().hostTable().getHosts());

        for (Iterator<HostTableEntry> i = list.iterator(); i.hasNext(); ) {
            HostTableEntry entry = i.next();
            if (! hostInPenaltyBox( entry.getAddress() ) )
                i.remove();
        }

        return list;
    }

    public LinkedList<HostTableEntry> getQuotaHosts()
    {
        LinkedList<HostTableEntry> list = new LinkedList<HostTableEntry>(UvmContextFactory.context().hostTable().getHosts());

        for (Iterator<HostTableEntry> i = list.iterator(); i.hasNext(); ) {
            HostTableEntry entry = i.next();
            if ( entry.getQuotaSize() <= 0 )
                i.remove();
        }

        return list;
    }
    
    public void registerListener( HostTable.HostTableListener listener )
    {
        this.listeners.add( listener );
    }

    public void unregisterListener( HostTable.HostTableListener listener )
    {
        this.listeners.remove( listener );
    }

    public EventLogQuery[] getPenaltyBoxEventQueries()
    {
        return new EventLogQuery[] { this.penaltyBoxEventQuery };
    }

    public EventLogQuery[] getQuotaEventQueries()
    {
        return new EventLogQuery[] { this.quotaEventQuery };
    }

    public int getCurrentSize()
    {
        return this.hostTable.size();
    }

    public int getCurrentLicensedSize()
    {
        int licenseSize = 0;

        /**
         * Only count hosts with getLastSessionTime() is > 0
         * Meaning the UVM has processed sessions for that host
         * and its processes sessions within 24 hours
         */
        try {
            long cutoffTime = System.currentTimeMillis() - 1000 * 60 * 60 * 24; /* 24 hours ago */
            for ( Iterator<HostTableEntry> i = hostTable.values().iterator() ; i.hasNext() ; ) {
                HostTableEntry entry = i.next();
                if (entry.getLastSessionTime() > cutoffTime)
                    licenseSize++;
            }
        }
        catch (java.util.ConcurrentModificationException e) {} // ignore this, just best effort
        
        return licenseSize;
    }
    
    public int getMaxLicensedSize()
    {
        return this.maxLicensedSize;
    }

    public void clearTable()
    {
        this.hostTable.clear();
    }
    
    private HostTableEntry createNewHostTableEntry( InetAddress address )
    {
        HostTableEntry entry = new HostTableEntry();

        entry.setAddress( address );
        
        String macAddress = UvmContextFactory.context().netcapManager().arpLookup( address.getHostAddress() );
        if ( macAddress != null && !("".equals(macAddress)) ) {
            entry.setMacAddress( macAddress );

            String macVendor = lookupMacVendor( macAddress );
            if ( macVendor != null && !("".equals(macVendor)) )
                entry.setMacVendor( macVendor );
        }
        
        int seatLimit = UvmContextFactory.context().licenseManager().getSeatLimit();
        int currentSize = getCurrentLicensedSize();
        
        // if there is a seat limit, and the size of the table is currently greater than that seatLimit
        // this host is out of compliance and not licensed
        if ( seatLimit > 0 && currentSize > seatLimit ) {
            entry.setLicensed( false );
        }
        
        return entry;
    }

    private void adjustMaxSizeIfNecessary()
    {
        int realSize = 0;

        /**
         * Only count hosts with getLastSessionTime() is > 0
         * Meaning the UVM has processed sessions for that host
         * and its processes sessions within 24 hours
         */
        long cutoffTime = System.currentTimeMillis() - 1000 * 60 * 60 * 24; /* 24 hours ago */
        for ( Iterator<HostTableEntry> i = hostTable.values().iterator() ; i.hasNext() ; ) {
            HostTableEntry entry = i.next();
            if ( entry.getLastSessionTime() > cutoffTime )
                realSize++;
        }
        
        if (realSize > this.maxLicensedSize)
            this.maxLicensedSize = realSize;
    }

    private String lookupMacVendor( String macAddress )
    {
        if ( macAddress == null )
            return null;

        String macPrefix = macAddress.substring( 0, 8 );
        return macVendorTable.get( macPrefix );
    }
    
    private void initializeMacVendorTable()
    {
        this.macVendorTable = new HashMap<String,String>();

        Runnable task = new Runnable()
        {
            public void run()
            {
                String filename = System.getProperty("uvm.lib.dir") + "/untangle-vm/oui-formatted.txt";

                long t0 = System.currentTimeMillis();

                java.io.BufferedReader br = null;
                try {
                    br = new java.io.BufferedReader(new java.io.FileReader(filename));
                    for (String line = br.readLine(); line != null ; line = br.readLine()) {
                        String[] parts = line.split("\\s+",2);
                        if ( parts.length < 2 )
                            continue;

                        String macPrefix = parts[0];
                        String vendor = parts[1];
                        //logger.debug( macPrefix + " ---> " + vendor );

                        macVendorTable.put( macPrefix, vendor );
                    }
                } catch (Exception e) {
                    logger.warn("Failed to load MAC OUI data.", e);
                } finally {
                    if ( br != null ) {
                        try {br.close();} catch(Exception e) {}
                    }
                }

                long t1 = System.currentTimeMillis();
                logger.info("Loaded MAC OUI table: " + (t1 - t0) + " millis");
            }
        };
        Thread t = new Thread(task, "MAC-oui-loader");
        t.setDaemon(true);
        t.start();
        return;
    }

    /**
     * This thread periodically walks through the entries and removes expired entries
     * It also explicitly releases hosts from the penalty box and quotas after expiration
     */
    private class HostTableCleaner implements Runnable
    {
        public void run()
        {
            cleanerThread = Thread.currentThread();

            while (cleanerThread != null) {
                try {Thread.sleep(CLEANER_SLEEP_TIME_MILLI);} catch (Exception e) {}
                logger.debug("HostTableCleaner: Running... ");

                try {
                    Long now = System.currentTimeMillis();
                    /**
                     * Remove old entries
                     */
                    LinkedList<HostTableEntry> entries = new LinkedList<HostTableEntry>(hostTable.values());
                    for (HostTableEntry entry : entries) {
                        InetAddress address = entry.getAddress();
                        if ( address == null )
                            continue;

                        /**
                         * Check penalty box expiration
                         * Remove from penalty box if expired
                         */
                        if ( entry.getPenaltyBoxed() ) {
                            long exitTime = entry.getPenaltyBoxExitTime();
                            if (now > exitTime) {
                                releaseHostFromPenaltyBox( address );
                            }
                        }

                        /**
                         * Check quota expiration
                         * Remove from quota if expired
                         */
                        if ( entry.getQuotaSize() > 0 ) {
                            long expireTime = entry.getQuotaExpirationTime();
                            if ( now > expireTime ) {
                                removeQuota( address );
                            }
                        }

                        /**
                         * If this host hasnt been touched recently, delete it
                         */
                        if ( now > (entry.getLastAccessTime() + CLEANER_LAST_ACCESS_MAX_TIME) ) {

                            /**
                             * If this host table entry is storing vital information, don't delete it
                             */
                            if ( entry.getQuotaSize() > 0 ||
                                 entry.getPenaltyBoxed() ||
                                 entry.getCaptivePortalAuthenticated() /* check authenticated flag instead of username because anonymous logins don't set username */
                                 ) {
                                continue;
                            }
                            /**
                             * If this host is still reachable/online, don't remove the information
                             * Limit this check to 3 hops TTL and 500 ms. Sometimes external (but pingable) hosts can get into the host table
                             * via spoofing and/or SSDP and UPnP and stuff like that.
                             * We want these hosts to expire once we don't have traffic for them.
                             * This should ensure that only *local* pingable addresses stay in the host table if they respond to ping.
                             */
                            if ( entry.getAddress().isReachable( null, 3, 500 ) ) {
                                continue;
                            }
                            
                            /**
                             * Otherwise just delete the entire entry
                             */
                            else {
                                logger.debug("HostTableCleaner: Removing " + address.getHostAddress());
                                hostTable.remove(address);
                            }
                        }
                    }

                    /**
                     * if certain hosts are "unlicensed" and show now be licensed, set them back
                     */
                    int numUnlicensed = 0;
                    entries = new LinkedList<HostTableEntry>(hostTable.values());
                    for (HostTableEntry entry : entries) {
                        if (!entry.getLicensed())
                            numUnlicensed++;
                    }
                    if ( UvmContextFactory.context().licenseManager() != null ) {
                        int seatLimit = UvmContextFactory.context().licenseManager().getSeatLimit();
                        int excess = hostTable.size() - seatLimit;
                        // if there number of unlicensed hosts is more than it should be - reduce it
                        if ( numUnlicensed > excess ) {
                            int reduction = numUnlicensed - excess;
                            for (HostTableEntry entry : entries) {
                                if (!entry.getLicensed()) {
                                    entry.setLicensed( true );
                                    reduction--;
                                    if ( reduction < 1 )
                                        break;
                                }
                            }
                        }
                    }
                    
                    adjustMaxSizeIfNecessary();
                    
                } catch (Exception e) {
                    logger.warn("Exception while cleaning host table",e);
                }
            }
        }
    }

    /**
     * This thread periodically walks through the entries and does a reverse lookup on the IP 
     * to see if it can determine the host's hostname (for hosts without a known hostname)
     *
     * This is done in a separate thread because it may hang on the DNS lookup.
     */
    private class HostTableReverseHostnameLookup implements Runnable
    {
        public void run()
        {
            reverseLookupThread = Thread.currentThread();

            while (reverseLookupThread != null) {
                try {Thread.sleep(CLEANER_SLEEP_TIME_MILLI);} catch (Exception e) {}
                logger.debug("HostTableReverseHostnameLookup: Running... ");

                try {
                    /**
                     * Remove old entries
                     */
                    LinkedList<HostTableEntry> entries = new LinkedList<HostTableEntry>(hostTable.values());
                    for (HostTableEntry entry : entries) {
                        String currentHostname = entry.getHostname();
                        InetAddress address = entry.getAddress();
                        if ( address == null )
                            continue;
                        if ( entry.isHostnameKnown() )
                            continue;
                        
                        try {
                            String hostname = address.getHostName();

                            if ( hostname == null )
                                continue;
                            if ( hostname.equals( currentHostname ) )
                                continue;
                            if ( hostname.equals( address.getHostAddress() ) )
                                continue;

                            /* use just the first part of the name */
                            int firstdot = hostname.indexOf('.');
                            if ( firstdot != -1 )
                                hostname = hostname.substring(0,firstdot);
                            
                            logger.debug("HostTable Reverse lookup hostname = " + hostname);
                            entry.setHostname( hostname );
                        } catch (Exception e) {
                            logger.warn("Exception in reverse lookup",e);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Exception while cleaning host table",e);
                }
            }
        }
    }
}

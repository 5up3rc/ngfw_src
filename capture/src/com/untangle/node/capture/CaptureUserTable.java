/**
 * $Id$
 */

package com.untangle.node.capture;

import java.net.InetAddress;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.TimerTask;
import org.apache.log4j.Logger;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.HostTable;
import com.untangle.uvm.HostTableEntry;

public class CaptureUserTable
{
    private final Logger logger = Logger.getLogger(getClass());
    private Hashtable<InetAddress, CaptureUserEntry> userTable;

    public class StaleUser
    {
        CaptureUserEvent.EventType reason;
        InetAddress address;

        StaleUser(InetAddress address, CaptureUserEvent.EventType reason)
        {
            this.address = address;
            this.reason = reason;
        }
    }

    public CaptureUserTable()
    {
        userTable = new Hashtable<InetAddress, CaptureUserEntry>();
    }

    public ArrayList<CaptureUserEntry> buildUserList()
    {
        ArrayList<CaptureUserEntry> userList = new ArrayList<CaptureUserEntry>(userTable.values());
        return (userList);
    }

    public CaptureUserEntry insertActiveUser(InetAddress address, String username, Boolean anonymous)
    {
        CaptureUserEntry local = new CaptureUserEntry(address, username, anonymous);
        userTable.put(address, local);

        // for anonymous users clear the global capture username which
        // shouldn't be required but always better safe than sorry
        if (anonymous == true) {
            UvmContextFactory.context().hostTable().getHostTableEntry(address, true).setUsernameCapture(null);
        }

        // for all other users set the global capture username
        else {
            UvmContextFactory.context().hostTable().getHostTableEntry(address, true).setUsernameCapture(username);
        }

        return (local);
    }

    public boolean removeActiveUser(InetAddress address)
    {
        CaptureUserEntry user = userTable.get(address);
        if (user == null)
            return (false);
        userTable.remove(address);

        UvmContextFactory.context().hostTable().getHostTableEntry(address, true).setUsernameCapture(null);
        return (true);
    }

    public CaptureUserEntry searchByAddress(InetAddress address)
    {
        return (userTable.get(address));
    }

    public CaptureUserEntry searchByUsername(String username, boolean ignoreCase)
    {
        Enumeration<CaptureUserEntry> ee = userTable.elements();

        while (ee.hasMoreElements()) {
            CaptureUserEntry item = ee.nextElement();

            // if the ignoreCase flag is set we compare both as lowercase
            if (ignoreCase == true) {
                if (username.toLowerCase().equals(item.getUserName().toLowerCase()) == true)
                    return (item);
            }

            // ignoreCase flag is not set so do a direct comparison
            else {
                if (username.equals(item.getUserName()) == true)
                    return (item);
            }
        }

        return (null);
    }

    public ArrayList<StaleUser> buildStaleList(long idleTimeout, long userTimeout)
    {
        ArrayList<StaleUser> wipelist = new ArrayList<StaleUser>();
        long currentTime, idleTrigger, idleTrigger2, userTrigger;
        StaleUser stale;
        int wipecount;

        currentTime = (System.currentTimeMillis() / 1000);
        Enumeration<CaptureUserEntry> ee = userTable.elements();
        wipecount = 0;

        while (ee.hasMoreElements()) {
            CaptureUserEntry item = ee.nextElement();
            userTrigger = ((item.getSessionCreation() / 1000) + userTimeout);

            HostTableEntry entry = UvmContextFactory.context().hostTable().getHostTableEntry(item.getUserAddress());
            if (entry != null) {
                idleTrigger = (entry.getLastSessionTime() / 1000) + idleTimeout;
            } else {
                logger.warn("HostTableEntry missing for logged in Captive Portal Entry: " + item.getUserAddress().getHostAddress() + " : " + item.getUserName());
                idleTrigger = ((item.getSessionActivity() / 1000) + userTimeout);
            }

            // look for users with no traffic within the configured non-zero
            // idle timeout
            if ((idleTimeout > 0) && (currentTime > idleTrigger)) {
                logger.info("Idle timeout removing user " + item.getUserAddress() + " " + item.getUserName());
                stale = new StaleUser(item.getUserAddress(), CaptureUserEvent.EventType.INACTIVE);
                wipelist.add(stale);
                wipecount++;
            }

            // look for users who have exceeded the configured maximum session
            // time
            if (currentTime > userTrigger) {
                logger.info("Session timeout removing user " + item.getUserAddress() + " " + item.getUserName());
                stale = new StaleUser(item.getUserAddress(), CaptureUserEvent.EventType.TIMEOUT);
                wipelist.add(stale);
                wipecount++;
            }
        }

        return (wipelist);
    }

    public void purgeAllUsers()
    {
        userTable.clear();
    }
}

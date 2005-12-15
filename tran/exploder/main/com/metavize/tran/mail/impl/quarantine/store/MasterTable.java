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

package com.metavize.tran.mail.impl.quarantine.store;
import com.metavize.tran.mail.papi.quarantine.InboxRecord;
import com.metavize.tran.util.Pair;
import org.apache.log4j.Logger;
import java.io.File;
import java.util.Set;
import java.util.Map;

//========================================================
// For add/remove operations, we make a copy
// of our "m_summary" reference, so reads
// do not have to be synchronized.  This assumes
// that lookups of account <-> dir are much more
// frequent (at steady state) than additions/removals
// of accounts.

/**
 * Manager of the master records for mapping
 * users to folders, as well as tracking
 * overall size of the store.
 * <br><br>
 * Assumes that caller has locked any addresses
 * being referenced, unless otherwise noted.
 */
final class MasterTable {

  private static final Logger s_logger =
    Logger.getLogger(MasterTable.class);

  private File m_rootDir;
  private StoreSummary m_summary;
  private volatile boolean m_closing = false;


  private MasterTable(File dir,
    StoreSummary initialSummary) {
    m_rootDir = dir;
    m_summary = initialSummary;
  }



  /**
   * Open the MasterTable.  The InboxDirectoryTree is
   * needed in case the system closed abnormally
   * and the StoreSummary needs to be rebuilt.
   */
  static MasterTable open(File rootDir,
    InboxDirectoryTree dirTracker) {

    Pair<StoreSummaryDriver.FileReadOutcome, StoreSummary> read =
      StoreSummaryDriver.readSummary(rootDir);

    boolean needRebuild = false;
    
    switch(read.a) {
      case OK:
        s_logger.debug("Read existing StoreSummary file from disk");
        StoreSummaryDriver.openSummary(rootDir);
        needRebuild = false;//redundant
        break;
      case NO_SUCH_FILE:
        s_logger.warn("No store summary on disk.  Either this is a " +
          "fresh startup, or the system experienced an improper shutdown " +
          "on last run");
        needRebuild = true;
        break;
      case EXCEPTION:
        s_logger.warn("Store summary cannot be read (system halt while writing to disk?). " +
          "Rebuild summary from Inbox scans");
        needRebuild = true;
        break;
      case FILE_CORRUPT:
        s_logger.warn("Store summary corrupt (system halt while writing to disk?). " +
          "Rebuild summary from Inbox scans");
        needRebuild = true;
    }

    if(needRebuild) {
      RebuildingVisitor visitor = new RebuildingVisitor();
      s_logger.debug("About to scan Inbox directories to rebuild summary");
      dirTracker.visitInboxes(visitor);
      s_logger.debug("Done scanning Inbox directories to rebuild summary");
      return new MasterTable(rootDir, visitor.getSummary());
    }
    else {
      return new MasterTable(rootDir, read.b);
    }
  }

  boolean inboxExists(String lcAddress) {
    return m_summary.containsInbox(lcAddress);
  }

  /**
   * Assumes caller has already found that there is no such
   * inbox, while holding the master lock for this
   * account
   */
  synchronized void addInbox(String address, RelativeFileName inboxDir) {

    StoreSummary newSummary = new StoreSummary(m_summary);
    newSummary.addInbox(address, new InboxSummary(inboxDir));
    m_summary = newSummary;
    save();
  }

  /**
   *
   */
  synchronized void removeInbox(String address) {
    StoreSummary newSummary = new StoreSummary(m_summary);
    newSummary.removeInbox(address);
    m_summary = newSummary;
    save();
  }

  /**
   * Assumes caller has already determined that this
   * inbox exists.
   *
   * PRE: address lower case
   */
  synchronized boolean mailAdded(String address, long sz) {
    InboxSummary meta = m_summary.getInbox(address);
    if(meta == null) {
      return false;
    }
    m_summary.mailAdded(meta, sz);
    save();
    return true;
  }

  /**
   * Assumes caller has already determined that this
   * inbox exists.
   *
   * PRE: address lower case
   */  
  synchronized boolean mailRemoved(String address, long sz) {
    InboxSummary meta = m_summary.getInbox(address);
    if(meta == null) {
      return false;
    }
    m_summary.mailRemoved(meta, sz);
    save();
    return true;
  }

  /**
   * Assumes caller has already determined that this
   * inbox exists.
   *
   * PRE: address lower case
   */  
  synchronized boolean updateMailbox(String address,
    long totalSz, int totalMails) {
    InboxSummary meta = m_summary.getInbox(address);
    if(meta == null) {
      return false;
    }
    m_summary.updateMailbox(meta, totalSz, totalMails);
    save();
    return true;
  }

  /**
   * Get the sum total of the lengths of all
   * mails across all inboxes.
   */
  long getTotalQuarantineSize() {
    return m_summary.getTotalSz();
  }

  /**
   * Get the total number of mails in all inboxes
   */
  int getTotalNumMails() {
    return m_summary.getTotalMails();
  }

  /**
   * Get the total number of inboxes
   */
  int getTotalInboxes() {
    return m_summary.size();
  }  

  /**
   * Close this table, causing data to be written
   * out to disk.  Any subsequent (stray) calls
   * to this object will also cause an update of
   * the on-disk representation of state.
   */
  synchronized void close() {
    m_closing = true;
    save();
  }

  private void save() {
    if(m_closing) {
      if(!StoreSummaryDriver.writeSummary(m_rootDir, m_summary)) {
        s_logger.warn("Unable to save StoreSummary.  Next startup " +
          "will have to rebuild index");
      }
    }
  }
  


  /**
   * Returns null if not found.  Note that since this is not
   * synchronized, one should call this while holding the
   * master account lock to ensure that concurrent
   * creation doesn't take place.
   */
  RelativeFileName getInboxDir(String address) {
    InboxSummary meta = m_summary.getInbox(address);
    return meta==null?
      null:
      meta.getDir();
  }

  /**
   * Do not modify any of the returned entries, as it is a shared
   * reference.  The returned set itself is guaranteed never
   * to be modified.
   */
  Set<Map.Entry<String,InboxSummary>> entries() {
    return m_summary.entries();
  }  

  //-------------- Inner Class ---------------------

  // Class used when we have to visit
  // all directories in the Inbox tree and
  // rebuild our index.
  private static class RebuildingVisitor
    implements InboxDirectoryTreeVisitor {

    private StoreSummary m_storeMeta = new StoreSummary();
    
    public void visit(RelativeFile f) {
      if(!f.file.isDirectory()) {
        //Defensive programming against myself!
        return;
      }
      Pair<InboxIndexDriver.FileReadOutcome, InboxIndexImpl>
        result = InboxIndexDriver.readIndex(f.file);

      if(result.a == InboxIndexDriver.FileReadOutcome.OK) {
        long totalSz = 0;
        int totalMails = 0;
        for(InboxRecord record : result.b) {
          totalSz+=record.getSize();
          totalMails++;
        }
        m_storeMeta.addInbox(result.b.getOwnerAddress(),
          new InboxSummary(f,
            totalSz,
            totalMails));
      }
    }

    StoreSummary getSummary() {
      return m_storeMeta;
    }
    
  }
  
}
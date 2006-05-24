/*
 * Copyright (c) 2004, 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.boxbackup;

import com.metavize.mvvm.CronJob;
import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.Period;
import com.metavize.mvvm.logging.EventLogger;
import com.metavize.mvvm.logging.EventLoggerFactory;
import com.metavize.mvvm.logging.EventManager;
import com.metavize.mvvm.logging.SimpleEventFilter;
import com.metavize.mvvm.tapi.AbstractTransform;
import com.metavize.mvvm.tapi.PipeSpec;
import com.metavize.mvvm.tran.TransformContext;
import com.metavize.mvvm.tran.TransformException;
import com.metavize.mvvm.tran.TransformStartException;
import com.metavize.mvvm.util.TransactionWork;
import com.metavize.tran.util.SimpleExec;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;

public class BoxBackupImpl extends AbstractTransform implements BoxBackup
{
    private static final String DEF_BACKUP_URL = "https://boxtrack.metavize.com/boxbackup/backup.php";

    private final EventHandler handler = new EventHandler(this);
    private final PipeSpec[] pipeSpecs = new PipeSpec[] { };
    private final Logger logger = Logger.getLogger(BoxBackupImpl.class);
    private EventLogger<BoxBackupEvent> eventLogger;
    private CronJob cronJob;

    private BoxBackupSettings settings = null;

    public BoxBackupImpl() {
      TransformContext tctx = getTransformContext();
      eventLogger = EventLoggerFactory.factory().getEventLogger(tctx);

      SimpleEventFilter ef = new BoxBackupFilterAllFilter();
      eventLogger.addSimpleEventFilter(ef);
    }

    public EventManager<BoxBackupEvent> getEventManager()
    {
        return eventLogger;
    }

    public BoxBackupSettings getBoxBackupSettings()
    {
        return this.settings;
    }

    public void setBoxBackupSettings(final BoxBackupSettings settings)
    {
        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    s.saveOrUpdate(settings);
                    BoxBackupImpl.this.settings = settings;
                    return true;
                }

                public Object getResult() { return null; }
            };
        getTransformContext().runTransaction(tw);

        try {
            reconfigure();
        }
        catch (TransformException exn) {
            logger.error("Could not save BoxBackup settings", exn);
        }
        if (null != cronJob) {
            int h = settings.getHourInDay();
            int m = settings.getMinuteInHour();
            Period p = new Period(h, m, true);
            cronJob.reschedule(p);
        }
    }

    @Override
    protected PipeSpec[] getPipeSpecs()
    {
        return pipeSpecs;
    }

    protected void initializeSettings()
    {
        BoxBackupSettings settings = new BoxBackupSettings(this.getTid());
        logger.info("Initializing Settings...");

        //Doesn't really matter when the backup takes place, but we
        //want it to be random so all customers do not post-back
        //their data files concurrently.
        java.util.Random r = new java.util.Random();
        settings.setHourInDay(r.nextInt(24));
        settings.setMinuteInHour(r.nextInt(60));
        settings.setBackupURL(DEF_BACKUP_URL);

        setBoxBackupSettings(settings);
    }

    protected void postInit(String[] args)
    {
        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    Query q = s.createQuery("from BoxBackupSettings bbs where bbs.tid = :tid");
                    q.setParameter("tid", getTid());
                    BoxBackupImpl.this.settings = (BoxBackupSettings)q.uniqueResult();
                    return true;
                }

                public Object getResult() { return null; }
            };
        getTransformContext().runTransaction(tw);

    }

    protected void preStart() throws TransformStartException
    {
        eventLogger.start();

        try {
            reconfigure();
        } catch (Exception e) {
            throw new TransformStartException(e);
        }
    }

    /**
     * Implemented to start the cron job
     */
    protected void postStart() {
      Period p;
      if (null == settings) {
        p = new Period(6, 0, true);
      } else {
        int h = settings.getHourInDay();
        int m = settings.getMinuteInHour();
        p = new Period(h, m, true);
      }

      Runnable r = new Runnable()
          {
              public void run()
              {
                  doBackup();
              }
          };
      cronJob = MvvmContextFactory.context().makeCronJob(p, r);
      logger.info("Created (java)cron job for 24 hour box backup");
    }

    protected void preStop() {
      if(cronJob != null) {
        logger.info("Canceling box backup (java)cron job");
        cronJob.cancel();
        cronJob = null;
      }
    }


    /**
     * Callback from the cron job that it is time
     * to try a backup.
     */
    private void doBackup() {
      logger.debug("doBackup invoked");

      BoxBackupEvent event = null;

      try {
        SimpleExec.SimpleExecResult result = SimpleExec.exec(
          "mv-remotebackup.sh",
          new String[] {
            "-u",
            settings.getBackupURL(),
            "-v",
            "-k",
            MvvmContextFactory.context().getActivationKey(),
            "-t",
            Integer.toString(60*3)//Units in seconds - 3 minutes
          },
          null,
          null,
          true,
          true,
          (1000*60*4), //3 minutes plus some slop for tar operations
          logger,
          true);


        if(result.exitCode != 0) {
          logger.error("Backup returned non-zero error code (" +
            result.exitCode + ").  Stdout \"" +
            new String(result.stdOut) + "\".  Stderr \"" +
            new String(result.stdErr));
          String reason = "";
          switch(result.exitCode) {
            case 1:
              reason = "Error in arguments";
              break;
            case 2:
              reason = "Error from remote server " + settings.getBackupURL();
              break;
            case 3:
              reason = "Permission problem with remote server " + settings.getBackupURL();
              break;
            case 4:
              reason = "Unable to contact " + settings.getBackupURL();
              break;
            case 5:
              reason = "Timeout contacting " + settings.getBackupURL();
            default:
              reason = "Unknown error";
          }
          event = new BoxBackupEvent(false, reason);
        }
        else {
          event = new BoxBackupEvent(true, "Posted to " + settings.getBackupURL());
        }
      }
      catch(java.io.IOException ex) {
        logger.error("Exception attempting to perform backup", ex);
        event = new BoxBackupEvent(false, "Local error in processing");
      }
      eventLogger.log(event);
    }




    private void reconfigure() throws TransformException
    {
        BoxBackupSettings settings = getBoxBackupSettings();
        logger.info("Reconfigure()");

        if (settings == null) {
            throw new TransformException("Failed to get BoxBackup settings: " + settings);
        }

        handler.setSettings(settings);
    }

    public Object getSettings()
    {
        return getBoxBackupSettings();
    }

    public void setSettings(Object settings)
    {
        setBoxBackupSettings((BoxBackupSettings)settings);
    }
}

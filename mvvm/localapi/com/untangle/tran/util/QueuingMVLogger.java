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

package com.untangle.tran.util;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;


/**
 * Logger which acts to queue messages which would <b>not</b> be sent to the
 * log based on current configuration.  However, if a message is sent to the
 * log which is greater-than the logging threshold, all queued messages
 * are also sent.
 * <br>
 * To prevent unbounded growth (some DOS attack) there is an upper limit on
 * how many messages are kept.  Older messages are discarded on a FIFO basis.
 * <br>
 * Similar interface to the log4j logger, except the "throwable" stuff
 * has been relocated to the first argument (to accomidate varargs).
 */
public class QueuingMVLogger {

  private final Logger m_logger;
  private final CircularQueue<DeferredEntry> m_list;
  private String m_banner = null;
  
  /**
   * Construct a new logger, wrapping the provided
   * logger with the given max number of queued context
   *
   * @param logger the logger to wrap
   * @param maxSize the max size of the queued history.  Once
   *        overflowed, older messages are discarded.
   */
  public QueuingMVLogger(Logger logger, int maxSize) {
    m_logger = logger;
    m_list = new CircularQueue<DeferredEntry>(maxSize);
  }
  
  public QueuingMVLogger(Class category, int maxSize) {
    this(Logger.getLogger(category), maxSize);
  }

  /**
   * Force a message to always appear when dumping
   * queued messages.  This prevents the first message
   * from being ejected, should the max capacity of the
   * internal queue overflow.  Expected usage is something
   * like "session from 1.2.3.4 port 456 to 4.6.7.8 port 786".
   */
  public void setBanner(String msg) {
    m_banner = msg;
  }

  public void debug(Object...msg) {
    logOrDefer(null, msg, Level.DEBUG);
  }

  public void debug(Throwable t) {
    logOrDefer(t, null, Level.DEBUG);
  }
  
  public void debug(Throwable t, Object...msg) {
    logOrDefer(t, msg, Level.DEBUG);
  }
  
  public void info(Object...msg) {
    logOrDefer(null, msg, Level.INFO);
  }

  public void info(Throwable t) {
    logOrDefer(t, null, Level.INFO);
  }

  public void info(Throwable t, Object...msg) {
    logOrDefer(t, msg, Level.INFO);
  }
  
  
  public void warn(Object...msg) {
    logOrDefer(null, msg, Level.WARN);
  }

  public void warn(Throwable t) {
    logOrDefer(t, null, Level.WARN);
  }  

  public void warn(Throwable t, Object...msg) {
    logOrDefer(t, msg, Level.WARN);
  }
  
  public void error(Object...msg) {
    logOrDefer(null, msg, Level.ERROR);
  }

  public void error(Throwable t) {
    logOrDefer(t, null, Level.ERROR);
  }    

  public void error(Throwable t, Object...msg) {
    logOrDefer(t, msg, Level.ERROR);
  }  
  
  public void fatal(Object...msg) {
    logOrDefer(null, msg, Level.FATAL);
  }

  public void fatal(Throwable t) {
    logOrDefer(t, null, Level.FATAL);
  }  

  public void fatal(Throwable t, Object...msg) {
    logOrDefer(t, msg, Level.FATAL);
  }

  private void logOrDefer(Throwable t, Object[] msg, Level level) {
    if(m_logger.isEnabledFor(level)) {
      if(!m_list.isEmpty()) {
        m_logger.log(level, "--- BEGIN Deferred Entries ---");
        if(m_banner != null) {
          m_logger.log(level, m_banner);
        }
        for(DeferredEntry entry : m_list.popAll(new DeferredEntry[m_list.length()])) {
          entry.logYourself(level);
        }
        m_logger.log(level, "--- ENDOF Deferred Entries ---");
//        m_list.clear();
      }
      if(t == null) {
        m_logger.log(level, arrayToString(msg));
      }
      else {
        m_logger.log(level, arrayToString(msg), t);
      }
    }
    else {
      m_list.push(new DeferredEntry(t, msg, level));
    }
  }


  private String arrayToString(Object...msg) {
    StringBuilder sb = new StringBuilder();
    for(Object obj : msg) {
      if(obj == null) {
        sb.append("null");
      }
      else {
        sb.append(obj.toString());
      }
    }
    return sb.toString();
  }  


  private String levelToString(Level level) {
    if(level.equals(Level.DEBUG)) {
      return "DEBUG";
    }
    if(level.equals(Level.INFO)) {
      return "INFO";
    }
    if(level.equals(Level.WARN)) {
      return "WARN";
    }
    if(level.equals(Level.ERROR)) {
      return "ERROR";
    }
    if(level.equals(Level.FATAL)) {
      return "FATAL";
    }
    return "UNKNOWN";
  }



  private class DeferredEntry {

    private final Throwable m_t;
    private final Object[] m_msg;
    private final Level m_level;
    private final Date m_date;
  
    DeferredEntry(Throwable t, Object[] msg, Level level) {
      m_t = t;
      m_msg = msg;
      m_level = level;
      m_date = new Date();
      //Make sure we don't hold onto any object for too long a period.  Numbers
      //and Strings are "OK" to hold as references.
      for(int i = 0; i<msg.length; i++) {
        if(!((msg[i] instanceof Number) || (msg[i] instanceof String))) {
          msg[i] = msg[i].toString();
        }
      }
    }

    void logYourself(Level logAt) {
      StringBuilder sb = new StringBuilder();
      sb.append("([DEFERRED][").append(levelToString(m_level)).append("]");
      sb.append("[").append(new SimpleDateFormat("dd HH:mm:ss.SSS").format(m_date)).append("])");
      sb.append(arrayToString(m_msg));
      if(m_t == null) {
        m_logger.log(logAt, sb.toString());
      }
      else {
        m_logger.log(logAt, sb.toString(), m_t);
      }      
    }
    
  }

}
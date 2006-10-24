/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.cfg.Configuration;

/**
 * These are internal utility methods for internal use by the MVVM or
 * other top-level tools (reporting).
 *
 * @author <a href="mailto:amread@metavize.com">Aaron Read</a>
 * @version 1.0
 */
class Util
{
    private static final Logger logger = Logger.getLogger(Util.class);

    private static final Map<ClassLoader, WeakReference<SessionFactory>> sessionFactories
        = new WeakHashMap<ClassLoader, WeakReference<SessionFactory>>();

    static SessionFactory makeSessionFactory(ClassLoader cl)
    {
        SessionFactory sessionFactory = null;
        synchronized (sessionFactories) {
            WeakReference<SessionFactory> wr = sessionFactories.get(cl);
            if (null != wr) {
                sessionFactory = wr.get();
            }

            if (null == sessionFactory) {
                sessionFactory = createSessionFactory(cl);
                if (null != sessionFactory) {
                    sessionFactories.put(cl, new WeakReference(sessionFactory));
                }
            }
        }
        return sessionFactory;
    }

    static SessionFactory makeStandaloneSessionFactory(List<JarFile> jfs)
    {
        SessionFactory sessionFactory = null;

        try {
            AnnotationConfiguration cfg = new AnnotationConfiguration();
            addAnnotatedClasses(jfs, cfg);

            long t0 = System.currentTimeMillis();
            sessionFactory = cfg.buildSessionFactory();
            long t1 = System.currentTimeMillis();
            logger.info("session factory in " + (t1 - t0) + " millis");
        } catch (HibernateException exn) {
            logger.warn("could not create SessionFactory", exn);
        }

        return sessionFactory;
    }

    // private methods --------------------------------------------------------

    private static SessionFactory createSessionFactory(ClassLoader cl)
    {
        SessionFactory sessionFactory = null;

        try {
            AnnotationConfiguration cfg = new AnnotationConfiguration();
            addAnnotatedClasses(cl, cfg);

            long t0 = System.currentTimeMillis();
            sessionFactory = cfg.buildSessionFactory();
            long t1 = System.currentTimeMillis();
            logger.info("session factory in " + (t1 - t0) + " millis");
        } catch (HibernateException exn) {
            logger.warn("could not create SessionFactory", exn);
        }

        return sessionFactory;
    }

    private static void addAnnotatedClasses(ClassLoader cl,
                                            AnnotationConfiguration cfg)
    {
        Enumeration<URL> e = null;

        try {
            e = cl.getResources("META-INF/annotated-classes");
        } catch (IOException exn) {
            logger.warn("could not load annotated-classes", exn);
        }

        while (null != e && e.hasMoreElements()) {
            Thread t = Thread.currentThread();
            ClassLoader oldCl = t.getContextClassLoader();
            try {
                t.setContextClassLoader(cl);
                URL url = e.nextElement();
                InputStream is = url.openStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                while (null != (line = br.readLine())) {
                    try {
                        Class c = cl.loadClass(line);
                        cfg.addAnnotatedClass(c);
                    } catch (ClassNotFoundException exn) {
                        logger.warn("skipping unknown class: " + line, exn);
                    }
                }
            } catch (IOException exn) {
                logger.warn("could not read annotated-classes", exn);
            } finally {
                t.setContextClassLoader(oldCl);
            }
        }
    }

    private static void addAnnotatedClasses(List<JarFile> jfs,
                                            AnnotationConfiguration cfg)
    {
        List<URL> urls = new ArrayList<URL>(jfs.size());

        for (JarFile jf : jfs) {
            String urlStr = "file://" + jf.getName();
            try {
                urls.add(new URL(urlStr));
            } catch (MalformedURLException exn) {
                logger.warn("skipping bad url: " + urlStr, exn);
            }
        }

        ClassLoader cl = new URLClassLoader(urls.toArray(new URL[urls.size()]));

        addAnnotatedClasses(cl, cfg);
    }
}

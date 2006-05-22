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

package com.metavize.mvvm.engine;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.log4j.Logger;


public class Main
{
    private static String MVVM_LOCAL_CONTEXT_CLASSNAME
        = "com.metavize.mvvm.engine.MvvmContextImpl";

    static {
        MvvmRepositorySelector.get().init("");
    }

    private final Logger logger = Logger.getLogger(getClass());

    private URLClassLoader ucl;
    private Class mvvmPrivClass;
    private MvvmContextBase mvvmContext;

    private String bunniculaConf;
    private String bunniculaData;
    private String bunniculaHome;
    private String bunniculaLib;
    private String bunniculaLog;
    private String bunniculaToolbox;
    private String bunniculaWeb;
    private String bunniculaTmp;

    // constructor ------------------------------------------------------------

    private Main() { }

    // public static methods --------------------------------------------------

    // XXX get rid of all these throws
    public static final void main(String[] args) throws Exception
    {
        SchemaUtil.initSchema("settings", "mvvm");

        new Main().init();
    }

    /**
     * <code>fatalError</code> can be called by any part of the mvvm
     * (even by transforms for now, change later XXX) to indicate that
     * a fatal error has occured and that the MVVM *must* restart (or
     * otherwise recover) itself.  One example is an OutOfMemory
     * error.
     *
     * @param x a <code>Throwable</code> giving the related/causing
     * exception, if any, otherwise null
     */
    public static void fatalError(String throwingLocation, Throwable x)
    {
        try {
            System.err.println("Fatal Error in MVVM in " + throwingLocation);
            if (x != null) {
                System.err.println("Throwable: " + x.getMessage());
                x.printStackTrace(System.err);
            }
        } catch (Throwable y) {
            // We want to always call System.exit(), so we do
            // absolutely nothing here.
        } finally {
            System.exit(-1);
        }
    }

    // private methods --------------------------------------------------------

    // XXX get rid of all these throws
    private void init() throws Exception
    {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run() { destroy(); }
            }));

        ClassLoader cl = getClass().getClassLoader();
        Thread.currentThread().setContextClassLoader(cl);

        logger.info("setting up properties");
        setProperties();
        logger.info("starting mvvm");
        try {
            startMvvm();
        } catch (Throwable exn) {
            fatalError("could not start mvvm", exn);
        }
        System.out.println("MVVM startup complete: \"Today vegetables...tomorrow the world!\"");
        logger.info("restarting transforms and socket invoker");
        restartTransfoms();
        System.out.println("MVVM postInit complete");
    }

    private void destroy()
    {
        mvvmContext.doDestroy();
        try {
            DataSourceFactory.factory().destroy();
        } catch (SQLException exn) {
            logger.warn("could not destory DataSourceFactory", exn);
        }
        System.out.println("MVVM shutdown complete.");
    }

    private void setProperties() throws Exception
    {
        bunniculaHome = System.getProperty("bunnicula.home");

        bunniculaLib = bunniculaHome + "/lib";
        System.setProperty("bunnicula.lib.dir", bunniculaLib);
        bunniculaToolbox = bunniculaHome + "/toolbox";
        System.setProperty("bunnicula.toolbox.dir", bunniculaToolbox);
        bunniculaLog = bunniculaHome + "/log";
        System.setProperty("bunnicula.log.dir", bunniculaLog);
        bunniculaData = bunniculaHome + "/data";
        System.setProperty("bunnicula.data.dir", bunniculaData);
        bunniculaWeb = bunniculaHome + "/web";
        System.setProperty("bunnicula.web.dir", bunniculaWeb);
        bunniculaConf = bunniculaHome + "/conf";
        System.setProperty("bunnicula.conf.dir", bunniculaConf);
        bunniculaTmp = bunniculaHome + "/tmp";
        System.setProperty("bunnicula.tmp.dir", bunniculaTmp);

        System.setProperty("derby.system.home", bunniculaHome + "/db");

        logger.info("bunnicula.home        " + bunniculaHome);
        logger.info("bunnicula.lib.dir     " + bunniculaLib);
        logger.info("bunnicula.toolbox.dir " + bunniculaToolbox);
        logger.info("bunnicula.log.dir     " + bunniculaLog);
        logger.info("bunnicula.data.dir    " + bunniculaData);
        logger.info("bunnicula.web.dir     " + bunniculaWeb);
        logger.info("bunnicula.conf.dir    " + bunniculaConf);
        logger.info("bunnicula.tmp.dir    " + bunniculaTmp);

        File f = new File(bunniculaConf + "/mvvm.properties");
        if (f.exists()) {
            logger.info("Loading " + f);
            System.getProperties().load(new FileInputStream(f));
        } else {
            logger.warn("Could not find " + f);
        }

        f = new File(bunniculaConf + "/mvvm.networking.properties");
        Properties networkingProperties = new Properties();

        if (f.exists()) {
            logger.info("Loading " + f);
            networkingProperties.load(new FileInputStream(f));
        } /* This file may not exist */
    }

    // private methods --------------------------------------------------------

    // XXX get rid of all these throws
    private void startMvvm() throws Exception
    {
        URL mvvmJar = new URL("file://" + bunniculaLib + "/mvvm.jar");
        URL jVectorJar = new URL("file://" + bunniculaLib + "/jvector.jar");
        URL jNetcapJar = new URL("file://" + bunniculaLib + "/jnetcap.jar");
        URL[] urls = new URL[] { mvvmJar, jVectorJar, jNetcapJar };
        ucl = new URLClassLoader(urls, getClass().getClassLoader());

        ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        try {
            // Entering MVVM ClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            Thread.currentThread().setContextClassLoader(ucl);

            MvvmRepositorySelector.get().init("mvvm");

            mvvmContext = (MvvmContextBase)ucl
                .loadClass(MVVM_LOCAL_CONTEXT_CLASSNAME)
                .getMethod("context").invoke(null);

            mvvmContext.doInit(this);
        } finally {
            Thread.currentThread().setContextClassLoader(oldCl);
            // restored classloader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        }
    }

    private void restartTransfoms() throws Exception
    {
        ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        try {
            // Entering MVVM ClassLoader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            Thread.currentThread().setContextClassLoader(ucl);

            mvvmContext.doPostInit();

        } finally {
            Thread.currentThread().setContextClassLoader(oldCl);
            // restored classloader ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        }
    }
}



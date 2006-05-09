/*
 * Copyright (c) 2003, 2004, 2005, 2006 Metavize Inc.
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
import java.util.Properties;

import com.metavize.mvvm.AppServerManager;
import com.metavize.mvvm.networking.RemoteSettingsListener;
import com.metavize.mvvm.networking.internal.RemoteInternalSettings;
import com.metavize.mvvm.security.CertInfo;
import com.metavize.mvvm.security.RFC2253Name;
import com.metavize.mvvm.security.RegistrationInfo;
import com.metavize.tran.util.MVKeyStore;
import com.metavize.tran.util.OpenSSLWrapper;
import org.apache.log4j.Logger;

/**
 * TODO A work in progress (currently a disorganized mess of crap taken
 * from the old "main" and "TomcatManager" code.
 */
class AppServerManagerImpl implements AppServerManager
{
    private static final String KS_STORE_PASS = "changeit";

    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    private final int externalHttpsPort;

    private final Logger logger = Logger.getLogger(getClass());

    private final MvvmContextImpl mctx;
    private final TomcatManager tomcatManager;

    private MVKeyStore keyStore;

    AppServerManagerImpl(MvvmContextImpl mctx)
    {
        this.mctx = mctx;
        //TODO Clean up stuff ported from "main"
        this.tomcatManager = mctx.getTomcatManager();

        Properties networkingProperties = new Properties();

        File f = new File(System.getProperty("bunnicula.conf.dir")
                          + "/mvvm.networking.properties");
        if (f.exists()) {
            FileInputStream fis = null;
            try {
                logger.info("Loading mvvm.networking.properties from " + f);
                fis = new FileInputStream(f);
                networkingProperties.load(fis);
            } catch (Exception exn) {
                logger.error("", exn);
            } finally {
                if (null != fis) {
                    try {
                        fis.close();
                    } catch (Exception exn) {
                        logger.warn("could not close file", exn);
                    }
                }
            }
        }

        int p = DEFAULT_HTTPS_PORT;

        String t = networkingProperties.getProperty("mvvm.https.port");
        if (null != t) {
            try {
                p = Integer.parseInt(t);
            } catch (NumberFormatException exn) {
                logger.warn( "Invalid port: " + t);
                p = DEFAULT_HTTPS_PORT;
            }
        } else {
            p = DEFAULT_HTTPS_PORT;
        }

        /* Illegal range */
        if (p <= 0 || p >= 0xFFFF || p == 80 ) {
            p = DEFAULT_HTTPS_PORT;
        }

        this.externalHttpsPort = p;
    }

    public void postInit(InvokerBase invokerBase)
    {
        String eHost = getFQDN();

        try {
            keyStore = MVKeyStore.open(System.getProperty("bunnicula.conf.dir")
                                       + "/keystore", KS_STORE_PASS, true);
        } catch (Exception ex) {
            logger.error("Exception opening KeyStore", ex);
        }

        // Check for the old key system (i.e. "tomcat" being the name
        // of the key).  If so, simply generate a new key based on
        // whatever is the current host name.
        try {
            if (!(keyStore.containsAlias(eHost))) {
                logger.debug("Adding key for effective hostname \""
                             + eHost + "\"");
                String OU = "mv-customer-" + System.currentTimeMillis();
                RegistrationInfo ri = mctx.adminManager().getRegistrationInfo();
                if (null != ri && null != ri.getCompanyName()) {
                    OU = ri.getCompanyName();
                }

                RFC2253Name dn = RFC2253Name.create();
                dn.add("OU", OU);

                regenCert(dn, 365 * 5 + 1);
            }
        } catch (Exception ex) {
            logger.error("could not update keystore", ex);
        }

        try {
            tomcatManager.setSecurityInfo("conf/keystore", KS_STORE_PASS,
                                          eHost);
        } catch (Exception ex) {
            logger.error("Exception passing cert parameters to Tomcat", ex);
        }

        try {
            String disableTomcat = System.getProperty("bunnicula.devel.notomcat");
            if (null == disableTomcat || !Boolean.valueOf(disableTomcat)) {
                tomcatManager.startTomcat(invokerBase, DEFAULT_HTTP_PORT,
                                          DEFAULT_HTTPS_PORT,
                                          externalHttpsPort);
            }
        } catch (Exception exn) {
            logger.warn("could not start Tomcat", exn);
        }

        mctx.networkManager().registerListener(new RemoteSettingsListener() {
                public void event(RemoteInternalSettings settings)
                {
                    String existingAlias = tomcatManager.getKeyAlias();
                    String currentHostname = mctx.networkManager()
                        .getHostname().toString();
                    if (null == existingAlias
                        || !(existingAlias.equals(currentHostname))) {
                        hostnameChanged(currentHostname);
                    }
                }
            });
    }

    public void rebindExternalHttpsPort(int port) throws Exception
    {
        tomcatManager.rebindExternalHttpsPort(port);
    }

    public boolean loadPortalApp(String urlBase, String rootDir)
    {
        return tomcatManager.loadPortalApp(urlBase, rootDir);
    }

    public boolean loadSystemApp(String urlBase, String rootDir)
    {
        return tomcatManager.loadSystemApp(urlBase, rootDir);
    }

    public boolean loadInsecureApp(String urlBase, String rootDir)
    {
        return tomcatManager.loadInsecureApp(urlBase, rootDir);
    }


    public boolean unloadWebApp(String contextRoot)
    {
        return tomcatManager.unloadWebApp(contextRoot);
    }


    //TODO bscott Sometime in the next two years we need a way for them
    //     to roll to a new key while maintaing their existing signed cert.

    public boolean regenCert(RFC2253Name dn, int durationInDays)
    {
        String eHost = getFQDN();
        String newName = "key" + Thread.currentThread().hashCode()
            + "_" + System.currentTimeMillis() + "_"
            + new java.util.Random().nextInt();

        try {
            int index = dn.indexOf("CN");
            if (index == -1) {
                index = 0;
            } else {
                dn.remove(index);
            }

            dn.add("CN", eHost);

            keyStore.generateKey(newName, dn, durationInDays);
            keyStore.renameEntry(newName, eHost);

            tomcatManager.setSecurityInfo("conf/keystore", KS_STORE_PASS,
                                          eHost);
            return true;
        } catch (Exception ex) {
            logger.error("Unable to regen cert", ex);
            return false;
        }
    }

    public boolean importServerCert(byte[] cert, byte[] caCert)
    {
        CertInfo localCertInfo = null;

        try {
            localCertInfo = OpenSSLWrapper.getCertInfo(cert);
        } catch (Exception ex) {
            logger.error("Unable to get info from cert", ex);
        }

        // This is a hack, but if they don't have a CN what the heck
        // are they doing
        String cn = localCertInfo.getSubjectCN();
        if (null == cn) {
            logger.error("Received a cert without a CN? \"" +
                         new String(cert) + "\"");
        }

        String reason = "";
        try {
            if (null != caCert) {
                reason = "Unable to import CA cert \"" + new String(caCert)
                    + "\"";
                keyStore.importCert(caCert, cn + "-ca");
            }
            reason = "Unable to CA cert \"" + new String(cert) + "\"";
            keyStore.importCert(cert, cn);
            tomcatManager.setSecurityInfo("conf/keystore", KS_STORE_PASS,
                                          getFQDN());
        } catch (Exception ex) {
            logger.error(reason, ex);
            return false;
        }

        return true;

    }

    public byte[] getCurrentServerCert()
    {
        String eHost = getFQDN();

        try {
            if (!keyStore.containsAlias(eHost)) {
                hostnameChanged(eHost);
            }
        } catch (Exception ex) {
            logger.error("Unable to list key store", ex);
        }

        try {
            return keyStore.exportEntry(eHost);
        } catch (Exception ex) {
            logger.error("Unable to retreive current cert", ex);
            return null;
        }
    }

    public byte[] generateCSR()
    {
        String eHost = getFQDN();

        try {
            return keyStore.createCSR(eHost);
        } catch (Exception ex) {
            logger.error("Exception generating a CSR", ex);
            return null;
        }
    }

    public CertInfo getCertInfo(byte[] certBytes)
    {
        try {
            return OpenSSLWrapper.getCertInfo(certBytes);
        } catch (Exception ex) {
            logger.error("Unable to get info from cert \"" +
                         new String(certBytes) + "\"", ex);
            return null;
        }
    }

    /**
     * Callback indicating that the hostname has changed
     */
    private void hostnameChanged(String newHostName)
    {
        String reason = "";
        try {
            if (keyStore.containsAlias(newHostName)
                && keyStore.getEntryType(newHostName) == MVKeyStore.MVKSEntryType.PrivKey) {
                reason = "Unable to rebind tomcat to existing key with alias \""
                    + newHostName + "\"";
                tomcatManager.setSecurityInfo("conf/keystore", KS_STORE_PASS,
                                              newHostName);
            } else {
                reason = "Unable to get current cert alias";
                String currentCertName = tomcatManager.getKeyAlias();
                reason = "Unable to export current cert";
                byte[] oldCert = keyStore.exportEntry(currentCertName);
                reason = "Unable to get current cert info";
                CertInfo ci = OpenSSLWrapper.getCertInfo(keyStore.exportEntry(currentCertName));
                RFC2253Name oldDN = ci.subjectDN;
                regenCert(oldDN, 365 * 5 +1);
            }
        } catch (Exception ex) {
            logger.error(reason, ex);
        }
    }

    private String getFQDN()
    {
        return mctx.networkManager().getHostname().toString();
    }
}

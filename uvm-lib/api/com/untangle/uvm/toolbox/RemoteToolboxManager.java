/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc.
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
 */

package com.untangle.uvm.toolbox;

import java.net.URL;
import java.util.List;

import com.untangle.uvm.alerts.MessageQueue;
import com.untangle.uvm.node.DeployException;

/**
 * Manager for the Toolbox, which holds Mackages. A Mackage is all
 * data concerning a Node that is not related to any particular
 * node instance.
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
public interface RemoteToolboxManager
{
    /**
     * All known mackages.
     *
     * @return an array of <code>MackageDesc</code>s.
     */
    MackageDesc[] available();

    /**
     * All installed mackages.
     *
     * @return a <code>MackageDesc[]</code> value
     */
    MackageDesc[] installed();

    /**
     * Tests if a package is installed.
     *
     * @param name of the package.
     * @return true if the package is installed.
     */
    boolean isInstalled(String name);

    /**
     * All installed mackages, which are visible within the GUI
     *
     * @return a <code>MackageDesc[]</code> value
     */
    MackageDesc[] installedVisible();

    /**
     * Mackages available but not installed.
     *
     * @return a <code>MackageDesc[]</code> value
     */
    MackageDesc[] uninstalled();

    /**
     * Mackages installed but not up to date.
     *
     * @return a <code>MackageDesc[]</code> value
     */
    MackageDesc[] upgradable();

    /**
     * Mackages installed with latest version.
     *
     * @return a <code>MackageDesc[]</code> value
     */
    MackageDesc[] upToDate();

    /**
     * Get the MackageDesc for a node.
     *
     * @param name the name of the node.
     * @return the MackageDesc.
     */
    MackageDesc mackageDesc(String name);

    /**
     * Returns install or upgrade events. When install or upgrade is
     * called, a key is returned that allows the client to get a list
     * of events since the client last called getProgress(). The end
     * of events is signaled by an element of type
     * <code>InstallComplete</code>. A call to this method after such
     * an element is returned will result in a
     * <code>RuntimeException</code>.
     *
     * @param key returned from the install or upgrade method.
     * @return list of events since the last call to getProgress().
     */
    List<InstallProgress> getProgress(long key);

    /**
     * Install a Mackage in the Toolbox.
     *
     * @param name the name of the Mackage.
     * @exception MackageInstallException when <code>name</code> cannot
     *     be installed.
     */
    long install(String name) throws MackageInstallException;

    /**
     * Install a Mackage in the Toolbox, returning only after it is
     * completely installed..
     *
     * @param name the name of the Mackage.
     * @exception MackageInstallException when <code>name</code> cannot
     *     be installed.
     */
    void installSynchronously(String name) throws MackageInstallException;

    /**
     * Remove a Mackage from the toolbox.
     *
     * @param name the name of the Mackage.
     * @exception MackageUninstallException when <code>name</code> cannot
     *    be uninstalled.
     */
    void uninstall(String name) throws MackageUninstallException;

    void update(long millis) throws MackageException;

    void update() throws MackageException;

    long upgrade() throws MackageException;

    void enable(String mackageName) throws MackageException;

    void disable(String mackageName) throws MackageException;

    void extraName(String mackageName, String extraName);

    void requestInstall(String mackageName);

    MessageQueue<ToolboxMessage> subscribe();

    /**
     * Register the deployment of a Mackage at a particular URL.
     *
     * @param url location of the Mackage.
     * @throws DeployException if deployment fails.
     */
    void register(String name) throws MackageInstallException;

    /**
     * Register the deployment of a Mackage at a particular URL.
     *
     * @param url location of the Mackage.
     * @throws DeployException if deployment fails.
     */
    void unregister(String mackageName) throws MackageInstallException;

    void setUpgradeSettings(UpgradeSettings u);

    UpgradeSettings getUpgradeSettings();

    List<String> getWebstartResources();

    boolean hasPremiumSubscription();

    /**
     * Get the host that is serving the library.
     */
    String getLibraryHost();
}

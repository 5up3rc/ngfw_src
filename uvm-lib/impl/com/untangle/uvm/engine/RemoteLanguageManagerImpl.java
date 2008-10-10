/*
 * $HeadURL: svn://chef/branch/prod/web-ui/work/src/uvm-lib/impl/com/untangle/uvm/engine/RemoteLanguageManagerImpl.java $
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.uvm.engine;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.untangle.uvm.LocaleInfo;
import com.untangle.uvm.LanguageSettings;
import com.untangle.uvm.RemoteLanguageManager;
import com.untangle.uvm.UvmException;
import com.untangle.uvm.util.DeletingDataSaver;
import com.untangle.uvm.util.TransactionWork;
import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.commons.fileupload.FileItem;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * Implementation of RemoteLanguageManagerImpl.
 *
 * @author <a href="mailto:cmatei@untangle.com">Catalin Matei</a>
 * @version 1.0
 */
class RemoteLanguageManagerImpl implements RemoteLanguageManager
{
    private static final String LANGUAGES_DIR;
    private static final String LANGUAGES_COMMUNITY_DIR;
    private static final String LANGUAGES_OFFICIAL_DIR;
    private static final String LOCALE_DIR;
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String BASENAME_COMMUNITY_PREFIX = "i18n.community";
    private static final String BASENAME_OFFICIAL_PREFIX = "i18n.official";
    private static final String LANGUAGES_CFG = "lang.cfg";
    private static final String COUNTRIES_CFG = "country.cfg";
    private static final String LC_MESSAGES = "LC_MESSAGES";
    private static final int BUFFER = 2048;

    private final Logger logger = Logger.getLogger(getClass());

    private final UvmContextImpl uvmContext;
    private LanguageSettings settings;
    private Map<String, String> allLanguages;
    private Map<String, String> allCountries;

    static {
        LANGUAGES_DIR = System.getProperty("bunnicula.lang.dir"); // place for languages resources files
        LANGUAGES_COMMUNITY_DIR = LANGUAGES_DIR + File.separator + "community"; // place for community languages resources files
        LANGUAGES_OFFICIAL_DIR = LANGUAGES_DIR + File.separator + "official"; // place for official languages resources files
        LOCALE_DIR = "/usr/share/locale"; // place for .mo files
    }

    RemoteLanguageManagerImpl(UvmContextImpl uvmContext) {
        this.uvmContext = uvmContext;

        TransactionWork tw = new TransactionWork()
        {
            public boolean doWork(Session s)
            {
                Query q = s.createQuery("from LanguageSettings");
                settings = (LanguageSettings)q.uniqueResult();

                if (null == settings) {
                    settings = new LanguageSettings();
                    settings.setLanguage(DEFAULT_LANGUAGE);
                    s.save(settings);
                }

                return true;
            }
        };
        uvmContext.runTransaction(tw);
        
        allLanguages = loadAllLanguages();
        allCountries = loadAllCountries();
        
    }

    // public methods ---------------------------------------------------------

    public LanguageSettings getLanguageSettings() {
        return settings;
    }

    public void setLanguageSettings(LanguageSettings settings) {
        /* delete whatever is in the db, and just make a fresh
         * settings object */
        LanguageSettings copy = new LanguageSettings();
        settings.copy(copy);
        saveSettings(copy);
        this.settings = copy;
    }

    public boolean uploadLanguagePack(FileItem item) throws UvmException {
        boolean success = true;
        try {
            BufferedOutputStream dest = null;
            ZipEntry entry = null;

            // validate language pack
            if (!item.getName().endsWith(".zip")) {
                throw new UvmException("Invalid Language Pack");
            }

            // Open the ZIP file
            InputStream uploadedStream = item.getInputStream();
            ZipInputStream zis = new ZipInputStream(uploadedStream);
            while ((entry = zis.getNextEntry()) != null) {
                if (!isValid(entry)){
                    success = false;
                    continue;
                }
                if (entry.isDirectory()) {
                    File dir = new File(LANGUAGES_COMMUNITY_DIR + File.separator + entry.getName());
                    if (!dir.exists()) {
                        dir.mkdir();
                    }
                } else {
                    File file = new File(LANGUAGES_COMMUNITY_DIR + File.separator + entry.getName());
                    File parentDir = file.getParentFile();
                    if (parentDir!=null && !parentDir.exists()) {
                        parentDir.mkdir();
                    }

                    // write the files to the disk
                    int count;
                    byte data[] = new byte[BUFFER];
                    FileOutputStream fos = new FileOutputStream(file);
                    dest = new BufferedOutputStream(fos, BUFFER);
                    while ((count = zis.read(data, 0, BUFFER)) != -1) {
                        dest.write(data, 0, count);
                    }
                    dest.flush();
                    dest.close();

                    // compile to .mo file and install it in the
                    // appropriate place (LOCALE_DIR)
                    success =  compileMoFile(entry) && success;

                    // compile the java properties version & install
                    // it in the classpath (LANGUAGES_DIR)
                    success = compileResourceBundle(entry) && success;
                }
            }
            zis.close();
            uploadedStream.close();

        } catch (IOException e) {
            logger.error("upload failed", e);
            throw new UvmException("Upload Language Pack Failed");
        }
        return success;
    }
    
    /*
     * Check if a language pack entry conform to the correct naming: <lang_code>/<module_name>.po 
     */
    private boolean isValid(ZipEntry entry) {
        String tokens[] = entry.getName().split(File.separator);
        if (entry.isDirectory()) {
            // in order to be a valid entry, the folder name should be a valid language code
            if (tokens.length != 1 || !isValidLocaleCode(tokens[0])) {
                logger.warn("The folder " + entry.getName() + " does not correspond to a valid language code");
                return false;
            }
        } else {
            // in order to be a valid entry, it should be in the following format: <lang_code>/<package_name>.po
            if (tokens.length != 2 || !isValidLocaleCode(tokens[0]) || !entry.getName().endsWith(".po")) {
                logger.warn("The entry " + entry.getName() + " does not conform to the correct naming: <lang_code>/<module_name>.po ");
                return false;
            }
        }
        return true;
    }

    private boolean compileResourceBundle(ZipEntry entry) {
        boolean success = true;
        try {
            String tokens[] = entry.getName().split(File.separator);
            String lang = tokens[0];
            String moduleName = tokens[1].substring(0, tokens[1].lastIndexOf(".")).replaceAll("-", "_");

            String cmd[] = { "msgfmt", "--java2",
                    "-d", LANGUAGES_DIR,
                    "-r", BASENAME_COMMUNITY_PREFIX + "." + moduleName,
                    "-l", lang,
                    LANGUAGES_COMMUNITY_DIR + File.separator + entry.getName()};
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            if (p.exitValue() != 0) {
                success = false;
                logProcessError(p, "Error compiling to resource bundle");                
            }

        } catch (Exception e) {
            success = false;
            logger.error("Error compiling to resource bundle", e);
        }
        return success;
    }

    private boolean compileMoFile(ZipEntry entry) {
        boolean success = true;
        try {
            String tokens[] = entry.getName().split(File.separator);
            String lang = tokens[0];
            String moduleName = tokens[1].substring(0, tokens[1].lastIndexOf("."));

            String cmd[] = { "msgfmt",
                    "-o", LOCALE_DIR + File.separator + lang + File.separator + LC_MESSAGES + File.separator + moduleName  + ".mo",
                    LANGUAGES_COMMUNITY_DIR + File.separator + entry.getName()};
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            if (p.exitValue() != 0) {
                success = false;
                logProcessError(p, "Error compiling to mo file");                
            }

        } catch (Exception e) {
            success = false;
            logger.error("Error compiling to mo file", e);
        }
        return success;
    }
    
    private void logProcessError(Process p, String errorMsg) throws IOException {
        InputStream stderr = p.getErrorStream ();
        BufferedReader br = new BufferedReader(new InputStreamReader(stderr));
        String line = null;
        StringBuffer errorBuffer = new StringBuffer(errorMsg);
        while ((line = br.readLine()) != null) {
            errorBuffer.append("\n");
            errorBuffer.append(line);
        }
        br.close();
        logger.error(errorBuffer);
    }

    public List<LocaleInfo> getLanguagesList() {
        List<LocaleInfo> locales = new ArrayList<LocaleInfo>();

        Set<String> availableLanguages = new HashSet<String>();
        // add default language
        availableLanguages.add(DEFAULT_LANGUAGE);
        // get available official languages
        Collections.addAll(availableLanguages, (new File(LANGUAGES_OFFICIAL_DIR)).list());
        // get available community languages
        Collections.addAll(availableLanguages, (new File(LANGUAGES_COMMUNITY_DIR)).list());

        // From all languages from config file, keep only the
        // one which we have translations for
        for (String code : availableLanguages) {
            String tokens[] = code.split("_");
            String langCode = tokens[0];
            String langName = allLanguages.get(langCode);
            String countryCode = tokens.length == 2 ? tokens[1] : null;
            String countryName = countryCode == null ? null : allCountries.get(countryCode);
            locales.add(new LocaleInfo(langCode, langName, countryCode, countryName));
        }
        
        return locales;
    }

    public Map<String, String> getTranslations(String module){
        Map<String, String> map = new HashMap<String, String>();
        String i18nModule = module.replaceAll("-", "_");
        Locale locale = getLocale(); 
        
        try {
            I18n i18n = null;
            ResourceBundle.clearCache(Thread.currentThread().getContextClassLoader());
            try {
                i18n = I18nFactory.getI18n(BASENAME_COMMUNITY_PREFIX+"."+i18nModule, i18nModule, Thread
                        .currentThread().getContextClassLoader(), locale, I18nFactory.DEFAULT);
            } catch (MissingResourceException e) {
                // fall back to official translations
                i18n = I18nFactory.getI18n(BASENAME_OFFICIAL_PREFIX+"."+i18nModule, i18nModule, Thread
                        .currentThread().getContextClassLoader(), locale, I18nFactory.DEFAULT);
            }

            if (i18n != null) {
                for (Enumeration<String> enumeration = i18n.getResources().getKeys(); enumeration
                        .hasMoreElements();) {
                    String key = enumeration.nextElement();
                    map.put(key, i18n.tr(key));
                }
            }
        } catch (MissingResourceException e) {
            // Do nothing - Fall back to a default that returns the passed text if no resource bundle can be located
            // is done in client side
        }

        return map;
    }


    // private methods --------------------------------------------------------
    private Locale getLocale() {
        Locale locale = new Locale(DEFAULT_LANGUAGE);
        String tokens[] = settings.getLanguage().split("_");
        if (tokens.length == 1) {
            locale = new Locale(tokens[0]);
        } else if (tokens.length == 2) {
            locale = new Locale(tokens[0], tokens[1]);
        }
        return locale;
    }
    
    private void saveSettings(LanguageSettings settings) {
        DeletingDataSaver<LanguageSettings> saver =
            new DeletingDataSaver<LanguageSettings>(uvmContext,"LanguageSettings");
        this.settings = saver.saveData(settings);
    }
    
    private Map<String, String> loadAllLanguages() {
        Map<String, String> languages = new HashMap<String, String>();

        // Reading all languages from config file
        try {
            BufferedReader in = new BufferedReader(new FileReader(LANGUAGES_DIR + File.separator + LANGUAGES_CFG));
            String s = new String();
            while((s = in.readLine())!= null) {
                if (s.trim().length() > 0){
                    String[] tokens = s.split("\\s+");
                    if (tokens.length >= 2) {
                        String langCode = tokens[0];
                        String langName = s.replaceFirst(langCode, "").trim();
                        languages.put(langCode, langName);
                    }
                }
            }
            in.close();
        } catch (IOException e) {
            logger.warn("Failed getting all languages!", e);
        }

        return languages;
    }
    
    private Map<String, String> loadAllCountries() {
        Map<String, String> countries = new HashMap<String, String>();

        // Reading all countries from config file
        try {
            BufferedReader in = new BufferedReader(new FileReader(LANGUAGES_DIR + File.separator + COUNTRIES_CFG));
            String s = new String();
            while((s = in.readLine())!= null) {
                if (s.trim().length() > 0){
                    String[] tokens = s.split("\\s+");
                    if (tokens.length >= 2) {
                        String countryCode = tokens[0];
                        String countryName = s.replaceFirst(countryCode, "").trim();;
                        countries.put(countryCode, countryName);
                    }
                }
            }
            in.close();
        } catch (IOException e) {
            logger.warn("Failed getting all countries!", e);
        }

        return countries;
    }
    
    private boolean isValidLocaleCode(String code) {
        if (code == null) {
            return false;
        }
        String tokens[] = code.split("_");
        if (tokens.length == 0 || tokens.length > 2) {
            return false;
        }
        
        String langCode = tokens[0];
        String countryCode = tokens.length == 2 ? tokens[1] : null;
        return isValidLanguageCode(langCode) 
            && (countryCode == null || isValidCountryCode(countryCode));
    }
    
    private boolean isValidLanguageCode(String code) {
        return allLanguages.containsKey(code);
    }
    
    private boolean isValidCountryCode(String code) {
        return allCountries.containsKey(code);
    }
    
}

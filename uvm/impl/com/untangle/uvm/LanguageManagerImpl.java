/**
 * $Id$
 */
package com.untangle.uvm;

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
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Iterator;

import org.apache.commons.fileupload.FileItem;
import org.apache.log4j.Logger;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import com.untangle.uvm.LanguageSettings;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.LocaleInfo;
import com.untangle.uvm.LanguageManager;
import com.untangle.uvm.UvmException;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.UvmContext;
import com.untangle.uvm.node.Node;
import com.untangle.uvm.node.NodeProperties;
import com.untangle.uvm.node.NodeManager;
import com.untangle.uvm.servlet.UploadHandler;
import com.untangle.uvm.SettingsManager;

/**
 * Implementation of LanguageManagerImpl.
 */
public class LanguageManagerImpl implements LanguageManager
{
    private static final String LANGUAGES_DIR;
    private static final String LANGUAGES_COMMUNITY_DIR;
    private static final String LANGUAGES_OFFICIAL_DIR;
    private static final String LOCALE_DIR;
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String BASENAME_COMMUNITY_PREFIX = "i18n.community";
    private static final String BASENAME_OFFICIAL_PREFIX = "i18n.official";
    private static final String LANGUAGES_CFG = "lang.cfg";
    private static final String BLACKLIST_CFG = "blacklist.cfg";
    private static final String COUNTRIES_CFG = "country.cfg";
    private static final String LC_MESSAGES = "LC_MESSAGES";
    private static final int BUFFER = 2048;

    private static final int CLEANER_SLEEP_TIME_MILLI = 60 * 1000; /* Check every minute */
    private static final int CLEANER_LAST_ACCESS_MAX_TIME = 5 * 60 * 1000; /* Expire if unused for 5 minutes */
    // private static final int CLEANER_SLEEP_TIME_MILLI = 10 * 1000; /* Check every minute */
    // private static final int CLEANER_LAST_ACCESS_MAX_TIME = 20 * 1000; /* Expire if unused for 5 minutes */

    private final Logger logger = Logger.getLogger(getClass());

    private LanguageSettings languageSettings;
    private Map<String, String> allLanguages;
    private ArrayList<String> blacklist;
    private Map<String, String> allCountries;

    private final Map<String, Map<String, String>> translations;
    private final Map<String, Long> translationsLastAccessed;

    private volatile Thread cleanerThread;
    private translationsCleaner cleaner = new translationsCleaner();

    static {
        LANGUAGES_DIR = System.getProperty("uvm.lang.dir"); // place for languages resources files
        LANGUAGES_COMMUNITY_DIR = LANGUAGES_DIR + File.separator + "community"; // place for community languages resources files
        LANGUAGES_OFFICIAL_DIR = LANGUAGES_DIR + File.separator + "official"; // place for official languages resources files
        LOCALE_DIR = "/usr/share/locale"; // place for .mo files
    }

    public LanguageManagerImpl()
    {
        readLanguageSettings();
        allLanguages = loadAllLanguages();
        blacklist = loadBlacklist();
        allCountries = loadAllCountries();
        translations = new HashMap<String, Map<String, String>>();
        translationsLastAccessed = new HashMap<String, Long>();
        UvmContextFactory.context().servletFileManager().registerUploadHandler( new LanguageUploadHandler() );
        UvmContextFactory.context().newThread(this.cleaner).start();
    }

    // public methods ---------------------------------------------------------

    public LanguageSettings getLanguageSettings()
    {
        return languageSettings;
    }

    public void setLanguageSettings(LanguageSettings newSettings)
    {
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        String settingsName = System.getProperty("uvm.settings.dir") + "/untangle-vm/language.js";

        try {
            settingsManager.save( settingsName, newSettings );
        } catch (Exception exn) {
            logger.error("Could not save language settings", exn);
            return;
        }

        this.languageSettings = newSettings;
    }

    public boolean uploadLanguagePack(FileItem item) throws UvmException
    {
        boolean success = true;
        String msg = "";
        try {
            BufferedOutputStream dest = null;
            ZipEntry entry = null;

            // validate language pack
            if (!item.getName().endsWith(".zip")) {
                success = false;
                msg = "Invalid Language Pack";
            }

            // Open the ZIP file
            InputStream uploadedStream = item.getInputStream();
            ZipInputStream zis = new ZipInputStream(uploadedStream);
            while ((entry = zis.getNextEntry()) != null) {
                if (!isValid(entry)){
                    success = false;
                    msg = "Invalid Entry";
                    break;
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
                    boolean ret = compileMoFile(entry);
                    if (!ret) {
                        success = false;
                        msg = "Couldn't compile MO file for entry '" + entry + "'";
                        file.delete();
                        break;
                    }

                    // compile the java properties version & install
                    // it in the classpath (LANGUAGES_DIR)
                    ret = compileResourceBundle(entry);
                    if (!ret) {
                        success = false;
                        msg = "Couldn't compile resource bundle for entry '" + entry + "'";
                        file.delete();
                        break;
                    }
                }
            }
            zis.close();
            uploadedStream.close();

        } catch (IOException e) {
            logger.error("upload failed", e);
            throw new UvmException("Upload Language Pack Failed");
        }

        if (!success) {
            throw new UvmException(msg);
        }
        return success;
    }

    /*
     * Check if a language pack entry conform to the correct naming: <lang_code>/<module_name>.po
     */
    private boolean isValid(ZipEntry entry)
    {
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

    private boolean compileResourceBundle(ZipEntry entry)
    {
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

    private boolean compileMoFile(ZipEntry entry)
    {
        boolean success = true;
        try {
            String tokens[] = entry.getName().split(File.separator);
            String lang = tokens[0];
            String moduleName = tokens[1].substring(0, tokens[1].lastIndexOf("."));
            String moduleLocaleDirName = LOCALE_DIR + File.separator + lang + File.separator + LC_MESSAGES;

            File moduleLocaleDir = new File(moduleLocaleDirName);
            if (!moduleLocaleDir.exists()){
                if (!moduleLocaleDir.mkdirs()) {
                    logger.error("Error creating locale folder: " + moduleLocaleDir );
                    return false;
                }
            }

            String cmd[] = { "msgfmt",
                    "-o", moduleLocaleDirName + File.separator + moduleName  + ".mo",
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

    private void logProcessError(Process p, String errorMsg) throws IOException
    {
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

    public List<LocaleInfo> getLanguagesList()
    {
        List<LocaleInfo> locales = new ArrayList<LocaleInfo>();

        Set<String> availableLanguages = new HashSet<String>();
        // add default language
        availableLanguages.add(DEFAULT_LANGUAGE);

        // Add languages for which we have translations:
        //  * available official languages
        Collections.addAll(availableLanguages, (new File(LANGUAGES_OFFICIAL_DIR)).list());
        //  * available community languages
        Collections.addAll(availableLanguages, (new File(LANGUAGES_COMMUNITY_DIR)).list());

        for (String code : availableLanguages) {
            String tokens[] = code.split("_");
            String langCode = tokens[0];
            String langName = allLanguages.get(langCode);
            String countryCode = tokens.length == 2 ? tokens[1] : null;
            String countryName = countryCode == null ? null : allCountries.get(countryCode);
            if (! blacklist.contains(code)) {
                locales.add(new LocaleInfo(langCode, langName, countryCode, countryName));
            }
        }

        return locales;
    }

    public Map<String, String> getTranslations(String module)
    {
        Map<String, String> map;

        if (null == module) {
            logger.warn("getTranslations called with no module, returning empty map");
            return new HashMap<String, String>();
        }

        String i18nModule = module.replaceAll("-", "_");
        Locale locale = getLocale();

        String translationKey = i18nModule + "_" + locale.getLanguage();
        
        synchronized(translations){
            map = translations.get(translationKey);

            if(map == null){
                translations.put(translationKey, new HashMap<String, String>());
                map = translations.get(translationKey);
            }

            if(map.size() == 0){
                try {
                    I18n i18n = null;
                    ResourceBundle.clearCache(getClass().getClassLoader());
                    try {
                        // Start with community...
                        i18n = I18nFactory.getI18n(BASENAME_COMMUNITY_PREFIX+"."+i18nModule, i18nModule,
                                           getClass().getClassLoader(), locale, I18nFactory.DEFAULT);
                    } catch (MissingResourceException e) {
                        // ...fall back to official translations
                        i18n = I18nFactory.getI18n(BASENAME_OFFICIAL_PREFIX+"."+i18nModule, i18nModule,
                                           getClass().getClassLoader(), locale, I18nFactory.DEFAULT);
                    }

                    if (i18n != null) {
                        for (Enumeration<String> enumeration = i18n.getResources().getKeys(); enumeration.hasMoreElements();) {
                            String key = enumeration.nextElement();
                            map.put(key, i18n.tr(key));
                        }
                    }
                } catch (MissingResourceException e) {
                    // Do nothing - Fall back to a default that returns the passed text if no resource bundle can be located
                    // is done in client side
                }
            }

            translationsLastAccessed.put(translationKey, System.currentTimeMillis());
            return map;
        }
    }


    // private methods --------------------------------------------------------

    private Locale getLocale()
    {
        Locale locale = new Locale(DEFAULT_LANGUAGE);
        if (languageSettings != null && languageSettings.getLanguage() != null) {
            String tokens[] = languageSettings.getLanguage().split("_");
            if (tokens.length == 1) {
                locale = new Locale(tokens[0]);
            } else if (tokens.length == 2) {
                locale = new Locale(tokens[0], tokens[1]);
            }
        }
        return locale;
    }

    private ArrayList<String> loadBlacklist()
    {
        ArrayList<String> bl = new ArrayList<String>();

        // Reading from config file
        try {
            BufferedReader in = new BufferedReader(new FileReader(LANGUAGES_DIR + File.separator + BLACKLIST_CFG));
            String s = new String();
            while((s = in.readLine())!= null) {
                s = s.trim();
                if (s.length() > 0){
                    bl.add(s);
                }
            }
            in.close();
        } catch (IOException e) {
            logger.warn("Failed getting blacklisted languages!", e);
        }

        return bl;
    }

    private Map<String, String> loadAllLanguages()
    {
        Map<String, String> languages = new HashMap<String, String>();

        // Reading all languages from config file
        try {
            BufferedReader in = new BufferedReader(new FileReader(LANGUAGES_DIR + File.separator + LANGUAGES_CFG));
            String s = new String();
            while((s = in.readLine())!= null) {
                s = s.trim();
                if (s.length() > 0){
                    String[] tokens = s.split("\\s+");
                    if (tokens.length >= 2) {
                        String langCode = tokens[0];
                        String langName = tokens[1];
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

    private Map<String, String> loadAllCountries()
    {
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

    private boolean isValidLocaleCode(String code)
    {
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

    private boolean isValidLanguageCode(String code)
    {
        return allLanguages.containsKey(code);
    }

    private boolean isValidCountryCode(String code)
    {
        return allCountries.containsKey(code);
    }

    private class LanguageUploadHandler implements UploadHandler
    {
        @Override
        public String getName()
        {
            return "language";
        }

        @Override
        public String handleFile(FileItem fileItem, String argument) throws Exception
        {
            if ( uploadLanguagePack(fileItem)) {
                return "Uploaded language pack successfully";
            }
            return "Language Pack Uploaded With Errors";
        }
    }

    private void readLanguageSettings()
    {
        SettingsManager settingsManager = UvmContextFactory.context().settingsManager();
        String settingsFile = System.getProperty("uvm.settings.dir") + "/untangle-vm/language.js";
        LanguageSettings readSettings = null;

        logger.debug("Loading language settings from " + settingsFile);

        try {
            readSettings =  settingsManager.load( LanguageSettings.class, settingsFile );
        } catch (Exception exn) {
            logger.error("Could not read language settings", exn);
        }

        if (readSettings == null) {
            logger.warn("No settings found... initializing with defaults");
            languageSettings = new LanguageSettings();
            languageSettings.setLanguage(DEFAULT_LANGUAGE);
            setLanguageSettings(languageSettings);
        }
        else {
            languageSettings = readSettings;
        }
    }

    /**
     * This thread periodically walks through translations instances and removes
     * those that have not been used recently.
     */
    private class translationsCleaner implements Runnable
    {
        public void run()
        {
            cleanerThread = Thread.currentThread();

            while (cleanerThread != null) {
                try {
                    Thread.sleep(CLEANER_SLEEP_TIME_MILLI);
                } catch (Exception e) {}

                try {
                    Long now = System.currentTimeMillis();
                    Map<String, String> map;

                    /**
                     * Remove old entries from map
                     */
                    synchronized(translations){
                        for( String translationKey : translationsLastAccessed.keySet() ){
                            if( translationsLastAccessed.get(translationKey) + CLEANER_LAST_ACCESS_MAX_TIME < now){
                                translationsLastAccessed.remove(translationKey);

                                map = translations.get(translationKey);

                                if(map != null){
                                    map.clear();
                                }
                            }
                        }
                    }
                    
                } catch (Exception e) {
                    logger.warn("Exception while cleaning translations",e);
                }
            }
        }
    }
}

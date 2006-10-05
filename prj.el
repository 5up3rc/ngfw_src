;; project file for jdee
;; $Id$

(jde-project-file-version "1.0")

(setq casings '("mail" "http" "ftp"))

(setq transforms '("airgap" "email" "fprot" "httpblocker" "protofilter"
                   "reporting" "sophos" "spyware" "test" "virus" "spam"
                   "portal"))

(setq jni-projects '("jnetcap" "jvector"))

(setq prj-dir (file-name-directory jde-loading-project-file))
(jde-set-variables
 ;; TODO base on dir listing, include webapps
 '(jde-make-program "rake")
 '(jde-compile-option-directory ".")
 '(jde-make-working-directory ".")
 '(jde-make-args "default")
 '(jde-global-classpath
   (append
    (mapcar (lambda (a)
              (concat "../downloads/output/" a))
            '("c3p0-0.9.0.4/lib/c3p0-0.9.0.4.jar"
              "commons-fileupload-1.1/commons-fileupload-1.1.jar"
              "commons-httpclient-3.0/commons-httpclient-3.0.jar"
              "hibernate-3.2/hibernate3.jar"
              "hibernate-annotations-3.2.0.CR3/hibernate-annotations.jar"
              "hibernate-annotations-3.2.0.CR3/lib/ejb3-persistence.jar"
              "htmlparser1_6_20060319/htmlparser1_6/lib/htmllexer.jar"
              "htmlparser1_6_20060319/htmlparser1_6/lib/htmlparser.jar"
              "apache-tomcat-5.5.17-embed/lib/catalina.jar"
              "apache-tomcat-5.5.17-embed/lib/catalina-optional.jar"
              "apache-tomcat-5.5.17-embed/lib/jsp-api.jar"
              "apache-tomcat-5.5.17-embed/lib/servlet-api.jar"
              "javamail-1.3.3_01/mail.jar"
              "jcifs_1.2.9/jcifs-1.2.9.jar"
              "junit3.8.1/junit.jar"
              "logging-log4j-1.2.9/dist/lib/log4j-1.2.9.jar"
              "bcel-5.1/bcel-5.1.jar"))
    (if (file-exists-p (concat prj-dir "./staging/grabbag"))
        (mapcar
         (lambda (a)
           (concat "./staging/grabbag/" a))
         (directory-files (concat prj-dir "./staging/grabbag") nil ".*\.jar"))))))

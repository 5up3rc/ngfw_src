;; project file for jdee
;; $Id$

;; Aaron Read <amread@untangle.com>

(jde-project-file-version "1.0")

(setq prj-dir (file-name-directory jde-loading-project-file))
(jde-set-variables
 '(jde-jdk-registry '(("1.5.0" . "/usr/lib/jvm/java-1.5.0-sun/")))
 '(jde-jdk '("1.5.0"))
 '(jde-make-program "rake")
 '(jde-compile-option-directory ".")
 '(jde-make-working-directory ".")
 '(jde-make-args "default")
 '(jde-global-classpath
   (append
    (mapcar (lambda (a)
              (concat "./downloads/output/" a))
            '(
              "apache-tomcat-5.5.17-embed/lib/catalina-optional.jar"
              "apache-tomcat-5.5.17-embed/lib/catalina.jar"
              "apache-tomcat-5.5.17-embed/lib/jsp-api.jar"
              "apache-tomcat-5.5.17-embed/lib/servlet-api.jar"
              "bcel-5.1/bcel-5.1.jar"
              "c3p0-0.9.0.4/lib/c3p0-0.9.0.4.jar"
              "commons-fileupload-1.1/commons-fileupload-1.1.jar"
              "commons-httpclient-3.0/commons-httpclient-3.0.jar"
              "hibernate-3.2/hibernate3.jar"
              "hibernate-annotations-3.2.0.CR3/hibernate-annotations.jar"
              "hibernate-annotations-3.2.0.CR3/lib/ejb3-persistence.jar"
              "htmlparser1_6_20060319/htmlparser1_6/lib/htmllexer.jar"
              "htmlparser1_6_20060319/htmlparser1_6/lib/htmlparser.jar"
              "jasperreports-1.1.1/dist/jasperreports-1.1.1.jar"
              "javamail-1.3.3_01/mail.jar"
              "jcifs_1.2.9/jcifs-1.2.9.jar"
              "je-3.2.13/lib/je-3.2.13.jar"
              "jfreechart-1.0.1/jfreechart-1.0.1.jar"
              "junit3.8.1/junit.jar"
              "logging-log4j-1.2.14/dist/lib/log4j-1.2.14.jar"
              ))
    (if (file-exists-p (concat prj-dir "./staging/grabbag"))
        (mapcar
         (lambda (a)
           (concat "./staging/grabbag/" a))
         (directory-files (concat prj-dir "./staging/grabbag") nil ".*\.jar"))))))

#!/bin/sh
# untangle-node-webfiter and untangle-node-commtouchav are not needed since they rely on base-webfilter and base-virus
ALL_MODULES='untangle-vm untangle-libuvm untangle-apache2-config untangle-casing-smtp 
    untangle-base-virus untangle-base-webfilter untangle-node-adblocker 
    untangle-node-firewall 
    untangle-node-ips untangle-node-openvpn untangle-node-phish 
    untangle-node-protofilter untangle-node-reporting untangle-node-shield 
    untangle-node-spamassassin 
    untangle-node-adconnector untangle-node-bandwidth untangle-node-boxbackup 
    untangle-node-branding untangle-node-commtouchas 
    untangle-node-faild untangle-node-ipsec 
    untangle-node-policy untangle-node-sitefilter untangle-node-splitd 
    untangle-node-support untangle-node-webcache untangle-node-classd
    untangle-node-capture untangle-casing-https'
OFFICIAL_LANGUAGES='de es fr ja pt_BR zh_CN'

function update_keys()
{
case "$1" in

"untangle-vm")
# TODO Find out what happend with uvm folder
#    cd ../uvm/po/
#    msgmerge -U -N $1.pot tmp_keys.pot
#    rm tmp_keys.pot
#    update_po $1
    ;;
"untangle-libuvm")
    cd ../uvm/po
    echo 'get main keys'
    xgettext --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../servlets/webui/root/script/main.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../servlets/webui/root/script/components.js
    #general wizard
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../../../pkgs/untangle-apache2-config/files/var/www/script/wizard.js
    #setup wizard
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../servlets/setup/root/script/setup.js
    echo 'get config keys'
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../servlets/webui/root/script/config/*.js

    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../../reporting/servlets/reports/root/script/*.js

    #xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../api/com/untangle/uvm/util/OutsideValve.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../bootstrap/com/untangle/uvm/engine/UvmErrorReportValve.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../impl/com/untangle/uvm/engine/MailSenderImpl.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../uvm/servlets/webui/src/com/untangle/uvm/webui/servlet/GridSettingsServlet.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../../../hades/src/branding/src/com/untangle/node/branding/BrandingSettings.java

    echo 'get blingers keys'
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../impl/com/untangle/uvm/engine/Dispatcher.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../api/com/untangle/uvm/vnet/NodeBase.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../firewall/src/com/untangle/node/firewall/FirewallImpl.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../firewall/src/com/untangle/node/firewall/FirewallImpl.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../ips/src/com/untangle/node/ips/IpsNodeImpl.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../openvpn/src/com/untangle/node/openvpn/OpenVpnNodeImpl.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../protofilter/src/com/untangle/node/protofilter/ProtoFilterImpl.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../spam-base/src/com/untangle/node/spam/SpamNodeImpl.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../virus-base/src/com/untangle/node/virus/VirusNodeImpl.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../webfilter-base/src/com/untangle/node/webfilter/WebFilterBase.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../../../hades/src/faild/src/com/untangle/node/faild/FailDImpl.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../../../hades/src/splitd/src/com/untangle/node/splitd/SplitDImpl.java 
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../../../hades/src/ipsec/src/com/untangle/node/ipsec/IPsecNodeImpl.java 
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../../../hades/src/classd/src/com/untangle/node/classd/ClassDNodeImpl.java 
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../../../hades/src/commtouchas/src/com/untangle/node/commtouchas/CommtouchAsNode.java 
    find ../../uvm/hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot
    find ../../reporting/hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot

    msgcat tmp_keys.pot fmt_keys.pot -o tmp_keys.pot
    msgmerge -U -N  $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
"untangle-apache2-config")
    cd ../../pkgs/untangle-apache2-config/po
    xgettext --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot ../files/usr/lib/python*/*.py
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot ../files/usr/share/untangle/mod_python/error/*.py
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot ../files/usr/share/untangle/mod_python/auth/*.py
    msgmerge -U -N $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
"untangle-base-webfilter")
    moduleName=`echo "$1"|cut -d"-" -f3`
    cd ../webfilter-base/po/
    echo 'get new keys'
    xgettext --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/share/untangle/web/webui/script/${1}/settings.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/lib/python*/reports/node/*.py
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../src/com/untangle/node/webfilter/BlockPageServlet.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../../http-casing/src/com/untangle/node/http/BlockPageUtil.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../src/com/untangle/node/webfilter/*.java
    find ../hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot
    ruby ../../i18ntools/xi18ntags.rb ../../uvm/servlets/blockpage/root/blockpage_template.jspx >> ./tmp_keys.pot
    msgmerge -U -N $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
"untangle-node-phish")
    moduleName=`echo "$1"|cut -d"-" -f3`
    cd ../${moduleName}/po/
    echo 'get new keys'
    xgettext --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/share/untangle/web/webui/script/${1}/settings.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/lib/python*/reports/node/*.py
    #xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../servlets/phish/src/com/untangle/node/phish/BlockPageServlet.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../../http-casing/src/com/untangle/node/http/BlockPageUtil.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../../spam-base/src/com/untangle/node/spam/*.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../src/com/untangle/node/${moduleName}/*.java
    find ../hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot
    ruby ../../i18ntools/xi18ntags.rb ../../uvm/servlets/blockpage/root/blockpage_template.jspx >> ./tmp_keys.pot
    msgmerge -U -N $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
"untangle-node-protofilter"|"untangle-node-ips"|"untangle-node-firewall"|"untangle-node-reporting"|"untangle-node-adblocker"|"untangle-node-spamassassin"|"untangle-node-capture")
    moduleName=`echo "$1"|cut -d"-" -f3`
    cd ../${moduleName}/po/
    echo 'get new keys'
    xgettext --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/share/untangle/web/webui/script/${1}/settings.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/lib/python*/reports/node/*.py
    echo 'get new keys2'
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../src/com/untangle/node/${moduleName}/*.java
    echo 'get new keys3'
    find ../hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot
    echo 'get new keys4'
    msgmerge -U -N $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
"untangle-node-openvpn")
    moduleName=`echo "$1"|cut -d"-" -f3`
    cd ../${moduleName}/po/
    echo 'get new keys'
    xgettext --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/share/untangle/web/webui/script/${1}/settings.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/lib/python*/reports/node/*.py
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../src/com/untangle/node/${moduleName}/*.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../src/com/untangle/node/${moduleName}/*.java
    #xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../../../pkgs/untangle-apache2-config/files/var/www/script/wizard.js
    find ../hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot
    msgmerge -U -N $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
"untangle-node-adconnector"|"untangle-node-bandwidth"|"untangle-node-boxbackup"|"untangle-node-faild"|"untangle-node-policy"|"untangle-node-faild"|"untangle-node-splitd"|"untangle-node-webcache"|"untangle-node-commtouchas"|"untangle-node-classd")
    moduleName=`echo "$1"|cut -d"-" -f3`
    cd ../../../hades/src/${moduleName}/po/
    echo 'get new keys'
    xgettext --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/share/untangle/web/webui/script/${1}/settings.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/lib/python*/reports/node/*.py
    echo 'get new keys2'
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../src/com/untangle/node/${moduleName}/*.java
    find ../hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot
    msgmerge -U -N $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
"untangle-node-branding"|"untangle-node-ipsec"|"untangle-node-support")
    moduleName=`echo "$1"|cut -d"-" -f3`
    cd ../../../hades/src/${moduleName}/po/
    echo 'get new keys'
    xgettext --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/share/untangle/web/webui/script/${1}/settings.js
    echo 'get new keys2'
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../src/com/untangle/node/${moduleName}/*.java
    find ../hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot
    msgmerge -U -N $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
"untangle-node-sitefilter")
moduleName=`echo "$1"|cut -d"-" -f3`
    cd ../../../hades/src/${moduleName}/po/
    echo 'get new keys'
    xgettext --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/share/untangle/web/webui/script/${1}/settings.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/lib/python*/reports/node/*.py
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../servlets/sitefilter/src/com/untangle/node/sitefilter/BlockPageServlet.java
    echo 'get new keys2'
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../src/com/untangle/node/${moduleName}/*.java
    find ../hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot
    msgmerge -U -N $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
"untangle-casing-https")
    cd ../../../hades/src/https-casing/po/
    xgettext --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/share/untangle/web/webui/script/${1}/settings.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../src/com/untangle/node/https/*.java
    find ../hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot
    msgmerge -U -N $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
"untangle-casing-smtp")
    cd ../smtp-casing/po/
    xgettext --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../servlets/quarantine/root/quarantine.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/lib/python*/reports/node/*.py
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../servlets/quarantine/root/remaps.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../servlets/quarantine/root/request.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../servlets/quarantine/root/safelist.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../src/com/untangle/node/smtp/quarantine/Quarantine.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../../spam-base/src/com/untangle/node/spam/SpamSmtpHandler.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../../phish/src/com/untangle/node/phish/PhishSmtpHandler.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../../virus-base/src/com/untangle/node/virus/SmtpSessionHandler.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ktr -o tmp_keys.pot ../src/resources/com/untangle/node/smtp/quarantine/DigestSimpleEmail_HTML.vm
    find ../hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot
    ruby ../../i18ntools/xi18ntags.rb ../servlets/quarantine/root/inbox.jspx >> ./tmp_keys.pot
    ruby ../../i18ntools/xi18ntags.rb ../servlets/quarantine/root/request.jspx >> ./tmp_keys.pot
    msgmerge -U -N $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
"untangle-base-virus")
    cd ../virus-base/po/
    echo 'get new keys'
    xgettext --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/share/untangle/web/webui/script/$1/settings.js
    xgettext -j --copyright-holder='Untangle, Inc.' -L Python -ki18n._ -o tmp_keys.pot ../hier/usr/lib/python*/reports/node/*.py
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../servlets/virus/src/com/untangle/node/virus/BlockPageServlet.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -ktr -o tmp_keys.pot ../../http-casing/src/com/untangle/node/http/BlockPageUtil.java
    xgettext -j --copyright-holder='Untangle, Inc.' -L Java -kmarktr -o tmp_keys.pot ../src/com/untangle/node/virus/*.java
    find ../hier -name '*.py' | xargs xgettext -j --copyright-holder='Untangle, Inc.' -L Python -k_ -o tmp_keys.pot
    ruby ../../i18ntools/xi18ntags.rb ../../uvm/servlets/blockpage/root/blockpage_template.jspx >> ./tmp_keys.pot
    msgmerge -U -N $1.pot tmp_keys.pot
    rm tmp_keys.pot
    update_po $1
    ;;
# "untangle-systray")
#     pygettext ../../../internal/isotools/wintangle-systray/*.py
#     ;;
*)
    echo 1>&2 Module Name \"$1\" is invalid ...
    exit 127
    ;;
esac
}

function update_po( )
{
    echo 'update po files'
    for lang in ${OFFICIAL_LANGUAGES}
    do
        pot=$1.pot
        po=${lang}/$1.po
        if [ ! -e $po ]; then
            mkdir -p $(dirname $po)
            touch $po
        fi
        msgmerge -U -N $po $pot
    done
}


if [ $# -ne 1 ]; then
     echo 1>&2 Usage: $0 "<module_name | all>"
     exit 127
fi

if [ $1 == 'all' ]
then

    current_dir=`pwd`
    for module in ${ALL_MODULES}
    do
        echo 'Updating keys for '${module}'...'
        update_keys ${module}
        cd ${current_dir}
    done

else
    update_keys $1
fi


# All done, exit ok
exit 0

#!/bin/bash

if [ $# -ne 1 ]; then
     echo 1>&2 Usage: $0 "lang_code"
     exit 127
fi

if [ ! -d $1 ]
then
    mkdir $1
fi

cp ../uvm/po/$1/untangle-libuvm.po ./$1/
cp ../gui/po/$1/untangle-install-wizard.po ./$1/
cp ../../pkgs/untangle-apache2-config/po/$1/untangle-apache2-config.po ./$1/
cp ../smtp-casing/po/$1/untangle-casing-smtp.po ./$1/
cp ../virus-blocker-base/po/$1/untangle-base-virus-blocker.po ./$1/
cp ../web-filter-base/po/$1/untangle-base-web-filter.po ./$1/

for module in untangle-node-phish-blocker untangle-node-spam-blocker-lite untangle-node-shield untangle-node-application-control-lite untangle-node-intrusion-prevention untangle-node-firewall untangle-node-reporting untangle-node-openvpn untangle-node-ad-blocker
do
    module_dir=`echo "${module}"|cut -d"-" -f3`
    cp ../${module_dir}/po/$1/${module}.po ./$1/
done

for module in untangle-node-adconnector untangle-node-bandwidth untangle-node-boxbackup untangle-node-branding untangle-node-spam-blocker untangle-node-virus-blocker untangle-node-faild untangle-node-license untangle-node-policy untangle-node-sitefilter untangle-node-faild untangle-node-splitd untangle-node-webcache untangle-node-ipsec untangle-node-classd
do
    module_dir=`echo "${module}"|cut -d"-" -f3`
    cp ../../../hades/src/${module_dir}/po/$1/${module}.po ./$1/
done

rm -f $1.zip
zip -r $1.zip ./$1/
rm -rf ./$1/

# All done, exit ok
exit 0

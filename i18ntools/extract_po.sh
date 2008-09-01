#!/bin/sh

if [ $# -ne 1 ]; then
     echo 1>&2 Usage: $0 "lang_code"
     exit 127
fi

if [ ! -d $1 ]
then
    mkdir $1
fi    

cp ../uvm-lib/po/$1/untangle-libuvm.po ./$1/
cp ../gui/po/$1/untangle-install-wizard.po ./$1/
cp ../../pkgs/untangle-apache2-config/po/$1/untangle-apache2-config.po ./$1/
cp ../mail-casing/po/$1/untangle-casing-mail.po ./$1/
cp ../virus-base/po/$1/untangle-base-virus.po ./$1/

for module in untangle-node-webfilter untangle-node-phish untangle-node-spyware untangle-node-spamassassin untangle-node-shield untangle-node-protofilter untangle-node-ips untangle-node-firewall untangle-node-reporting untangle-node-openvpn
do 
    module_dir=`echo "${module}"|cut -d"-" -f3`
    cp ../${module_dir}/po/$1/${module}.po ./$1/
done

for module in untangle-node-adconnector untangle-node-boxbackup untangle-node-policy untangle-node-portal untangle-node-pcremote
do 
    module_dir=`echo "${module}"|cut -d"-" -f3`
    cp ../../../hades/rup/${module_dir}/po/$1/${module}.po ./$1/
done

rm -f $1.zip
zip -r $1.zip ./$1/
rm -rf ./$1/

# All done, exit ok
exit 0
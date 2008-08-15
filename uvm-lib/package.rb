# -*-ruby-*-
# $HeadURL$
# Copyright (c) 2003-2007 Untangle, Inc.
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License, version 2,
# as published by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful, but
# AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
# NONINFRINGEMENT.  See the GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#

jnetcap = BuildEnv::SRC['jnetcap']
jvector = BuildEnv::SRC['jvector']
uvm_lib = BuildEnv::SRC['untangle-libuvm']
BuildEnv::SRC.installTarget.register_dependency(uvm_lib)

jts = []

## Bootstrap
jts << JarTarget.build_target(uvm_lib, Jars::Base, 'bootstrap', "./uvm-lib/bootstrap")

## API
jts << (jt = JarTarget.build_target(uvm_lib, Jars::Base, 'api', ["./uvm-lib/api", 'version']))
BuildEnv::SRC.installTarget.install_jars(jt, uvm_lib.getWebappDir('webstart'), nil, true)

## Local API
jts << JarTarget.build_target(uvm_lib, Jars::Base + [uvm_lib['api']], 'localapi', "./uvm-lib/localapi")

## Reporting
deps  = Jars::Base + Jars::Jasper + Jars::JFreeChart + [uvm_lib['api']]
jts << JarTarget.build_target(uvm_lib, deps, 'reporting', "./uvm-lib/reporting")

## Implementation
deps  = Jars::Base + Jars::TomcatEmb + Jars::JavaMail + Jars::Jcifs +
  Jars::Dom4j + Jars::Activation + Jars::Trove + Jars::Jasper + Jars::JFreeChart +
  [ uvm_lib['bootstrap'], uvm_lib['api'], uvm_lib['localapi'], uvm_lib['reporting'], jnetcap['impl'], jvector['impl']]

jts << JarTarget.build_target(uvm_lib, deps, 'impl', "./uvm-lib/impl")

## This little piggy doesn't go to the normal place.
taglib = JarTarget.build_target(uvm_lib, deps, 'taglib', "./uvm-lib/taglib")
BuildEnv::SRC.installTarget.install_jars(taglib, "#{uvm_lib.distDirectory}/usr/share/java/uvm" )

# servlets
ServletBuilder.new(uvm_lib, 'com.untangle.uvm.reports.jsp',
                   "./uvm-lib/servlets/reports")

ServletBuilder.new(uvm_lib, 'com.untangle.uvm.alpaca.jsp',
                   "./uvm-lib/servlets/alpaca")

# library
ServletBuilder.new(uvm_lib, "com.untangle.uvm.installer.servlet",
                   "uvm-lib/servlets/library", [])

deps = %w(
           slf4j-1.4.3/slf4j-log4j12-1.4.3.jar
           slf4j-1.4.3/slf4j-api-1.4.3.jar
           Ajax/jars/jstl.jar
           Ajax/jars/standard.jar
         ).map { |f| Jars.downloadTarget(f) }
deps += Jars::Jabsorb

deps << taglib

ServletBuilder.new(uvm_lib, "com.untangle.uvm.webui.servlet",
                   "./uvm-lib/servlets/webui", deps)


# Ajax Tk
deps = FileList["#{BuildEnv::downloads}/Ajax/jars/*jar"].exclude(/.*servlet-api.jar/).map { |n| ThirdpartyJar.get(n) }
ServletBuilder.new(uvm_lib, 'com.untangle.uvm.blockpage.jsp',
                   "./uvm-lib/servlets/blockpage", deps, [], [])

# ServletBuilder.new(uvm_lib, 'com.untangle.uvm.sessiondumper.jsp',
#                    "./uvm-lib/servlets/session-dumper")

BuildEnv::SRC.installTarget.install_jars(jts, "#{uvm_lib.distDirectory}/usr/share/untangle/lib",
                                        nil, false, true)

thirdparty = BuildEnv::SRC['untangle-libuvmthirdparty']

BuildEnv::SRC.installTarget.install_jars(Jars::Base, "#{thirdparty.distDirectory}/usr/share/java/uvm")
BuildEnv::SRC.installTarget.install_jars(Jars::ProperJavaRDP, "#{thirdparty.distDirectory}/usr/share/java/uvm")
BuildEnv::SRC.installTarget.install_jars(Jars::Reporting, "#{thirdparty.distDirectory}/usr/share/java/reports")

BuildEnv::SRC.installTarget.install_dirs("#{uvm_lib.distDirectory}/usr/share/untangle/toolbox")

uvm_cacerts = "#{uvm_lib.distDirectory}/usr/share/untangle/conf/cacerts"
java_cacerts = "#{BuildEnv::JAVA_HOME}/jre/lib/security/cacerts"
mv_ca = "./uvm-lib/resources/mv-ca.pem"
ut_ca = "./uvm-lib/resources/ut-ca.pem"

file uvm_cacerts => [ java_cacerts, mv_ca, ut_ca ] do
  ensureDirectory(File.dirname(uvm_cacerts))
  FileUtils.cp(java_cacerts, uvm_cacerts)
  FileUtils.chmod(0666, uvm_cacerts)
  Kernel.system("#{BuildEnv::JAVA_HOME}/bin/keytool", '-import', '-noprompt',
                '-keystore', uvm_cacerts, '-storepass', 'changeit', '-file',
                mv_ca, '-alias', 'metavizeprivateca')
  Kernel.system("#{BuildEnv::JAVA_HOME}/bin/keytool", '-import', '-noprompt',
                '-keystore', uvm_cacerts, '-storepass', 'changeit', '-file',
                ut_ca, '-alias', 'untangleprivateca')
end

if BuildEnv::SRC.isDevel
  BuildEnv::SRC.installTarget.install_files("./debian/control",
                                           "#{uvm_lib.distDirectory}/tmp",
                                           'pkg-list-main')

  activationKey = "#{uvm_lib.distDirectory}/usr/share/untangle/activation.key"

  ## Insert the activation key if necessary.  Done here to not include
  ## The file inside of packages
  file activationKey do
    File.open( activationKey, "w" ) { |f| f.puts( "0000-0000-0000-0000" ) }
  end

  BuildEnv::SRC.installTarget.register_dependency(activationKey)
end

BuildEnv::SRC.installTarget.register_dependency(uvm_cacerts)

deps  = Jars::Base + Jars::TomcatEmb + Jars::JavaMail + Jars::Jcifs +
  Jars::Dom4j + Jars::Activation + Jars::Trove + Jars::Junit +
  [ uvm_lib['bootstrap'], uvm_lib['api'], uvm_lib['localapi'], uvm_lib['impl'], jnetcap['impl'], jvector['impl']]

JarTarget.build_target(BuildEnv::SRC["unittest"], deps, 'untangle-libuvm', "./uvm-lib/unittest")

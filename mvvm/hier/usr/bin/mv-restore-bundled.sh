#!/bin/sh

####################################################################
# Copyright (c) 2006 Metavize Inc.
# All rights reserved.
#
# This software is the confidential and proprietary information of
# Metavize Inc. ("Confidential Information").  You shall
# not disclose such Confidential Information.
#
#  $Id$
#
####################################################################

#=============================================================
# Script which takes the output of "mv-backup-bundled" and
# restores it to a system.  This is a wrapper around "restore-mv"
# which deals with single .tar.gz files rather than the three
# files from the "old-style" backups.
#
#
# 1 - Not a valid gzip file
# 2 - Not a tar file
# 3 - Missing content from file
# 4 - Error from restore file
# 
#==============================================================

#================================================================
#
# **************************************************
# ******************** WARNING  ********************
# **************************************************
#
# As this file is maintained, note that its behavior is bound
# to com.metavize.tran.boxbackup.BoxBackupImpl.  Any changes
# this this script should be reflected in that Java code
#================================================================


IN_FILE=INVALID
VERBOSE=false


function debug() {
  if [ "true" == $VERBOSE ]; then
    echo $*
  fi
}

function err() {
  echo $* > /dev/stderr
}

function doHelp() {
  echo "$0 -i (input bundle file) -h (help) -v (verbose)"
}



####################################
# "Main" logic starts here

while getopts "hi:v" opt; do
  case $opt in
    h) doHelp;exit 0;;
    i) IN_FILE=$OPTARG;;
    v) VERBOSE=true;;
  esac
done

if [ "INVALID" == $IN_FILE ]; then
  err "Please provide an input file";
  exit 1;
fi

debug "Restoring from file -" $IN_FILE


# Create a working directory
WORKING_DIR=`mktemp -d`
debug "Working in directory $WORKING_DIR"

# Copy our file to the working directory
cp $IN_FILE $WORKING_DIR/x.tar.gz

# Unzip
gzip -t $WORKING_DIR/x.tar.gz
EXIT_VAL=$?

if [ $EXIT_VAL != 0 ]; then
  err "$IN_FILE Does not seem to be a valid gzip file"
  rm -rf $WORKING_DIR
  exit 1
fi

debug "Gunzip"
gunzip $WORKING_DIR/x.tar.gz


# Now, untar
pushd $WORKING_DIR > /dev/null 2>&1
debug "Untar"
tar -xvf x.tar  > /dev/null 2>&1
EXIT_VAL=$?
popd  > /dev/null 2>&1

if [ $EXIT_VAL != 0 ]; then
  err "$IN_FILE Does not seem to be a valid gzip tar file"
  rm -rf $WORKING_DIR
  exit 2
fi


# Find the specfic files
pushd $WORKING_DIR > /dev/null 2>&1

DB_FILE=`ls | grep mvvmdb*.gz`
FILES_FILE=`ls | grep files*.tar.gz`
INSTALLED_FILE=`ls | grep installed*`

debug "DB file $DB_FILE"
debug "Files file $FILES_FILE"
debug "Installed file $INSTALLED_FILE"

popd  > /dev/null 2>&1


# Verify files
if [ -z $INSTALLED_FILE ]; then
  err "Unable to find installed packages file"
  rm -rf $WORKING_DIR
  exit 3
fi

if [ -z $FILES_FILE ]; then
  err "Unable to find system files file"
  rm -rf $WORKING_DIR
  exit 3
fi

if [ -z $DB_FILE ]; then
  err "Unable to find database file"
  rm -rf $WORKING_DIR
  exit 3
fi

# Invoke restore-mv ("Usage: $0 dumpfile tarfile instfile")

restore-mv $DB_FILE $FILES_FILE $INSTALLED_FILE

EXIT_VAL=$?

rm -rf $WORKING_DIR

if [ $EXIT_VAL != 0 ]; then
  err "Error $EXIT_VAL returned from restore-mv"
  exit 4
fi

debug "Completed.  Success"


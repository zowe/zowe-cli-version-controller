#!/usr/bin/env bash
set -e # fail the script if we get a non zero exit code

keyname=$1
user=$2
password=$3
url=$4

groovy ../src/verifyReleaseLabel.groovy $1 $2 $3 $4
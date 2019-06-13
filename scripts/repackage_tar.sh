#!/bin/bash
###
# This program and the accompanying materials are made available under the terms of the
# Eclipse Public License v2.0 which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-v20.html
#
# SPDX-License-Identifier: EPL-2.0
#
# Copyright Contributors to the Zowe Project.
#
###

die () {
    echo "$@"
    exit 1
}

[ "$#" -eq 3 ] || die "3 arguments required, $# provided"

tarfile=$1;
registry=$2;
newversion=$3;

mkdir temp
tar xzf $tarfile -C temp
cd temp
cd package
# Unholy one liner which replace registry and repository with blank strings. Should convert this to javascript file soonTM.
# Takes in package.json, outputs package_new.json
node -e "package = require('./package.json');package.publishConfig.registry='$registry';package.version='$newversion';require('fs').writeFile('package_new.json', JSON.stringify(package, null, 4), 'utf8')"
# Move the old package JSON to build dir so we can publish as a Jenkins artifact?
mv package.json ../../$tarfile_package.json
# Replace package json with our new one
mv package_new.json package.json

# Update npm-shrinkwrap.json if necessary
if [ -e "npm-shrinkwrap.json" ]; then
    # debug
    cat npm-shrinkwrap.json | grep perf-timing

    npm install --only=prod

    gizafile="gizapackages.txt"
    awk '/giza/ { print $2 }' npm-shrinkwrap.json | awk -F"@" '{ print $3 }' | awk -F".tgz" '{ print $1 }'| awk NF > $gizafile
    cat $gizafile

    depsfile="dependenciesfiles.txt"
    node -e "package = require('./package.json');var logger = require('fs').createWriteStream('$depsfile', {flags:'a'});for(pkg in package.dependencies){if(pkg.indexOf('@') >= 0)logger.write(pkg + ' ' + package.dependencies[pkg] + '\n');};logger.end();"
    cat $depsfile

    while read p1; do
        echo "$p1"
        tpkg=$(echo $p1 | cut -d' ' -f 1)
        tver=$(echo $p1 | cut -d' ' -f 2)
        temppkg="@$tpkg@$tver"
        echo "$tpkg --- $tver --- $temppkg"
        while read p2; do
            if [[ $p2 == *$tpkg* ]]; then
                npm install $temppkg --registry $registry --force
            fi
        done < $gizafile
    done < $depsfile

    # debug
    cat npm-shrinkwrap.json | grep perf-timing

    echo "look for gulp"
    cat npm-shrinkwrap.json | grep gulp
    echo "---------should be above this line----------"
fi

npm pack

# delete the original tar
rm -f ../../$tarfile

#move the new tar into the original directory
mv *.tgz ../../$tarfile

cd ../../
# cleanup temp directory
rm -rf temp/

exit 0
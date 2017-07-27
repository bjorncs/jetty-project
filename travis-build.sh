#!/bin/sh
set -e
cp -r /source /home/vespabuilder/src
cd /home/vespabuilder/src
mvn install

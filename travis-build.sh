#!/bin/sh
set -e
export MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"
cp /source /home/vespabuilder/src
cd /home/vespabuilder/src
mvn install

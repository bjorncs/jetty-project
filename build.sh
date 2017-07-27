#!/bin/sh
export MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"
cd /source && mvn install

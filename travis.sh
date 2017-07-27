#!/bin/bash
set -e

docker pull vespaengine/vespa-dev:latest
docker run --rm -v .:/source --entrypoint /source/build.sh vespaengine/vespa-dev:latest

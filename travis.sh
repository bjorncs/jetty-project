#!/bin/bash
set -e

docker pull vespaengine/vespa-dev:latest
docker run --rm -v $(pwd):/source --entrypoint /source/travis-build.sh vespaengine/vespa-dev:latest

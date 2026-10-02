#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
./scripts/build.sh
exec java -jar target/opencep-java.jar "$@"

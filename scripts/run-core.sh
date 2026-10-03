#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
./scripts/build-core.sh
if [ "$#" -eq 0 ]; then
  set -- --declarations test-data/core-cer/examples/schema.core --query test-data/core-cer/examples/sequence.core --events test-data/core-cer/examples/events.csv --stream S
fi
exec java -jar target/core/core-cer.jar "$@"

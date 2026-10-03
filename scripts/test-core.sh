#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
./scripts/build-core.sh
exec java -ea -cp target/core/core-cer.jar:target/core/test-classes corecer.CoreRegressionSuite

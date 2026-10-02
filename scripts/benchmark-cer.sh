#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
./scripts/build.sh
java -cp target/opencep-java.jar cersrt.BenchmarkMain "$@"

#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
./scripts/build.sh
find src/test/java -name '*.java' -print > target/test-sources.txt
javac --release 17 -encoding UTF-8 -Xlint:all,-path -cp target/classes -d target/test-classes @target/test-sources.txt
java -ea -cp target/classes:target/test-classes opencep.RegressionSuite
java -ea -cp target/classes:target/test-classes opencep.UpstreamParitySuite
java -ea -cp target/classes:target/test-classes opencep.UpstreamParitySuite --raw
java -ea -cp target/classes:target/test-classes cersrt.CerSrtSuite
java -ea -cp target/classes:target/test-classes cersrt.CerSrtParitySuite

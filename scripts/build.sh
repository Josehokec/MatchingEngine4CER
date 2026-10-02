#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p target/classes target/test-classes
find src/main/java -name '*.java' -print > target/main-sources.txt
javac --release 17 -encoding UTF-8 -Xlint:all,-path -d target/classes @target/main-sources.txt
if [ -d src/main/resources ]; then
  cp -R src/main/resources/. target/classes/
fi
jar --create --file target/opencep-java.jar --main-class TestMain -C target/classes .

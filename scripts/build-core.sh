#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -n "${MAVEN_REPO_LOCAL:-}" ]; then
  exec mvn -q -Pcore "-Dmaven.repo.local=$MAVEN_REPO_LOCAL" package
fi
exec mvn -q -Pcore package

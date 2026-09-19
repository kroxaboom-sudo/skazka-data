#!/usr/bin/env bash
set -euo pipefail

rm -rf build/sync-self-test
mkdir -p build/sync-self-test

javac -encoding UTF-8 -d build/sync-self-test \
  sync-core/src/main/java/com/kroxaboom/skazka/data/sync/*.java \
  tests/SyncCoreSelfTest.java

java -cp build/sync-self-test SyncCoreSelfTest

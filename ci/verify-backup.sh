#!/usr/bin/env bash
set -euo pipefail

rm -rf build/self-test
mkdir -p build/self-test

javac -encoding UTF-8 -d build/self-test \
  backup-core/src/main/java/com/kroxaboom/skazka/data/backup/*.java \
  tests/BackupCoreSelfTest.java

java -cp build/self-test BackupCoreSelfTest

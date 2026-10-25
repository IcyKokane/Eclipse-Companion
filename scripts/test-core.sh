#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=$(mktemp -d); trap 'rm -rf "$out"' EXIT
kotlinc app/src/main/java/dev/privatecompanion/eclipse/CompanionLogic.kt app/src/main/java/dev/privatecompanion/eclipse/ReplySessionGate.kt app/src/main/java/dev/privatecompanion/eclipse/ModelFiles.kt app/src/test/java/dev/privatecompanion/eclipse/ModelFilesTest.kt app/src/test/java/dev/privatecompanion/eclipse/ReplySessionGateTest.kt app/src/test/java/dev/privatecompanion/eclipse/CompanionLogicTest.kt -include-runtime -d "$out/checks.jar"
java -jar "$out/checks.jar"

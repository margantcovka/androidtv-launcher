#!/bin/bash
# Собрать release, поставить на телевизор, заранее скомпилировать и запустить.
# Предкомпиляция обязательна: на слабом ТВ JIT во время анимации съедает ~30% CPU.
set -euo pipefail
cd "$(dirname "$0")/.."
SERIAL="${SERIAL:-10.100.102.13:5555}"
ADB="${ADB:-/opt/homebrew/share/android-commandlinetools/platform-tools/adb}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
PKG=io.github.margantcovka.tvlauncher

./gradlew -q assembleRelease
"$ADB" connect "$SERIAL" >/dev/null
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/release/app-release.apk
"$ADB" -s "$SERIAL" shell cmd package compile -m speed -f "$PKG" | grep -v -E "Init wrapper|open mma dev"
"$ADB" -s "$SERIAL" shell am start -S -n "$PKG/.MainActivity" | grep -v -E "Init wrapper|open mma dev"

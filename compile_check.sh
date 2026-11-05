#!/usr/bin/env bash
# Offline compile check of all non-Compose/non-Room Kotlin against android.jar + a litertlm stub. Not a substitute for Gradle.
set -e
L=${GRADLE_LIB:-/opt/gradle-8.14.3/lib}; AJ=${ANDROID_JAR:-/home/claude/ref/androidjar/android-platforms/android-34/android.jar}
STUBS=$(ls tools/stubs/*.kt); OUT=${OUT:-/tmp/kavach-fw-out}
cd "$(dirname "$0")"
CP=$(ls $L/kotlin-compiler-embeddable-*.jar $L/kotlin-stdlib-2*.jar $L/kotlin-script-runtime-*.jar $L/kotlin-reflect-*.jar $L/kotlin-daemon-embeddable-*.jar $L/annotations-*.jar $L/trove4j*.jar $L/kotlinx-coroutines-core-jvm-*.jar | tr '\n' ':')
rm -rf "$OUT"; mkdir -p "$OUT"
FILES=$(grep -L -E "androidx\.(compose|lifecycle|navigation|activity)" $(find app/src/main/java -name '*.kt') || true)
JAVA_TOOL_OPTIONS= java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
  -cp "$(ls $L/kotlin-stdlib-2*.jar):$(ls $L/kotlinx-coroutines-core-jvm-*.jar):$AJ" -d "$OUT" $FILES $STUBS 2>&1 | grep -v "^Picked" || true
echo "compile_check finished (no lines above = no errors)"

#!/usr/bin/env bash
# Compiles the pure-Kotlin core (no Android deps) with the Kotlin compiler bundled in Gradle and runs the JUnit tests on the JVM.
# Useful in environments without the Android SDK. In Android Studio just run: ./gradlew testDebugUnitTest
set -e
L=${GRADLE_LIB:-/opt/gradle-8.14.3/lib}
OUT=${OUT:-/tmp/kavach-core-out}
cd "$(dirname "$0")"
CP=$(ls $L/kotlin-compiler-embeddable-*.jar $L/kotlin-stdlib-2*.jar $L/kotlin-script-runtime-*.jar $L/kotlin-reflect-*.jar $L/kotlin-daemon-embeddable-*.jar $L/annotations-*.jar $L/trove4j*.jar $L/kotlinx-coroutines-core-jvm-*.jar | tr '\n' ':')
RT="$(ls $L/kotlin-stdlib-2*.jar):$(ls $L/kotlinx-coroutines-core-jvm-*.jar):$L/junit-4.13.2.jar:$L/hamcrest-core-1.3.jar"
rm -rf "$OUT"; mkdir -p "$OUT"
JAVA_TOOL_OPTIONS= java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -cp "$RT" -d "$OUT" \
  $(find app/src/main/java/com/kishan/kavach/{core,domain,ai/parser,ai/prompts,agent,automation/external} -name "*.kt" ! -name "ExternalAutomationReceiver.kt" ! -path "*/permissions/*" ! -path "*/core/ui/*" ! -name "Mvi.kt") $(find app/src/test/java/com/kishan/kavach -name 'Core*.kt')
JAVA_TOOL_OPTIONS= java -cp "$OUT:$RT" org.junit.runner.JUnitCore com.kishan.kavach.JsonTest com.kishan.kavach.ParserTest com.kishan.kavach.ValidatorTest com.kishan.kavach.GuardTest com.kishan.kavach.ExecutorTest com.kishan.kavach.AgentLoopTest com.kishan.kavach.SkillAndRouterTest com.kishan.kavach.TaskManagerTest com.kishan.kavach.RetrieverTest com.kishan.kavach.ConditionTest com.kishan.kavach.ExternalContractTest com.kishan.kavach.AutomationEngineTest

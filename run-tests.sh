#!/bin/bash
# iceBrowser 单元测试 —— 纯 JVM，不需要 Android SDK / 模拟器。
#
# AdRules 刻意与 Android 解耦，所以用普通 javac + JUnit 就能跑，
# 不需要 Robolectric（那会引入第三方依赖，违背本项目的底线）。
#
# 依赖：JDK 17+（javac/java）。JUnit 4.13.2 与 hamcrest 按需自动下载到 .test-libs/。
set -euo pipefail
cd "$(dirname "$0")"

JAVA_HOME="${JAVA_HOME:-}"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVAC" >/dev/null 2>&1 || { echo "❌ 找不到 javac，请设置 JAVA_HOME"; exit 1; }

LIB=.test-libs
JUNIT_VER=4.13.2
HAMCREST_VER=1.3
mkdir -p "$LIB"

fetch() {  # url dest
  [ -f "$2" ] && return 0
  echo "下载 $(basename "$2") ..."
  curl -fsSL -o "$2" "$1"
}
M=https://repo1.maven.org/maven2
fetch "$M/junit/junit/$JUNIT_VER/junit-$JUNIT_VER.jar" "$LIB/junit.jar"
fetch "$M/org/hamcrest/hamcrest-core/$HAMCREST_VER/hamcrest-core-$HAMCREST_VER.jar" "$LIB/hamcrest.jar"

CP="$LIB/junit.jar:$LIB/hamcrest.jar"
SRC="src/com/icebrowser/app/AdRules.java"
TESTS=$(find test -name '*Test.java')
OUT=build/test-classes
rm -rf "$OUT" && mkdir -p "$OUT"

echo "编译 ..."
"$JAVAC" -encoding UTF-8 -d "$OUT" -cp "$CP" $SRC $TESTS

CLASSES=()
for t in $TESTS; do
  rel=${t#test/}
  CLASSES+=("$(echo "${rel%.java}" | tr '/' '.')")
done

echo "运行 ${#CLASSES[@]} 个测试类 ..."
"$JAVA" -cp "$OUT:$CP" org.junit.runner.JUnitCore "${CLASSES[@]}"

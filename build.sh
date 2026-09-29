#!/bin/bash
# iceBrowser 构建脚本 —— 任意 Linux + Android SDK 环境可用
#
# 依赖（全部来自 ANDROID_HOME，不再硬编码 Termux 路径）：
#   build-tools 里的 aapt2 / d8 / zipalign / apksigner
#   platforms 里的 android.jar
#   JDK 17 的 javac（替代 Termux 专有的 ecj + dalvikvm）
#
# 环境变量：
#   ANDROID_HOME / ANDROID_SDK_ROOT   Android SDK 根目录
#   JAVA_HOME                         可选，默认取 PATH 里的 javac
#   KEYSTORE / KS_PASS / KEY_PASS     可选，不传则自动生成/复用 build/keystore.jks
set -euo pipefail
cd "$(dirname "$0")"

# ---------- 定位 Android SDK ----------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  for c in "$HOME/Android/Sdk" "$HOME/apkbuild" /usr/local/lib/android/sdk /opt/android-sdk; do
    [ -d "$c" ] && SDK="$c" && break
  done
fi
[ -n "$SDK" ] && [ -d "$SDK" ] || { echo "❌ 未找到 Android SDK，请设置 ANDROID_HOME"; exit 1; }

# 挑最高版本的 build-tools
BT=$(ls -1d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
[ -n "$BT" ] || { echo "❌ $SDK/build-tools 下没有可用版本"; exit 1; }

# 挑 compileSdk：优先 android-35/34，否则取最高
PLATFORM=""
for v in 35 34 33 31 30 29 28 27 26 25 24 23 22 21; do
  [ -f "$SDK/platforms/android-$v/android.jar" ] && PLATFORM="$v" && break
done
[ -n "$PLATFORM" ] || { echo "❌ $SDK/platforms 下没有可用 platform"; exit 1; }
ANDROID_JAR="$SDK/platforms/android-$PLATFORM/android.jar"

AAPT2="$BT/aapt2"
D8="$BT/d8"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"
command -v javac >/dev/null || { echo "❌ 需要 javac（JDK 17）"; exit 1; }

echo "SDK       : $SDK"
echo "build-tools: $(basename "$BT")"
echo "platform  : android-$PLATFORM"

WORK=build
rm -rf "$WORK"
mkdir -p "$WORK"/{res,gen,classes,dex}

# ---------- 1. 资源 ----------
echo "=== 1/6 编译资源 (aapt2) ==="
"$AAPT2" compile --dir res -o "$WORK/res.zip"

echo "=== 2/6 链接资源 + 生成 R.java (aapt2) ==="
LINK_ARGS=(
  link
  -o "$WORK/base.apk"
  -I "$ANDROID_JAR"
  --manifest AndroidManifest.xml
  -R "$WORK/res.zip"
  --java "$WORK/gen"
  --min-sdk-version 21
  --target-sdk-version 30
  --auto-add-overlay
)
[ -d assets ] && LINK_ARGS+=(-A assets)
"$AAPT2" "${LINK_ARGS[@]}"

# ---------- 2. Java ----------
echo "=== 3/6 编译 Java (javac) ==="
SRC=$(find src -name '*.java')
GEN=$(find "$WORK/gen" -name '*.java' 2>/dev/null || true)
javac -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -nowarn -d "$WORK/classes" $SRC $GEN 2>&1 | grep -v 'bootstrap class path' || true

CLASS_COUNT=$(find "$WORK/classes" -name '*.class' | wc -l)
[ "$CLASS_COUNT" -gt 0 ] || { echo "❌ 没有生成 class 文件"; exit 1; }
echo "生成 class 文件: $CLASS_COUNT"

echo "=== 4/6 转 DEX (d8) ==="
"$D8" --min-api 21 --lib "$ANDROID_JAR" --output "$WORK/dex" \
  $(find "$WORK/classes" -name '*.class') > /dev/null
[ -f "$WORK/dex/classes.dex" ] || { echo "❌ d8 失败"; exit 1; }

# ---------- 3. 打包 ----------
echo "=== 5/6 打包 + 对齐 ==="
cp "$WORK/base.apk" "$WORK/unsigned.apk"
(cd "$WORK/dex" && zip -q -j "../unsigned.apk" classes.dex)
"$ZIPALIGN" -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"

echo "=== 6/6 签名 ==="
KS="${KEYSTORE:-$WORK/keystore.jks}"
KS_PASS="${KS_PASS:-icebrowser123}"
KEY_PASS="${KEY_PASS:-icebrowser123}"
if [ ! -f "$KS" ]; then
  keytool -genkey -v -keystore "$KS" -alias icebrowser -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KS_PASS" -keypass "$KEY_PASS" \
    -dname "CN=iceBrowser, OU=App, O=iceBrowser, L=Shanghai, ST=Shanghai, C=CN" 2>/dev/null
fi
"$APKSIGNER" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
  --out icebrowser.apk "$WORK/aligned.apk"
"$APKSIGNER" verify icebrowser.apk > /dev/null && echo "✅ 签名验证通过"

SIZE=$(stat -c%s icebrowser.apk 2>/dev/null || stat -f%z icebrowser.apk)
echo "==== ✅ 成品: icebrowser.apk ($SIZE bytes / $((SIZE/1024)) KB) ===="

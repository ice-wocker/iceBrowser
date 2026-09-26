#!/bin/bash
set -e
cd "$(dirname "$0")"

ANDROID_JAR=~/apkbuild/android.jar
AAPT=/data/data/com.termux/files/usr/bin/aapt
DX=/data/data/com.termux/files/usr/bin/dx
APKSIGNER=/data/data/com.termux/files/usr/bin/apksigner
ZIPALIGN=/data/data/com.termux/files/usr/bin/zipalign
KEYTOOL=/data/data/com.termux/files/usr/bin/keytool
ECJ=/data/data/com.termux/files/usr/share/dex/ecj.jar

WORK=build
rm -rf $WORK
mkdir -p $WORK/{gen,classes,dex}

echo "=== 1. AAPT: 生成 R.java 并打包资源(含 assets) ==="
# -A assets 会把 assets/ 下的全部文件(主页 home.html、广告规则 adblock.txt 等)
# 一起打进 APK，避免只打包单个文件导致运行期读不到资源。
AAPT_EXTRA=""
if [ -d assets ]; then
    AAPT_EXTRA="-A assets"
fi
$AAPT package -f -m -J $WORK/gen -M AndroidManifest.xml -S res -I "$ANDROID_JAR" $AAPT_EXTRA -F $WORK/res.zip

echo "=== 2. ECJ: 编译 Java 源码 ==="
SRC=$(find src -name '*.java')
GEN=$(find $WORK/gen -name '*.java' 2>/dev/null)
dalvikvm -Xmx512m -cp $ECJ org.eclipse.jdt.internal.compiler.batch.Main \
  -proc:none -source 1.8 -target 1.8 -bootclasspath "$ANDROID_JAR" -cp "$ANDROID_JAR" -d $WORK/classes $SRC $GEN

echo "=== 3. DX: 转换为 DEX ==="
$DX --dex --output=$WORK/dex/classes.dex $WORK/classes

echo "=== 4. 打包 APK ==="
cp $WORK/res.zip app-unsigned.apk
(cd $WORK/dex && zip -q -j ../../app-unsigned.apk classes.dex)

echo "=== 5. ZIPALIGN 对齐 ==="
$ZIPALIGN -f 4 app-unsigned.apk app-aligned.apk

echo "=== 6. 签名 ==="
KS=keystore.jks
if [ ! -f "$KS" ]; then
    $KEYTOOL -genkey -v -keystore $KS -alias icebrowser -keyalg RSA -keysize 2048 -validity 10000 \
      -storepass icebrowser123 -keypass icebrowser123 -dname "CN=iceBrowser, OU=App, O=iceBrowser, L=Shanghai, ST=Shanghai, C=CN"
fi
$APKSIGNER sign --ks $KS --ks-pass pass:icebrowser123 --key-pass pass:icebrowser123 --out icebrowser.apk app-aligned.apk

echo "=== 7. 验证签名 ==="
$APKSIGNER verify icebrowser.apk

echo "✅ 成品: icebrowser.apk"
ls -la icebrowser.apk
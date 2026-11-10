#!/usr/bin/env bash
# YÖBF Otomatörü — kurulum gerektirmeyen uygulama imajı üretir (jlink + jpackage --type app-image).
# Windows'ta (Git Bash / GitHub Actions windows-latest) çalıştırıldığında içinde runtime ve .exe
# bulunan klasör ve onun zip'i oluşur. jpackage çapraz derleme yapmaz: Windows paketi Windows'ta üretilir.
#
# Kullanım:  bash packaging/paketle.sh            (testlerle birlikte)
#            SKIP_TESTS=1 bash packaging/paketle.sh
set -euo pipefail

cd "$(dirname "$0")/.."
KOK="$(pwd)"
AD="YOBF-Otomator"
SURUM="$(sed -n 's:.*<version>\([0-9][0-9.]*\)</version>.*:\1:p' pom.xml | head -1)"
ANA_SINIF="legal.erk.yobf.App"
MODULLER="java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,\
java.security.jgss,java.sql,java.xml,java.xml.crypto,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.charsets,\
jdk.localedata,jdk.unsupported,jdk.zipfs,jdk.accessibility"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) OS="windows" ;;
  Darwin*)              OS="macos" ;;
  *)                    OS="linux" ;;
esac

echo ">> Sürüm $SURUM, platform $OS"

MVN_ARGS=(-B -q package)
[[ "${SKIP_TESTS:-0}" == "1" ]] && MVN_ARGS+=(-DskipTests)
mvn "${MVN_ARGS[@]}"

GIRDI="target/jpackage-girdi"
RUNTIME="target/runtime"
rm -rf "$GIRDI" "$RUNTIME" "dist/$AD"
mkdir -p "$GIRDI" dist
cp "target/yobf-otomator-$SURUM.jar" "$GIRDI/"
cp -r target/lib "$GIRDI/lib"

echo ">> jlink: küçük runtime imajı"
jlink --add-modules "$MODULLER" \
      --include-locales=en,tr --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
      --output "$RUNTIME"

echo ">> jpackage: app-image"
JP_ARGS=(--type app-image
         --name "$AD"
         --app-version "$SURUM"
         --vendor "Enerji Grubu Hukuk ve Uyum Musavirligi"
         --description "YOBF Otomatoru"
         --input "$GIRDI"
         --main-jar "yobf-otomator-$SURUM.jar"
         --main-class "$ANA_SINIF"
         --runtime-image "$RUNTIME"
         --java-options "-Dfile.encoding=UTF-8"
         --java-options "-Dsun.jnu.encoding=UTF-8"
         --dest dist)
if [[ -f packaging/yobf.ico && "$OS" == "windows" ]]; then JP_ARGS+=(--icon packaging/yobf.ico); fi
jpackage "${JP_ARGS[@]}"

# Düzenlenebilir dosyalar .exe'nin yanına (derleme dışı): config/, prompts/, assets/
HEDEF="dist/$AD"
[[ "$OS" == "macos" ]] && HEDEF="dist/$AD.app/Contents"
cp -r config prompts assets "$HEDEF/"
cp packaging/BENIOKU.txt "$HEDEF/" 2>/dev/null || true

ZIP="dist/$AD-$SURUM-$OS.zip"
rm -f "$ZIP"
echo ">> zip: $ZIP"
if [[ "$OS" == "windows" ]]; then
  powershell -NoProfile -Command "Compress-Archive -Path 'dist/$AD' -DestinationPath '$ZIP' -Force"
else
  (cd dist && zip -qr "$(basename "$ZIP")" "$AD"*)
fi

echo ">> Tamam: $KOK/$ZIP"

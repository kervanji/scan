#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout)}"
PLATFORM="${PLATFORM:-win}"
OUT_DIR="$ROOT/release-output/qr-attendance-${VERSION}-${PLATFORM}"

case "$PLATFORM" in
  win) NATIVE_PLATFORM="windows-x86_64" ;;
  mac-aarch64) NATIVE_PLATFORM="macosx-arm64" ;;
  mac|mac-x86_64) NATIVE_PLATFORM="macosx-x86_64" ;;
  linux) NATIVE_PLATFORM="linux-x86_64" ;;
  *)
    echo "Unknown PLATFORM: $PLATFORM" >&2
    exit 1
    ;;
esac

echo "==> Building v${VERSION} for platform: ${PLATFORM} (native: ${NATIVE_PLATFORM})"

mvn -B clean package -Prelease -DskipTests \
  -Djavafx.platform="${PLATFORM}" \
  -Dnative.platform="${NATIVE_PLATFORM}" \
  -Dupdate.manifest.url="https://raw.githubusercontent.com/kervanji/scan/main/updates/version.json" \
  -Dupdate.github.repo="kervanji/scan"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/lib"

cp "target/qr-attendance-${VERSION}.jar" "$OUT_DIR/"
cp -R target/lib/. "$OUT_DIR/lib/"

if [[ "$PLATFORM" == "win" ]]; then
  cp release-templates/start-windows.bat "$OUT_DIR/Start Attendance.bat"
  if [[ -f "target/Murakib Attendance.exe" ]]; then
    cp "target/Murakib Attendance.exe" "$OUT_DIR/"
  else
    echo "WARNING: target/Murakib Attendance.exe not found — run mvn package -Prelease first" >&2
  fi
else
  cp release-templates/start-mac.sh "$OUT_DIR/start.sh"
  chmod +x "$OUT_DIR/start.sh"
fi

cp release-templates/README-RELEASE.txt "$OUT_DIR/README.txt"

ZIP="$ROOT/release-output/qr-attendance-${VERSION}-${PLATFORM}.zip"
rm -f "$ZIP"

create_zip() {
  local folder="$1"
  local archive="$2"
  if command -v zip >/dev/null 2>&1; then
    (cd "$(dirname "$folder")" && zip -r "$(basename "$archive")" "$(basename "$folder")")
  elif [[ "${RUNNER_OS:-}" == "Windows" ]] || [[ "$(uname -s 2>/dev/null)" == MINGW* ]]; then
    powershell.exe -NoProfile -Command \
      "Compress-Archive -Path '$(cygpath -w "$folder")' -DestinationPath '$(cygpath -w "$archive")' -Force"
  else
    echo "zip not found and no Windows fallback available" >&2
    exit 1
  fi
}

hash_file() {
  local file="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  else
    powershell.exe -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '$(cygpath -w "$file")').Hash.ToLower()"
  fi
}

create_zip "$OUT_DIR" "$ZIP"

JAR="$OUT_DIR/qr-attendance-${VERSION}.jar"
SHA256="$(hash_file "$JAR")"
ZIP_SHA256="$(hash_file "$ZIP")"

mkdir -p updates
cat > "updates/version-${PLATFORM}.json" <<EOF
{
  "version": "${VERSION}",
  "downloadUrl": "https://github.com/kervanji/scan/releases/download/v${VERSION}/qr-attendance-${VERSION}-${PLATFORM}.zip",
  "sha256": "${ZIP_SHA256}",
  "releaseNotes": "Release v${VERSION} (${PLATFORM})",
  "mandatory": false,
  "minVersion": "1.0.0"
}
EOF

if [[ "$PLATFORM" == "win" ]]; then
  cp "updates/version-${PLATFORM}.json" updates/version.json
fi

echo ""
echo "✅ Done"
echo "   Folder: $OUT_DIR"
echo "   Zip:    $ZIP"
echo "   JAR SHA256: $SHA256"
echo "   ZIP SHA256: $ZIP_SHA256"
echo "   Manifest:   updates/version.json"

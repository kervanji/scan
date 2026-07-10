#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout)}"
PLATFORM="${PLATFORM:-win}"
OUT_DIR="$ROOT/release-output/qr-attendance-${VERSION}-${PLATFORM}"

echo "==> Building v${VERSION} for platform: ${PLATFORM}"

mvn -B clean package -Prelease -DskipTests \
  -Djavafx.platform="${PLATFORM}" \
  -Dupdate.manifest.url="https://raw.githubusercontent.com/kervanji/scan/main/updates/version.json" \
  -Dupdate.github.repo="kervanji/scan"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/lib"

cp "target/qr-attendance-${VERSION}.jar" "$OUT_DIR/"
cp -R target/lib/. "$OUT_DIR/lib/"

if [[ "$PLATFORM" == "win" ]]; then
  cp release-templates/start-windows.bat "$OUT_DIR/Start Attendance.bat"
else
  cp release-templates/start-mac.sh "$OUT_DIR/start.sh"
  chmod +x "$OUT_DIR/start.sh"
fi

cp release-templates/README-RELEASE.txt "$OUT_DIR/README.txt"

ZIP="$ROOT/release-output/qr-attendance-${VERSION}-${PLATFORM}.zip"
rm -f "$ZIP"
(cd "$ROOT/release-output" && zip -r "$(basename "$ZIP")" "$(basename "$OUT_DIR")")

JAR="$OUT_DIR/qr-attendance-${VERSION}.jar"
SHA256="$(shasum -a 256 "$JAR" | awk '{print $1}')"
ZIP_SHA256="$(shasum -a 256 "$ZIP" | awk '{print $1}')"

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

#!/bin/bash
cd "$(dirname "$0")"
JAR=$(ls qr-attendance-*.jar 2>/dev/null | head -1)
if [[ -z "$JAR" ]]; then
  echo "لم يُعثر على ملف التطبيق"
  exit 1
fi
exec java --module-path lib --add-modules javafx.controls,javafx.fxml,javafx.swing \
  -cp "${JAR}:lib/*" com.murakib.attendance.Main

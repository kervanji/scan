@echo off
cd /d "%~dp0"
title QR Attendance

where java >nul 2>nul
if errorlevel 1 (
  echo Java 17+ مطلوب. حمّله من https://adoptium.net
  pause
  exit /b 1
)

set MODULES=javafx.controls,javafx.fxml,javafx.swing
set CP=qr-attendance-*.jar;lib\*

for %%f in (qr-attendance-*.jar) do set APPJAR=%%f

java --module-path lib --add-modules %MODULES% -cp "%APPJAR%;lib\*" com.murakib.attendance.Main

if errorlevel 1 pause

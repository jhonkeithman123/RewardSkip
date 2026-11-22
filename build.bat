@echo off
REM RewardSkip one-click builder (Windows)
REM Usage:
REM   build.bat                       build RewardSkip.apk
REM   build.bat --install             build and install on the connected device
REM   build.bat --install --serial X  target a specific device
REM   build.bat --sdk C:\path\to\Sdk  use a custom Android SDK
cd /d "%~dp0"

where python >nul 2>nul
if errorlevel 1 (
  echo Python was not found on PATH. Install Python 3 and try again.
  exit /b 1
)

echo === RewardSkip builder ===
python scripts\build.py %*
exit /b %ERRORLEVEL%

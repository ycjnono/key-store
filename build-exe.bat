@echo off
setlocal

cd /d "%~dp0"

echo ============================================================
echo   Key-Store - Build Windows EXE
echo ============================================================
echo.

where node >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Node.js not found. Install Node.js 18 LTS or newer.
    echo Download: https://nodejs.org/
    pause
    exit /b 1
)

where npm >nul 2>&1
if errorlevel 1 (
    echo [ERROR] npm not found.
    pause
    exit /b 1
)

echo [1/3] Installing dependencies...
call "%~dp0install-deps.bat"
if errorlevel 1 (
    echo [ERROR] Dependency install failed.
    pause
    exit /b 1
)

echo.
echo [2/5] Generating icons from logo...
call npm --userconfig "%~dp0.npmrc.user" run prepare:icons
if errorlevel 1 (
    echo [ERROR] Icon preparation failed. Ensure assets/logo.png exists and Python+Pillow installed.
    pause
    exit /b 1
)

echo.
echo [3/5] Cleaning dist/ ...
call npm --userconfig "%~dp0.npmrc.user" run clean:dist
if errorlevel 1 (
    echo [ERROR] Failed to clean dist/. Close Key-Store and retry.
    pause
    exit /b 1
)

echo.
echo [4/5] Rebuilding native modules...
call npm --userconfig "%~dp0.npmrc.user" run postinstall
if errorlevel 1 (
    echo [WARN] postinstall failed, continuing build...
)

echo.
echo [5/5] Building Windows installer...
call npm --userconfig "%~dp0.npmrc.user" run build
if errorlevel 1 (
    echo [ERROR] Build failed.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   Build complete.
echo   Output: release\KeyStore-1.0.2-Setup.exe
echo ============================================================
pause

@echo off
setlocal

cd /d "%~dp0"

echo ============================================================
echo   Key-Store - install dependencies (npmmirror)
echo ============================================================
echo.

where node >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Node.js not found. Install Node.js 18+ LTS first.
    pause
    exit /b 1
)

set ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
set ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/
set PREBUILD_INSTALL_TIMEOUT=120000
set npm_config_better_sqlite3_binary_host_mirror=https://npmmirror.com/mirrors/better-sqlite3/
set npm_config_electron_mirror=https://npmmirror.com/mirrors/electron/

echo Using .npmrc.user to override broken global taobao mirrors...
echo.

call npm install --userconfig "%~dp0.npmrc.user" %*
set EXIT_CODE=%ERRORLEVEL%

if %EXIT_CODE% neq 0 (
    echo.
    echo [ERROR] npm install failed.
    echo If node-gyp / Visual Studio is mentioned, better-sqlite3 prebuild download failed.
    echo Check network or install Visual Studio Build Tools with C++ desktop workload.
    pause
    exit /b %EXIT_CODE%
)

echo.
echo Dependencies installed successfully.
exit /b 0

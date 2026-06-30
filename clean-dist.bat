@echo off
cd /d "%~dp0"
call npm --userconfig "%~dp0.npmrc.user" run clean:dist
exit /b %ERRORLEVEL%

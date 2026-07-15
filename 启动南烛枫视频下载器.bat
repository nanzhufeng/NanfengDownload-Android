@echo off
chcp 65001 >nul
title Nanzhufeng Video Downloader
cd /d "%~dp0"

set "LOG=%~dp0startup-log.txt"
echo [%date% %time%] Starting app... > "%LOG%"
echo Working directory: %cd% >> "%LOG%"

set "PYTHONW="
for /f "delims=" %%P in ('where.exe pythonw.exe 2^>nul') do (
    if not defined PYTHONW set "PYTHONW=%%P"
)

if defined PYTHONW (
    start "" /D "%~dp0" "%PYTHONW%" "%~dp0start.py"
    exit /b 0
)

echo Starting Nanzhufeng Video Downloader...
echo Log file: %LOG%
echo.
echo pythonw.exe was not found, using visible Python fallback. >> "%LOG%"

python "%~dp0start.py" >> "%LOG%" 2>&1
set "EXITCODE=%ERRORLEVEL%"

echo.
if "%EXITCODE%"=="0" (
    echo App closed.
    echo [%date% %time%] App closed. Exit code: %EXITCODE% >> "%LOG%"
) else (
    echo Failed to start. Exit code: %EXITCODE%
    echo.
    type "%LOG%"
    echo.
    echo Send startup-log.txt to Codex for troubleshooting.
    echo.
    pause
)

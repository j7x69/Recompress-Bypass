@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

if "%~1"=="" (
    echo ========================================================
    echo   MP4 Quality Bypass Optimizer - Drag and Drop
    echo ========================================================
    echo.
    echo Please drag and drop one or multiple .mp4 files onto this file!
    echo.
    pause
    exit /b
)

echo [*] Launching MP4 Quality Bypass Optimizer...
python "%~dp0optimizer.py" %*

echo.
echo Press any key to exit...
pause >nul

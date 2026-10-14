@echo off
chcp 65001 >nul
title MP4 Quality Bypass Optimizer - Drag ^& Drop

set "PYTHON_EXE=python"
where %PYTHON_EXE% >nul 2>&1
if %ERRORLEVEL% neq 0 (
    set "PYTHON_EXE=py"
    where py >nul 2>&1
    if %ERRORLEVEL% neq 0 (
        echo [!] Loi: Khong tim thay Python! Vui long cai dat Python hoac them Python vao PATH.
        echo.
        pause
        exit /b 1
    )
)

:: Auto-detect optimizer_v2.py in current directory or fallback paths
set "SCRIPT_PATH=%~dp0optimizer_v2.py"
if not exist "%SCRIPT_PATH%" (
    set "SCRIPT_PATH=G:\videos\optimizer_v2.py"
)
if not exist "%SCRIPT_PATH%" (
    set "SCRIPT_PATH=G:\Dowload\optimizer_v2.py"
)
if not exist "%SCRIPT_PATH%" (
    set "SCRIPT_PATH=%~dp0optimizer.py"
)
if not exist "%SCRIPT_PATH%" (
    set "SCRIPT_PATH=G:\videos\optimizer.py"
)

if not exist "%SCRIPT_PATH%" (
    echo [!] Loi: Khong tim thay file script optimizer_v2.py!
    echo Vui long kiem tra file optimizer_v2.py trong thu muc.
    echo.
    pause
    exit /b 1
)

if "%~1"=="" goto :NO_ARGS

echo ======================================================================
echo           MP4 Quality Bypass Optimizer - Drag ^& Drop Mode
echo ======================================================================
echo.
echo Dang chay script: %SCRIPT_PATH%
echo Dang xu ly cac file duoc tha vao...
echo.

"%PYTHON_EXE%" "%SCRIPT_PATH%" -v %*
goto :END

:NO_ARGS
echo ======================================================================
echo           MP4 Quality Bypass Optimizer (400 FPS Bypass)
echo ======================================================================
echo.
echo   HUONG DAN SU DUNG:
echo   - Keo va tha (Drag ^& Drop) mot hoac nhieu file video MP4 vao file .bat nay.
echo   - Hoac nhap / dan duong dan file video ben duoi roi nhan Enter:
echo.
set /p "USER_INPUT=>> Nhap duong dan file video: "
if "%USER_INPUT%"=="" (
    echo Khong co file nao duoc chon.
    goto :END
)

cls
echo ======================================================================
echo   Dang xu ly video...
echo ======================================================================
echo.
"%PYTHON_EXE%" "%SCRIPT_PATH%" -v "%USER_INPUT:"=%"

:END
echo.
echo ======================================================================
echo   Da hoan tat xu ly! Nhan phim bat ky de dong cua so...
echo ======================================================================
pause >nul

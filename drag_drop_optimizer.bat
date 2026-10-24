@echo off
chcp 65001 >nul
title MP4 Quality Bypass Optimizer v3 - Drag ^& Drop

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

:: Auto-detect optimizer_v3.py in current directory or fallback paths
set "SCRIPT_PATH=%~dp0optimizer_v3.py"
if not exist "%SCRIPT_PATH%" (
    set "SCRIPT_PATH=G:\videos\davinci export\optimizer_v3.py"
)
if not exist "%SCRIPT_PATH%" (
    set "SCRIPT_PATH=%~dp0optimizer.py"
)
if not exist "%SCRIPT_PATH%" (
    set "SCRIPT_PATH=G:\videos\optimizer_v3.py"
)
if not exist "%SCRIPT_PATH%" (
    set "SCRIPT_PATH=G:\videos\optimizer.py"
)

if not exist "%SCRIPT_PATH%" (
    echo [!] Loi: Khong tim thay file script optimizer_v3.py!
    echo Vui long kiem tra file optimizer_v3.py trong thu muc: %~dp0
    echo.
    pause
    exit /b 1
)

if "%~1"=="" goto :NO_ARGS

echo ======================================================================
echo          MP4 Quality Bypass Optimizer v3 - Drag ^& Drop Mode
echo ======================================================================
echo.
echo Dang chay script: %SCRIPT_PATH%
echo Dang xu ly cac file duoc tha vao...
echo.

"%PYTHON_EXE%" "%SCRIPT_PATH%" -v %*

echo.
echo ======================================================================
echo   Da hoan tat xu ly! Nhan phim bat ky de dong cua so...
echo ======================================================================
pause >nul
goto :EOF

:NO_ARGS
:: Khi mo truc tiep (nhap dup chuot), chuyen quyen xu ly tuong tac sang script Python
"%PYTHON_EXE%" "%SCRIPT_PATH%" -v

@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

echo ========================================================
echo    RecompressBypass - Auto Version Bump ^& APK Builder
echo ========================================================

set NEW_VER=%1
if "%NEW_VER%"=="" (
    set /p NEW_VER="👉 Nhập phiên bản mới (ví dụ 1.0.7): "
)

if "%NEW_VER%"=="" (
    echo [ERROR] Bạn chưa nhập phiên bản!
    pause
    exit /b 1
)

echo.
echo [*] Đang cập nhật phiên bản lên: %NEW_VER% ...

powershell -NoProfile -Command ^
    "$v = '%NEW_VER%'.Trim().TrimStart('v','V').TrimStart('.');" ^
    "$parts = $v.Split('.') | ForEach-Object { [int]$_ };" ^
    "while ($parts.Count -lt 4) { $parts += 0 };" ^
    "$code = $parts[0] * 1000000 + $parts[1] * 10000 + $parts[2] * 100 + $parts[3];" ^
    "$gradleFile = 'app\build.gradle.kts';" ^
    "$content = Get-Content $gradleFile -Raw;" ^
    "$content = $content -replace 'versionCode\s*=\s*\d+', ('versionCode = ' + $code);" ^
    "$content = $content -replace 'versionName\s*=\s*\"[^\"]+\"', ('versionName = \"' + $v + '\"');" ^
    "Set-Content -Path $gradleFile -Value $content -NoNewline;" ^
    "Write-Host '[OK] build.gradle.kts: versionCode =' $code ', versionName =' $v;"

echo.
echo [*] Đang biên dịch Release APK (R8/ProGuard) & Debug APK với Gradle...
set JAVA_HOME=G:\ADRstudio\jbr
call gradlew.bat assembleRelease assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Biên dịch thất bại! Vui lòng kiểm tra lỗi bên trên.
    pause
    exit /b 1
)

echo.
echo ========================================================
echo    BIÊN DỊCH HOÀN TẤT THÀNH CÔNG!
echo    Phiên bản: %NEW_VER%
echo    Thư mục xuất chuẩn: release\
echo    - Release APK: release\RecompressBypass-v%NEW_VER%-release.apk
echo    - Debug APK:   release\RecompressBypass-v%NEW_VER%-debug.apk
echo ========================================================
echo.
echo Bạn chỉ cần lấy file Release trong thư mục "release" để up GitHub Release!
pause

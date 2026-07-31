@echo off
chcp 65001 >nul
echo ==========================================
echo  KPKN Beta 8.9.1 - Build Base Release
echo ==========================================
cd /d "C:\Users\valen\Documents\KPKNFit\android-native"
if errorlevel 1 (
    echo ERROR: No se encontro la carpeta del proyecto.
    pause
    exit /b 1
)

echo.
echo Iniciando build :app:assembleBaseRelease ...
echo Esto puede tardar varios minutos.
echo.

rem Intentamos usar daemon si esta caliente. Si falla, usamos --no-daemon.
call gradlew.bat :app:assembleBaseRelease

if errorlevel 1 (
    echo.
    echo BUILD FALLO. Intentando con --no-daemon ...
    call gradlew.bat :app:assembleBaseRelease --no-daemon
)

if errorlevel 1 (
    echo.
    echo ERROR: El build fallo. Revisa los mensajes arriba.
    pause
    exit /b 1
)

set APK_SRC=app\build\outputs\apk\base\release\app-base-release.apk
set APK_DST=G:\KPKN-Beta-8.9.1.apk

if not exist "%APK_SRC%" (
    echo ERROR: No se encontro el APK generado en %APK_SRC%
    pause
    exit /b 1
)

copy /Y "%APK_SRC%" "%APK_DST%"
if errorlevel 1 (
    echo ERROR: No se pudo copiar a G:\
    pause
    exit /b 1
)

echo.
echo ==========================================
echo  BUILD EXITOSO
echo  APK copiado a: %APK_DST%
echo ==========================================
pause

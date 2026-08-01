@echo off
cd /d C:\Users\valen\Documents\KPKNFit\android-native
call C:\Users\valen\Documents\KPKNFit\android-native\gradlew.bat --no-daemon :app:compileBaseDebugKotlin > C:\Users\valen\Documents\KPKNFit\build_result.txt 2>&1
echo EXIT=%ERRORLEVEL% >> C:\Users\valen\Documents\KPKNFit\build_result.txt

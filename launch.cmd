@echo off
cd /d "%LOCALAPPDATA%\Android\Sdk\emulator"
echo Launching emulator...
emulator.exe -avd Pixel_9_Pro_XL
echo Emulator exited with code %ERRORLEVEL%

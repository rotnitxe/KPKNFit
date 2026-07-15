$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $scriptDir "app\build\outputs\apk\base\debug\app-base-debug.apk"
$devices = & $adb devices | Select-String -Pattern "emulator-\d+\s+device"
if ($devices) {
    $device = ($devices[0].Line -split '\s+')[0]
} else {
    $device = "emulator-5554"
}

Write-Host ">> Building..." -ForegroundColor Cyan
Push-Location $scriptDir
.\gradlew.bat assembleBaseDebug
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Write-Host "BUILD FAILED" -ForegroundColor Red
    exit 1
}

Write-Host ">> Installing..." -ForegroundColor Cyan
& $adb -s $device install -r $apk

Write-Host ">> Launching..." -ForegroundColor Cyan
& $adb -s $device shell am start -n "com.example.kpkn/.MainActivity"
Pop-Location

Write-Host ">> Done!" -ForegroundColor Green

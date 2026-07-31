# Build script for KPKN Beta 8.9.1 (base release)
$ErrorActionPreference = "Stop"
$repo = "C:\Users\valen\Documents\KPKNFit\android-native"
$outputApk = "G:\KPKN-Beta-8.9.1.apk"
$flagFile = "$env:TEMP\kpkn-build-done.flag"

Remove-Item -Path $flagFile -ErrorAction SilentlyContinue

Set-Location $repo

Write-Host "Starting Gradle build :app:assembleBaseRelease ..."
# Use --no-daemon to avoid leaving a long-lived process, but if you have a warm daemon it may be slower.
& .\gradlew.bat :app:assembleBaseRelease --no-daemon

if ($LASTEXITCODE -ne 0) {
    Write-Error "BUILD FAILED (exit code $LASTEXITCODE). Check output above."
    exit 1
}

$apk = Get-ChildItem -Path "$repo\app\build\outputs\apk\base\release\*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) {
    Write-Error "APK not found after build."
    exit 1
}

Copy-Item $apk.FullName $outputApk -Force
Write-Host "APK copied to $outputApk"
New-Item -Path $flagFile -ItemType File -Value "OK" -Force | Out-Null

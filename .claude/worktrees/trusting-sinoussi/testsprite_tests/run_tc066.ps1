# Ejecuta el test TC066 (detección de pantallas negras y errores)
# Requisitos: Python 3, playwright (pip install playwright; playwright install chromium)
# La app debe estar sirviendo en http://localhost:5500 (npm run serve)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

Set-Location $projectRoot

Write-Host "=== TC066: Black screen and error detection ===" -ForegroundColor Cyan
Write-Host "Asegúrate de que la app está en http://localhost:5500 (npm run serve)" -ForegroundColor Yellow
Write-Host ""

python testsprite_tests/TC066_Black_screen_and_error_detection_smoke_test.py

Write-Host ""
Write-Host "=== TC066 completado ===" -ForegroundColor Cyan

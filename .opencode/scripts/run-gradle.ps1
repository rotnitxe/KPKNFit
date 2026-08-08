param(
  [Parameter(Mandatory=$true, Position=0)]
  [string]$Tasks,
  [string]$WorkDir = "android-native",
  [int]$TimeoutSec = 600,
  [switch]$StopDaemonsFirst
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$gradleDir = Join-Path $repoRoot $WorkDir
if (-not (Test-Path $gradleDir)) {
  Write-Error "WorkDir no existe: $gradleDir"
  exit 2
}

if ($StopDaemonsFirst) {
  try {
    Push-Location $gradleDir
    & ".\gradlew.bat" --stop --console=plain 2>&1 | Out-Null
  } catch {}
  finally { Pop-Location }
}

# Forzar flags que evitan el hang en Windows/OpenCode:
# --no-daemon evita que el daemon quede con handles de stdout/stderr abiertos
# --console=plain evita ANSI/rich console que confunde el capturador
# -Dorg.gradle.daemon=false es redundancia por si el wrapper ignora --no-daemon
$extraFlags = "--no-daemon --console=plain --warning-mode=summary -Dorg.gradle.daemon=false"

$cmd = ".\gradlew.bat $extraFlags $Tasks"
Write-Host ">> $cmd (workdir=$gradleDir, timeout=${TimeoutSec}s)" -ForegroundColor Cyan

# Usar cmd /c para desacoplar handles del daemon (fix definitivo en pwsh + gradle 9.x)
# y capturar salida a archivo para no bloquear el pipe de OpenCode
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logFile = Join-Path $env:TEMP "opencode-gradle-$stamp.log"
$proc = $null
try {
  Push-Location $gradleDir
  # Start-Process con redireccion evita que pwsh quede esperando handles heredados
  $proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c $cmd 1`>`"$logFile`" 2`>`&1" -PassThru -NoNewWindow
  $exited = $proc.WaitForExit($TimeoutSec * 1000)
  if (-not $exited) {
    Write-Warning "Timeout ${TimeoutSec}s alcanzado, matando arbol gradle..."
    try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch {}
    # Matar java daemons huerfanos del mismo worktree (solo daemons de este repo)
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" 2>$null | Where-Object { $_.CommandLine -like "*GradleDaemon*" } | ForEach-Object {
      try { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } catch {}
    }
    Write-Host "--- LOG (tail) ---" -ForegroundColor Yellow
    if (Test-Path $logFile) { Get-Content $logFile -Tail 200 }
    exit 124
  }
  $exitCode = $proc.ExitCode
  # Volcar log al stdout de OpenCode de forma truncada (evita pipe colgado)
  if (Test-Path $logFile) {
    $lines = Get-Content $logFile
    $maxLines = 1800
    if ($lines.Count -gt $maxLines) {
      Write-Host "--- LOG truncado ($($lines.Count) lineas, mostrando ultimas $maxLines) ---" -ForegroundColor Yellow
      $lines | Select-Object -Last $maxLines | ForEach-Object { Write-Host $_ }
      Write-Host "--- log completo en $logFile ---" -ForegroundColor Yellow
    } else {
      $lines | ForEach-Object { Write-Host $_ }
    }
  }
  exit $exitCode
} finally {
  Pop-Location
}

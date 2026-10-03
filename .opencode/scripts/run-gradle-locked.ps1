param(
  [Parameter(Mandatory=$true, Position=0)]
  [string]$Tasks,
  [string]$Owner = $env:KPKN_SESSION_NAME,
  [string]$WorkDir = "android-native",
  [int]$TimeoutSec = 600,
  [int]$WaitSec = 900,
  [switch]$StopDaemonsFirst
)

# Wrapper con candado para que varias sesiones/agentes que comparten este repo no lancen
# Gradle (ni installDebug / connectedAndroidTest / QA de emulador) al mismo tiempo.
# - Adquiere el candado creando atomicamente android-native/.gradle-session.lock/
# - Escribe owner.json {session, tasks, startedAt, pid}
# - Espera sondeando cada 30 s hasta -WaitSec; candado obsoleto si el pid dueno murio o lleva > 60 min
# - Delega en run-gradle.ps1 (anti-hang: --no-daemon --console=plain) y libera en finally.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$lockDir = Join-Path $repoRoot "android-native\.gradle-session.lock"
$ownerFile = Join-Path $lockDir "owner.json"
if (-not $Owner) { $Owner = "unknown-$PID" }

function Try-AcquireLock {
  try { New-Item -ItemType Directory -Path $lockDir -ErrorAction Stop | Out-Null; return $true } catch { return $false }
}

$deadline = (Get-Date).AddSeconds($WaitSec)
while (-not (Try-AcquireLock)) {
  $info = $null
  if (Test-Path $ownerFile) { try { $info = Get-Content $ownerFile -Raw | ConvertFrom-Json } catch { $info = $null } }
  $stale = $false
  if (-not $info) {
    $dirAgeSec = ((Get-Date) - (Get-Item $lockDir).CreationTime).TotalSeconds
    if ($dirAgeSec -gt 120) { $stale = $true }
  } else {
    $ownerAlive = $false
    if ($info.pid) { $ownerAlive = [bool](Get-Process -Id ([int]$info.pid) -ErrorAction SilentlyContinue) }
    $ageMin = 9999
    if ($info.startedAt) { try { $ageMin = ((Get-Date) - [datetime]::Parse($info.startedAt)).TotalMinutes } catch {} }
    if ((-not $ownerAlive) -or ($ageMin -gt 60)) { $stale = $true }
  }
  if ($stale) {
    Write-Warning "Candado de Gradle obsoleto (dueno sin proceso vivo o > 60 min). Retirando."
    Remove-Item -Recurse -Force $lockDir -ErrorAction SilentlyContinue
    continue
  }
  if ((Get-Date) -gt $deadline) {
    $desc = if ($info) { ($info | ConvertTo-Json -Compress) } else { "(sin owner.json)" }
    Write-Host "!! Timeout de $WaitSec s esperando el candado de Gradle. Dueno actual: $desc (candado NO retirado)" -ForegroundColor Red
    exit 125
  }
  $who = if ($info) { "$($info.session) [$($info.tasks)] desde $($info.startedAt)" } else { "desconocido" }
  Write-Host ">> Gradle ocupado por $who; reintento en 30 s" -ForegroundColor Yellow
  Start-Sleep -Seconds 30
}

$code = 1
try {
  @{ session = $Owner; tasks = $Tasks; startedAt = (Get-Date).ToString("o"); pid = $PID } |
    ConvertTo-Json -Compress | Set-Content -Path $ownerFile -Encoding UTF8
  Write-Host ">> Candado de Gradle adquirido por '$Owner'" -ForegroundColor Green
  $inner = Join-Path $PSScriptRoot "run-gradle.ps1"
  $argList = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $inner, "-Tasks", $Tasks, "-WorkDir", $WorkDir, "-TimeoutSec", $TimeoutSec)
  if ($StopDaemonsFirst) { $argList += "-StopDaemonsFirst" }
  & powershell @argList
  $code = $LASTEXITCODE
} finally {
  Remove-Item -Recurse -Force $lockDir -ErrorAction SilentlyContinue
  Write-Host ">> Candado de Gradle liberado" -ForegroundColor Green
}
exit $code

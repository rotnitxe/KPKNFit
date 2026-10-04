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

# Normalizar comillas simples a dobles: cmd y el launcher de java no tratan ' como
# delimitador, asi que '--tests *.Foo' llegaria a Gradle con las comillas literales
# en el patron y fallaria con "No tests found for given includes".
# Con comillas dobles, cmd/java las agrupan y las eliminan antes de Gradle.
$normalizedTasks = $Tasks.Replace("'", '"')
$cmd = ".\gradlew.bat $extraFlags $normalizedTasks"
Write-Host ">> $cmd (workdir=$gradleDir, timeout=${TimeoutSec}s)" -ForegroundColor Cyan

# Usar cmd /c para desacoplar handles del daemon (fix definitivo en pwsh + gradle 9.x)
# y capturar salida a archivo para no bloquear el pipe de OpenCode
$stamp = (Get-Date -Format "yyyyMMdd-HHmmss-fff") + "-$PID"
$logFile = Join-Path $env:TEMP "opencode-gradle-$stamp.log"
$proc = $null
try {
  Push-Location $gradleDir
  # Process.Start conserva el handle y el ExitCode incluso si cmd termina enseguida.
  # Start-Process -PassThru podia devolver ExitCode=$null; "exit $null" reportaba exito ante un build fallido.
  $startInfo = New-Object System.Diagnostics.ProcessStartInfo
  $startInfo.FileName = $env:ComSpec
  $startInfo.Arguments = "/c $cmd 1`>`"$logFile`" 2`>`&1"
  $startInfo.WorkingDirectory = $gradleDir
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $proc = New-Object System.Diagnostics.Process
  $proc.StartInfo = $startInfo
  if (-not $proc.Start()) { throw "No se pudo iniciar Gradle" }
  $exited = $proc.WaitForExit($TimeoutSec * 1000)
  if (-not $exited) {
    Write-Warning "Timeout ${TimeoutSec}s alcanzado, matando arbol gradle..."
    # Solo el arbol del cmd que iniciamos: otros worktrees/sesiones pueden tener su propio Gradle.
    # Un filtro por "GradleDaemon" mataba tambien compilaciones ajenas al llegar a este timeout.
    try { & "$env:SystemRoot\System32\taskkill.exe" /PID $proc.Id /T /F 2>$null | Out-Null } catch {}
    Write-Host "--- LOG (tail) ---" -ForegroundColor Yellow
    if (Test-Path $logFile) { Get-Content $logFile -Tail 200 }
    exit 124
  }
  $exitCode = $proc.ExitCode
  if ($null -eq $exitCode) { throw "Gradle termino sin codigo de salida verificable" }
  # Volcar log al stdout de OpenCode de forma truncada (evita pipe colgado)
  if (Test-Path $logFile) {
    $lines = @(Get-Content $logFile)
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
  if ($null -ne $proc) { $proc.Dispose() }
  Pop-Location
}

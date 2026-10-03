param(
  [Parameter(Mandatory=$true)][string]$Project,
  [Parameter(Mandatory=$true)][string[]]$Tasks,
  [Parameter(Mandatory=$true)][string]$LogName,
  [string]$Label = 'unlabeled',
  [string]$TestHeap = '2g',
  [switch]$ForkEveryClass,
  [string]$Exclude = '',
  [string]$TaskTimeoutMin = '',
  [int]$WaitMinutes = 240
)
# Serialises every Gradle invocation of the consolidation (one build at a time on this machine).
# A named OS mutex is released automatically if the holder dies.
$ErrorActionPreference = 'Stop'
$C = 'C:\Users\valen\Documents\KPKNFit\artifacts\consolidation-20261001'
$logs = Join-Path $C 'logs'; $receipts = Join-Path $C 'receipts'
New-Item -ItemType Directory -Force $logs, $receipts | Out-Null
$mutex = New-Object System.Threading.Mutex($false, 'Global\KPKNFitGradleSerial')
$acquired = $false
$waitStart = Get-Date
try {
  try { $acquired = $mutex.WaitOne([TimeSpan]::FromMinutes($WaitMinutes)) }
  catch [System.Threading.AbandonedMutexException] { $acquired = $true }
  if (-not $acquired) { Write-Output "TIMEOUT waiting for Gradle slot"; exit 90 }

  $init = Join-Path $C 'tools\test-jvm.init.gradle'
  $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
  $env:KPKN_TEST_HEAP = $TestHeap
  $env:KPKN_FORK_EVERY = $(if ($ForkEveryClass) { '1' } else { '0' })
  $env:KPKN_EXCLUDE = $Exclude
  $env:KPKN_TASK_TIMEOUT_MIN = $TaskTimeoutMin
  $jvm = @(
    '--add-opens=java.base/java.lang=ALL-UNNAMED','--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
    '--add-opens=java.base/java.util=ALL-UNNAMED','--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED',
    '--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED','--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED',
    '--add-opens=java.base/java.nio.charset=ALL-UNNAMED','--add-opens=java.base/java.net=ALL-UNNAMED',
    '--add-opens=java.base/java.util.concurrent=ALL-UNNAMED','--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED',
    '--add-opens=java.xml/javax.xml.namespace=ALL-UNNAMED','--add-opens=java.base/java.time=ALL-UNNAMED',
    '-Xms64m','-Xmx5g','-XX:G1PeriodicGCInterval=1000','-Dfile.encoding=UTF-8'
  )
  $agent = 'C:\Users\valen\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\lib\agents\gradle-instrumentation-agent-9.4.1.jar'
  if (-not (Test-Path -LiteralPath $agent)) { throw 'Gradle instrumentation agent not found' }
  $env:JAVA_OPTS = ($jvm -join ' ') + ' "-javaagent:' + $agent + '"'

  $logPath = Join-Path $logs $LogName
  $started = (Get-Date).ToUniversalTime()
  Set-Location -LiteralPath $Project
  $ErrorActionPreference = 'Continue'
  & .\gradlew.bat --no-daemon --console=plain --warning-mode=summary `
      ('-Dorg.gradle.jvmargs=' + ($jvm -join ' ')) '-Pkotlin.daemon.jvmargs=-Xmx3g' `
      --max-workers=2 -I $init @Tasks 2>&1 | Tee-Object -FilePath $logPath
  $code = $LASTEXITCODE
  $ErrorActionPreference = 'Stop'
  $finished = (Get-Date).ToUniversalTime()
  $receipt = [ordered]@{
    label = $Label; project = $Project; tasks = $Tasks; exitCode = $code
    queuedSeconds = [int]((Get-Date) - $waitStart).TotalSeconds - [int]($finished - $started).TotalSeconds
    startedUtc = $started.ToString('o'); finishedUtc = $finished.ToString('o')
    log = $logPath; logSha256 = (Get-FileHash -LiteralPath $logPath -Algorithm SHA256).Hash
  }
  $receipt | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $receipts ([IO.Path]::GetFileNameWithoutExtension($LogName) + '.receipt.json'))
  exit $code
}
finally { if ($acquired) { $mutex.ReleaseMutex() }; $mutex.Dispose() }

param([int]$DurationSec = 900, [int]$EverySec = 20, [string]$Tag = 'diag')
# Polls for Gradle test-executor JVMs and thread-dumps each one (jstack) while a hung test runs.
$out = 'C:\Users\valen\Documents\KPKNFit\artifacts\consolidation-20261001\logs'
$jstack = 'C:\Program Files\Android\Android Studio\jbr\bin\jstack.exe'
$deadline = (Get-Date).AddSeconds($DurationSec)
$seen = @{}
while ((Get-Date) -lt $deadline) {
  $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
    $_.CommandLine -match 'GradleWorkerMain' -and $_.CommandLine -match 'Android Studio'
  }
  foreach ($p in $procs) {
    $key = "$($p.ProcessId)"
    $age = ((Get-Date) - $p.CreationDate).TotalSeconds
    if (-not $seen.ContainsKey($key)) { $seen[$key] = 0 }
    if ($age -ge 30 -and ((Get-Date) - [datetime]'2000-01-01').TotalSeconds - $seen[$key] -ge 60) {
      $seen[$key] = ((Get-Date) - [datetime]'2000-01-01').TotalSeconds
      $file = Join-Path $out ("$Tag-jstack-$($p.ProcessId)-$((Get-Date).ToString('HHmmss')).txt")
      & $jstack $p.ProcessId > $file 2>&1
      "$(Get-Date -Format o) dumped pid=$($p.ProcessId) age=$([int]$age)s -> $file"
    }
  }
  Start-Sleep -Seconds $EverySec
}

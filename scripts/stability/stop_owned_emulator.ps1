param([Parameter(Mandatory=$true)][string]$InstanceRecord)
$ErrorActionPreference = 'Stop'
$workspaceRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$instancePath = (Resolve-Path -LiteralPath $InstanceRecord).Path
if (-not $instancePath.StartsWith($workspaceRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Instance record must stay in this worktree' }
$record = Get-Content -LiteralPath $instancePath
$avdName = (($record | Where-Object { $_ -like 'avd=*' }) -replace '^avd=','')
if ('serial=emulator-5554' -notin $record -or 'readOnly=true' -notin $record -or $avdName -notin @('Pixel_9_Pro_XL','KPKN_STABILITY_16KB','KPKN_STABILITY_API26')) { throw 'Unsupported emulator instance' }
$launcherPid = [int](($record | Where-Object { $_ -like 'launcherPid=*' }) -replace '^launcherPid=','')
$lockPath = 'C:\kw\emu.lock'
if (Test-Path -LiteralPath $lockPath) { throw 'Another emulator owner is working' }
New-Item -ItemType Directory -Path $lockPath -ErrorAction Stop | Out-Null
$ticket = [guid]::NewGuid().ToString()
$ownerPath = Join-Path $lockPath 'owner.txt'
Set-Content -LiteralPath $ownerPath -Value "ticket=$ticket worktree=$workspaceRoot" -Encoding utf8
try {
    $launcher = Get-CimInstance Win32_Process -Filter "ProcessId=$launcherPid"
    $expectedAvd = '-avd\s+"?' + [regex]::Escape($avdName) + '"?(\s|$)'
    if (-not $launcher -or $launcher.Name -ne 'emulator.exe' -or $launcher.CommandLine -notmatch '-read-only' -or $launcher.CommandLine -notmatch '-port\s+5554' -or $launcher.CommandLine -notmatch $expectedAvd) { throw 'The original emulator launcher is no longer provably owned' }
    $qemuName = if ($avdName -eq 'KPKN_STABILITY_API26') { 'qemu-system-i386-headless.exe' } else { 'qemu-system-x86_64-headless.exe' }
    $ownedQemu = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$launcherPid" | Where-Object { $_.Name -eq $qemuName -and $_.CommandLine -match '-read-only' -and $_.CommandLine -match '-port\s+5554' })
    if ($ownedQemu.Count -ne 1) { throw 'The original emulator process is no longer provably owned' }
    & 'C:\Users\valen\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 emu kill
    if ($LASTEXITCODE -ne 0) { throw 'The owned emulator did not accept shutdown' }
    Write-Output 'Stopped the verified read-only emulator created for this worktree'
} finally {
    if ((Get-Content -LiteralPath $ownerPath -Raw) -like "*ticket=$ticket*") {
        Remove-Item -LiteralPath $ownerPath
        [System.IO.Directory]::Delete($lockPath)
    }
}

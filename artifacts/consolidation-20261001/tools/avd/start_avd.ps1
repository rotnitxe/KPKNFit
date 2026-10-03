<#
.SYNOPSIS
  Start one of the two AVDs the consolidation toolset is allowed to use.

.DESCRIPTION
  Same emulator flags as OLDROOT start-owned-avd.ps1:
    -no-snapshot-load -no-snapshot-save -no-boot-anim -memory 2048 -cores 2
    -gpu host (default)  -feature -Vulkan      and windowed by default (-Headless adds -no-window)

  -Avd Audit   KPKNFitSessionAudit20260929  console port 5580  -> emulator-5580
               ANDROID_AVD_HOME = C:\Users\valen\AppData\Local\Temp\KPKNFitSessionAuditAVDs
  -Avd Wizard  KPKNWizchatQA                console port 5582  -> emulator-5582 (assumed serial)
               default AVD home (%USERPROFILE%\.android\avd; ANDROID_AVD_HOME is cleared for the child)

  The script can never start anything on 5554/5556 (the ports are fixed by -Avd) and it refuses to
  start an AVD whose serial is already online.  Nothing in this script edits an AVD's config.ini:
  camera sources are overridden on the command line only (-CameraBack / -CameraFront).

  Camera note (see README "CameraX capture"): the Audit AVD's config.ini already has
  hw.camera.back=emulated and hw.camera.front=emulated, so CameraX binds to the software-emulated
  camera without any change.  For the 3D Virtual Scene camera pass -CameraBack virtualscene
  (requires a hardware GPU renderer; keep -Renderer host).

.EXAMPLE
  .\start_avd.ps1 -DryRun                       # print the command, start nothing
  .\start_avd.ps1 -WaitBoot
  .\start_avd.ps1 -Avd Wizard -WaitBoot
  .\start_avd.ps1 -CameraBack virtualscene -WaitBoot
#>
[CmdletBinding()]
param(
    [ValidateSet('Audit', 'Wizard')][string]$Avd = 'Audit',
    [ValidateSet('host', 'swangle', 'swiftshader', 'software', 'lavapipe')][string]$Renderer = 'host',
    [switch]$Headless,
    [ValidateSet('', 'emulated', 'virtualscene', 'videoplayback', 'none')][string]$CameraBack = '',
    [ValidateSet('', 'emulated', 'none')][string]$CameraFront = '',
    [switch]$WaitBoot,
    [int]$BootTimeoutSeconds = 300,
    [switch]$DryRun,
    [string]$LogDir
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$sdk = if ($env:KPKN_QA_SDK) { $env:KPKN_QA_SDK } else { 'C:\Users\valen\AppData\Local\Android\Sdk' }
$emulatorExe = Join-Path $sdk 'emulator\emulator.exe'
$adbExe = if ($env:KPKN_QA_ADB) { $env:KPKN_QA_ADB } else { Join-Path $sdk 'platform-tools\adb.exe' }

# The only two AVDs this toolset may start.  Keep in sync with qa_paths.OWNED_DEVICES.
$contracts = @{
    Audit  = @{ Name = 'KPKNFitSessionAudit20260929'; Port = 5580; AvdHome = 'C:\Users\valen\AppData\Local\Temp\KPKNFitSessionAuditAVDs' }
    Wizard = @{ Name = 'KPKNWizchatQA';               Port = $(if ($env:KPKN_QA_WIZARD_PORT) { [int]$env:KPKN_QA_WIZARD_PORT } else { 5582 }); AvdHome = $null }
}
$contract = $contracts[$Avd]
$serial = 'emulator-' + $contract.Port
if (@('emulator-5554', 'emulator-5556') -contains $serial) { throw "Refusing forbidden serial $serial" }
if ($contract.Port % 2 -ne 0 -or $contract.Port -lt 5560 -or $contract.Port -gt 5680) { throw "Console port $($contract.Port) outside the allowed even range 5560-5680" }

if (-not $LogDir) {
    $LogDir = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'device-evidence\avd-logs'
}

function Get-AvdConfigPath {
    param($Contract)
    if ($Contract.AvdHome) {
        return Join-Path $Contract.AvdHome ($Contract.Name + '.avd\config.ini')
    }
    $ini = Join-Path $env:USERPROFILE ('.android\avd\' + $Contract.Name + '.ini')
    if (-not (Test-Path -LiteralPath $ini)) { return $null }
    $pathLine = Get-Content -LiteralPath $ini | Where-Object { $_ -match '^path=' } | Select-Object -First 1
    if (-not $pathLine) { return $null }
    return Join-Path ($pathLine -replace '^path=', '') 'config.ini'
}

function Read-CameraConfig {
    param([string]$ConfigPath)
    $result = [ordered]@{ configPath = $ConfigPath; back = $null; front = $null }
    if ($ConfigPath -and (Test-Path -LiteralPath $ConfigPath)) {
        foreach ($line in Get-Content -LiteralPath $ConfigPath) {
            if ($line -match '^\s*hw\.camera\.back\s*=\s*(.+?)\s*$') { $result.back = $Matches[1] }
            if ($line -match '^\s*hw\.camera\.front\s*=\s*(.+?)\s*$') { $result.front = $Matches[1] }
        }
    }
    return $result
}

$emulatorArgs = @(
    '-avd', $contract.Name,
    '-port', [string]$contract.Port,
    '-no-snapshot-load', '-no-snapshot-save', '-no-boot-anim',
    '-memory', '2048', '-cores', '2',
    '-gpu', $Renderer,
    '-feature', '-Vulkan'
)
if ($Headless) { $emulatorArgs += '-no-window' }
if ($CameraBack) { $emulatorArgs += @('-camera-back', $CameraBack) }
if ($CameraFront) { $emulatorArgs += @('-camera-front', $CameraFront) }
if ($CameraBack -eq 'virtualscene' -and $Renderer -ne 'host') {
    Write-Warning 'virtualscene needs a hardware GPU renderer; use -Renderer host.'
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$prefix = "avd-$($Avd.ToLower())-$Renderer-$(if ($Headless) { 'headless' } else { 'windowed' })-$stamp"
$cameraConfig = Read-CameraConfig (Get-AvdConfigPath $contract)
$plan = [ordered]@{
    avd             = $contract.Name
    role            = $Avd
    serial          = $serial
    port            = $contract.Port
    avdHome         = if ($contract.AvdHome) { $contract.AvdHome } else { '(default AVD home)' }
    emulator        = $emulatorExe
    arguments       = $emulatorArgs
    renderer        = $Renderer
    windowed        = (-not $Headless)
    vulkan          = $false
    cameraConfigIni = $cameraConfig
    cameraOverride  = [ordered]@{ back = $CameraBack; front = $CameraFront }
    logPrefix       = $prefix
    dryRun          = [bool]$DryRun
}

if ($DryRun) {
    $plan | ConvertTo-Json -Depth 6
    return
}

if (-not (Test-Path -LiteralPath $emulatorExe)) { throw "emulator.exe not found: $emulatorExe" }
if (-not (Test-Path -LiteralPath $adbExe)) { throw "adb.exe not found: $adbExe" }
$online = & $adbExe devices | Where-Object { $_ -match ('^' + [regex]::Escape($serial) + '\s+') }
if ($online) { throw "$serial is already present in 'adb devices' ($online); refusing to start a second instance." }

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$savedHome = $env:ANDROID_AVD_HOME
try {
    if ($contract.AvdHome) { $env:ANDROID_AVD_HOME = $contract.AvdHome } else { Remove-Item Env:ANDROID_AVD_HOME -ErrorAction SilentlyContinue }
    $process = Start-Process -FilePath $emulatorExe -ArgumentList $emulatorArgs -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $LogDir ($prefix + '.stdout.log')) `
        -RedirectStandardError (Join-Path $LogDir ($prefix + '.stderr.log'))
}
finally {
    if ($null -ne $savedHome) { $env:ANDROID_AVD_HOME = $savedHome } else { Remove-Item Env:ANDROID_AVD_HOME -ErrorAction SilentlyContinue }
}
$plan['pid'] = $process.Id
$plan['startedAt'] = (Get-Date).ToString('o')

if ($WaitBoot) {
    $deadline = (Get-Date).AddSeconds($BootTimeoutSeconds)
    $booted = $false
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        # While the emulator is still coming up adb answers "error: device offline" / "device not found" on stderr
        # with exit 1.  Under $ErrorActionPreference = 'Stop' a native command's stderr becomes a terminating
        # NativeCommandError, which used to abort this script mid-boot (no launch record written, nothing printed).
        # A transient adb failure only means "not booted yet".
        $value = ''
        $adbOk = $false
        try {
            $value = & $adbExe -s $serial shell getprop sys.boot_completed 2>$null
            $adbOk = ($LASTEXITCODE -eq 0)
        }
        catch {
            $adbOk = $false
        }
        if ($adbOk -and "$value".Trim() -eq '1') { $booted = $true; break }
        if ($process.HasExited) { throw "emulator exited early with code $($process.ExitCode); see $LogDir\$prefix.stderr.log" }
    }
    $plan['bootCompleted'] = $booted
    if (-not $booted) { throw "boot did not complete within $BootTimeoutSeconds s" }
}

$launchFile = Join-Path $LogDir ($prefix + '.launch.json')
$plan | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $launchFile -Encoding UTF8
Write-Output ("AVD $($contract.Name) started as $serial (pid $($process.Id)); launch record: $launchFile")

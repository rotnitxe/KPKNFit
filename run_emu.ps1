$log = "$PSScriptRoot\emu_debug.log"
"Starting..." | Out-File $log
$emu = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
& $emu -avd Pixel_9_Pro_XL 2>&1 | Tee-Object -FilePath $log -Append

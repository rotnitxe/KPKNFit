$sh = New-Object -ComObject Shell.Application
$emu = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
$dir = "$env:LOCALAPPDATA\Android\Sdk\emulator"
$sh.ShellExecute($emu, "-avd Pixel_9_Pro_XL", $dir, "open", 1)

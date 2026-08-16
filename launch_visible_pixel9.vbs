Set WshShell = CreateObject("WScript.Shell")
WshShell.CurrentDirectory = "C:\Users\valen\AppData\Local\Android\Sdk\emulator"
WshShell.Run "emulator.exe -avd Pixel_9_Pro_XL", 1, False

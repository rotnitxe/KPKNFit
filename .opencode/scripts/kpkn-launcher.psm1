#Requires -Version 5.1
<#
.SYNOPSIS
  Extension de PowerShell para abrir Cline y OpenCode en la carpeta KPKN Fit.

.DESCRIPTION
  Proporciona comandos dedicados para lanzar Cline y OpenCode apuntando
  automaticamente al directorio raiz de KPKN Fit
  (C:\Users\valen\Documents\KPKNFit).

  Uso:
    Import-Module .opencode\scripts\kpkn-launcher.psm1
    Open-KpknCline          # Abre Cline en la carpeta KPKN Fit
    Open-KpknOpenCode       # Abre OpenCode en la carpeta KPKN Fit
    Open-Kpkn -All          # Abre ambos (por defecto)
    Get-KpknRoot            # Muestra la ruta raiz apuntada
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Region raiz de KPKN Fit (documentos)
$script:KpknRoot = "C:\Users\valen\Documents\KPKNFit"
# --- Colores de estado (pestaña de Windows Terminal) ---------------------
$script:KpknColors = @{
  Free    = "#3b88c3"  # azul  : sin tareas
  Working = "#c9a227"  # amarillo: en proceso
  Done    = "#28a745"  # verde : terminado
}

function Set-KpknTabColor {
  <#
  .SYNOPSIS
    Cambia el color de la pestaña de Windows Terminal que aloja esta consola.
  .PARAMETER Color
    Nombre del estado (Free|Working|Done) o un valor hex "#rrggbb".
  .PARAMETER Reset
    Restaura el color por defecto del perfil.
  #>
  param(
    [Parameter(ParameterSetName="State")][ValidateSet("Free","Working","Done")][string]$State,
    [Parameter(ParameterSetName="Hex")][string]$Hex,
    [Parameter(ParameterSetName="Reset")][switch]$Reset
  )
  $value = if ($PSCmdlet.ParameterSetName -eq "State") { $script:KpknColors[$State] }
           elseif ($PSCmdlet.ParameterSetName -eq "Hex") { $Hex }
           else { $null }
  $ESC = [char]27
  if ($null -eq $value) {
    [Console]::Write("${ESC}]9;4;0${ESC}\\")
  } else {
    $hex = $value.TrimStart("#")
    [Console]::Write("${ESC}]9;4;3;$hex${ESC}\\")
  }
}

function Send-KpknNotify {
  <#
  .SYNOPSIS
    Muestra una notificacion de Windows y reproduce un sonido.
  #>
  param(
    [string]$Title = "KPKN Fit",
    [Parameter(Mandatory=$true)][string]$Message,
    [ValidateSet("Exclamation","Hand","Asterisk","Beep","None")]
    [string]$Sound = "Exclamation"
  )
  try {
    $null = Add-Type -AssemblyName System.Windows.Forms
    $notification = New-Object System.Windows.Forms.NotifyIcon
    $notification.Icon = [System.Drawing.SystemIcons]::Information
    $notification.BalloonTipTitle = $Title
    $notification.BalloonTipText = $Message
    $notification.Visible = $true
    $notification.ShowBalloonTip(4000)
    Start-Sleep -Milliseconds 500
    $notification.Dispose()
  } catch {
    Write-Host "[Notify] $Title - $Message" -ForegroundColor Cyan
  }
  if ($Sound -ne "None") {
    try {
      $null = Add-Type -AssemblyName System.Windows.Forms
      if ($Sound -eq "Beep") { [System.Console]::Beep(880, 300) }
      else { ([System.Media.SystemSounds]::$Sound).Play() }
    } catch { }
  }
}

function Get-KpknRoot {
  <#
  .SYNOPSIS
    Devuelve la ruta raiz de KPKN Fit usada por la extension.
  #>
  if (-not (Test-Path $script:KpknRoot)) {
    throw "KPKN Fit no encontrado en: $($script:KpknRoot)"
  }
  return $script:KpknRoot
}

function Find-KpknCli {
  <#
  .SYNOPSIS
    Localiza el CLI de Cline u OpenCode instalado via npm.
  #>
  param([Parameter(Mandatory=$true)][ValidateSet("Cline","OpenCode")][string]$Name)
  $cmd = Get-Command $Name -ErrorAction SilentlyContinue
  if (-not $cmd) {
    throw "No se encontro '$Name'. Verifica que este instalado globalmente (npm i -g cline / @opencode-ai/opencode)."
  }
  return $cmd.Source
}

function Start-KpknTool {
  <#
  .SYNOPSIS
    Tarea interna: lanza un CLI interactivo de KPKN en una ventana nueva.
  #>
  param(
    [Parameter(Mandatory=$true)][string]$DisplayName,
    [Parameter(Mandatory=$true)][string[]]$CliArgs,
    [switch]$PassThru
  )
  $root = Get-KpknRoot
  Write-Host ">> Abriendo $DisplayName en: $root" -ForegroundColor Cyan

  # El CLI es un shim .ps1 de npm; se invoca dentro de un powershell nuevo
  # para que ambas TUIs corran de forma concurrente en ventanas propias.
  $escaped = ($CliArgs | ForEach-Object { "`"$_`"" }) -join " "
  # Pestaña azul (libre) al abrir la ventana nueva.
  $ESC = [char]27
  $tabBlue = "${ESC}]9;4;3;" + $script:KpknColors.Free.TrimStart("#") + "${ESC}\\"
  $cmdLine = "" + $tabBlue + "; & { Set-Location -LiteralPath " + ($root -replace "'", "''") + " ; & " + $CliArgs[0] + " " + $escaped + " } 2>&1"

  $p = Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-NoLogo -NoExit -ExecutionPolicy Bypass -Command `"$cmdLine`"" `
        -WorkingDirectory $root -PassThru
  if ($PassThru) { return $p }
}

function Open-KpknCline {
  <#
  .SYNOPSIS
    Abre Cline (TUI) en la carpeta KPKN Fit.
  .PARAMETER Model
    Modelo opcional a usar en la sesion de Cline.
  .PARAMETER PassThru
    Devuelve el proceso lanzado.
  #>
  [CmdletBinding()]
  param(
    [string]$Model,
    [switch]$PassThru
  )
  $cli = Find-KpknCli -Name "Cline"
  $argsList = @($cli)
  if ($Model) { $argsList += @("-m", $Model) }
  $argsList += @("-i", "-c", (Get-KpknRoot))
  return Start-KpknTool -DisplayName "Cline" -CliArgs $argsList -PassThru:$PassThru
}

function Open-KpknOpenCode {
  <#
  .SYNOPSIS
    Abre OpenCode en la carpeta KPKN Fit.
  .PARAMETER PassThru
    Devuelve el proceso lanzado.
  #>
  [CmdletBinding()]
  param([switch]$PassThru)
  $cli = Find-KpknCli -Name "OpenCode"
  $argsList = @($cli, (Get-KpknRoot))
  return Start-KpknTool -DisplayName "OpenCode" -CliArgs $argsList -PassThru:$PassThru
}

function Open-Kpkn {
  <#
  .SYNOPSIS
    Abre Cline y/u OpenCode en la carpeta KPKN Fit.
  .PARAMETER All
    Abre ambos (comportamiento por defecto).
  .PARAMETER ClineOnly
    Solo abre Cline.
  .PARAMETER OpenCodeOnly
    Solo abre OpenCode.
  #>
  [CmdletBinding()]
  param(
    [switch]$All,
    [switch]$ClineOnly,
    [switch]$OpenCodeOnly
  )
  $openCline = ($All -or -not ($OpenCodeOnly)) -and -not ($ClineOnly -and $OpenCodeOnly)
  $openOC    = $OpenCodeOnly -or $All -or -not ($ClineOnly)

  if ($openCline) { Open-KpknCline }
  if ($openOC)    { Open-KpknOpenCode }

  if (-not ($openCline -or $openOC)) {
    Write-Warning "No se abrio ningun CLI (usa -All, -ClineOnly o -OpenCodeOnly)."
  }
}

Export-ModuleMember -Function Get-KpknRoot, Find-KpknCli, Start-KpknTool, Open-KpknCline, Open-KpknOpenCode, Open-Kpkn, Set-KpknTabColor, Send-KpknNotify

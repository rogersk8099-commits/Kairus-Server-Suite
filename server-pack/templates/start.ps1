# Start from the server root. Change XMS/XMX to suit available host memory.
[CmdletBinding()]
param(
    [string]$Xms = '2G',
    [string]$Xmx = '4G',
    [string]$JavaBin = 'java'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $MyInvocation.MyCommand.Path)

$VersionText = & $JavaBin -version 2>&1 | Out-String
if ($VersionText -notmatch 'version "(?<version>\d+)(?:\.|"|_)' -or [int]$Matches.version -lt 21) {
    throw "Java 21 or newer is required. Detected: $VersionText"
}
$Jars = @(Get-ChildItem -File | Where-Object { $_.Name -match '^(paper|purpur)-1\.21\.4-.*\.jar$' } | Sort-Object Name)
if ($Jars.Count -ne 1) {
    throw "Expected exactly one paper-1.21.4-*.jar or purpur-1.21.4-*.jar in this directory. Found: $($Jars.Name -join ', ')"
}

# Paper 1.21.4 includes spark; no separate spark JAR is needed.
& $JavaBin "-Xms$Xms" "-Xmx$Xmx" '-XX:+UseG1GC' '-XX:+ParallelRefProcEnabled' `
    '-XX:MaxGCPauseMillis=200' '-XX:+UnlockExperimentalVMOptions' '-XX:+DisableExplicitGC' `
    '-Dfile.encoding=UTF-8' '-jar' $Jars[0].Name '--nogui'
exit $LASTEXITCODE

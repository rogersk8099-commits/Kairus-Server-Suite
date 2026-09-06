<#
.SYNOPSIS
  Copies Kairu suite source into clearly named Windows folders without provisioning secrets.
#>
[CmdletBinding()]
param(
    [string]$DestinationRoot = (Join-Path $env:USERPROFILE 'KairuSMP'),
    [switch]$InstallDependencies,
    [switch]$Force
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$SourceRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Components = @(
    @{ Source = 'website'; Destination = 'Kairu-Website'; PackageManager = 'pnpm' },
    @{ Source = 'server-pack'; Destination = 'Kairu-Server-Pack'; PackageManager = $null },
    @{ Source = 'control-plane'; Destination = 'Kairu-Control-Plane'; PackageManager = 'npm' }
)
$ExcludedDirectories = @('node_modules','.output','dist','build','.gradle','.git','.wrangler')
$ExcludedFiles = @('.env','.env.local','.env.production','.env.production.local')

function Copy-SafeTree([string]$Source, [string]$Destination) {
    if (Test-Path -LiteralPath $Destination) {
        if (-not $Force) { throw "Destination already exists: $Destination. Use -Force only after reviewing it." }
        Remove-Item -LiteralPath $Destination -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    Get-ChildItem -LiteralPath $Source -Recurse -Force | ForEach-Object {
        $Relative = $_.FullName.Substring($Source.Length).TrimStart('\')
        if ([string]::IsNullOrEmpty($Relative)) { return }
        $Segments = $Relative -split '[\\/]'
        if ($Segments | Where-Object { $ExcludedDirectories -contains $_ }) { return }
        if (-not $_.PSIsContainer -and (($ExcludedFiles -contains $_.Name) -or $_.Extension -in @('.log','.pem','.key'))) { return }
        $Target = Join-Path $Destination $Relative
        if ($_.PSIsContainer) { New-Item -ItemType Directory -Path $Target -Force | Out-Null }
        else { New-Item -ItemType Directory -Path (Split-Path -Parent $Target) -Force | Out-Null; Copy-Item -LiteralPath $_.FullName -Destination $Target -Force }
    }
}

New-Item -ItemType Directory -Path $DestinationRoot -Force | Out-Null
foreach ($Component in $Components) {
    $Source = Join-Path $SourceRoot $Component.Source
    if (-not (Test-Path -LiteralPath $Source -PathType Container)) { throw "Missing suite component: $Source" }
    $Destination = Join-Path $DestinationRoot $Component.Destination
    Copy-SafeTree $Source $Destination
    Write-Host "Installed source: $Destination" -ForegroundColor Green
    if ($InstallDependencies -and $null -ne $Component.PackageManager) {
        Push-Location $Destination
        try {
            if ($Component.PackageManager -eq 'pnpm') { & pnpm install --frozen-lockfile; if ($LASTEXITCODE -ne 0) { throw 'pnpm install failed' } }
            else { & npm ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' } }
        } finally { Pop-Location }
    }
}
Write-Host "No secrets were created or copied. Read docs\INSTALLATION.md and each component .env.example before configuration." -ForegroundColor Yellow

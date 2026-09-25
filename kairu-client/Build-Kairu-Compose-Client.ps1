$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    # Build only the installed Minecraft 26.2 target. Building every historical
    # Stonecutter target would compile Kairu's modern 26.2 screen against old
    # Minecraft APIs and is not needed for this client.
    & .\gradlew.bat ':bootstrap:26.2-fabric:clean' ':bootstrap:26.2-fabric:build' --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Kairu Compose client build failed with exit code $LASTEXITCODE" }
    $output = Join-Path $root 'output'
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    Get-ChildItem -Path $root -Recurse -File -Filter 'KairuSmpClient-*.jar' | Where-Object { $_.FullName -match '[\\/]bootstrap[\\/]' } | Copy-Item -Destination $output -Force
    Write-Host ''
    Write-Host 'Build succeeded. The Kairu JAR contains the Compose/Skiko runtime; no separate Compose JAR is required.'
} finally {
    Pop-Location
}

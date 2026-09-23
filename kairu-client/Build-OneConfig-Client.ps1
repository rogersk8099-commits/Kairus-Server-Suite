$ErrorActionPreference = 'Stop'

# Builds the pinned OneConfig Fabric 26.2 bootstrap first, then embeds that resulting
# mod inside the Kairu client. The released client is still one JAR in the player's mods folder.
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$oneConfig = Join-Path $root 'oneconfig-source'

if (-not (Test-Path (Join-Path $oneConfig 'gradlew.bat'))) {
    throw "Missing bundled OneConfig source at $oneConfig"
}

$bootstrapDirectory = Join-Path $oneConfig 'bootstrap\versions\26.2-fabric\build\libs'
$bootstrap = Get-ChildItem $bootstrapDirectory -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources|dev' } |
    Select-Object -First 1

if ($null -eq $bootstrap) {
    Push-Location $oneConfig
    try {
        & .\gradlew.bat ':bootstrap:26.2-fabric:build' --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "OneConfig build failed with exit code $LASTEXITCODE" }
    } finally { Pop-Location }
    $bootstrap = Get-ChildItem $bootstrapDirectory -Filter '*.jar' |
        Where-Object { $_.Name -notmatch 'sources|dev' } |
        Select-Object -First 1
}
if ($null -eq $bootstrap) { throw 'OneConfig did not produce its Fabric 26.2 bootstrap JAR.' }

Push-Location $root
try {
    & .\gradlew.bat clean build "-Pkairu.oneconfigJar=$($bootstrap.FullName)" --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Kairu client build failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }

Write-Host "Built standalone Kairu client: $root\build\libs\KairuSmpClient-0.5.0.jar"

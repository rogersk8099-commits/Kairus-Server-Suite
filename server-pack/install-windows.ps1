<#
.SYNOPSIS
  Kairu Paper/Purpur 1.21.4 installation pack — Windows PowerShell installer.
.DESCRIPTION
  Downloads server/plugin artifacts only from their documented official APIs or official release URLs.
  It never starts the server and never accepts the Minecraft EULA.
.EXAMPLE
  $env:PAPER_USER_AGENT_CONTACT = 'https://example.org/contact'
  .\install-windows.ps1 -ServerFlavor Paper
.EXAMPLE
  .\install-windows.ps1 -ServerFlavor Purpur -NoFloodgate -InstallPlaceholderAPI -InstallVault
#>
[CmdletBinding()]
param(
    [ValidateSet('Paper', 'Purpur')]
    [string]$ServerFlavor = 'Paper',
    [switch]$NoFloodgate,
    [switch]$InstallPlaceholderAPI,
    [switch]$InstallVault
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$PackDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $PackDir
$TempDir = Join-Path $PackDir '.installer-tmp'
$DownloadsDir = Join-Path $PackDir 'downloads'
New-Item -ItemType Directory -Force -Path $TempDir, $DownloadsDir, (Join-Path $PackDir 'plugins') | Out-Null

function Fail([string]$Message) { throw "ERROR: $Message" }
function Get-Json([string]$Url, [string]$OutFile, [hashtable]$Headers = @{}) {
    Invoke-WebRequest -Uri $Url -OutFile $OutFile -Headers $Headers -UseBasicParsing
    try { return (Get-Content -Raw -LiteralPath $OutFile | ConvertFrom-Json) }
    catch { Fail "Official API response was not valid JSON: $Url" }
}
function Test-Hash([string]$File, [string]$Expected, [string]$Algorithm = 'SHA256') {
    if ([string]::IsNullOrWhiteSpace($Expected)) { Fail "No official $Algorithm checksum was supplied for $(Split-Path -Leaf $File)." }
    $Actual = (Get-FileHash -Algorithm $Algorithm -LiteralPath $File).Hash.ToLowerInvariant()
    if ($Actual -ne $Expected.ToLowerInvariant()) { Fail "$Algorithm mismatch for $(Split-Path -Leaf $File). Expected $Expected; got $Actual." }
    return $Actual
}
function Test-JarHeader([string]$File) {
    $Stream = [System.IO.File]::OpenRead($File)
    try {
        if ($Stream.Length -lt 4) { Fail "$(Split-Path -Leaf $File) is too small to be a JAR." }
        $Header = New-Object byte[] 2
        [void]$Stream.Read($Header, 0, 2)
        if ($Header[0] -ne 0x50 -or $Header[1] -ne 0x4B) { Fail "$(Split-Path -Leaf $File) is not a JAR/ZIP; no file was installed." }
    } finally { $Stream.Dispose() }
}
function Save-ChecksumLine([string]$Algorithm, [string]$Hash, [string]$File, [bool]$Official) {
    $Name = if ($Official) { "${Algorithm}SUMS.txt" } else { "${Algorithm}SUMS-unverified-source.txt" }
    "${Hash}  $File" | Add-Content -LiteralPath (Join-Path $DownloadsDir $Name) -Encoding utf8
}
function Download-Verified([string]$Url, [string]$Destination, [string]$Sha256, [hashtable]$Headers = @{}) {
    $Temp = Join-Path $TempDir ((Split-Path -Leaf $Destination) + '.part')
    Remove-Item -LiteralPath $Temp -Force -ErrorAction SilentlyContinue
    Invoke-WebRequest -Uri $Url -OutFile $Temp -Headers $Headers -UseBasicParsing
    if (-not (Test-Path -LiteralPath $Temp) -or (Get-Item -LiteralPath $Temp).Length -eq 0) { Fail "Downloaded file is empty: $Destination" }
    $Actual = Test-Hash $Temp $Sha256 'SHA256'
    Move-Item -LiteralPath $Temp -Destination $Destination -Force
    Save-ChecksumLine 'SHA256' $Actual $Destination $true
    Write-Host "Verified SHA-256: $Destination" -ForegroundColor Green
}
function Download-NoOfficialHash([string]$Url, [string]$Destination) {
    $Temp = Join-Path $TempDir ((Split-Path -Leaf $Destination) + '.part')
    Remove-Item -LiteralPath $Temp -Force -ErrorAction SilentlyContinue
    Invoke-WebRequest -Uri $Url -OutFile $Temp -UseBasicParsing
    Test-JarHeader $Temp
    Move-Item -LiteralPath $Temp -Destination $Destination -Force
    $LocalHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Destination).Hash.ToLowerInvariant()
    Save-ChecksumLine 'SHA256' $LocalHash $Destination $false
    Write-Warning "The official source does not publish a checksum for $(Split-Path -Leaf $Destination). Recorded local SHA-256 only; it is not source verification."
}
function Copy-IfAbsent([string]$Source, [string]$Destination) {
    if (-not (Test-Path -LiteralPath $Destination)) { Copy-Item -LiteralPath $Source -Destination $Destination }
}

try {
    $JavaOutput = & java -version 2>&1 | Out-String
    if ($JavaOutput -notmatch 'version "(?<version>\d+)(?:\.|"|_)') { Fail 'Cannot determine the installed Java version. Java 21+ is required.' }
    if ([int]$Matches.version -lt 21) { Fail "Java 21+ is required; detected Java $($Matches.version)." }
} catch {
    if ($_.Exception.Message -like 'ERROR:*') { throw }
    Fail 'Java is not on PATH. Install a Java 21 JRE/JDK, then rerun.'
}

try {
$ExistingServer = Get-ChildItem -LiteralPath $PackDir -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^(paper|purpur)-1\.21\.4-.*\.jar$' } | Select-Object -First 1
if ($null -ne $ExistingServer) { Fail "A server JAR already exists ($($ExistingServer.Name)). Use the update procedure instead of overwriting it." }

if ($ServerFlavor -eq 'Paper') {
    $Contact = $env:PAPER_USER_AGENT_CONTACT
    if ([string]::IsNullOrWhiteSpace($Contact)) { Fail 'Set PAPER_USER_AGENT_CONTACT to your actual administrator contact URL or email before downloading Paper.' }
    $Headers = @{ 'User-Agent' = "KairuServerPack/1.0 ($Contact)" }
    $Paper = Get-Json 'https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds' (Join-Path $TempDir 'paper-builds.json') $Headers
    $Build = @($Paper | Where-Object { $_.channel -eq 'STABLE' } | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1)
    if ($Build.Count -ne 1) { Fail 'No stable Paper 1.21.4 build is available from the official API.' }
    $Artifact = $Build[0].downloads.'server:default'
    if ($Artifact.name -notmatch '^paper-1\.21\.4-\d+\.jar$' -or $Artifact.url -notmatch '^https://' -or $Artifact.checksums.sha256 -notmatch '^[0-9a-fA-F]{64}$') { Fail 'Paper API response failed strict artifact validation.' }
    $ServerFile = $Artifact.name
    Download-Verified $Artifact.url (Join-Path $PackDir $ServerFile) $Artifact.checksums.sha256 $Headers
} else {
    $Purpur = Get-Json 'https://api.purpurmc.org/v2/purpur/1.21.4/latest' (Join-Path $TempDir 'purpur-latest.json')
    $Build = [string]$Purpur.build
    $OfficialMd5 = [string]$Purpur.md5
    if ($Build -notmatch '^\d+$' -or $OfficialMd5 -notmatch '^[0-9a-fA-F]{32}$') { Fail 'Purpur API response failed strict build/checksum validation.' }
    $ServerFile = "purpur-1.21.4-$Build.jar"
    $Destination = Join-Path $PackDir $ServerFile
    $Temp = Join-Path $TempDir "$ServerFile.part"
    Invoke-WebRequest -Uri "https://api.purpurmc.org/v2/purpur/1.21.4/$Build/download" -OutFile $Temp -UseBasicParsing
    $ActualMd5 = Test-Hash $Temp $OfficialMd5 'MD5'
    Move-Item -LiteralPath $Temp -Destination $Destination -Force
    Save-ChecksumLine 'MD5' $ActualMd5 $Destination $true
    $LocalSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $Destination).Hash.ToLowerInvariant()
    Save-ChecksumLine 'SHA256' $LocalSha256 $Destination $false
    Write-Warning 'The Purpur 1.21.4 API currently exposes MD5 (verified) but no SHA-256. The recorded SHA-256 is inventory only.'
}

# Required for current Geyser's newer Java protocol client to reach this older 1.21.4 backend.
$Via = Get-Json 'https://hangar.papermc.io/api/v1/projects/ViaVersion/ViaVersion/versions/5.11.0' (Join-Path $TempDir 'viaversion.json')
$ViaArtifact = $Via.downloads.PAPER
if ($ViaArtifact.fileInfo.name -ne 'ViaVersion-5.11.0.jar' -or $ViaArtifact.downloadUrl -notmatch '^https://' -or $ViaArtifact.fileInfo.sha256Hash -notmatch '^[0-9a-fA-F]{64}$') { Fail 'ViaVersion API response failed strict artifact validation.' }
Download-Verified $ViaArtifact.downloadUrl (Join-Path $PackDir "plugins\$($ViaArtifact.fileInfo.name)") $ViaArtifact.fileInfo.sha256Hash

$Geyser = Get-Json 'https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest' (Join-Path $TempDir 'geyser.json')
if ($Geyser.downloads.spigot.name -ne 'Geyser-Spigot.jar' -or $Geyser.downloads.spigot.sha256 -notmatch '^[0-9a-fA-F]{64}$') { Fail 'Geyser API response failed strict artifact validation.' }
Download-Verified 'https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot' (Join-Path $PackDir 'plugins\Geyser-Spigot.jar') $Geyser.downloads.spigot.sha256

if (-not $NoFloodgate) {
    $Floodgate = Get-Json 'https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest' (Join-Path $TempDir 'floodgate.json')
    if ($Floodgate.downloads.spigot.name -ne 'floodgate-spigot.jar' -or $Floodgate.downloads.spigot.sha256 -notmatch '^[0-9a-fA-F]{64}$') { Fail 'Floodgate API response failed strict artifact validation.' }
    Download-Verified 'https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot' (Join-Path $PackDir 'plugins\floodgate-spigot.jar') $Floodgate.downloads.spigot.sha256
}

# Pinned 1.21.4-compatible EssentialsX and official LuckPerms Bukkit JAR. Neither source endpoint publishes a digest.
Download-NoOfficialHash 'https://github.com/EssentialsX/Essentials/releases/download/2.21.0/EssentialsX-2.21.0.jar' (Join-Path $PackDir 'plugins\EssentialsX-2.21.0.jar')
Download-NoOfficialHash 'https://download.luckperms.net/1668/bukkit/loader/LuckPerms-Bukkit-5.5.81.jar' (Join-Path $PackDir 'plugins\LuckPerms-Bukkit-5.5.81.jar')
if ($InstallPlaceholderAPI) {
    Download-Verified 'https://github.com/PlaceholderAPI/PlaceholderAPI/releases/download/2.12.3/PlaceholderAPI-2.12.3.jar' (Join-Path $PackDir 'plugins\PlaceholderAPI-2.12.3.jar') 'fde03259f5af6938f3c33eeb4d814000a1adabf1d2304ce14970be81f609a437'
}
if ($InstallVault) { Download-NoOfficialHash 'https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar' (Join-Path $PackDir 'plugins\Vault.jar') }

Copy-IfAbsent (Join-Path $PackDir 'templates\server.properties') (Join-Path $PackDir 'server.properties')
Copy-IfAbsent (Join-Path $PackDir 'templates\start.ps1') (Join-Path $PackDir 'start.ps1')
# Geyser and Floodgate config formats are release-sensitive. Let the installed plugins generate their
# version-matched files after EULA acceptance, then merge the reviewed templates explicitly.
Copy-IfAbsent (Join-Path $PackDir 'templates\KairuBridge-config.yml.example') (Join-Path $PackDir 'plugins\KairuBridge-config.yml.example')

Write-Host @"

Installation complete. No server has been started and the Minecraft EULA remains unaccepted.

Next steps:
  1. Review server.properties; keep online-mode=true.
  2. Start once with .\start.ps1. Read eula.txt, then manually change eula=false to eula=true only if you agree.
  3. Start again, stop cleanly, then merge templates\Geyser-config.yml and templates\floodgate-config.yml into the version-generated plugin configs.
  4. Verify the bundled plugins\KairuBridge.jar and configure it from the example.
  5. Restart; allow UDP 19132 and TCP 25565 through your host firewall/security group.

Installed server: $ServerFile
"@
} finally {
    Remove-Item -LiteralPath $TempDir -Recurse -Force -ErrorAction SilentlyContinue
}

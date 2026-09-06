#!/usr/bin/env bash
# Kairu Paper/Purpur 1.21.4 installation pack — Linux installer
# Downloads only from documented official services/APIs. It never accepts the EULA.
set -Eeuo pipefail
IFS=$'\n\t'

PACK_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cd "$PACK_DIR"

SERVER_FLAVOR="${1:-paper}"
INSTALL_FLOODGATE="${INSTALL_FLOODGATE:-true}"
INSTALL_PLACEHOLDERAPI="${INSTALL_PLACEHOLDERAPI:-false}"
INSTALL_VAULT="${INSTALL_VAULT:-false}"
CONTACT_URL="${PAPER_USER_AGENT_CONTACT:-}"
USER_AGENT="KairuServerPack/1.0 (${CONTACT_URL})"
CURL=(curl --fail --show-error --silent --location --proto '=https' --tlsv1.2 --retry 3 --retry-delay 2 --connect-timeout 20)

case "${SERVER_FLAVOR,,}" in
  paper|purpur) SERVER_FLAVOR="${SERVER_FLAVOR,,}" ;;
  *) echo "Usage: $0 [paper|purpur]" >&2; exit 64 ;;
esac

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java is not on PATH. Install a Java 21 JRE/JDK, then rerun." >&2
  exit 1
fi
JAVA_VERSION="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
if [[ ! "$JAVA_VERSION" =~ ^[0-9]+$ ]] || (( JAVA_VERSION < 21 )); then
  echo "ERROR: Java 21 or newer is required; detected major version '${JAVA_VERSION:-unknown}'." >&2
  exit 1
fi

for command in python3 sha256sum; do
  command -v "$command" >/dev/null 2>&1 || { echo "ERROR: Missing required command: $command" >&2; exit 1; }
done

mkdir -p plugins downloads .installer-tmp
if find . -maxdepth 1 -type f \( -name 'paper-1.21.4-*.jar' -o -name 'purpur-1.21.4-*.jar' \) -print -quit | grep -q .; then
  echo "ERROR: A server JAR is already present. Stop the server and use the update procedure instead of overwriting it." >&2
  exit 1
fi

cleanup() { rm -rf .installer-tmp; }
trap cleanup EXIT

fail() { echo "ERROR: $*" >&2; exit 1; }
json_value() {
  # Usage: json_value FILE PYTHON_EXPRESSION. The expression receives object 'd'.
  python3 - "$1" "$2" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f:
    d = json.load(f)
print(eval(sys.argv[2], {"__builtins__": {}}, {"d": d}))
PY
}
download_verified() {
  # Usage: download_verified URL OUTPUT SHA256 [USER_AGENT]
  local url="$1" output="$2" expected="$3" agent="${4:-}"
  local temporary=".installer-tmp/$(basename "$output").part"
  [[ -n "$expected" ]] || fail "No official SHA-256 was supplied for $(basename "$output"); refusing download."
  rm -f "$temporary"
  if [[ -n "$agent" ]]; then
    "${CURL[@]}" -A "$agent" --output "$temporary" "$url"
  else
    "${CURL[@]}" --output "$temporary" "$url"
  fi
  [[ -s "$temporary" ]] || fail "Downloaded file is empty: $output"
  local actual
  actual="$(sha256sum "$temporary" | awk '{print $1}')"
  [[ "${actual,,}" == "${expected,,}" ]] || fail "SHA-256 mismatch for $(basename "$output"). Expected $expected; got $actual."
  mv -f "$temporary" "$output"
  printf 'Verified SHA-256: %s  %s\n' "$actual" "$output" | tee -a downloads/SHA256SUMS.txt
}
download_without_hash() {
  # The source has no official digest. Verify an expected non-empty JAR/ZIP header only.
  local url="$1" output="$2" temporary=".installer-tmp/$(basename "$output").part"
  rm -f "$temporary"
  "${CURL[@]}" --output "$temporary" "$url"
  [[ -s "$temporary" ]] || fail "Downloaded file is empty: $output"
  if ! head -c 2 "$temporary" | grep -q '^PK'; then
    fail "$(basename "$output") is not a JAR/ZIP file; no file was installed."
  fi
  mv -f "$temporary" "$output"
  sha256sum "$output" | tee -a downloads/SHA256SUMS-unverified-source.txt
  echo "NOTICE: No upstream SHA-256 was published for $(basename "$output"); recorded local digest only." >&2
}
fetch_json() {
  local url="$1" output="$2" agent="${3:-}"
  if [[ -n "$agent" ]]; then
    "${CURL[@]}" -A "$agent" --output "$output" "$url"
  else
    "${CURL[@]}" --output "$output" "$url"
  fi
  python3 -m json.tool "$output" >/dev/null || fail "Official API response was not valid JSON: $url"
}

printf '\nKairu server pack installer (%s)\n' "$SERVER_FLAVOR"
printf 'Target: %s\n\n' "$PACK_DIR"

if [[ "$SERVER_FLAVOR" == paper ]]; then
  [[ -n "$CONTACT_URL" ]] || fail "Set PAPER_USER_AGENT_CONTACT to your actual administrator contact URL or email before downloading Paper."
  paper_json=".installer-tmp/paper-builds.json"
  fetch_json "https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds" "$paper_json" "$USER_AGENT"
  readarray -t paper_fields < <(python3 - "$paper_json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f: builds=json.load(f)
stable=[b for b in builds if b.get('channel') == 'STABLE']
if not stable: raise SystemExit('No stable Paper 1.21.4 build available.')
b=max(stable, key=lambda x: x['id'])
a=b['downloads']['server:default']
print(b['id']); print(a['name']); print(a['url']); print(a['checksums']['sha256'])
PY
  )
  build="${paper_fields[0]}"; server_file="${paper_fields[1]}"; server_url="${paper_fields[2]}"; server_hash="${paper_fields[3]}"
  [[ "$build" =~ ^[0-9]+$ && "$server_file" == paper-1.21.4-*.jar && "$server_url" == https://* && "$server_hash" =~ ^[A-Fa-f0-9]{64}$ ]] || fail "Paper API response failed strict validation."
  download_verified "$server_url" "$server_file" "$server_hash" "$USER_AGENT"
else
  purpur_json=".installer-tmp/purpur-latest.json"
  fetch_json "https://api.purpurmc.org/v2/purpur/1.21.4/latest" "$purpur_json"
  build="$(json_value "$purpur_json" "str(d['build'])")"
  [[ "$build" =~ ^[0-9]+$ ]] || fail "Purpur API returned an invalid build number."
  # The official Purpur API presently publishes MD5, not SHA-256, for this legacy line.
  server_file="purpur-1.21.4-${build}.jar"
  server_url="https://api.purpurmc.org/v2/purpur/1.21.4/${build}/download"
  server_md5="$(json_value "$purpur_json" "d.get('md5', '')")"
  [[ "$server_md5" =~ ^[A-Fa-f0-9]{32}$ ]] || fail "Purpur API supplied no valid official MD5."
  temporary=".installer-tmp/${server_file}.part"
  "${CURL[@]}" --output "$temporary" "$server_url"
  [[ -s "$temporary" ]] || fail "Purpur server file is empty."
  actual_md5="$(md5sum "$temporary" | awk '{print $1}')"
  [[ "${actual_md5,,}" == "${server_md5,,}" ]] || fail "Purpur MD5 mismatch. Expected $server_md5; got $actual_md5."
  mv -f "$temporary" "$server_file"
  sha256sum "$server_file" | tee -a downloads/SHA256SUMS-unverified-source.txt
  printf 'Verified official MD5: %s  %s\n' "$actual_md5" "$server_file" | tee -a downloads/MD5SUMS.txt
  echo "NOTICE: The official Purpur API exposes MD5 (verified) but no SHA-256 for 1.21.4; the recorded SHA-256 is local inventory only." >&2
fi

# Required for this pack's modern Geyser-to-1.21.4 protocol path.
via_json=".installer-tmp/viaversion.json"
fetch_json "https://hangar.papermc.io/api/v1/projects/ViaVersion/ViaVersion/versions/5.11.0" "$via_json"
readarray -t via_fields < <(python3 - "$via_json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f: d=json.load(f)
a=d['downloads']['PAPER']
print(a['fileInfo']['name']); print(a['downloadUrl']); print(a['fileInfo']['sha256Hash'])
PY
)
[[ "${via_fields[0]}" == 'ViaVersion-5.11.0.jar' && "${via_fields[1]}" == https://* && "${via_fields[2]}" =~ ^[A-Fa-f0-9]{64}$ ]] || fail "ViaVersion API response failed strict validation."
download_verified "${via_fields[1]}" "plugins/${via_fields[0]}" "${via_fields[2]}"

geyser_json=".installer-tmp/geyser.json"
fetch_json "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest" "$geyser_json"
readarray -t geyser_fields < <(python3 - "$geyser_json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f: d=json.load(f)
a=d['downloads']['spigot']
print(a['name']); print(a['sha256'])
PY
)
[[ "${geyser_fields[0]}" == 'Geyser-Spigot.jar' && "${geyser_fields[1]}" =~ ^[A-Fa-f0-9]{64}$ ]] || fail "Geyser API response failed strict validation."
download_verified "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot" "plugins/${geyser_fields[0]}" "${geyser_fields[1]}"

if [[ "$INSTALL_FLOODGATE" == true ]]; then
  floodgate_json=".installer-tmp/floodgate.json"
  fetch_json "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest" "$floodgate_json"
  readarray -t floodgate_fields < <(python3 - "$floodgate_json" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f: d=json.load(f)
a=d['downloads']['spigot']
print(a['name']); print(a['sha256'])
PY
  )
  [[ "${floodgate_fields[0]}" == 'floodgate-spigot.jar' && "${floodgate_fields[1]}" =~ ^[A-Fa-f0-9]{64}$ ]] || fail "Floodgate API response failed strict validation."
  download_verified "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot" "plugins/${floodgate_fields[0]}" "${floodgate_fields[1]}"
fi

# Pinned 1.21.4-compatible EssentialsX core. The official GitHub release has no published digest.
download_without_hash "https://github.com/EssentialsX/Essentials/releases/download/2.21.0/EssentialsX-2.21.0.jar" "plugins/EssentialsX-2.21.0.jar"
# Pinned official LuckPerms Bukkit release. Its source does not publish a digest.
download_without_hash "https://download.luckperms.net/1668/bukkit/loader/LuckPerms-Bukkit-5.5.81.jar" "plugins/LuckPerms-Bukkit-5.5.81.jar"

if [[ "$INSTALL_PLACEHOLDERAPI" == true ]]; then
  download_verified \
    "https://github.com/PlaceholderAPI/PlaceholderAPI/releases/download/2.12.3/PlaceholderAPI-2.12.3.jar" \
    "plugins/PlaceholderAPI-2.12.3.jar" \
    "fde03259f5af6938f3c33eeb4d814000a1adabf1d2304ce14970be81f609a437"
fi
if [[ "$INSTALL_VAULT" == true ]]; then
  download_without_hash "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar" "plugins/Vault.jar"
fi

# Instantiate administrator-owned configs only if absent. Never overwrite generated config/data.
if [[ ! -e server.properties ]]; then cp templates/server.properties server.properties; fi
if [[ ! -e start.sh ]]; then cp templates/start.sh start.sh; chmod 750 start.sh; fi
# Geyser and Floodgate config formats are release-sensitive. Let the installed plugins generate their
# version-matched files after EULA acceptance, then merge the reviewed templates explicitly.
if [[ ! -e plugins/KairuBridge-config.yml.example ]]; then cp templates/KairuBridge-config.yml.example plugins/KairuBridge-config.yml.example; fi

cat <<EOF

Installation complete. No server has been started and the Minecraft EULA remains unaccepted.

Next steps:
  1. Review server.properties; keep online-mode=true.
  2. Start once with ./start.sh. Read eula.txt, then manually change eula=false to eula=true only if you agree.
  3. Start again, stop cleanly, then merge templates/Geyser-config.yml and templates/floodgate-config.yml into the version-generated plugin configs.
  4. Verify the bundled plugins/KairuBridge.jar, then configure it from the example.
  5. Restart; allow UDP 19132 and TCP 25565 through your host firewall/security group.

Installed server: ${server_file}
EOF

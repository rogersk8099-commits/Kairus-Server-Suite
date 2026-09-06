#!/usr/bin/env bash
# Start from the server root. Adjust XMS/XMX to fit your host; do not exceed available RAM.
set -Eeuo pipefail
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

XMS="${XMS:-2G}"
XMX="${XMX:-4G}"
JAVA_BIN="${JAVA_BIN:-java}"

JAVA_MAJOR="$($JAVA_BIN -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
if [[ ! "$JAVA_MAJOR" =~ ^[0-9]+$ ]] || (( JAVA_MAJOR < 21 )); then
  echo "ERROR: Java 21 or newer is required (detected: ${JAVA_MAJOR:-unknown})." >&2
  exit 1
fi

mapfile -t JARS < <(find . -maxdepth 1 -type f \( -name 'paper-1.21.4-*.jar' -o -name 'purpur-1.21.4-*.jar' \) -printf '%f\n' | sort)
if (( ${#JARS[@]} != 1 )); then
  echo "ERROR: Expected exactly one paper-1.21.4-*.jar or purpur-1.21.4-*.jar in this directory." >&2
  printf 'Found: %s\n' "${JARS[*]:-(none)}" >&2
  exit 1
fi

# Paper 1.21.4 includes spark; no separate spark jar is needed.
exec "$JAVA_BIN" -Xms"$XMS" -Xmx"$XMX" -XX:+UseG1GC -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC \
  -Dfile.encoding=UTF-8 -jar "${JARS[0]}" --nogui

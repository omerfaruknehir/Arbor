#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_AAB="${1:-$ROOT_DIR/app/build/outputs/bundle/debug/app-debug.aab}"
OUTPUT_AAB="${2:-$ROOT_DIR/app/build/outputs/bundle/debug/app-debug-signed.aab}"

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/jarsigner" ]]; then
    JAVA_BIN="$JAVA_HOME/bin/jarsigner"
else
    JAVA_BIN="$(command -v jarsigner || true)"
fi
[[ -n "$JAVA_BIN" && -x "$JAVA_BIN" ]] || {
    echo "jarsigner was not found. Set JAVA_HOME to JDK 17." >&2
    exit 2
}

KEYSTORE="${XYLUNE_DEBUG_KEYSTORE_FILE:-${ANDROID_USER_HOME:-$HOME/.android}/debug.keystore}"
STORE_PASSWORD="${XYLUNE_DEBUG_KEYSTORE_PASSWORD:-android}"
KEY_ALIAS="${XYLUNE_DEBUG_KEY_ALIAS:-androiddebugkey}"
KEY_PASSWORD="${XYLUNE_DEBUG_KEY_PASSWORD:-android}"

[[ -f "$INPUT_AAB" ]] || {
    echo "Input bundle does not exist: $INPUT_AAB" >&2
    echo "Run ./gradlew --offline :app:bundleDebug first." >&2
    exit 2
}
[[ -f "$KEYSTORE" ]] || {
    echo "Debug keystore does not exist: $KEYSTORE" >&2
    exit 2
}

mkdir -p "$(dirname "$OUTPUT_AAB")"
cp -f "$INPUT_AAB" "$OUTPUT_AAB"

"$JAVA_BIN" \
    -keystore "$KEYSTORE" \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -sigalg SHA256withRSA \
    -digestalg SHA-256 \
    "$OUTPUT_AAB" "$KEY_ALIAS"

"$JAVA_BIN" -verify -verbose -certs "$OUTPUT_AAB" >/dev/null
printf 'Signed and verified: %s\n' "$OUTPUT_AAB"

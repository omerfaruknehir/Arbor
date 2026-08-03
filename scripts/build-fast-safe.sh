#!/usr/bin/env bash
set -euo pipefail

# Xylune has useful CPU parallelism, but APK/AAB packaging can consume the full
# 4 GiB cgroup because of D8 plus bundled Python/ML/native runtimes.
COMPILE_WORKERS="${XYLUNE_COMPILE_WORKERS:-2}"
PACKAGE_WORKERS="${XYLUNE_PACKAGE_WORKERS:-1}"
GRADLE_CMD="${XYLUNE_GRADLE:-./gradlew}"
COMMON=(--offline --no-daemon)

run() {
  printf '\n+ '
  printf '%q ' "$@"
  printf '\n'
  "$@"
}

case "${1:-all}" in
  compile)
    run "$GRADLE_CMD" "${COMMON[@]}" --max-workers="$COMPILE_WORKERS" :app:compileDebugKotlin
    ;;
  test)
    run "$GRADLE_CMD" "${COMMON[@]}" --max-workers="$COMPILE_WORKERS" :app:testDebugUnitTest
    ;;
  lint)
    run "$GRADLE_CMD" "${COMMON[@]}" --max-workers="$COMPILE_WORKERS" :app:lintDebug
    ;;
  apk)
    run "$GRADLE_CMD" "${COMMON[@]}" --max-workers="$PACKAGE_WORKERS" :app:assembleDebug
    ;;
  all)
    run "$GRADLE_CMD" "${COMMON[@]}" --max-workers="$COMPILE_WORKERS" :app:testDebugUnitTest :app:lintDebug
    run "$GRADLE_CMD" "${COMMON[@]}" --max-workers="$PACKAGE_WORKERS" :app:assembleDebug
    ;;
  *)
    echo "Usage: $0 [compile|test|lint|apk|all]" >&2
    echo "Overrides: XYLUNE_COMPILE_WORKERS=2 XYLUNE_PACKAGE_WORKERS=1" >&2
    exit 2
    ;;
esac

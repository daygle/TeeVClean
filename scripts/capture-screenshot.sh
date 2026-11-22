#!/usr/bin/env bash
# Capture a Play-ready screenshot from a connected Android TV device or emulator.
#
#   ./scripts/capture-screenshot.sh 01-overview
#   ./scripts/capture-screenshot.sh 02-cleanup
#
# Navigate the app with the remote / D-pad first, then run the command for each
# screen. Files land in store-listing/screenshots/ at the device's native
# resolution (1920x1080 on a 1080p TV profile).
#
# Requires adb on PATH and an authorised device (`adb devices` shows "device").
set -euo pipefail

usage() {
    sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
    usage
    exit 0
fi

NAME="${1:-screenshot}"
OUT_DIR="${OUT_DIR:-store-listing/screenshots}"
mkdir -p "$OUT_DIR"

if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found on PATH. Install platform-tools first." >&2
    exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
    echo "No authorised device. Check 'adb devices' and accept the prompt on the TV." >&2
    exit 1
fi

SIZE="$(adb shell wm size | tr -d '\r' | sed -n 's/^.*: //p')"
case "$SIZE" in
    1920x1080) ;;
    *) echo "warning: device reports ${SIZE:-unknown}; Play TV screenshots should be 1920x1080" >&2 ;;
esac

FILE="$OUT_DIR/$NAME.png"
adb exec-out screencap -p > "$FILE"

DIMENSIONS="$(file -b "$FILE" | grep -o '[0-9]\+ x [0-9]\+' || true)"
echo "saved $FILE${DIMENSIONS:+ ($DIMENSIONS)}"
echo "next: navigate to the next screen, then run this script with the next name"

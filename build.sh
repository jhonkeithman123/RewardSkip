#!/usr/bin/env sh
# RewardSkip one-click builder (Linux / macOS)
# Usage:
#   ./build.sh                       build RewardSkip.apk
#   ./build.sh --install             build and install on the connected device
#   ./build.sh --install --serial X  target a specific device
#   ./build.sh --sdk /path/to/Sdk    use a custom Android SDK
set -e
cd "$(dirname "$0")"

if ! command -v python3 >/dev/null 2>&1; then
    echo "python3 was not found on PATH." >&2
    exit 1
fi

echo "=== RewardSkip builder ==="
python3 scripts/build.py "$@"

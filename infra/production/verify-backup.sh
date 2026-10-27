#!/usr/bin/env bash
# Read-only. Digest-only sidecar remains portable when the snapshot is copied off-host.
set -euo pipefail
[[ $# = 1 && -f $1 && -f $1.sha256 ]] || exit 2
expected=$(cat -- "$1.sha256")
[[ $expected =~ ^[0-9a-f]{64}$ ]] || exit 2
actual=$(sha256sum < "$1")
[[ ${actual%% *} = "$expected" ]] || { echo 'Backup checksum mismatch' >&2; exit 1; }

#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENDOR="$ROOT/app/src/main/cpp/vendor/llama.cpp"
# Verified 2026-10-05 v0.6.0 release; do not float against the changing master branch.
PIN="d81235049384534c167caea52b85a694f6103d14"
if [ ! -d "$VENDOR/.git" ]; then
  mkdir -p "$(dirname "$VENDOR")"
  git clone --depth 1 --branch v0.6.0 https://github.com/ggml-org/llama.cpp.git "$VENDOR"
fi
git -C "$VENDOR" fetch --depth 1 origin tag v0.6.0
git -C "$VENDOR" checkout --detach v0.6.0
ACTUAL="$(git -C "$VENDOR" rev-parse HEAD)"
if [ "$ACTUAL" != "$PIN" ]; then echo "Unexpected llama.cpp revision: $ACTUAL" >&2; exit 1; fi
printf '%s\n' "$ACTUAL" | tee "$ROOT/LLAMA_COMMIT.txt"

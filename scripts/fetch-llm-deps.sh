#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODELS_DIR="$ROOT/apps/android/app/src/main/assets/models"
CPP_DIR="$ROOT/apps/android/app/src/main/cpp"

mkdir -p "$MODELS_DIR" "$CPP_DIR"

GGUF_PATH="$MODELS_DIR/smollm2-135m-instruct-q4_k_m.gguf"
if [ ! -f "$GGUF_PATH" ]; then
  curl -L --fail --retry 3 -o "$GGUF_PATH" \
    "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"
fi

if [ ! -f "$CPP_DIR/llama.cpp/CMakeLists.txt" ]; then
  git clone --depth 1 https://github.com/ggml-org/llama.cpp.git "$CPP_DIR/llama.cpp"
fi

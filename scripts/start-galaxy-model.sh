#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

if ! command -v llama-server >/dev/null 2>&1; then
  echo "llama-server is missing. Run bash scripts/setup-galaxy-termux.sh first." >&2
  exit 1
fi

echo "Starting the small Qwen3.5 0.8B model on loopback only."
echo "First run downloads the model; allow several minutes and about 563 MB."
exec llama-server \
  -hf ggml-org/Qwen3.5-0.8B-GGUF:Q4_0 \
  --host 127.0.0.1 \
  --port 8080 \
  --ctx-size 2048 \
  --threads 4

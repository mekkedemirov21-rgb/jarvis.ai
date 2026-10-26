#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "\${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ ! -f .env ]; then
  echo "Missing .env. Run ./scripts/setup-local.sh first." >&2
  exit 1
fi
if [ ! -x .venv/bin/python ]; then
  echo "Missing virtual environment. Run ./scripts/setup-local.sh first." >&2
  exit 1
fi

echo "Starting JARVIS on 127.0.0.1:8765 (loopback only)."
exec .venv/bin/python -m uvicorn backend.main:app --host 127.0.0.1 --port 8765

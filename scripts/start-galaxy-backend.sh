#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ ! -f .env ] || [ ! -x .venv/bin/python ]; then
  echo "Run bash scripts/setup-galaxy-termux.sh first." >&2
  exit 1
fi

echo "Starting JARVIS API at http://127.0.0.1:8765 (this phone only)."
exec .venv/bin/python -m uvicorn backend.main:app --host 127.0.0.1 --port 8765

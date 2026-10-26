#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PYTHON="python3"
if ! command -v "$PYTHON" >/dev/null 2>&1; then
  echo "Python 3.11+ is required. Install Python and rerun this script." >&2
  exit 1
fi
"$PYTHON" - <<'PY'
import sys
if sys.version_info < (3, 11):
    raise SystemExit("Python 3.11 or newer is required.")
print(f"Using Python {sys.version.split()[0]}")
PY

if [ ! -d .venv ]; then
  "$PYTHON" -m venv .venv
fi
VENV_PY=".venv/bin/python"
"$VENV_PY" -m pip install --upgrade pip
"$VENV_PY" -m pip install -r backend/requirements.txt

if [ ! -f .env ]; then
  cp .env.example .env
  "$VENV_PY" - <<'PY'
from pathlib import Path
import base64, secrets
path = Path(".env")
text = path.read_text(encoding="utf-8")
text = text.replace("replace_with_at_least_32_random_characters", secrets.token_urlsafe(48))
text = text.replace(
    "replace_with_locally_generated_32_byte_key",
    base64.urlsafe_b64encode(secrets.token_bytes(32)).decode("ascii"),
)
path.write_text(text, encoding="utf-8")
print("Created .env with fresh local API and encryption keys.")
PY
else
  echo ".env already exists; leaving it unchanged."
fi

echo
echo "Setup finished."
echo "1. Install/start Ollama, then run: ollama pull qwen3.5:4b"
echo "2. Start JARVIS with: bash scripts/start-local.sh"
echo "3. Open http://127.0.0.1:8765/ in this computer's browser."
echo "4. Keep .env private. Never commit it or expose port 8765 publicly."

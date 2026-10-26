#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v pkg >/dev/null 2>&1; then
  echo "This script must run inside Termux on Android." >&2
  exit 1
fi

echo "Updating Termux packages and installing the local runtime..."
pkg update -y
pkg install -y git python python-pip python-cryptography llama-cpp rust clang pkg-config libffi openssl

# Pydantic may need an Android-specific native extension build; keep it to two jobs
# to reduce memory pressure on a phone. No uvloop/httptools extras are requested.
if [ ! -d .venv ]; then
  python -m venv --system-site-packages .venv
fi
CARGO_BUILD_JOBS=2 .venv/bin/python -m pip install --upgrade pip setuptools wheel
CARGO_BUILD_JOBS=2 .venv/bin/python -m pip install --no-cache-dir \
  "fastapi>=0.115,<1.0" \
  "uvicorn>=0.30,<1.0" \
  "httpx>=0.27,<1.0" \
  "pydantic>=2.8,<3.0" \
  "python-dotenv>=1.0,<2.0" \
  "google-api-python-client>=2.150,<3.0" \
  "google-auth-oauthlib>=1.2,<2.0"

if [ ! -f .env ]; then
  cp .env.example .env
fi

# Generate secrets locally and configure a small, on-device llama.cpp model.
# This edits only this Termux checkout's ignored .env; no secrets go to Git.
.venv/bin/python - <<'PY'
from pathlib import Path
import base64
import secrets

path = Path(".env")
lines = path.read_text(encoding="utf-8").splitlines()
values = {}
for line in lines:
    stripped = line.strip()
    if stripped and not stripped.startswith("#") and "=" in stripped:
        key, value = stripped.split("=", 1)
        values[key] = value

def generated_if_placeholder(key, value):
    current = values.get(key, "")
    if not current or current.startswith(("replace_with", "change_me", "your_")):
        values[key] = value

generated_if_placeholder("JARVIS_API_TOKEN", secrets.token_urlsafe(48))
generated_if_placeholder(
    "JARVIS_DATA_KEY",
    base64.urlsafe_b64encode(secrets.token_bytes(32)).decode("ascii"),
)

values.update({
    "JARVIS_LLM_PROVIDER": "lmstudio",
    "JARVIS_LLM_MODEL": "ggml-org/Qwen3.5-0.8B-GGUF:Q4_0",
    "LMSTUDIO_BASE_URL": "http://127.0.0.1:8080/v1",
    "JARVIS_HOST": "127.0.0.1",
    "JARVIS_PORT": "8765",
    "JARVIS_DATA_DIR": "./backend/data",
    "JARVIS_VAULT_PATH": "./backend/data/vault",
    "JARVIS_ENABLE_EMAIL_MONITOR": "false",
    "JARVIS_ENABLE_JOB_MONITOR": "false",
    "JARVIS_ENABLE_GOOGLE_WRITES": "false",
})

written = []
seen = set()
for line in lines:
    stripped = line.strip()
    if stripped and not stripped.startswith("#") and "=" in stripped:
        key = stripped.split("=", 1)[0]
        if key in values:
            written.append(f"{key}={values[key]}")
            seen.add(key)
        else:
            written.append(line)
    else:
        written.append(line)
for key, value in values.items():
    if key not in seen:
        written.append(f"{key}={value}")
path.write_text("\n".join(written) + "\n", encoding="utf-8")
print("Local .env configured; API token and encryption key remain on this phone.")
PY
chmod 600 .env
mkdir -p backend/data/vault .secrets

echo
echo "Setup finished."
echo "Next: start a second Termux session and run:"
echo "  bash scripts/start-galaxy-model.sh"
echo "Wait for the model server to start, then open another session and run:"
echo "  bash scripts/start-galaxy-backend.sh"
echo "To copy the API token into the JARVIS APK, display it privately with:"
echo "  grep '^JARVIS_API_TOKEN=' .env"
echo "Keep the token private. Both services bind to 127.0.0.1 only."

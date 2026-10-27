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
pkg install -y git python python-pip python-cryptography rust clang pkg-config libffi openssl nano

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
    "JARVIS_LLM_PROVIDER": "groq",
    "JARVIS_LLM_MODEL": "openai/gpt-oss-20b",
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
echo "Setup finished. Groq cloud inference selected; no local model download is needed."
echo "1. Create a Groq API key at https://console.groq.com/keys"
echo "2. Add it privately to .env with: nano .env"
echo "   Set GROQ_API_KEY=your_key and keep the key out of chat/screenshots/GitHub."
echo "   Save in nano with Ctrl+O, Enter; exit with Ctrl+X."
echo "3. Start JARVIS: bash scripts/start-galaxy-backend.sh"
echo "4. To copy the separate JARVIS companion token, display it locally with:"
echo "   grep '^JARVIS_API_TOKEN=' .env"
echo "Never share either key. The backend listens only on 127.0.0.1; Groq receives chat prompts for inference."

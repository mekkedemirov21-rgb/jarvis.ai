# JARVIS local companion service

The companion runs on the user's own PC/home server. Bind it to loopback only; the Android client connects through a private HTTPS endpoint/VPN. Dashboard: `http://127.0.0.1:8765/` on the same computer; API docs: `http://127.0.0.1:8765/docs`.

## Requirements

- Python 3.11+
- **One local inference provider:** Ollama or LM Studio
- A locally downloaded model. Recommended starting point: `qwen3.5:4b` in Ollama (about 3.3–4.0 GB download)
- Optional: Google OAuth Web client JSON for Gmail/Calendar read-only
- Optional: Adzuna API ID/key for job search and opt-in job watches

## Configure a local model

### Ollama (recommended default)

```bash
ollama pull qwen3.5:4b
```

In local `.env`:

```dotenv
JARVIS_LLM_PROVIDER=ollama
JARVIS_LLM_MODEL=qwen3.5:4b
OLLAMA_BASE_URL=http://127.0.0.1:11434
```

You can try `qwen3.5:9b` if your RAM/VRAM can support it. Larger is not always better for every task; evaluate matching and German email classification on representative examples.

### LM Studio alternative

Download/load a local model in LM Studio, start its local API server bound to localhost, then set:

```dotenv
JARVIS_LLM_PROVIDER=lmstudio
JARVIS_LLM_MODEL=exact-model-id-from-lm-studio
LMSTUDIO_BASE_URL=http://127.0.0.1:1234/v1
```

If local server authentication is enabled, put its key in `LMSTUDIO_API_KEY` locally. Do not commit it. The adapter supports Ollama chat and LM Studio's OpenAI-compatible chat endpoint and JSON mode. Both configured endpoints are validated as loopback-only; cloud endpoints are intentionally unsupported.

## Start locally

1. Copy `.env.example` to `.env` in the repository root.
2. Generate an API token: `python -c "import secrets; print(secrets.token_urlsafe(48))"`. Put it in `JARVIS_API_TOKEN`.
3. Generate an AES-256 key: `python -c "import base64,secrets; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode())"`. Put it in `JARVIS_DATA_KEY`.
4. Replace both placeholders. The app refuses obvious placeholder values. Keep `.env` outside Git.
5. Install dependencies: `python -m pip install -r backend/requirements.txt`.
6. Start the companion: `python -m uvicorn backend.main:app --host 127.0.0.1 --port 8765`.
7. Open `http://127.0.0.1:8765/` and enter `JARVIS_API_TOKEN`.

The browser holds the token only in the current page session. The API and local model server must remain loopback-only. Do not expose them on LAN/Internet or port-forward them.

## Connect Android securely

1. Keep the FastAPI service running on loopback, port 8765.
2. Install Tailscale on the companion PC and Android. Authenticate both into your private tailnet.
3. On the PC run `tailscale serve 8765`, following any Tailscale prompts to enable HTTPS certificates. Use the HTTPS URL it prints. Do not use Funnel/public sharing.
4. Install the APK artifact from a successful [Build JARVIS APK](https://github.com/mekkedemirov21-rgb/jarvis.ai/actions/workflows/build-apk.yml) workflow run.
5. In the Android app enter the HTTPS URL and your local API token. The app stores the token encrypted using Android Keystore.
6. Grant notification permission. Android polls generic alerts through periodic WorkManager jobs; Android can delay the check and it is not real-time push.

For Google OAuth initiated through the phone, set `GOOGLE_REDIRECT_URI` to the exact HTTPS companion URL plus `/oauth/google/callback` and register that URI in the Google Cloud OAuth Web client. For OAuth on the same PC, the default loopback redirect works.

## Google read-only connector

1. In Google Cloud Console enable Gmail API and Calendar API.
2. Configure OAuth consent and create an OAuth **Web application** client.
3. Register the exact redirect URI described above.
4. Save client JSON as `.secrets/google-oauth-client.json`; this path is ignored by Git.
5. Restart JARVIS and select Google authorization in the dashboard.
6. Approve only `gmail.readonly` and `calendar.events.readonly`.

The encrypted token is stored under `backend/data/google-token.enc`. Gmail triage classifies metadata/snippets locally. It never sends, deletes, archives or edits messages. Disconnecting locally removes the stored token but does not revoke the provider-side grant; remove JARVIS from the Google account's third-party connections separately if desired. Google may require OAuth verification for public distribution.

## Optional mail and job monitoring

### Email

Monitoring is off by default. Configure Google OAuth, then set `JARVIS_ENABLE_EMAIL_MONITOR=true` in the local `.env` and restart. The default interval is 15 minutes; minimum is 5 minutes. First pass establishes a baseline. Future mail is classified by the selected local model and only encrypted alert payloads are stored. No account actions are taken. The Android worker polls the alert queue over HTTPS and emits a generic notification.

### Jobs

Adzuna terms permit personal job search and issue a free API key without payment details, but the free tier is intended for evaluation and limits can change. Add `ADZUNA_APP_ID` and `ADZUNA_APP_KEY` locally. Create an encrypted job watch; opt in with `JARVIS_ENABLE_JOB_MONITOR=true`. The worker searches, locally scores new listings and queues encrypted alerts. No application is submitted.

Indeed is offered as a normal search link only. Indeed terms prohibit unapproved automated access, scraping, and automated content submissions. Do not build an unauthorized scraper to bypass that boundary.

## Current behavior and limits

- Local LLM requests are routed by `backend/llm.py`; prompts are not intentionally stored by the API.
- LLM chat and classification calls have no tool access.
- API token is required for protected endpoints; approval payloads, OAuth token, relationship reminders, and saved job-watch profiles use AES-256-GCM.
- Obsidian-compatible Markdown note contents are plaintext files. Use OS full-disk encryption.
- The approval queue records approve/reject status only. External action execution is not implemented.
- Gmail and Calendar writes are not implemented; scopes are read-only.
- No automated application submission and no personal WhatsApp chat-history import.
- The Android HTTPS transport is not the same as application-layer E2E encryption. The local companion/model sees input in plaintext for processing.
- Never put tokens, OAuth client JSON, private notes, emails, CVs or databases in the public repository.

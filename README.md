# JARVIS — Local-first Personal Assistant

JARVIS is being developed as a privacy-first personal assistant. The Android APK currently hosts the original visual interface; the local companion service provides a growing functional foundation.

## Current functions

- **Local AI chat:** Ollama, with default model `qwen3.5:4b`; prompts are not intentionally persisted.
- **Google Mail / Calendar:** optional official OAuth connector with Gmail and Calendar read-only scopes. Reads recent inbox metadata/snippets and upcoming primary-calendar events. Google OAuth credentials must be configured locally.
- **Email triage:** uses the local model to classify snippets into possible job offer, rejection, interview, application update or other. Results require human review; no mail is sent, deleted or archived.
- **Job search:** optional Adzuna developer API for permitted personal job searching, plus normal search links for Indeed, Bundesagentur für Arbeit and LinkedIn. Indeed is not scraped and applications are never automatically submitted.
- **Job fit:** evaluates user-provided profile and job text with local Ollama without saving those inputs.
- **Memory:** stores local Obsidian-compatible Markdown notes.
- **Approval queue:** stores proposed action payloads encrypted with AES-256-GCM. Approval/rejection is recorded, but does not execute actions.
- **Local dashboard:** served at `http://127.0.0.1:8765/` by the companion API.
- **Android:** Kotlin/WebView APK and JARVIS-inspired vector icon; secure phone-to-companion pairing is not implemented yet.

## Start the local companion

Requirements: Python 3.11+, Ollama and a supported local model.

1. Copy `.env.example` to `.env` in the repository root.
2. Generate a strong API token: `python -c "import secrets; print(secrets.token_urlsafe(48))"`
3. Generate a separate 32-byte encryption key: `python -c "import base64,secrets; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode())"`
4. Set both values in `.env` as `JARVIS_API_TOKEN` and `JARVIS_DATA_KEY`. Do not leave example placeholders.
5. Install Ollama and run `ollama pull qwen3.5:4b`.
6. Install dependencies: `python -m pip install -r backend/requirements.txt`.
7. Start from the repository root: `python -m uvicorn backend.main:app --host 127.0.0.1 --port 8765`.
8. Open `http://127.0.0.1:8765/` in the same computer's browser and enter your local API token. It is not stored in browser storage.

### Optional Gmail and Calendar connection

- In Google Cloud Console, enable Gmail API and Google Calendar API and configure OAuth consent.
- Create an OAuth **Web application** client with this exact redirect URI: `http://127.0.0.1:8765/oauth/google/callback`.
- Save the downloaded client JSON as `.secrets/google-oauth-client.json` (the folder is ignored by Git).
- Restart the API, open the dashboard, enter the local token and select **Google-Konto autorisieren**.
- The first connector requests read-only `gmail.readonly` and `calendar.events.readonly` scopes. Google may require verification for restricted scopes before distributing a public app.

### Optional job-search API

Create a free personal-search developer key at [Adzuna Developer](https://developer.adzuna.com/), then put `ADZUNA_APP_ID` and `ADZUNA_APP_KEY` in the local `.env`. Without the key, the dashboard still provides standard links to Indeed, Bundesagentur für Arbeit and LinkedIn. No secret is sent to the browser or saved in Git.

## Security and scope boundaries

Read [docs/SECURITY.md](docs/SECURITY.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md).

- Keep Ollama and the API bound to loopback. Never port-forward these ports or expose them through a public tunnel.
- Approval payloads are encrypted locally; Markdown memory notes are plaintext for Obsidian compatibility, so enable full-disk encryption.
- WhatsApp personal-chat history is not available through a general official consumer API. The relationship module starts with private notes/reminders, not covert chat access.
- Gmail/Calendar access is read-only. Sending mail, deleting messages, changing calendar entries, push notifications to Android, automatic background monitoring, trusted device pairing and real action execution are not implemented.
- "No data leaks" or "zero vulnerabilities" cannot be guaranteed. Security needs layered controls, patching, tests, backups and external review.
- This GitHub repository is public. Never commit secrets, OAuth files, private data or local database/vault files.

## Validation

GitHub Actions has separate workflows for Android APK builds and backend tests. Download the APK only from a successful **Build JARVIS APK** run under **Artifacts**. Backend tests run on changes to `backend/**` and `tests/**`.

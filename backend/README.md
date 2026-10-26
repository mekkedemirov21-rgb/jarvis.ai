# JARVIS local assistant service

This local companion service runs on the same computer as Ollama. Run it on loopback only. The dashboard is served at `http://127.0.0.1:8765/`; API documentation is at `/docs`.

## Requirements

- Python 3.11+
- Ollama installed locally and `ollama pull qwen3.5:4b` (or another supported local model)
- Optional: Google OAuth Web client JSON for read-only Gmail/Calendar
- Optional: Adzuna developer app ID/key for personal job search

## Start locally

1. Copy `.env.example` to `.env` in the repository root.
2. Generate a random API token: `python -c "import secrets; print(secrets.token_urlsafe(48))"`. Put it in `JARVIS_API_TOKEN`.
3. Generate a separate AES-256 key: `python -c "import base64,secrets; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode())"`. Put it in `JARVIS_DATA_KEY`.
4. Replace both placeholders. The app intentionally refuses obvious placeholder values.
5. Install dependencies: `python -m pip install -r backend/requirements.txt`.
6. Ensure Ollama is available only at `http://127.0.0.1:11434`.
7. Start from the repository root: `python -m uvicorn backend.main:app --host 127.0.0.1 --port 8765`.
8. Open `http://127.0.0.1:8765/` and enter `JARVIS_API_TOKEN`. It is held only in the page's memory/input field; the dashboard does not use browser storage.

Protected API calls use `Authorization: Bearer <JARVIS_API_TOKEN>`. `/health` is intentionally minimal.

## Optional Google read-only connector

1. In Google Cloud Console enable Gmail API and Google Calendar API.
2. Configure OAuth consent, then create an OAuth **Web application** client.
3. Add this exact authorized redirect URI: `http://127.0.0.1:8765/oauth/google/callback`.
4. Save the downloaded client JSON as `.secrets/google-oauth-client.json`; the directory is ignored by Git.
5. Restart JARVIS, open the dashboard and select **Google-Konto autorisieren**.
6. Approve the read-only scopes: `gmail.readonly` and `calendar.events.readonly`.

The encrypted token is stored under `backend/data/google-token.enc`. The API exposes recent email metadata/snippets, local-model triage, and upcoming calendar events. It has no send, delete, or calendar-write methods. To remove the local token, call `DELETE /api/google/disconnect-local` or remove the local data file. Also revoke JARVIS under Google Account third-party connections if you want to revoke the provider grant.

Google may require restricted-scope verification before a public app can be distributed broadly. Use test accounts while developing.

## Optional personal job search

Register at [Adzuna Developer](https://developer.adzuna.com/) and place your `app_id` and `app_key` in the ignored local `.env` as `ADZUNA_APP_ID` and `ADZUNA_APP_KEY`. Adzuna's terms permit personal job search under its free/evaluation tier, subject to its current limits and terms. Without keys, the app shows normal search links for Indeed, Bundesagentur für Arbeit and LinkedIn.

No job-board scraping or automatic application submissions are implemented.

## Current behavior and limits

- Local Ollama chat is not intentionally persisted and exposes no tools to the model.
- Job-fit analysis sends the supplied profile/description only to local Ollama and does not save the inputs.
- Markdown memory notes are local plaintext for Obsidian interoperability; enable full-disk encryption.
- Approval payloads and Google OAuth token files are encrypted with AES-256-GCM, but external action execution is not implemented.
- Email triage is manually triggered. It classifies snippets and returns a suggestion; it cannot notify in the background when the dashboard is closed.
- Gmail/Calendar writes, background monitoring, mobile push notifications, secure Android-to-companion pairing, OS keychain integration and access to personal WhatsApp chat history are not implemented.
- Never expose the API or Ollama to the LAN/Internet or place credentials or personal data in this public repository.

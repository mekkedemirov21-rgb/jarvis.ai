# JARVIS — Local-first Personal Assistant

JARVIS is being developed as a privacy-first personal assistant. The Android app currently hosts the visual interface; a separate local companion API is the first functional foundation.

## Product direction

- Local language model via Ollama; recommended initial model: `qwen3.5:4b`.
- Local Markdown memory compatible with Obsidian.
- Encrypted approval queue for proposed consequential actions.
- Human-reviewed job-search links (Indeed, Bundesagentur für Arbeit, LinkedIn) with no scraping or auto-application.
- Planned least-privilege integrations: Gmail and Google Calendar via OAuth.
- Relationship reminders and user-written memory; no covert messaging surveillance.
- WhatsApp personal-chat access is not available through an official general-purpose consumer API.

## Current implementation

### Android
- Native Kotlin Android wrapper around the JARVIS static HTML/CSS interface.
- Launcher icon inspired by the supplied reference image.
- APK builds through GitHub Actions when Android/project build files change or when manually dispatched.

### Local API foundation
- Local Ollama chat endpoint; requests and responses are not intentionally persisted.
- Bearer-token authentication on API endpoints.
- Service validates that Ollama points to loopback, and recommends the API itself bind to `127.0.0.1`.
- AES-256-GCM encryption of stored approval payloads, with a key that must be generated and held locally.
- Obsidian-compatible Markdown memory vault stored on the local filesystem.
- Approval proposal/decision queue; decisions are recorded, but external actions are **not yet executed**.
- Job-search URLs open job boards for normal human review; this is not a scraper.

## Run the API locally

Requirements: Python 3.11+, Ollama, and the model selected in the local environment.

1. Copy `.env.example` to `.env` in the repository root.
2. Generate a long API token and a separate encryption key locally. For the AES key, use:
   `python -c "import base64,secrets; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode())"`
3. Put those values in `.env` as `JARVIS_API_TOKEN` and `JARVIS_DATA_KEY`. Do not commit `.env`, secrets, OAuth credentials, personal notes, or exports.
4. Install Ollama and download the initial model: `ollama pull qwen3.5:4b`.
5. Install dependencies: `python -m pip install -r backend/requirements.txt`.
6. Start the service from the repository root: `python -m uvicorn backend.main:app --host 127.0.0.1 --port 8765`.
7. Visit `http://127.0.0.1:8765/docs` on the same computer and supply `Authorization: Bearer <JARVIS_API_TOKEN>` for protected API calls.

The API refuses to start if the token or AES key is missing. The API and Ollama should not be exposed to the Internet. The Android app does not yet have the secure pairing/network transport needed to call a desktop-hosted companion.

## Privacy and security

Read [docs/SECURITY.md](docs/SECURITY.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) before connecting accounts. The GitHub repository is public; keep real credentials and personal data outside it. No security design can honestly guarantee zero vulnerabilities.

## Current missing pieces

Gmail/Calendar OAuth, push notifications, secure Android-device pairing, OS keychain-backed secret storage, automatic job refresh from authorized sources, app UI connection to the local API, and execution of explicitly approved actions are not implemented yet. WhatsApp personal-chat ingestion is intentionally not included. The connector work must follow official API availability, platform terms, explicit authorization, and least-privilege permissions.

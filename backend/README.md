# JARVIS local assistant service

This is the privacy-first service foundation for the Android JARVIS UI. The service is designed to run on the same computer as Ollama and bind to loopback only. It does not scrape job boards, send applications, access private WhatsApp chats, or connect to Gmail/Google Calendar until their official authorization flows are implemented.

## Requirements

- Python 3.11+
- Ollama installed locally
- A downloaded model, for example `ollama pull qwen3.5:4b`

## Start locally

1. Copy `.env.example` to `.env` in the repository root.
2. Generate an API token and put it in `JARVIS_API_TOKEN`. For example: `python -c "import secrets; print(secrets.token_urlsafe(48))"`
3. Generate the AES-256 data key locally: `python -c "import base64,secrets; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode())"`. Put the output in `JARVIS_DATA_KEY`. Keep this key safe; encrypted approval payloads cannot be decrypted if it is lost.
4. Keep `.env` outside Git; never commit real credentials, keys, or private data.
5. Install dependencies: `python -m pip install -r backend/requirements.txt`
6. Start Ollama locally and ensure its API is available at `http://127.0.0.1:11434`.
7. Run from the repository root: `python -m uvicorn backend.main:app --host 127.0.0.1 --port 8765`
8. Open `http://127.0.0.1:8765/docs` locally. API endpoints except `/health` require `Authorization: Bearer <JARVIS_API_TOKEN>`.

The service refuses to start if either secret or key is missing. The API and Ollama should not be exposed to the Internet. The Markdown vault is plaintext for Obsidian interoperability: use full-disk encryption on the host. Approval payloads are AES-256-GCM encrypted in SQLite.

## Current capabilities

- Local Ollama chat endpoint, with no prompt-history persistence.
- Local Markdown memory notes in an Obsidian-compatible vault folder.
- A human approval queue for proposed actions. Approval records are encrypted and a decision is stored, but actions are not executed.
- Safe external job-search links without scraping or auto-applying.
- Integration status endpoint that openly reports unimplemented connectors.
- Backend tests in GitHub Actions.

## Not implemented yet

Gmail/Google Calendar OAuth, scheduled mailbox monitoring, push notifications, trusted device pairing, OS keychain-backed secret storage, integration execution, and Android-to-companion networking all require additional implementation and user-provided OAuth setup. No credentials belong in this public repository.

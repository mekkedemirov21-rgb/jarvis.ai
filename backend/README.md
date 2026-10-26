# JARVIS local assistant service

This is the privacy-first service foundation for the Android JARVIS UI. The service is designed to run on the same computer as Ollama and bind to loopback only. It does not scrape job boards, send applications, access private WhatsApp chats, or connect to Gmail/Google Calendar until their official authorization flows are implemented.

## Requirements

- Python 3.11+
- Ollama installed locally
- A downloaded model, for example `ollama pull qwen3.5:4b`

## Start locally

1. Copy `.env.example` to `.env` in the repository root.
2. Replace `JARVIS_API_TOKEN` with a random secret of at least 32 characters. Keep `.env` out of Git.
3. Install dependencies: `python -m pip install -r backend/requirements.txt`
4. Start Ollama locally and ensure its API is available at `http://127.0.0.1:11434`.
5. Run: `python -m uvicorn backend.main:app --host 127.0.0.1 --port 8765`
6. Open `http://127.0.0.1:8765/docs` locally. API endpoints except `/health` require `Authorization: Bearer <JARVIS_API_TOKEN>`.

The Android app does not yet connect to this service. A secure pairing flow and authenticated transport must be implemented before connecting a phone to a desktop-hosted service. Do not expose ports 8765 or 11434 to the public Internet.

## Current capabilities

- Local Ollama chat endpoint, with no prompt-history persistence.
- Local Markdown memory notes in an Obsidian-compatible vault folder.
- A human approval queue for proposed actions (the queue records decisions only; it does not execute them).
- Safe external job-search links without scraping or auto-applying.
- Integration status endpoint that openly reports unimplemented connectors.

## Not implemented yet

Google OAuth and Gmail/Calendar access, scheduled mailbox monitoring, push notifications, trusted device pairing, encrypted token storage, true integration execution, and Android-to-companion networking all require additional implementation and user-provided OAuth setup. No credentials belong in this public repository.

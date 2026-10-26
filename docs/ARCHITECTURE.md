# JARVIS Architecture and implementation status

**Stand: 26. Oktober 2026.** The project is a local-first foundation with a working Android client and local companion. It is not yet a fully autonomous agent, and security has not been externally audited.

## System architecture

```text
Android app
  ├─ remembers only private HTTPS companion URL
  ├─ API token encrypted with Android Keystore (AES-GCM)
  ├─ renders companion dashboard; token stays in the page's current session
  └─ periodic WorkManager task polls private companion alerts over HTTPS
       |
       | HTTPS over a private VPN / tailnet (recommended)
       v
User-owned PC / home server
  ├─ FastAPI service bound to 127.0.0.1:8765
  ├─ Bearer-token authentication; random secret required at start
  ├─ local model adapter
  │    ├─ Ollama /api/chat
  │    └─ LM Studio OpenAI-compatible /v1/chat/completions
  │       (the chosen model server must resolve to loopback)
  ├─ local SQLite DB
  │    ├─ encrypted proposal payloads / relationship reminders / job-watch profiles
  │    ├─ hashed IDs for monitored messages/listings
  │    └─ encrypted monitor-alert payloads
  ├─ local Markdown vault (Obsidian-compatible; plaintext on disk)
  ├─ optional Google OAuth (Gmail / Calendar, read-only)
  ├─ optional scheduled email triage and job matching
  └─ external job provider API and ordinary job-search links
```

The public repository must contain source only. Never commit `.env`, OAuth client secrets, token files, local databases, CVs, email contents, Markdown vault notes, WhatsApp exports or API keys.

## Feature status

| Feature | Current state | Safety boundary |
|---|---|---|
| Android companion client | Implemented; requires reachable HTTPS endpoint | No insecure HTTP, no embedded API secret; API token is Android-Keystore encrypted |
| Local AI chat | Implemented for Ollama and LM Studio | No model tools; prompt/reply not intentionally persisted by the API |
| Memory | Local Markdown create/list, Obsidian-compatible | Markdown note content is plaintext; enable full-disk encryption |
| Gmail read | Optional Google OAuth | Read-only `gmail.readonly` scope |
| Calendar read | Optional Google OAuth | Read-only `calendar.events.readonly` scope |
| Email classification | Manual triage and opt-in monitor | Model proposes categories; no send/delete/archive/accept/reject |
| Job search | Optional Adzuna API + human-operated links | No Indeed scraping; listing text is untrusted |
| Job-fit evaluation | User profile and listing evaluated with local LLM | No application submitted; matching can be wrong |
| Scheduled job monitor | Optional, configured watches and encrypted alerts | No automatic applications |
| Relationship reminders | Create/list/cancel reminders; encrypted content | User provides notes; no private WhatsApp read access |
| Android alert notifications | Periodic WorkManager poll (minimum 15 minutes; Android may delay) | Generic message text, not mailbox subject/content; requires network and permission |
| Approval queue | Stores proposal and user decision | Approval does **not** execute an external action yet |
| Send/edit/delete email or calendar events | Not implemented | No write token scope requested |
| Automatic application submission | Not implemented | Human review required |
| Read personal WhatsApp chat history | Not implemented / no general official consumer API | No QR/session scraping or covert import |
| True application-layer end-to-end encryption from Android to model | Not implemented | HTTPS protects transport; local model/companion must see plaintext to process |

## Model selection

Default: `JARVIS_LLM_PROVIDER=ollama`, `JARVIS_LLM_MODEL=qwen3.5:4b`. As listed by Ollama, the Qwen3.5 4B model download is approximately 3.3–4.0 GB; the 9B variant is approximately 6.6–7.6 GB. Qwen3.5 is the recommended first test for local assistant tasks. Quality depends on RAM/VRAM, quantization, prompt design and language/task. Do not assume a 4B model is always accurate.

Setup:

```bash
ollama pull qwen3.5:4b
```

LM Studio can be used instead:

- Set `JARVIS_LLM_PROVIDER=lmstudio`.
- Start LM Studio's local API server at `http://127.0.0.1:1234/v1`.
- Set `JARVIS_LLM_MODEL` to the exact model identifier exposed by LM Studio.
- Keep the server bound to loopback and never place an externally hosted endpoint in this local-only adapter.

The central adapter normalizes chat and JSON-mode responses for chat, job review, email classification and scheduled job-matching. Tests exercise both provider request shapes without contacting either server.

See [AI Models and Integrations](AI_MODELS_AND_INTEGRATIONS.md) for model comparison, hardware guidance, provider setup and linked primary sources.

## Data flow and trust boundaries

1. The user enters an API token locally. The backend requires Bearer authentication on protected routes.
2. A prompt moves from the dashboard to the local FastAPI service and then to a loopback-only model endpoint. The model has no tools or ability to execute user-account actions.
3. When Gmail is connected, metadata/snippets are fetched from Google's API after explicit OAuth consent; Google still processes the account data. The local model sees the selected snippets in plaintext while classifying them.
4. When Adzuna keys are configured, job listing data arrives from that provider and is treated as untrusted third-party content. User profile/watch state and generated alerts are encrypted at rest in the local database.
5. Pending action payloads and relationship reminders are encrypted with AES-256-GCM. Obsidian-compatible Markdown notes are intentionally not encrypted by the application; full-disk encryption is required for at-rest protection.
6. Android sends requests through HTTPS. The token stored by Android is encrypted with AES-GCM using Android Keystore. The server still sees request content, because it is the trusted local processing endpoint.

## Recommended private Android connection

1. Start JARVIS on the PC using `python -m uvicorn backend.main:app --host 127.0.0.1 --port 8765`.
2. Install Tailscale on both PC and phone and authenticate both into the private tailnet.
3. On the PC run `tailscale serve 8765`. Use the HTTPS URL shown by Tailscale Serve. Do not use public Funnel; keep the API port private.
4. In the Android app enter the HTTPS URL and the API token. Grant Android notification permission if prompted.

If you authorize Google from the phone through this HTTPS endpoint, set `GOOGLE_REDIRECT_URI` to that exact HTTPS URL plus `/oauth/google/callback` and add the identical redirect URI to the Google OAuth Web client. For local desktop-only OAuth the default loopback callback can be used.

## Development roadmap

1. **Foundation and tests (current):** local model abstraction, secure local companion, read-only Google connector, local memory, job matching, relationship reminders, approval queue and Android notifications.
2. **Reliability before autonomy:** test matrix for German emails and jobs; audit log for proposed actions; retention settings; export/delete workflow; OAuth-expiry handling; backup and recovery tests.
3. **Controlled writes:** only after explicit action design, least-privilege OAuth scopes, preview of exact payload, replay/idempotency guards, approval expiry and rollback support. Never use one broad permission for convenience.
4. **Security review:** rate-limit the local API, bind tokens to devices, review WebView/session handling, inspect dependencies/SBOM, threat model and independent penetration test.
5. **Future real-time notifications:** choose a transport after threat-modeling. A cloud push provider may receive notification metadata and therefore requires separate privacy review. Current notifications use periodic polling and are not real-time.

## Security claims

No implementation can responsibly guarantee zero leaks or zero vulnerabilities. The aim is to reduce risk by keeping inference local, exposing only a private HTTPS companion, minimizing OAuth scopes, encrypting selected records, avoiding automatic consequential actions and testing changes in CI. A full external security audit has not been completed.

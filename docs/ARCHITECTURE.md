# JARVIS Architecture and implementation status

**Stand: 26. Oktober 2026.** The project is a local-first foundation with a working Android client and companion API. Model inference supports local Ollama/LM Studio or an explicit Groq cloud mode. Groq mode sends prompts to Groq; it is not local-first for inference. It is not yet a fully autonomous agent, and security has not been externally audited.

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
  ├─ model adapter
  │    ├─ Ollama /api/chat (loopback only)
  │    ├─ LM Studio OpenAI-compatible /v1/chat/completions (loopback only)
  │    └─ optional Groq HTTPS cloud API (prompts leave the device)
  ├─ local SQLite DB
  │    ├─ encrypted proposal payloads / relationship reminders / job-watch profiles
  │    ├─ hashed IDs for monitored messages/listings
  │    └─ encrypted monitor-alert payloads
  ├─ local Markdown vault (Obsidian-compatible; plaintext on disk)
  ├─ optional Google OAuth (read-only by default; opt-in write scopes for drafts/send and calendar create/update)
  ├─ optional scheduled email triage and job matching
  └─ external job provider API and ordinary job-search links
```

The public repository must contain source only. Never commit `.env`, OAuth client secrets, token files, local databases, CVs, email contents, Markdown vault notes, WhatsApp exports or API keys.

## Feature status

| Feature | Current state | Safety boundary |
|---|---|---|
| Android companion client | Implemented; requires reachable HTTPS endpoint | No insecure HTTP, no embedded API secret; API token is Android-Keystore encrypted |
| AI chat | Implemented for Ollama, LM Studio, and opt-in Groq | No model tools; prompt/reply not intentionally persisted by JARVIS API; Groq processes prompts in its cloud |
| Memory | Obsidian-compatible local Markdown notes plus keyword-retrieval into chat | Only a few lexically relevant excerpts are inserted; notes remain plaintext on disk |
| Gmail read | Optional Google OAuth | Read-only `gmail.readonly` scope by default |
| Calendar read | Optional Google OAuth | Read-only `calendar.events.readonly` scope by default |
| Email classification | Manual triage and opt-in monitor | Model proposes categories; no send/delete/archive/accept/reject |
| Job search | Optional Adzuna API + human-operated links | No Indeed scraping; listing text is untrusted |
| Job-fit evaluation | User profile and listing evaluated with local LLM | No application submitted; matching can be wrong |
| Scheduled job monitor | Optional, configured watches and encrypted alerts | No automatic applications |
| Relationship reminders | Create/list/cancel reminders; encrypted content | User provides notes; no private WhatsApp read access |
| Android alert notifications | Periodic WorkManager poll (minimum 15 minutes; Android may delay) | Generic message text, not mailbox subject/content; requires network and permission |
| Approval queue | Encrypted proposals, decision and execution status | Google actions require proposal review, approval, then a separate execute request; 15-minute approval expiry, no automatic retries |
| Gmail draft/send | Implemented behind `JARVIS_ENABLE_GOOGLE_WRITES=true` | Requires a fresh OAuth grant for `gmail.compose`, a validated one-recipient payload, and two separate user confirmations; no delete action |
| Calendar create/update | Implemented behind `JARVIS_ENABLE_GOOGLE_WRITES=true` | Requires a fresh OAuth grant for `calendar.events`, explicit payload preview, approval, and separate execution |\n| Automatic application submission | Not implemented | Human review required; no Indeed scraping |
| Read personal WhatsApp chat history | Not implemented / no general official consumer API | No QR/session scraping or covert import |
| True application-layer end-to-end encryption from Android to model | Not implemented | HTTPS protects transport; local model/companion must see plaintext to process |

## Model selection

Default on a fresh general setup: `JARVIS_LLM_PROVIDER=ollama`, `JARVIS_LLM_MODEL=qwen3.5:4b`. The Galaxy Termux setup defaults to `JARVIS_LLM_PROVIDER=groq`, `JARVIS_LLM_MODEL=openai/gpt-oss-20b` to avoid phone-side model compilation/download. Groq requires a private `GROQ_API_KEY` and sends prompts to the cloud; usage may be rate-limited. As listed by Ollama, the Qwen3.5 4B model download is approximately 3.3–4.0 GB; the 9B variant is approximately 6.6–7.6 GB. Quality depends on hardware, quantization, prompts and language/task.

Setup:

```bash
ollama pull qwen3.5:4b
```

LM Studio can be used instead:

- Set `JARVIS_LLM_PROVIDER=lmstudio`.
- Start LM Studio's local API server at `http://127.0.0.1:1234/v1`.
- Set `JARVIS_LLM_MODEL` to the exact model identifier exposed by LM Studio.
- Keep Ollama/LM Studio bound to loopback. Groq is the sole supported cloud provider and uses a fixed HTTPS endpoint; prompts sent to Groq leave the device.

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

1. **Foundation and tests (current):** local model abstraction, secure local companion, Google read-only by default with opt-in draft/send/calendar writes, keyword-based memory retrieval, job matching, relationship reminders, approval queue and Android notifications.
2. **Reliability before autonomy:** test matrix for German emails and jobs; audit log for proposed actions; retention settings; export/delete workflow; OAuth-expiry handling; backup and recovery tests.
3. **Controlled writes (first stage implemented for Google):** Gmail draft/send and calendar create/update are available only with opt-in scopes, exact preview, approval plus separate execute request, a 15-minute expiry and no automatic retries. Broader error recovery, stronger idempotency, undo/rollback and public-app OAuth review remain before considering a general release. Never use one broad permission for convenience.
4. **Security review:** rate-limit the local API, bind tokens to devices, review WebView/session handling, inspect dependencies/SBOM, threat model and independent penetration test.
5. **Future real-time notifications:** choose a transport after threat-modeling. A cloud push provider may receive notification metadata and therefore requires separate privacy review. Current notifications use periodic polling and are not real-time.

## Security claims

No implementation can responsibly guarantee zero leaks or zero vulnerabilities. The aim is to reduce risk by keeping inference local, exposing only a private HTTPS companion, minimizing OAuth scopes, encrypting selected records, avoiding automatic consequential actions and testing changes in CI. A full external security audit has not been completed.

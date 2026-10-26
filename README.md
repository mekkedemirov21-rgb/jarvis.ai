# JARVIS — local-first personal assistant

JARVIS is a privacy-oriented personal assistant with an Android client and a local companion service. The repository is public; **do not commit secrets, account data, CVs, messages, notes, or local databases**.

## What is implemented today

- **Android client:** connects to the private companion over HTTPS; the API token is encrypted with Android Keystore and inserted into the dashboard session. It can show neutral Android notifications for new queued job/mail hints and due reminders (WorkManager polling, not real-time push).
- **Local AI:** supports Ollama or LM Studio loopback APIs. Default is Qwen3.5 4B through Ollama. Prompts are not intentionally persisted by the API and the model has no tools or direct authority to act.
- **Gmail and Google Calendar:** optional official OAuth connector, read-only by default; opt-in Gmail draft/send and Calendar create/update are available through the two-step approval queue after fresh write-scope consent. Optional background triage can notify about a likely job offer, rejection, interview or application update.
- **Job search:** optional Adzuna personal-search API (free/evaluation tier subject to provider limits/terms) plus normal links to Indeed, Bundesagentur für Arbeit, and LinkedIn. The Jobs tab can save encrypted watch profiles, display monitor status and show ranked job alerts; the local model explains fit and missing requirements, then you decide whether to apply.
- **Application preparation:** evaluates fit and missing qualifications. Automatic application submission is not implemented; Indeed is not scraped and its site is not automated.
- **Memory:** portable Obsidian-compatible Markdown notes. JARVIS retrieves up to three lexically relevant local excerpts into each chat context; it does not send the whole vault. The Markdown files are plaintext, so host full-disk encryption is required.
- **Relationship support:** encrypted reminders and user-written notes. No covert access to private WhatsApp chats.
- **Approval queue:** encrypted proposed actions with separate proposal, approval and execution steps. Gmail draft/send and Google Calendar create/update can be executed only when `JARVIS_ENABLE_GOOGLE_WRITES=true` is explicitly set and Google is re-authorized with the added scopes. Every action must be inspected, approved and then separately executed within 15 minutes. Job applications, mail deletion, automatic offer acceptance/rejection and WhatsApp personal messages remain manual/not implemented.

For detailed model comparisons, setup choices, providers and the current feature boundary, read [AI Models and Integrations](docs/AI_MODELS_AND_INTEGRATIONS.md), [Architecture](docs/ARCHITECTURE.md), [Product Specification](docs/PRODUCT_SPEC.md), and [Security](docs/SECURITY.md).

## Run JARVIS directly on an Android phone (experimental)

The Android APK can run against a local companion in Termux on the same phone. See [Galaxy local setup](docs/GALAXY_LOCAL_SETUP.md). In this mode, the APK allows HTTP only to `127.0.0.1`; all non-loopback server connections still require HTTPS. The small Qwen3.5 0.8B model is a low-resource starting point and is much less capable than a larger PC model.

## Recommended free local model

Start with Ollama + Qwen3.5 4B:

```bash
ollama pull qwen3.5:4b
```

It uses your own computer rather than paying for a hosted inference API. A larger 9B model can be tested if the machine has enough memory. The 4B model is useful for general chat, summarization, simple JSON triage and job comparison, but can still make mistakes. Benchmark it on your own representative cases before trusting important recommendations.

LM Studio is another supported local provider. Configure `JARVIS_LLM_PROVIDER=lmstudio`, use its local OpenAI-compatible server at `http://127.0.0.1:1234/v1`, and set `JARVIS_LLM_MODEL` to the model identifier exposed by the LM Studio server. Never configure a public/cloud endpoint for personal mail or CV content.

## Run the local companion on your computer

Requirements: Python 3.11+, either Ollama or LM Studio, and a model downloaded locally.

### Windows (PowerShell)

1. Install Python 3.11+ and Ollama.
2. From the repository root run `Set-ExecutionPolicy -Scope Process Bypass` if PowerShell blocks local scripts, then `.`\\scripts\\setup-windows.ps1`.
3. Download the model once: `ollama pull qwen3.5:4b`.
4. Start JARVIS with `.`\\scripts\\start-windows.ps1` and open `http://127.0.0.1:8765/`.

### macOS / Linux

```bash
bash scripts/setup-local.sh
ollama pull qwen3.5:4b
bash scripts/start-local.sh
```

The setup script creates a Python virtual environment and, if missing, a local `.env` with fresh random API and encryption keys. It does not overwrite an existing `.env`. Open `http://127.0.0.1:8765/` on the same computer and enter `JARVIS_API_TOKEN`.

The API token is not saved in browser storage. The API and local model server must remain loopback-only. Do not bind them to `0.0.0.0` or port-forward them.

### Optional Google Mail and Calendar

1. In Google Cloud Console enable Gmail API and Google Calendar API.
2. Create an OAuth **Web application** client.
3. For same-computer local use, register `http://127.0.0.1:8765/oauth/google/callback`. For Android via private HTTPS, set `GOOGLE_REDIRECT_URI` to the exact HTTPS companion URL plus `/oauth/google/callback` and register that same URI in Google Cloud.
4. Save client JSON to `.secrets/google-oauth-client.json` (ignored by Git), restart the companion, and authorize from the dashboard.

By default, the scopes are read-only: `gmail.readonly` and `calendar.events.readonly`. Google writes are opt-in: setting `JARVIS_ENABLE_GOOGLE_WRITES=true` additionally requests `gmail.compose` (draft/send) and `calendar.events` (create/update). Restart JARVIS, disconnect the old local token and reconnect Google to grant the new scopes. These Gmail scopes are restricted and Google may require app verification; keep this in personal testing until OAuth review is addressed. Email monitoring is disabled by default. To opt in on your own PC set `JARVIS_ENABLE_EMAIL_MONITOR=true` and restart the companion. The first monitoring run establishes a baseline rather than notifying about old mail.

### Optional job monitoring

Register for an Adzuna developer key and set `ADZUNA_APP_ID` and `ADZUNA_APP_KEY` in local `.env`. The free tier is for evaluation/personal-search use and is subject to provider limits and terms. In the Jobs tab, save a search profile and opt in with `JARVIS_ENABLE_JOB_MONITOR=true`. Restart JARVIS. The first monitor pass establishes a baseline; later new listings are locally scored and suitable hits create alerts. The PC must stay on and the monitor is limited by provider schedule/quotas. Android notifications are periodic, not real-time. No automated applications are submitted.

## Security boundaries

- Use full-disk encryption for the machine and back up encryption keys safely.
- AES-256-GCM encrypts approval payloads, local Google OAuth tokens, saved job watches and relationship reminders. Obsidian Markdown notes remain plaintext files for compatibility.
- The Android API token uses Android Keystore-backed encryption; the companion connection requires HTTPS.
- The server and local model must stay private. The model sees input in plaintext because it must process it.
- There is no formal E2E encryption of model prompts from a server that processes them. HTTPS protects transport; at-rest encryption protects only the specified files/records.
- No product can guarantee zero vulnerabilities. Keep software patched and run tests before releasing.
- Supported Google writes (Gmail draft/send and Calendar create/update) require `JARVIS_ENABLE_GOOGLE_WRITES=true`, fresh OAuth consent, exact payload review, approval and a second explicit execution confirmation. An attempted or uncertain remote operation is never retried automatically. Mail deletion, automatic offer acceptance/rejection, application submission and private WhatsApp messaging are not implemented.
- WhatsApp personal chat history is not available through a general official consumer API. JARVIS only supports user-managed relationship reminders and notes.

## Validation

- Backend tests: [Test JARVIS backend](https://github.com/mekkedemirov21-rgb/jarvis.ai/actions/workflows/test-backend.yml)
- Android packaging: [Build JARVIS APK](https://github.com/mekkedemirov21-rgb/jarvis.ai/actions/workflows/build-apk.yml)
- Download APK only from a successful run's `JARVIS-debug-APK` artifact.

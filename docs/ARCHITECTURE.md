# JARVIS Architecture — Initial Design

**Status: foundation in progress, not a finished personal-agent product.** This document separates implemented features from planned connectors so no feature is mistaken for working code.

## 1. Target architecture

```text
Android JARVIS UI
  └── authenticated device pairing (PLANNED, not implemented)
        └── local companion service on user's PC/home server
              ├── policy / approval gate
              ├── Ollama local inference (loopback only)
              ├── local Obsidian-compatible memory vault
              ├── encrypted pending-action database
              ├── Gmail connector (OAuth, PLANNED)
              ├── Google Calendar connector (OAuth, PLANNED)
              ├── job-search link layer / permitted providers
              ├── notification service (PLANNED)
              └── relationship notes and user-controlled reminders
```

The public Git repository contains source code only. It must never contain OAuth files, tokens, mailbox content, WhatsApp exports, CVs, notes, or the local database.

## 2. Model selection

### Recommended start: Ollama + Qwen 3.5 4B

The current Ollama library lists Qwen 3.5 variants from 0.8B through 122B parameters. The 4B download is about 3.3–4.0 GB; the 9B variant is about 6.6–7.6 GB. Use `qwen3.5:4b` as a practical local starting point, then test the 9B variant if the computer has more memory. The model's listed feature set includes tool use, vision, and reasoning; JARVIS must still apply its own allow-list, confirmation gate, and output validation.

Commands:
```sh
ollama pull qwen3.5:4b
ollama run qwen3.5:4b
```

Alternative: `gpt-oss:20b` in Ollama is an open-weight reasoning/agentic model, with a listed download footprint around 14 GB. It may be more capable for complex tasks but needs substantially more RAM/VRAM and will be slower on modest hardware. LM Studio is also a viable local inference host and provides an OpenAI-compatible local API; the app can later expose a provider setting for Ollama vs LM Studio.

All local models can still hallucinate or follow malicious document instructions. No model is allowed to directly execute tools. The policy service, not the model, determines which actions exist and whether explicit approval is needed.

## 3. Integrations

### Gmail / email

- Use official Google OAuth; request only scopes needed for specific features.
- Start read-only for classification, detecting interview invites and job outcomes.
- Add draft creation as a later, separate capability.
- Require an approval screen before sending or deleting anything.
- Do not request permanent-delete access. Prefer labels/Trash and reversible actions.
- Access/refresh tokens must be stored in an OS secure store, not the repository or browser localStorage.
- Google can require application verification for sensitive/restricted scopes. Personal testing and a production release have different setup requirements.

### Google Calendar

- Begin with read-only event access; use least-privilege event scopes for creating/editing only when enabled.
- Show the proposed title, attendees, timezone, start/end, location, and notifications before an event is written.
- Never silently invite external attendees or cancel meetings.

### Jobs and applications

- Indeed must not be scraped and an agent must not automate Indeed Apply without express permission / official tools. The current implementation returns ordinary search links for Indeed, Bundesagentur für Arbeit, and LinkedIn so the user opens and reviews listings.
- Never present an unofficial community wrapper as an official, authorized API.
- The provider layer should support sources only when their documented APIs or user-approved feeds permit this use.
- JARVIS can rank an imported job description against a user-approved CV profile, explain why it is a match, draft a tailored CV/cover letter, track deadlines, and prepare an application package.
- It must not submit an application until the user sees the exact destination, job, form answers, uploaded files and generated text and explicitly confirms. Avoid mass applications.
- Email-based job outcomes can become inbox labels/reminders after Gmail integration; don't auto-delete acceptance or rejection messages.

### WhatsApp / relationship

- The official WhatsApp Business Platform is for business messaging and is not a general consumer API for silently reading a person's private chat history.
- Do not reverse-engineer WhatsApp Web, steal session cookies, request a QR login into JARVIS, or collect a partner's messages covertly.
- Safe initial relationship support: user-written notes, important dates, reminders, communication goals and message drafts that the user reviews and sends manually.
- A supported business account integration, where policy permits, is a separate feature and is not a path into private personal chats.

### Memory / Obsidian

- Use a local Markdown vault so notes stay portable and can be opened in Obsidian.
- Store curated stable facts/preferences separately from transient conversation context.
- Store the minimum necessary; define note TTL/deletion controls and a "forget this" workflow before saving personal records automatically.
- Plain Markdown is not encrypted by itself. Protect the vault with full-disk encryption and do not sync it to a third-party cloud unless explicitly chosen by the user.
- Pending action payloads in the current API are encrypted using AES-256-GCM, with a local key supplied outside Git. The key should eventually move into an OS keychain/keystore.

## 4. Current API endpoints

Run the local API as documented in `backend/README.md`. Endpoints require `Authorization: Bearer <JARVIS_API_TOKEN>` except the minimal health check.

- `GET /health`: local service status only.
- `GET /api/integrations/status`: honest connector status.
- `POST /api/chat`: non-persistent local Ollama chat without tools.
- `GET/POST /api/memory`: list note metadata and write local Markdown notes.
- `GET/POST /api/approvals`: encrypted action proposals and review queue.
- `POST /api/approvals/{id}/decision`: record approval/rejection only; it does not execute an external action.
- `GET /api/jobs/search-links?q=...&location=...`: create human-operated search links, not scraped results.

The API currently is not connected to the Android UI. A secure phone-to-companion pairing and TLS-protected transport is required before networking the APK to a PC service.

## 5. Phased roadmap

1. Local foundations (current): model adapter, local memory vault, encrypted approval records, auth, job-search links.
2. Verification: automated tests, security review, rate limits, audit events and retention controls.
3. Device pairing: Android Keystore-backed key, authenticated pairing, TLS, revocation and no localStorage secrets.
4. Mail/calendar: OAuth setup, read-only sync, triage and notifications; add drafts next.
5. Job workflow: preferences/profile, permitted data sources, match scoring with explanations, application kit and human-confirmed submissions only through allowed channels.
6. Relationship module: private memory and reminders with a clear no-covert-surveillance boundary.
7. Hardening and release: dependency scanning, backup/recovery, threat modeling, privacy policy, consent screens and external testing.

## 6. Non-goals

No security promise of "zero vulnerabilities", no silent account takeover, no unapproved external sends/submissions, no destructive actions from a model-generated instruction, and no private WhatsApp account scraping.

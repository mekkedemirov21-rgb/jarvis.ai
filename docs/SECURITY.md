# JARVIS Security and Privacy Policy (development baseline)

JARVIS handles highly sensitive material. This is a practical baseline, not a certification or a claim that the system is free of vulnerabilities.

## Data boundaries

| Data | Intended location | Current treatment |
|---|---|---|
| Chat prompts/replies | Local Ollama process | Not written intentionally to API logs or application database |
| Approval payloads | Local SQLite DB | AES-256-GCM authenticated encryption with local key |
| Memory notes | Local Markdown vault | Plain text for Obsidian interoperability; requires OS full-disk encryption |
| OAuth tokens | OS credential store (planned) | OAuth connectors are not implemented |
| Email/calendar contents | Not connected yet | No live mailbox/calendar access |
| WhatsApp personal chats | Not collected | No supported general consumer API integration |
| GitHub repository | Public source code | Never put secrets, personal data, notes, exports, or database files here |

## Security controls currently present

- The API refuses to start without a `JARVIS_API_TOKEN` of at least 32 characters.
- Protected endpoints require Bearer-token authentication using constant-time comparison.
- The API checks that `OLLAMA_BASE_URL` resolves to loopback only; default is `http://127.0.0.1:11434`.
- API is documented to bind to `127.0.0.1`; do not change to `0.0.0.0`.
- Approval payloads are encrypted with AES-256-GCM; nonce and ciphertext are stored together and authenticated on read.
- The local chat endpoint passes no tools to the model and does not persist prompts or responses.
- Action decisions are logged in a local state table but do not currently execute side effects.
- `.gitignore` excludes local .env files, databases, logs and local data.

## Required deployment precautions

1. Enable full-disk encryption on the computer hosting JARVIS (BitLocker, FileVault or LUKS). The Markdown vault is plaintext on that device.
2. Generate secrets locally. Keep `.env` outside Git and never put keys, OAuth client secrets, session cookies or refresh tokens in source files, screenshots, issue reports or chat prompts.
3. Keep Ollama and JARVIS API on loopback. Do not port-forward them or expose them through a public tunnel.
4. Do not connect a phone to the host API over plain HTTP on a LAN. A future pairing flow must use authenticated TLS or a properly configured private VPN plus device authentication.
5. Use Google OAuth scopes incrementally. Start read-only; add write access only after explaining it to the user.
6. All consequential actions (send email, submit application, alter calendar, send a message, delete/ archive mail) must show the full proposed action and require fresh user confirmation.
7. Treat incoming email, job descriptions, documents and chat exports as untrusted. They can contain prompt-injection instructions. Never allow content to grant itself permissions.
8. Prefer reversible actions; never automatically permanently delete email or accept/reject an offer.
9. Store only necessary facts, offer deletion and export, and implement retention limits before automatic memory capture.
10. Back up encrypted data and test recovery. Keep encryption key backups separately and securely; losing the key makes encrypted approval records unrecoverable.

## Limits and honest claims

- "No data leaks" cannot be guaranteed. Risk can be reduced with minimized permissions, local processing, encryption, isolation, patching, audits, testing, backup hygiene and explicit approval gates.
- Local inference protects prompts from being sent to a model provider, but the external service (for example Google) still handles its own account data. If an email body is retrieved, JARVIS necessarily sees that body in plaintext locally to analyze it.
- Local Ollama only stays local if it is configured and operated locally. If a cloud model/provider is selected later, data will cross the device boundary; that mode must be explicit and off by default.
- AES-GCM protects encrypted approval payloads, not Markdown notes or all device storage.
- The public code repository is not a place to store private data. A private account or repository alone is also not a substitute for local secret management.
- Android-to-desktop pairing, OS keychain support, OAuth tokens, connectors, rate limiting, notification delivery, and a formal threat review are not fully implemented yet.

## Incident response

If a token is exposed: stop the local service, rotate the API token, rotate the data key only with a deliberate data migration/backup plan, remove the leaked value from the secret store, inspect Git history and access logs, and invalidate affected OAuth grants from the provider's account security page. Rewriting Git history does not undo a secret already copied by others.

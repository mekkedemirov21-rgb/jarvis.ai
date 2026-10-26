# JARVIS security and privacy policy (development baseline)

Last updated: 26 October 2026. This is a practical development policy, not a security certification or guarantee that no vulnerabilities exist.

## Data inventory

| Data | Where it goes | Current protection |
|---|---|---|
| Chat prompts and replies | Android dashboard → local FastAPI → loopback Ollama/LM Studio | Not intentionally logged or stored by the API; local model necessarily sees plaintext for inference |
| API token on Android | App-private preferences | AES-GCM ciphertext; encryption key stored in Android Keystore |
| Companion API token on PC | User's local `.env` | Plaintext file; must stay outside Git and under OS account/full-disk protection |
| Google OAuth credentials | Local `backend/data/google-token.enc` | AES-256-GCM ciphertext using local `JARVIS_DATA_KEY` |
| Approval payloads | Local SQLite DB | AES-256-GCM ciphertext |
| Job watch/profile records | Local SQLite DB | AES-256-GCM ciphertext |
| Relationship reminders | Local SQLite DB | AES-256-GCM ciphertext |
| Email and job-monitor alerts | Local SQLite DB | AES-256-GCM ciphertext; IDs used for deduplication are hashed |
| Obsidian-compatible notes | Local `backend/data/vault/*.md` | Plaintext Markdown, so use full-disk encryption and do not save high-risk secrets here |
| Gmail/calendar data | Google API after explicit OAuth | Current scopes are read-only; selected metadata/snippets are processed locally |
| WhatsApp personal history | Not collected | No general official consumer integration; do not scrape local sessions/cookies |
| GitHub | Source/config/documentation | Public repository—never store private data or live credentials |

## Current controls

- Backend requires a random API token of at least 32 characters before startup.
- Protected endpoints use Bearer-token auth and constant-time token comparison.
- Both configured local inference endpoints (Ollama and LM Studio) must resolve to loopback; the adapter rejects non-loopback endpoints.
- Android accepts HTTPS companion URLs only; cleartext HTTP and mixed content are disabled.
- Android encrypts the saved API token with AES-GCM using an Android Keystore key and does not store it in page localStorage.
- Android WebView disables file/content access and blocks third-party frames/navigation; external HTTPS links open outside the WebView.
- Approval payloads, relationship reminders, Google OAuth tokens and saved job watch data are encrypted with AES-256-GCM.
- Mail/job monitor alert payloads are encrypted; monitor notifications contain generic wording rather than mail subjects/snippets.
- Model prompts have no tools/action capabilities. Emails/job listings are untrusted input; prompt-injection content is not a source of permissions.
- External actions are not implemented. An approval record currently records a decision but does not send email, edit calendar, delete messages or submit applications.
- Background mail/job monitoring is disabled by default.
- Android background notification polling is periodic and best-effort; Android may delay it.

## Required deployment precautions

1. **Do not expose the API or model server publicly.** Bind FastAPI and Ollama/LM Studio to loopback. For phone access use a private VPN plus authenticated HTTPS (for example Tailscale Serve within the tailnet). Do not use a public tunnel.
2. **Use OS disk encryption.** The Markdown vault, local `.env`, some metadata and SQLite database must be protected by the host OS. Store backups of `JARVIS_DATA_KEY` separately and securely. Loss of the key can make encrypted records unrecoverable.
3. **Keep secrets out of Git.** Never paste API tokens, Google OAuth client secrets, refresh tokens, cookies, exported mail, CVs, WhatsApp chats or the local database into issues, commits, screenshots or chat prompts.
4. **Use minimum OAuth scopes.** Current Gmail and Calendar access is read-only. Google can require verification or additional review for public apps using restricted scopes. Review scopes before adding a future write capability.
5. **Keep human confirmation.** Any later action that sends an email, changes a calendar, submits an application or sends a message must present the exact recipient/target and final payload and require fresh confirmation immediately before execution. Prefer reversible actions and an audit record. Never automatically delete an acceptance/rejection email or accept/reject an offer.
6. **Treat third-party content as untrusted.** Emails, invites, job descriptions, documents and search results can contain prompt injection. Never let content itself grant tool permissions.
7. **Minimize memory.** Do not automatically store every conversation or private email. Memory should be editable, exportable, deletable and have retention limits before automatic capture is added.
8. **Protect personal notifications.** Android notifications intentionally do not include sender, subject or private text. Use lock-screen privacy settings too.
9. **Update and test.** CI tests help catch regressions but are not a security audit. Patch operating system and dependencies. Review dependency changes and perform independent threat modeling and penetration testing before distributing widely.

## Encryption: what is and is not true

- HTTPS encrypts data **in transit** between Android and the companion.
- Android Keystore protects the key that encrypts the saved Android API token.
- AES-256-GCM protects specific records stored by the companion, not all data on the device.
- The local inference server and companion must see the input in plaintext to process it. Therefore the current app does **not** provide application-layer end-to-end encryption where the server cannot read the prompt.
- Google processes email/calendar data through its service because JARVIS calls Google's API. Local inference avoids forwarding prompts to a model API, but does not prevent Google from handling the account data it already stores.
- Messages sent to third-party AI agents through WhatsApp's supported agent experience are not considered E2E-encrypted personal chats by WhatsApp; private personal chat history is not imported by JARVIS.
- No one can guarantee “zero data leaks” or “no security holes.” Only reduce and test risk.

## Incident response

If a credential is exposed: stop the service, rotate the API token, revoke the affected OAuth grant with Google, inspect repository/history and access logs, and review affected data. Rotate `JARVIS_DATA_KEY` only with a deliberate migration/backup plan; simply changing it will make the old encrypted records unreadable. Rewriting Git history cannot undo a secret already copied by someone else.

## Primary references

- Google Gmail API scopes: https://developers.google.com/workspace/gmail/api/auth/scopes
- Adzuna terms of service: https://developer.adzuna.com/docs/terms_of_service
- Indeed terms: https://de.indeed.com/legal
- WhatsApp third-party agents: https://faq.whatsapp.com/1050934623978152
- Tailscale Serve: https://tailscale.com/docs/features/tailscale-serve

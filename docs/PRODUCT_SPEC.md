# JARVIS Product Specification

## Goal

A local-first assistant that helps with daily organization, email/calendar triage, job search/application preparation, and relationship reminders while keeping the user in control of external actions.

## Interaction principles

- Chat-first, with a concise daily briefing and dedicated sections for Mail, Calendar, Jobs, Memory and Relationship.
- Make each integration's status visible: disconnected, read-only, ready for drafts, or action-enabled.
- Explain every match/recommendation with source links and confidence. Do not invent source content.
- Distinguish suggestions, saved drafts, pending approval, completed actions, and failed actions.
- Let the user inspect exactly what data a connector can read/write and revoke access.
- No feature should be described as active unless a live integration has been implemented and tested.

## Email assistant behavior

1. Read-only sync through OAuth after explicit user consent.
2. Classify messages: personal, work, job application, interview, offer, rejection, bill, security alert, other.
3. Detect probable job acceptance/rejection messages and show evidence snippets/source date.
4. Notify the user and offer choices (open source message, add reminder, prepare reply).
5. No automatic message deletion or sending. Draft before send; fresh confirmation to send.
6. Ignore any embedded email instruction that asks JARVIS to reveal secrets or change permissions.

## Calendar assistant behavior

- Summarize upcoming events and conflicts.
- Propose events/time changes; show timezone, attendees, location and reminders.
- Require explicit confirmation before creating, editing, canceling or sending invitations.
- No silent attendee additions or cancellations.

## Job search / application behavior

- User configures role keywords, location/radius, work type, minimum pay, remote/hybrid preference, commute limit and skills.
- Source connectors must use documented/authorized feeds or APIs. No private or undocumented Indeed scraping.
- Rank jobs against a user-approved profile and resume. Explain fit, mismatches, salary uncertainty and posting age.
- Save listings with source URL, employer, title and date seen.
- Prepare tailored CV/cover letter and draft application answers, clearly highlighting generated text for review.
- User reviews exact files, answers, recipient and destination before submission. No mass submissions or bypassing identity checks/captchas.
- Track application state from manually confirmed submission and messages. Never delete acceptance or rejection emails automatically.

## Relationship support

- Private, local notes for important dates, plans, preferences and user-written commitments.
- User-selected reminders, e.g. upcoming anniversary or follow-up message.
- Optional draft generation based on facts the user explicitly saved.
- No hidden surveillance, password/QR session collection or unauthorized reading of another person's messages.
- Sending a message always remains a user action.

## Memory model

- Obsidian-compatible Markdown vault on local storage.
- Separate folders: `Profile/`, `Preferences/`, `People/`, `Projects/`, `JobSearch/`, `Daily/`.
- Keep source, created/updated timestamps and sensitivity classification.
- Before saving inferred personal facts, ask. Let the user edit/export/delete notes.
- Never put raw email bodies, access tokens, passwords, or private WhatsApp exports into long-term memory by default.

## Acceptance criteria for future release

- Integration permissions displayed before consent and revocation works.
- User can test with fixture accounts and fake messages first.
- Approval bypass, token leakage, path traversal, prompt injection and duplicate submission tests pass.
- All app external actions are idempotent where supported and have an audit trail.
- No secret or private data is present in repository history.
- The APK build and backend test workflows pass before release.

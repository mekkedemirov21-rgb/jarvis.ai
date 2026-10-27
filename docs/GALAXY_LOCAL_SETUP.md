# JARVIS directly on a Galaxy phone (Groq API)

The `groq-direct-apk` build works without Termux, a companion server, laptop, or terminal. The Android app asks for a Groq API key, stores it encrypted using Android Keystore, and calls Groq directly over HTTPS.

## Setup

1. Open the [Groq Console API Keys page](https://console.groq.com/keys) and create a key.
2. Download the latest successful `JARVIS-debug-APK` artifact from the [Groq-direct APK workflow](https://github.com/mekkedemirov21-rgb/jarvis.ai/actions/workflows/build-apk.yml?query=branch%3Agroq-direct-apk).
3. Install and open JARVIS on the Galaxy.
4. Paste the key and tap **Key speichern und JARVIS starten**. There is no server URL, Termux, or JARVIS companion token.
5. Send a simple greeting to test the chat.

## Privacy and cost

- Messages and chat context are sent directly to Groq for inference; this mode is not local/offline.
- The API key is encrypted at rest through Android Keystore. Do not paste it into chats, issues, screenshots, or public repositories.
- The default model is `openai/gpt-oss-20b`. Model availability, quotas, and prices are controlled by Groq and may change.
- Before using the app, verify the plan/billing settings of your Groq account. If billing is enabled, API calls may be chargeable; this APK does not enforce a spending cap or make payments.
- Groq usage limits can cause requests to fail with an error; the app does not purchase credits automatically.
- The app has no access to your email/calendar, does not send messages in other apps, and does not submit job applications. Those capabilities are not implemented by the direct Groq chat screen.
- Voice recognition depends on Android's available speech-recognition service. The foreground wake-word control is not guaranteed to work while JARVIS is closed or the phone is locked.

## If a chat request fails

- **401/403:** check that you pasted the correct Groq API key; you can reopen **API-Key** in JARVIS.
- **429:** the account's rate limit or quota was reached; check Groq Console and retry later.
- **402:** Groq indicates the request requires billing/credits; do not enable a paid plan unless you choose to.
- **No internet:** the direct cloud chat cannot generate a reply offline.

For a local model on a future Surface, use the main branch companion with Ollama or LM Studio instead.

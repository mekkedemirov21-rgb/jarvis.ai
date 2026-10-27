# JARVIS on a Galaxy phone with Groq API

This experimental setup keeps the JARVIS Android APK and companion API on the same Galaxy phone, but sends model prompts to the Groq cloud API. It avoids running a local model on the phone and avoids a PC. Groq's free usage is rate-limited and may change; check your Groq console before relying on it.

## Privacy and cost notice

- **This mode is not fully local.** User prompts and the relevant chat context are sent over HTTPS to Groq to generate answers. Do not submit highly sensitive content unless you accept Groq's data-processing terms.
- Create your own Groq API key. Never commit it to Git, paste it into this chat, or show it in screenshots.
- The API key remains in the local Termux `.env`; the APK only stores the separate local JARVIS companion token encrypted with Android Keystore.
- The JARVIS API binds to `127.0.0.1` on this phone. Do not bind it to `0.0.0.0`, expose port 8765, or forward ports.
- Free Groq API use is subject to model-specific usage limits and current plan terms; an HTTP 429 means a limit was reached.
- Gmail and Google Calendar are not configured by this guide. No email/calendar write is enabled by default.

## 1. Install Termux on the Galaxy

Install Termux from [F-Droid](https://f-droid.org/) or another official trusted Termux distribution. Do not mix Termux builds/plugins from different signing sources.

Open Termux on the **Galaxy S22 Ultra** (not the Nokia) and run:

```bash
pkg update
pkg install git
git clone -b jarvis-galaxy-localhost https://github.com/mekkedemirov21-rgb/jarvis.ai.git
cd jarvis.ai
bash scripts/setup-galaxy-termux.sh
```

This prepares the Python companion and local keys. It no longer installs llama.cpp or downloads a local model for this Groq setup.

If dependency installation fails, stop and share only the error text. Do not share any lines containing secret keys.

## 2. Create your Groq API key

1. Open [Groq Console API Keys](https://console.groq.com/keys) in your browser and sign in.
2. Create a key for this personal project and copy it once.
3. In Termux run:

```bash
cd ~/jarvis.ai
nano .env
```

Find the line `GROQ_API_KEY=` and paste your key after the equals sign, with no quotes or spaces. Do not replace `JARVIS_API_TOKEN` or `JARVIS_DATA_KEY`.

Save in nano using **Ctrl+O**, press **Enter**, then exit with **Ctrl+X**. The key must remain private. If you accidentally reveal it in a screenshot or public post, revoke it in Groq Console and create a new one.

Default model: `openai/gpt-oss-20b`. Groq model availability and quotas can change; the currently supported models are listed in [Groq's model documentation](https://console.groq.com/docs/models).

## 3. Start JARVIS

In Termux:

```bash
cd ~/jarvis.ai
termux-wake-lock
bash scripts/start-galaxy-backend.sh
```

Keep this Termux session running. In the phone browser, open:

```
http://127.0.0.1:8765/health
```

A healthy server returns JSON with `"status":"ok"`. If you see a missing Groq key error on the first chat, re-open `.env` and check that `GROQ_API_KEY` has been set privately.

## 4. Build/install the local-mode APK and connect

Use the successful `JARVIS-debug-APK` artifact from the [Galaxy branch's APK workflow](https://github.com/mekkedemirov21-rgb/jarvis.ai/actions/workflows/build-apk.yml?query=branch%3Ajarvis-galaxy-localhost). Install the APK on the same Galaxy and open it.

- Server URL: `http://127.0.0.1:8765`
- Companion token: get it in Termux with `grep '^JARVIS_API_TOKEN=' .env`; copy the value directly into the APK. **Never send this token in chat or a screenshot.**
- Tap **Sicher verbinden**.

The dashboard should load from the companion. Try a basic chat prompt. If the API returns a Groq authentication or rate-limit error, check your key and Groq Console usage.

## 5. Keep it running

- Keep the phone powered while using JARVIS; use `termux-wake-lock` in the backend session.
- In Android settings, allow Termux unrestricted battery usage/background activity. Menu names vary with One UI.
- Android can still terminate background processes; this is not guaranteed 24/7 availability.
- Groq inference requires an internet connection. The JARVIS dashboard/API is local, but model answers will not work offline.
- When you later move to a Surface or other computer, change the provider back to Ollama/LM Studio for fully local inference, or keep Groq if you prefer cloud inference.

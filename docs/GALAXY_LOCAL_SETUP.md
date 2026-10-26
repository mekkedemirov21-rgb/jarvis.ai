# JARVIS on a Galaxy phone with Termux

This is an experimental, local-only route for a capable Android phone. The Android APK stays the JARVIS interface; Termux runs the companion API and a small local model on the same phone. This avoids a PC, a public server, a paid API and a network-exposed port. It is not the recommended production deployment, and Android may stop background apps unless battery settings are adjusted.

## What the 1.5 local build changes

- HTTPS remains required for every non-loopback server.
- Plain HTTP is accepted only for `http://127.0.0.1` on this same phone.
- The Android network security config keeps cleartext disabled by default and opens the loopback exception only.
- The API and model bind to `127.0.0.1`, not the Wi-Fi address.

## 1. Install Termux on the Galaxy

Install Termux from F-Droid (or another trusted official Termux distribution). Install the Termux app and any companion plugins from the same source; do not mix signatures from F-Droid, GitHub or Play Store builds.

In Termux, run:

```bash
pkg update
pkg install git
git clone -b jarvis-galaxy-localhost https://github.com/mekkedemirov21-rgb/jarvis.ai.git
cd jarvis.ai
bash scripts/setup-galaxy-termux.sh
```

The setup installs Python dependencies and llama.cpp, prepares a private local `.env`, and generates random API/encryption keys on the phone. Dependency compilation on Android can take a while. If pip fails while compiling `pydantic-core`, stop and share the final error rather than running random internet scripts.

## 2. Start the local model

Open a second Termux session and run:

```bash
cd ~/jarvis.ai
termux-wake-lock
bash scripts/start-galaxy-model.sh
```

The first run downloads the Qwen3.5 0.8B Q4_0 GGUF model (about 563 MB). Its quality is limited compared with large models, but it is a sensible first test for a phone. Keep the session open.

## 3. Start JARVIS

Open a third Termux session and run:

```bash
cd ~/jarvis.ai
termux-wake-lock
bash scripts/start-galaxy-backend.sh
```

The API should stay on `http://127.0.0.1:8765`. Test it in the phone browser by opening `http://127.0.0.1:8765/health`; the response should say `"status":"ok"`.

If the backend does not start, copy the error text. Do not expose port 8765 or 8080 to Wi-Fi/the Internet.

## 4. Connect the updated JARVIS APK

Open the JARVIS 1.5 APK built from this branch. Set the server URL to:

```
http://127.0.0.1:8765
```

For the API token, display it inside Termux and copy it directly into the APK:

```bash
cd ~/jarvis.ai
grep '^JARVIS_API_TOKEN=' .env
```

Do not send this token in chat, screenshots or issue reports. The APK encrypts it using Android Keystore. Tap **Sicher verbinden**. The JARVIS dashboard should load from the local companion.

## 5. Keep it running

- Keep the phone powered and Termux sessions alive; use `termux-wake-lock` while the services should stay active.
- In Android app settings, set Termux battery usage to unrestricted or allow background activity. Exact labels vary by Android/One UI version.
- Heat and battery wear are possible during sustained inference. Do not cover the phone or leave it in direct sun while the model runs.
- The phone can still kill background processes; this is not a guarantee of 24/7 server uptime.
- Local HTTP is safe here only because the app accepts it for loopback on the same device. Never change the API bind host to `0.0.0.0` and never forward these ports.
- Google/OAuth features are not configured by this setup. Mail and calendar stay disconnected until separately set up and consented to.

When you later move to a Surface or another computer, return the APK to a private HTTPS URL through your own VPN and use a larger local model if hardware allows.

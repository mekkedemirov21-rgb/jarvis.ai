# JARVIS — Personal AI UI

A responsive, static JARVIS interface inspired by the supplied reference image, packaged as a minimal Android WebView app.

## Android APK

A GitHub Actions workflow builds a debug APK on every push to `main` and when manually started.

1. Open the repository's **Actions** tab.
2. Select **Build JARVIS APK** and open the latest run.
3. When the run succeeds, download the **JARVIS-debug-APK** artifact.
4. Extract the ZIP and install `app-debug.apk` on an Android device. You may need to allow installs from your browser or file manager.

Workflow file: `.github/workflows/build-apk.yml`.

## Run the web UI locally

Open `index.html` in a modern browser. No web build step is required.

## Scope

- Static visual UI only: dashboard, decorative voice orb, schedule, knowledge categories, project progress, and settings.
- No backend, AI integration, authentication, persistence, or functional app behavior.
- Responsive desktop and mobile layout.
- Android app icon is a vector recreation inspired by the JARVIS icon in the supplied image.

## Android build

The Android wrapper uses Kotlin, Android WebView, Android Gradle Plugin 8.7.3, Gradle 8.9, and Java 17.

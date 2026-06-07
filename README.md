# AI Keyboard

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-26-blue)
![License](https://img.shields.io/badge/license-MIT-green)
![Status](https://img.shields.io/badge/status-alpha-orange)

**AI Keyboard** is an open-source Android keyboard focused on Brazilian Portuguese, privacy, and AI-assisted writing.

It combines fast local autocorrection, PT-BR–aware suggestions, and on-demand AI actions such as correction, rewriting, summarization, WhatsApp-style text, and translation. Unlike traditional AI writing apps that live inside a single app or a cloud platform, AI Keyboard works directly inside **any** Android text field through the system keyboard interface (`InputMethodService`), while keeping every network request under the user's explicit control.

> **Status: alpha.** Core keyboard behavior, local correction, AI actions, and provider integration are implemented and unit-tested, but the project still needs more device testing, accessibility review, UI refinement, and community feedback. See [ROADMAP.md](ROADMAP.md).

## Why this project matters

Most AI writing tools are locked inside specific apps, cloud platforms, or commercial keyboards. AI Keyboard explores a transparent, auditable, privacy-first alternative for Android users — especially Portuguese-speaking users who need corrections that preserve slang, abbreviations, informal writing, and real conversational tone.

The project is also a useful reference for developers who want to study Android IME behavior, on-device autocorrection, encrypted key storage, and AI-assisted text transformation inside system-level input fields.

## Key features

- **QWERTY IME** (Portuguese + English) declared as an `InputMethodService`, rendered on a `Canvas` for minimal latency.
- **Instant, multitouch typing** — the character is committed on `ACTION_DOWN` (touch), not on release, so two thumbs can type in parallel without dropped keys.
- **Gesture (swipe) typing** decoded against the local PT-BR lexicon; alternatives stay tappable in the suggestion bar. Only turns into a swipe at 3+ keys, so a tap with a slight slide is never mistaken for a gesture.
- **Local PT-BR autocorrection** on word boundary, with selectable strength (Off / Light / Medium / Strong / Maximum) and **auto-undo**: a backspace right after a correction reverts it and records the pair as rejected.
- **Slang-safe correction** — `vc`, `pra`, `pq`, `blz`, laughter (`kkk`, `rsrs`), emphatic stretches (`siiim`), CamelCase brands (`iPhone`, `WhatsApp`) and tokens with digits are never "corrected". This is what makes aggressive levels trustworthy.
- **Field-aware** — password, email, URL, and numeric fields automatically disable autocorrection, suggestions, and auto-capitalization.
- **On-demand AI actions** in a scrollable bar above the keyboard: Correct, WhatsApp, Rewrite, Professional, Formal, Friendly, Shorten, Expand, Summarize, Emojify, and EN/PT-BR translation.
- **Groq** as the primary AI provider (`llama-3.3-70b-versatile`; WhatsApp mode uses `llama-3.1-8b-instant`) with an optional **Gemini** (`gemini-2.0-flash`) fallback.
- **Voice dictation** (🎤) via the native `SpeechRecognizer` (live PT-BR).
- **Rich suggestions** — tappable local corrections, next-word prediction, a personal dictionary, local frequency-based learning, and contextual emoji — in a fixed-height bar so keys never jump.
- **Privacy by design** — no analytics, no telemetry, no ad SDKs. AI runs only when you tap an action; local typing never touches the network.

## How it works

```
┌─────────────────────────────────────────────────────────┐
│  User types or selects text in any app                   │
└────────────────────┬─────────────────────────────────────┘
                     ▼
┌─────────────────────────────────────────────────────────┐
│  AIKeyboardService (InputMethodService)                  │
│  ├─ Local autocorrect / suggestions (no network)         │
│  └─ On AI chip tap: read text via InputConnection,       │
│     apply system prompt, send to provider                │
└────────────────────┬─────────────────────────────────────┘
                     ▼
┌─────────────────────────────────────────────────────────┐
│  Groq API  →  (fallback) Gemini API   [HTTPS only]       │
└────────────────────┬─────────────────────────────────────┘
                     ▼
┌─────────────────────────────────────────────────────────┐
│  Text replaced in-place via                              │
│  InputConnection.beginBatchEdit + commitText             │
└─────────────────────────────────────────────────────────┘
```

## Tech stack

| Layer            | Technology                                                                 |
|------------------|----------------------------------------------------------------------------|
| Language         | Kotlin 2.0.21                                                               |
| Settings UI      | Jetpack Compose + Material 3                                                |
| Keyboard UI      | Custom `Canvas`/View rendering                                              |
| Build            | Gradle 9.0.0 + AGP 8.7.3, JDK 17                                            |
| HTTP             | OkHttp 4 + kotlinx.serialization                                            |
| Async            | Kotlin Coroutines                                                           |
| AI providers     | Groq (`llama-3.3-70b-versatile` / `llama-3.1-8b-instant`) + Gemini fallback |
| Secure storage   | AndroidX Security `EncryptedSharedPreferences` (user-entered keys)          |
| Min / Target SDK | 26 (Android 8.0) / 35 (Android 15), compileSdk 35                           |

## Project structure

```
.
├── app/
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/aikeyboard/app/
│       │   │   ├── KeyboardApp.kt          # Application + manual DI
│       │   │   ├── MainActivity.kt         # Compose setup screen
│       │   │   ├── ai/                      # AiClient, GroqClient, GeminiClient,
│       │   │   │                            # TextCorrector, AiOutputValidator, PromptPolicy
│       │   │   ├── data/                    # ApiKeyStore (encrypted), Settings/Personalization/
│       │   │   │                            # UserDictionary/ClipboardHistory stores
│       │   │   └── ime/                     # AIKeyboardService (IME), KeyboardView,
│       │   │                                # TypingEngine, LocalCorrections, PortugueseLexicon,
│       │   │                                # FieldPolicy, VoiceInputController…
│       │   └── res/                         # layouts, colors, strings, raw/pt_br_words.txt
│       └── test/java/com/aikeyboard/app/    # 88 JVM unit tests (8 files)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml               # version catalog
├── PERFORMANCE.md · SECURITY.md · ROADMAP.md · CONTRIBUTING.md
└── local.properties.example                # copy to local.properties, add your keys
```

## Getting started

### Prerequisites

- **Android Studio** (latest stable) with the Android SDK (compileSdk 35 / Build-Tools 35).
- **JDK 17** (the Gradle build targets JVM 17).

### 1. Clone and configure keys

```bash
git clone <your-fork-url> ai-keyboard
cd ai-keyboard
cp local.properties.example local.properties
```

Edit `local.properties` and set `sdk.dir` plus your own API keys. **`local.properties` is git-ignored and must never be committed.**

> **Note on keys & distribution.** For local development the build can read default keys from `local.properties` and expose them via `BuildConfig`. This is **for personal builds only** — any APK built this way embeds those keys in cleartext. Leave the key fields empty (or remove the `buildConfigField` injection in `app/build.gradle.kts`) before distributing a build. See [SECURITY.md](SECURITY.md).

### 2. Build

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (unsigned)
```

### 3. Enable the keyboard

1. Install and open the **AI Keyboard** app.
2. Tap **Open keyboard settings** and enable AI Keyboard.
3. Tap **Select keyboard** and choose AI Keyboard as the current input method.
4. Paste your Groq API key in-app and save it (stored with `EncryptedSharedPreferences`).
5. Open any app (WhatsApp, Notes, Chrome…), type, and tap an AI action.

## Running tests

```bash
./gradlew test
```

The suite has **88 JVM unit tests** across 8 files covering the typing/autocorrect engine, field policy, AI transformation styles, and output validation. Instrumented IME tests are not yet implemented (tracked in the roadmap).

## Privacy and security

AI Keyboard contains **no analytics, tracking, advertising SDKs, or telemetry**.

- Local autocorrection and suggestions run entirely on-device and never touch the network.
- AI requests are made **only** when you explicitly tap an AI action, sent over HTTPS directly to the configured provider.
- **User-provided API keys** are stored locally with `EncryptedSharedPreferences` (AES-256-GCM via AndroidX Security / Keystore).
- **Disclosure:** default/bundled keys injected at build time from `local.properties` are compiled into the APK via `BuildConfig` and are **not** encrypted at rest. This path exists only for personal builds and should be removed before public distribution.

Full policy and reporting instructions: [SECURITY.md](SECURITY.md).

## Distribution (signing)

```bash
# Generate a keystore once (store it safely, never commit it)
keytool -genkey -v -keystore release.keystore \
  -alias aikeyboard -keyalg RSA -keysize 2048 -validity 10000

# Add to ~/.gradle/gradle.properties:
#   RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD

./gradlew assembleRelease
```

## Roadmap

A short selection — see [ROADMAP.md](ROADMAP.md) for the full plan.

- [x] Local PT-BR autocorrection with slang-safe, field-aware behavior
- [x] On-demand AI actions (correct / rewrite / summarize / WhatsApp / translate)
- [x] Groq + Gemini provider chain with encrypted key storage for user keys
- [x] 88 JVM unit tests for core logic
- [ ] Remove default-key injection from public builds
- [ ] Instrumented IME tests
- [ ] CI on every PR
- [ ] Custom AI prompt actions
- [ ] Accessibility review

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). Good first areas: PT-BR lexicon coverage, device testing, accessibility, tests, and documentation. Please never commit secrets, API keys, build files, or local environment files.

## Open-source goals

AI Keyboard is open-source because keyboard software handles highly sensitive user input. The goal is a community-maintained Android keyboard that combines local-first typing assistance, privacy-first design, and optional AI actions fully controlled by the user — auditable and adaptable for Brazilian Portuguese.

## Screenshots

> Screenshots will be added under [`docs/screenshots/`](docs/screenshots/) (keyboard, settings, AI actions, correction, translation). Contributions of clean, secret-free screenshots are welcome.

## License

Released under the [MIT License](LICENSE).

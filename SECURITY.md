# Security Policy

AI Keyboard is an Android keyboard, so security and privacy are core priorities. A keyboard can observe everything a user types, which raises the bar for transparency.

## Supported versions

The project is in **alpha**. Security reports are welcome for the `main` branch.

## Reporting a vulnerability

If you find a security vulnerability, **please do not open a public issue first.** Report it privately to the maintainer (e.g. via a GitHub Security Advisory or direct contact) and allow time for a fix before any public disclosure.

Please report things such as:

- API key exposure
- Insecure storage of secrets or user data
- Unintended or background network requests
- Keyboard input leakage
- Privacy issues
- Dependency vulnerabilities
- Unsafe AI provider behavior

## Security principles

AI Keyboard aims to follow these principles:

- No analytics, telemetry, or advertising SDKs
- No hidden or automatic network requests — AI runs only on explicit user action
- No API keys committed to the repository
- User-controlled AI actions
- Local-first behavior whenever possible
- Transparent provider configuration

## How API keys are handled (be aware)

There are **two** key paths, and they have different security properties:

1. **User-entered keys (encrypted).** Keys a user types into the app are stored with
   `EncryptedSharedPreferences` (AES-256-GCM, AndroidX Security / Android Keystore) in
   `data/ApiKeyStore.kt`. This is encrypted at rest on the device.

2. **Build-time default keys (NOT encrypted).** `app/build.gradle.kts` can read
   `groq.api.key`, `gemini.api.key.primary`, and `gemini.api.key.secondary` from the
   git-ignored `local.properties` and bake them into `BuildConfig` fields. **Any APK built
   this way contains those keys in cleartext** and they are recoverable by decompiling the
   binary. This path exists only for convenience in personal/local builds.

**Recommendations:**

- Keep `local.properties` git-ignored (it already is). Never `git add -f` it.
- Leave the default-key fields empty — or remove the `buildConfigField` injection in
  `app/build.gradle.kts` — before building any APK you intend to share or release.
- If a key has ever been shared inside a built APK, **rotate it** in the provider console
  (Groq / Google AI Studio) and issue a new one.

## Git history

`local.properties` has never been tracked, and no real API key appears anywhere in the git
history. If a secret is ever committed by mistake, scrub it with `git filter-repo`
(`git filter-repo --invert-paths --path local.properties`) or BFG, force-push, and rotate
the exposed credential immediately.

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

## How API keys are handled

API keys are entered by each user **inside the app** and stored with
`EncryptedSharedPreferences` (AES-256-GCM, AndroidX Security / Android Keystore) in
`data/ApiKeyStore.kt`. They are encrypted at rest on the device.

**The build does not embed any API key.** Earlier versions could inject default keys from
`local.properties` into `BuildConfig`; that mechanism has been **removed**, so no APK ships
with a bundled key and there is nothing to extract by decompiling the binary. The app simply
requires the user to configure their own key before any AI action can run.

**Recommendations:**

- Keep `local.properties` git-ignored (it already is). Never `git add -f` it, and never put
  real secrets in `local.properties` or `local.properties.example`.
- If a key was ever shared inside a previously built APK, **rotate it** in the provider
  console (Groq / Google AI Studio) and issue a new one.

## Git history

`local.properties` has never been tracked, and no real API key appears anywhere in the git
history. If a secret is ever committed by mistake, scrub it with `git filter-repo`
(`git filter-repo --invert-paths --path local.properties`) or BFG, force-push, and rotate
the exposed credential immediately.

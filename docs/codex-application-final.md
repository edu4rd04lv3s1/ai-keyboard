# Codex for Open Source — final application (copy & paste)

> Ready-to-submit answers for the OpenAI Codex for Open Source form. Pick the fields the
> form asks for; the wording below is final. Everything stated here is true of the repo today.

---

**Project name**

AI Keyboard

**Repository URL**

https://github.com/Edward1611/ai-keyboard

**License**

MIT

**One-line description**

An open-source, privacy-first Android keyboard for Brazilian Portuguese with on-device autocorrection and on-demand, user-controlled AI writing actions.

---

**What is the project?**

AI Keyboard is an open-source Android keyboard (Kotlin, built on `InputMethodService`) focused on Brazilian Portuguese. It combines fast on-device autocorrection with on-demand AI writing actions — correct, rewrite, summarize, WhatsApp-style, and translate — that the user triggers explicitly. Because it is a real system keyboard, it works inside any Android text field rather than being locked to one app or a cloud product. AI providers are Groq (primary) and Gemini (fallback); each user supplies their own API key, which is stored encrypted on the device.

**Why does this project matter for open source?**

A keyboard observes everything a person types, which makes it the category of software that most needs to be open, auditable, and inspectable — closed keyboards demand enormous trust with no way to verify it. AI Keyboard also serves an underserved audience: informal Brazilian Portuguese, with the slang, abbreviations, laughter, and CamelCase brands that generic autocorrect breaks. And it is a practical reference for an area with little open material — Android IME internals, low-latency typing, on-device correction, encrypted key storage, and AI-assisted text transformation inside system input fields.

**Why is privacy critical here?**

The keyboard is the most sensitive input surface on a phone: passwords, messages, 2FA codes, and private conversations all pass through it. AI Keyboard makes its privacy stance verifiable in code: no analytics, no telemetry, and no advertising SDKs; local autocorrection and suggestions never touch the network; AI requests happen only on an explicit user tap, over HTTPS, to the configured provider; user API keys are stored with EncryptedSharedPreferences (AES-256-GCM); and no API keys are embedded in the build, so nothing can be extracted by decompiling the APK.

**How would Codex help?**

Codex would help maintain and grow the parts of the project that are hardest and highest-risk: adding instrumented tests for `InputMethodService` / `InputConnection` behavior that current JVM unit tests can't reach; continuously reviewing security- and privacy-sensitive code (key handling, network paths) to keep the "nothing leaves the device except explicit AI actions" guarantee true as the code grows; refactoring the AI provider layer into a clean, plugin-style architecture; improving Brazilian-Portuguese correction quality and lexicon coverage without breaking slang-safe behavior; strengthening AI output validation and cross-provider error handling; adding latency/performance benchmarks; and improving documentation, contributor onboarding, and automated pull-request review.

**Which ongoing tasks would Codex help maintain?**

Reviewing each pull request for security/privacy regressions before merge; keeping CI green and growing test coverage (unit and instrumented); performing refactors that keep the keyboard fast and the provider layer clean; and keeping documentation, the roadmap, and well-scoped issues up to date.

**Why does the project deserve support?**

It is small but real and disciplined: MIT-licensed, CI green on every push, a tagged v0.1.0-alpha release, 88 unit tests, a documented roadmap, issue and pull-request templates, and an explicit, code-backed privacy stance. The riskiest parts of a keyboard — IME behavior, latency, and handling sensitive input safely — are exactly where sustained AI-assisted review and testing add the most value, so support would translate directly into a safer, more capable, community-maintainable keyboard for an underserved language.

**Current status**

Alpha, with a working and tested foundation. Implemented today: an `InputMethodService` keyboard, on-device PT-BR autocorrection (slang- and field-aware), on-demand AI actions, a Groq + Gemini provider chain, encrypted user-key storage, voice dictation, 88 JVM unit tests, green GitHub Actions CI, and a v0.1.0-alpha release. Still to do (clearly tracked, not yet built): instrumented IME tests, device testing across manufacturers, accessibility review, UI polish, screenshots/demo, and community feedback.

---

_Generated from the repository state. Verify the links above still resolve before submitting._

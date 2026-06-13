# Codex for Open Source — application notes (working draft)

> Working notes and reasoning for applying to OpenAI's Codex for Open Source program.
> Not part of the app. The polished, copy-paste version lives in
> [codex-application-final.md](codex-application-final.md).

- **Project name:** AI Keyboard
- **Repository:** https://github.com/edu4rd04lv3s1/ai-keyboard
- **License:** MIT
- **Language / platform:** Kotlin, Android (`InputMethodService`)
- **Status:** alpha (working foundation: 88 unit tests, green CI, v1.1.1-alpha release)

## What AI Keyboard is

An open-source Android keyboard for **Brazilian Portuguese** that combines **on-device**
autocorrection with **on-demand**, user-triggered AI writing actions (correct, rewrite,
summarize, WhatsApp-style, translate). It is a real system keyboard built on
`InputMethodService`, so it works inside any Android text field — not a single app or a
cloud product. AI providers are Groq (primary) and Gemini (fallback); keys are supplied by
the user and stored encrypted on-device.

## Why it matters for open source

- A keyboard observes **everything** a person types. That is precisely the category of
  software that should be **open, auditable, and inspectable** — closed keyboards ask for
  enormous trust with no way to verify it.
- It serves an **underserved audience**: informal Brazilian Portuguese, with slang,
  abbreviations, laughter, and CamelCase brands that generic autocorrect mangles.
- It is a **didactic reference** for an area with little open material: Android IME
  internals, low-latency typing, on-device correction, encrypted key storage, and
  AI-assisted text transformation inside system input fields.

## Why privacy is critical in a keyboard

A keyboard is the most sensitive input surface on a phone — passwords, messages, 2FA codes,
private conversations all pass through it. AI Keyboard treats that seriously and makes it
**verifiable in code**:

- No analytics, no telemetry, no advertising SDKs (verifiable in the dependency list).
- Local autocorrection/suggestions never touch the network.
- AI requests happen **only** on an explicit user tap, over HTTPS, to the configured provider.
- API keys are entered by the user and stored with `EncryptedSharedPreferences`
  (AES-256-GCM). **No keys are embedded in the build** — the default-key injection that used
  to bake keys into the APK was removed, so nothing is extractable from the binary.

## How Codex would help

- **Test coverage** — add instrumented tests for `InputMethodService` / `InputConnection`
  behavior (commit, delete, selection, field policy) that the JVM unit tests can't cover.
- **Security & privacy review** — continuously audit key handling and network paths to keep
  the "nothing leaves the device except explicit AI actions" guarantee true as the code grows.
- **Architecture** — refactor the AI provider layer into a clean, plugin-style interface so
  new providers can be added safely.
- **PT-BR quality** — expand lexicon coverage and correction quality without breaking
  slang-safe behavior; add evaluation cases.
- **Robustness** — strengthen AI output validation and cross-provider error handling.
- **Performance** — add latency/throughput benchmarks for the typing path.
- **Maintainership** — improve docs, contributor onboarding, issue triage, and automate
  pull-request review.

## Tasks Codex would help maintain (ongoing)

- Reviewing every PR for security/privacy regressions before merge.
- Keeping tests green and growing coverage (unit + instrumented).
- Refactors that keep the keyboard fast and the provider layer clean.
- Documentation and roadmap upkeep; turning roadmap items into well-scoped issues.

## Why the project deserves support

It is small but **real and disciplined**: MIT-licensed, CI green on every push, a tagged
release, 88 unit tests, a documented roadmap, issue/PR templates, and an explicit,
code-backed privacy stance. The hardest, highest-risk parts of a keyboard — IME behavior,
latency, and handling sensitive input safely — are exactly where sustained AI-assisted
review and testing add the most value. Support would translate directly into a safer, more
capable, community-maintainable keyboard for an underserved language.

## Honest status

Alpha. The foundation works and is tested, but it still needs instrumented IME tests,
device testing across manufacturers, accessibility review, UI polish, a short demo, and
community feedback. None of the claims above describe unbuilt features — they describe what
exists today plus clearly-labeled roadmap work.

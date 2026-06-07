# Roadmap

AI Keyboard is currently in **alpha**. The goal is to become a reliable, privacy-first, open-source Android keyboard for Brazilian Portuguese with optional, user-controlled AI-assisted writing actions.

## Done

- [x] QWERTY IME (`InputMethodService`) with low-latency `Canvas` rendering
- [x] Instant multitouch typing and gesture (swipe) decoding
- [x] Local PT-BR autocorrection (Off / Light / Medium / Strong / Maximum) with auto-undo
- [x] Slang-safe, field-aware correction
- [x] On-demand AI actions (correct / rewrite / summarize / WhatsApp / translate / …)
- [x] Groq primary provider with Gemini fallback
- [x] `EncryptedSharedPreferences` storage for user-entered keys
- [x] Voice dictation via native `SpeechRecognizer`
- [x] 88 JVM unit tests for core logic

## Short term

- [ ] Remove default-key (`BuildConfig`) injection from public builds so keys are never baked into the APK
- [ ] Add GitHub Actions CI to run `./gradlew test` on every PR and push to `main`
- [ ] Add screenshots to the README
- [ ] Add GitHub issue and pull request templates
- [ ] Document `local.properties` key setup for contributors
- [ ] Add an accessibility checklist

## Medium term

- [ ] Add instrumented tests for `InputMethodService` behavior
- [ ] Improve PT-BR lexicon coverage
- [ ] Improve autocorrection quality and add an offline correction mode
- [ ] Custom prompt actions (user-defined AI prompts)
- [ ] Stronger AI output validation
- [ ] Better error handling and messaging for AI providers
- [ ] Benchmark tests for keyboard latency
- [ ] Keyboard UI and typing-experience refinement

## Long term

- [ ] Community-maintained PT-BR writing dataset
- [ ] Plugin-style provider architecture for more AI providers
- [ ] Public performance benchmarks
- [ ] Better support for slang, abbreviations, and informal Brazilian Portuguese
- [ ] Stable releases for public testing

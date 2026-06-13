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
- [x] No API keys embedded in public builds
- [x] GitHub Actions CI for `./gradlew test`
- [x] GitHub issue and pull request templates
- [x] Public contribution guide, security policy, release notes, and Codex/agent instructions

## Short term

- [ ] Add verified app screenshots to the README ([#1](https://github.com/edu4rd04lv3s1/ai-keyboard/issues/1))
- [ ] Record a short emulator/device demo ([#1](https://github.com/edu4rd04lv3s1/ai-keyboard/issues/1))
- [ ] Add an accessibility checklist ([#4](https://github.com/edu4rd04lv3s1/ai-keyboard/issues/4))
- [ ] Add instrumented tests for `InputMethodService` / `InputConnection` behavior ([#2](https://github.com/edu4rd04lv3s1/ai-keyboard/issues/2))
- [ ] Run a focused security review of InputConnection handling, network boundaries, dependency risk, and API-key storage ([#3](https://github.com/edu4rd04lv3s1/ai-keyboard/issues/3))
- [ ] Add latency/performance benchmarks for typing responsiveness ([#6](https://github.com/edu4rd04lv3s1/ai-keyboard/issues/6))

## Medium term

- [ ] Improve PT-BR lexicon coverage
- [ ] Improve autocorrection quality and add an offline correction mode
- [ ] Custom prompt actions (user-defined AI prompts)
- [ ] Stronger AI output validation
- [ ] Better error handling and messaging for AI providers
- [ ] Keyboard UI and typing-experience refinement
- [ ] Plugin-style provider architecture for more AI providers ([#5](https://github.com/edu4rd04lv3s1/ai-keyboard/issues/5))

## Long term

- [ ] Community-maintained PT-BR writing dataset
- [ ] Public performance benchmarks
- [ ] Better support for slang, abbreviations, and informal Brazilian Portuguese
- [ ] Stable releases for public testing

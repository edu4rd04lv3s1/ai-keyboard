# Codex for Open Source — application draft

> Internal notes for applying to OpenAI's Codex for Open Source program. Not part of the app.
> Replace https://github.com/Edward1611/ai-keyboard with the final public repository URL before submitting.

**Project name:** AI Keyboard

**Repository:** https://github.com/Edward1611/ai-keyboard

**Short description:**
AI Keyboard is an open-source Android keyboard focused on Brazilian Portuguese, privacy-first local autocorrection, and optional, user-controlled AI-assisted writing actions.

**Why this project matters:**
Most AI writing tools are locked inside specific apps or commercial keyboards. AI Keyboard provides a transparent, auditable alternative for Android users — especially Portuguese-speaking users who need better support for informal Brazilian Portuguese, slang, abbreviations, and natural conversational tone. It is also a useful reference for developers studying Android IME behavior, on-device correction, encrypted key storage, and AI-assisted text transformation inside system-level input fields.

**Current status:**
Alpha. Implemented and unit-tested: an Android `InputMethodService` keyboard (Kotlin 2.0.21, Jetpack Compose for settings), local PT-BR autocorrection with slang-safe and field-aware behavior, on-demand AI actions (correct / rewrite / summarize / WhatsApp / translate), Groq primary provider with Gemini fallback, `EncryptedSharedPreferences` storage for user-entered keys, voice dictation, and 88 JVM unit tests across 8 files. No instrumented IME tests yet.

**How Codex would help:**
- Improve `InputMethodService` test coverage and add instrumented tests
- Review security- and privacy-sensitive code (key handling, network paths)
- Refactor the AI provider layer into a cleaner, plugin-style architecture
- Improve documentation and contributor workflows
- Strengthen privacy guarantees (e.g. remove build-time default-key injection)
- Automate review of future pull requests
- Improve PT-BR correction quality and lexicon coverage
- Add performance/latency benchmarks
- Prepare stable public releases

Because keyboard software handles sensitive user input, it needs careful maintenance around performance, security, privacy, and Android compatibility — areas where AI-assisted review and testing add real value.

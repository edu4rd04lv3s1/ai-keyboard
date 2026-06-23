# AGENTS.md

Instructions for AI coding agents working on AI Keyboard.

AI Keyboard is an Android `InputMethodService`. Treat every change as security- and
privacy-sensitive because keyboards can observe arbitrary user text.

## Project priorities

- Preserve the privacy model: local typing, suggestions, and autocorrection must stay
  on-device and must not make network requests.
- AI requests are allowed only after an explicit user action on an AI action chip.
- Never embed API keys, tokens, credentials, analytics identifiers, personal data, or
  private messages in code, tests, docs, screenshots, releases, or build artifacts.
- Keep Brazilian Portuguese behavior first-class, including slang, abbreviations,
  laughter tokens, informal writing, and field-aware safety.
- Prefer small, reviewable changes with tests.

## Commands

Use these commands from the repository root:

```bash
./gradlew test
./gradlew assembleDebug
```

The CI workflow runs `./gradlew test --stacktrace` on every push and pull request.

## Files and architecture

- `app/src/main/java/com/aikeyboard/app/ime/` contains the keyboard service, rendering,
  typing engine, field policy, local corrections, and voice input.
- `app/src/main/java/com/aikeyboard/app/ai/` contains provider clients, prompts, output
  validation, and text transformation logic.
- `app/src/main/java/com/aikeyboard/app/data/ApiKeyStore.kt` handles user-entered API
  keys through encrypted local storage.
- `app/src/test/java/com/aikeyboard/app/` contains JVM tests for core logic.

## Safety checklist for code changes

Before proposing or merging a change:

- Run `./gradlew test`.
- Confirm `local.properties`, build outputs, APKs, `.env` files, and keys are not staged.
- Check that password, email, URL, and numeric fields still disable unsafe typing
  assistance where appropriate.
- Check that new AI behavior cannot run automatically in the background.
- Add or update tests for typing, field policy, prompt/output validation, provider
  behavior, or accessibility when relevant.
- Update `README.md`, `SECURITY.md`, `ROADMAP.md`, or `docs/accessibility-review.md`
  when the user-facing privacy, security, accessibility, or release story changes.

## Accessibility checklist for agents

- Preserve touch targets at 48 dp or larger where possible.
- Decorative icons should keep `contentDescription = null`; icon-only actions need a
  meaningful accessible label.
- For custom keyboard drawing, remember that Canvas-rendered keys are not automatically
  discoverable by screen readers. Any accessibility work must account for virtual nodes
  or another explicit accessibility strategy.
- Do not regress text scaling, contrast, focus order, TalkBack labels, or hardware
  keyboard navigation in the setup/settings flow.

## Screenshot and demo rules

Only commit screenshots that are captured on a real device or emulator and verified to
contain no personal data, no API keys, and no private messages. Mockups are allowed only
if they are clearly labeled as mockups and not presented as app screenshots.

## Release rules

Before creating a public release:

- Run the unit test suite.
- Confirm the release notes mention alpha status and known limitations.
- Do not attach unsigned or debug APKs unless the release clearly explains that they are
  for testing only.
- Verify the GitHub Actions workflow is green on `main`.

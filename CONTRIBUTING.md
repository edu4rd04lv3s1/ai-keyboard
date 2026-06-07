# Contributing to AI Keyboard

Thank you for considering contributing to AI Keyboard.

AI Keyboard is an open-source Android keyboard focused on Brazilian Portuguese, privacy, local correction, and AI-assisted writing.

## Ways to contribute

- Improving PT-BR autocorrection quality
- Expanding the local dictionary (`app/src/main/res/raw/pt_br_words.txt`)
- Testing keyboard behavior on different Android devices and versions
- Improving accessibility
- Reviewing security and privacy
- Adding tests (especially instrumented IME tests, which don't exist yet)
- Improving documentation
- Reporting bugs and suggesting new AI writing actions

## Development setup

1. Clone the repository (or your fork).
2. Open the project in **Android Studio** (latest stable, with Android SDK 35 / Build-Tools 35).
3. Copy `local.properties.example` to `local.properties` and set `sdk.dir` and your own API keys.
4. Let Gradle sync, then build with `./gradlew assembleDebug`.
5. Run the unit tests before submitting changes.

The build targets **JDK 17**, Kotlin **2.0.21**, AGP **8.7.3**, Gradle **9.0.0**.

## Running tests

```bash
./gradlew test
```

## Pull request guidelines

Before opening a pull request:

- Keep changes focused and scoped to one concern.
- Explain what problem the PR solves.
- Add or update tests when possible.
- **Do not include secrets, API keys, build outputs, or local environment files** (`local.properties`, keystores, `.apk`/`.aab`).
- Make sure `./gradlew test` passes and the project builds.

See [`.github/pull_request_template.md`](.github/pull_request_template.md) for the PR checklist.

## Code style

- Kotlin official style; keep new code consistent with the surrounding files.
- Prefer small, readable functions; match existing naming and comment density.

## Security

Never commit API keys, tokens, passwords, keystores, or private configuration files.

If you find a security issue, please follow the private reporting process in [SECURITY.md](SECURITY.md) before publishing any details.

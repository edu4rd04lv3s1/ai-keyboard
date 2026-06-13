# Maintenance plan

AI Keyboard is maintained as a privacy-first Android IME for Brazilian Portuguese. This
document explains how the project is organized for public collaboration and AI-assisted
maintenance.

## Maintenance goals

- Keep typing assistance local by default.
- Keep AI actions explicit, user-controlled, and auditable.
- Expand PT-BR correction quality without breaking slang-safe behavior.
- Increase Android device coverage through tests, issues, and public reports.
- Review security-sensitive changes carefully because the app is a keyboard.

## Current maintenance signals

- Public MIT-licensed repository.
- CI runs JVM tests on pushes and pull requests.
- 88 unit tests cover the typing engine, field policy, AI transformation styles, and
  output validation.
- Security policy documents key handling, network expectations, and vulnerability
  reporting.
- GitHub issue and pull request templates ask contributors not to include secrets.
- `AGENTS.md` gives Codex and other coding agents project-specific safety instructions.

## Near-term work

The next public milestones are:

- Record a short demo captured from an emulator or device.
- Add instrumented tests for `InputMethodService` and `InputConnection` behavior.
- Run a focused security review of network boundaries, encrypted key storage, and AI
  output validation.
- Add an accessibility checklist for keyboard layout, touch targets, labels, and setup
  flow.
- Add latency benchmarks so typing responsiveness does not regress.

## How Codex helps

Codex is useful in this project when it is constrained by tests and explicit safety
rules. Good Codex tasks include:

- drafting failing tests for IME edge cases;
- reviewing PRs for privacy regressions;
- expanding PT-BR test cases and lexicon coverage;
- generating release checklists and documentation updates;
- checking that code changes do not introduce automatic network requests;
- triaging issues into reproducible Android, IME, provider, or documentation work.

Human review remains required for releases, security-sensitive changes, prompt changes,
and any behavior that reads or sends user text.

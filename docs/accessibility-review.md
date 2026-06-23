# Accessibility review

Date: 2026-06-23

AI Keyboard is an Android IME, so accessibility work covers two surfaces:

- the setup/settings app built with Jetpack Compose and Material 3;
- the runtime keyboard UI, which is custom-rendered with `Canvas` inside
  `KeyboardView`.

This is the first documented review pass. It is intentionally conservative: it records
what is already covered, what still needs implementation, and which issues should be
tracked before the project can call accessibility support mature.

## Checklist

| Area | Status | Notes |
|------|--------|-------|
| Touch target size | Partial | Keyboard rows use 52 dp row height at medium scale, and settings use Material controls. Small keyboard scale may reduce effective touch area and needs device review. |
| Setup/settings labels | Partial | Most buttons and text fields use visible Material text, but some icon-only actions and API-key fields need explicit accessible labels. |
| TalkBack support for settings | Partial | Compose and Material provide baseline semantics. Manual TalkBack traversal still needs to be recorded on a device. |
| TalkBack support for keyboard keys | Not implemented | `FastKeysView` draws keys on Canvas. Individual keys are not exposed as virtual accessibility nodes yet. |
| Suggestions and AI action chips | Partial | Chips and suggestion slots are real `TextView`s and focusable, but busy/error state announcements need review. |
| Contrast and themes | Partial | Light/dark palettes exist. A contrast audit should verify text, chip, and key states against WCAG contrast targets. |
| Text scaling | Partial | Settings screen should scale with system font size. Canvas-rendered keyboard labels use fixed dp-derived text sizes and need large-font review. |
| Voice input affordance | Partial | Voice input has visible state, progress, and error messages. The microphone/stop key needs an accessible state label. |
| Error announcements | Partial | Errors currently use Toasts. Toasts are not a complete accessibility announcement strategy for all screen reader users. |
| Hardware keyboard / switch access | Not reviewed | Setup/settings may inherit basic focus behavior. Custom keyboard interactions need separate review. |

## Findings

### 1. Canvas keyboard keys are not individually accessible yet

`KeyboardView.FastKeysView` custom-draws letter, symbol, emoji, and special keys. This is
good for latency, but Android accessibility services cannot automatically discover
individual Canvas-drawn keys.

Recommended follow-up:

- implement virtual accessibility nodes for keys, likely with `ExploreByTouchHelper` or
  a custom `AccessibilityNodeProvider`;
- expose labels for letters, space, backspace, enter, shift, voice, emoji/symbol mode,
  and switch-keyboard actions;
- update labels when state changes, for example shift/caps and microphone/listening.

### 2. Icon-only controls in setup/settings need explicit accessible names

Most setup controls have visible text, but some icon-only actions are ambiguous to
screen readers. The personal dictionary add button displays only an add icon with a null
description, so it should expose a label such as "Adicionar palavra".

Recommended follow-up:

- add explicit labels/content descriptions for icon-only controls;
- ensure decorative icons remain hidden with `contentDescription = null`;
- add strings for accessibility labels instead of hard-coding text.

### 3. API-key fields should expose stronger labels and privacy hints

The API-key editors use placeholders (`gsk_...`, `AIza...`) and surrounding explanatory
text, but the `OutlinedTextField` itself does not expose a formal label. TalkBack users
should hear which key field is focused and that the field is visually masked.

Recommended follow-up:

- add labels to Groq and Gemini key fields;
- add supporting text or semantics that explains keys are stored encrypted on device;
- verify that the saved-key preview does not read a sensitive value beyond the intended
  redacted preview.

### 4. Toast-only feedback should become explicit announcements where needed

Voice input errors, AI provider errors, and "no text" states currently use Toasts. Toasts
are useful visually, but they are not enough as the only accessibility feedback channel.

Recommended follow-up:

- call accessibility announcements for high-priority keyboard state changes;
- announce busy/completed/failure states for AI actions;
- announce voice listening start/stop and permission/network errors.

### 5. Contrast and scaling still need manual device review

The project includes light/dark keyboard themes and adjustable keyboard sizes, but no
documented contrast or large-font pass exists yet.

Recommended follow-up:

- test setup/settings with default, large, and maximum system font sizes;
- test keyboard small/medium/large/extra-large sizes on a real device;
- verify contrast for normal keys, pressed keys, disabled buttons, highlighted
  suggestions, WhatsApp chip, primary AI chips, and secondary chips.

## Manual review plan

Use a clean Android emulator or physical device:

1. Enable TalkBack.
2. Open the AI Keyboard setup app.
3. Traverse every setup, API-key, keyboard setting, dictionary, and test-field control.
4. Enable AI Keyboard and switch to it in a text field.
5. Traverse the keyboard with TalkBack and document what is reachable.
6. Test voice input state changes and AI action busy/error states.
7. Repeat with large font size and high-contrast display settings if available.

## Follow-up issues

- Expose Canvas-rendered keyboard keys to TalkBack through virtual accessibility nodes.
- Add explicit labels for icon-only setup/settings actions and API-key fields.
- Add accessibility announcements for keyboard busy/error/listening states.

This review closes the initial checklist/documentation gap, but it does not claim that
AI Keyboard is fully accessible yet. The keyboard is still alpha and needs implementation
work before accessibility can be considered complete.

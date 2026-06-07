# Social preview & public presentation

Reference for presenting the repository publicly (GitHub social preview, About field,
short pitch) and for adding real screenshots later. Nothing here invents features —
it describes the project as it exists today.

## Repository social preview

The cover image is already in the repo at [`docs/assets/repository-cover.png`](assets/repository-cover.png)
(1280×640, the size GitHub recommends).

- **Title:** AI Keyboard
- **Subtitle:** Privacy-first Android AI keyboard for Brazilian Portuguese.

## Short GitHub description (About field)

> Open-source Android AI keyboard for Brazilian Portuguese with local autocorrection, privacy-first design, and on-demand AI writing actions.

Suggested **topics** (already applied to the repo): `android`, `kotlin`, `keyboard`, `ime`,
`ai-keyboard`, `portuguese`, `pt-br`, `groq`, `gemini`, `open-source`, `privacy`,
`android-keyboard`, `artificial-intelligence`.

## Short project pitch (for sharing)

> AI Keyboard is an open-source Android keyboard focused on Brazilian Portuguese, privacy,
> local autocorrection, and optional AI-assisted writing actions. It is designed to be
> transparent, auditable, and useful for developers studying Android IME behavior,
> privacy-first keyboard design, and AI-powered text transformation.

## Manual checklist — set the GitHub social preview

The social preview image can only be uploaded through the GitHub web UI (not the API),
so this step is manual:

- [ ] Open the repository on GitHub: https://github.com/Edward1611/ai-keyboard
- [ ] Go to **Settings**.
- [ ] Scroll to **Social preview**.
- [ ] Upload **`docs/assets/repository-cover.png`**.
- [ ] Save changes.
- [ ] (Optional) Confirm the **About** sidebar shows the short description and topics above.

## Screenshots — what to add later

Add **real** screenshots (captured on a device/emulator) under `docs/screenshots/`:

- [ ] `docs/screenshots/keyboard-main.png` — keyboard open with the suggestion bar
- [ ] `docs/screenshots/ai-actions.png` — the on-demand AI action bar
- [ ] `docs/screenshots/settings.png` — settings / setup screen
- [ ] `docs/screenshots/privacy-settings.png` — privacy-related settings

### Rules for screenshots

- Use **real screenshots only**. Do not use mockups unless clearly labeled as mockups.
- Make sure each image contains **no personal data and no API keys** before committing.
- After adding the real screenshots:
  1. Update the **Screenshots** section in [`README.md`](../README.md).
  2. Uncomment the image block there (it is ready to enable) and remove the placeholder note.

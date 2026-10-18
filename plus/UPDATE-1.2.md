# Sam App Locker 1.2

Version 1.2.0 (code 8), Android 15+. Install over 1.1 to retain your credential and selections. Release ID `app.sam.locker`; debug ID `app.sam.locker.debug`. See RELEASE-1.2-VERIFICATION.md for exact results and limitations; older evidence remains historical.

## Changes

- The selected credential input is directly available underneath the automatic biometric flow. There is no fingerprint-versus-PIN chooser screen.
- Authenticated settings can change the fallback to PIN (6–12 digits), pattern (4–9 unique dots with midpoint inclusion), or password (8–64 characters containing letters and numbers). PIN auto-submit remains optional and enabled by default. Drawn patterns submit on release; password submits with Continue or the keyboard action.
- All credential methods use salted Argon2id and Android Keystore encryption with persistent rate limiting. Existing PIN records remain readable. Changing credentials invalidates in-memory app sessions.
- Rounded credential cards and optional suggestions for installed messaging, gallery, notes, email and file apps. Suggestions require explicit selection; nothing is silently protected.
- Authentication hosts are invalidated when their challenge becomes stale. Completion callbacks survive replacement-shield removal. Focus can initiate the opaque-frame handoff when Android omits its enter-animation callback.

## Stability corrections

- A scan during authentication Activity startup could lose the obscured target window and cancel fingerprint. The revised handoff binds the verified launch before startActivity and continues to reject changed foreground/challenge state.
- Giving the always-visible PIN keypad window focus hid foreground evidence on Samsung and caused stuck overlays. PIN and pattern now remain touch-only and non-focusable; password requests keyboard focus.
- A cancelled or stale biometric host is finished after safe shield handoff, including when the replacement shield is removed first.
- Backgrounding a credential change immediately hides the enrollment form until stored state is rechecked; a completed change revokes app sessions even if the settings Activity is already in the background.

The remaining items in NEXT-UPDATE.md stay open until individually verified. No zero-frame guarantee, universal external-uninstall interception, multiwindow certification, or Android 15/16 physical-device result is claimed. Password IME app-switching and broader pattern accessibility coverage remain separate test work.

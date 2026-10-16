# Sam App Locker 1.1.0-rc2 verification

Version code 4; release `app.sam.locker`; debug `app.sam.locker.debug`. In-place update signed with the existing release key; existing PIN and selections remain private and retained.

## Changes

- First opaque native frame before queued root inspection; animation-free shield window; main-thread, attachment-validated frame callbacks.
- Automatic fingerprint starts only after the protected application window is confirmed, after any pending unprotected transition has completed its bounded frame rechecks. Recheck before the authentication Activity takes ownership. A fresh auth-only task prevents an old finishing host from consuming a new challenge.
- Complete, matching package snapshots can reconcile a changed event window ID. After an actual protected application window has been observed, a later complete single-package unprotected snapshot can also resolve a missed exit event when our auth Activity is inactive. Empty, unknown, mixed or system-transient snapshots cannot authorize that fallback. An initially stale public snapshot does not cancel a new protected entry.
- At most three frame-driven event/snapshot rechecks, without idle polling. Scan on authentication-host stop before deciding whether to restore the overlay.
- Touch-blocking opaque entry overlay leaves keyboard focus available for reliable application transition reporting. Once the PIN keypad appears, it takes exclusive keyboard focus. The PIN is entered on the app's own keypad. External keyboards, DeX and external displays are not certified.

## Investigation and rejected variants

Initial rapid-switch probes reproduced stuck covers or a late biometric Activity over the public fixture. Failed attempts included zero-based exits 3, 1, 1, 6, 2, 4, 7, 3 and 23 across diagnostic variants. Switching the test to explicit foreground launches did not alone resolve the failure. Refreshing the auth task, lifecycle-stop recovery and window-snapshot reconciliation alone were insufficient. Diagnostic focus capture confirmed a late BiometricActivity in some failures; a later focus-isolated attempt confirmed the public fixture was focused underneath a stuck cover.

A non-keyboard-focused entry shield combined with verified-window automatic prompting passed 100 rapid switches in 132.705 seconds. Restoring keyboard focus throughout initial entry reproduced a stuck cover at exit 3. The final change therefore requests keyboard focus when PIN becomes visible rather than throughout initial biometric entry. That intermediate passing run is not acceptance evidence for the final APK. Raw failed test logs are preserved separately, and no failed attempt is counted as a pass.

## Final-build evidence

Tested on 2026-10-16. The installed APK SHA-256 matches the published signed release artifact:
`53769d5ac2ecd3ded7e13c5e43ba9efbbeab102458ac333ac36d9ef486ddead5`.

| Check | Final result |
| --- | --- |
| Local debug/release builds | Passed; release signed with existing key and non-debuggable |
| JVM regression tests | 33 passed, zero failed or skipped |
| Debug/release lint | Zero errors/fatal findings; 22 warnings per variant remain |
| APK/native alignment | 16 KB ZIP/native alignment checks passed; physical phone uses 4 KB pages |
| Protected entry, biometric cancellation and public exit | 100/100 passed in 343.457 s; 100 prompts, no duplicate prompts |
| Rapid protected-to-public switches | 100/100 passed in 134.005 s; no stuck locker asserted at public exit |
| Unlocked public/Home cycles | 100/100 passed in 150.992 s, including 10 keyboard opens; no shield or biometric events in the captured locker log |
| Recorded synthetic launches | 12/12 passed; 2,883 frames over 60.093 s analyzed |
| Owner authentication check | Owner confirmed fingerprint and automatic PIN both work on this final installed release |

Protected cycles alternate blue/red, with 34 MAIN, 33 VIEW and 33 SEND explicit foreground intents and 10 cold blue starts. These are intent-path probes, not a substitute for manual launcher, notification, Recents or share-sheet coverage. The test runner checks successful launches rather than assuming background activity starts succeed. Earlier harness runs that did not verify foreground launches failed and are excluded from acceptance counts.

Direct window-event receipt to opaque-frame commit: protected run n=83, median 24 ms, p95 36 ms, maximum 194 ms; rapid run n=98, median 23 ms, p95 31 ms, maximum 66 ms. The p95 below 100 ms target is met for these direct-event samples only. Scan-origin measurements omit earlier queue time and are reported separately in the JSON evidence. Cancellation-result to replacement shield commit: n=100, median 45 ms, p95 51 ms, maximum 57 ms. Successful authentication-to-removal latency was not instrumented with the owner's credentials.

Recording classification found initial synthetic app color before the secure blackout: median 37.322 ms, p95/maximum 177.656 ms across 12 identifiable launches. No target-color segment was detected between secure blackouts during handoffs. This is not zero-frame protection: secure windows are blacked out by screenrecord, which samples at variable intervals and cannot establish every physical display frame. Raw video is kept private; only content-free timings/classifications are included.

Additional rejected iterations included a late auth Activity with PIN-only focus handling, indefinite automatic-prompt deferral by an unrelated pending event, and unchecked background test launches. Final prompting requires the protected package in the current root snapshot, not merely historical confirmation, and waits only the bounded transition frame budget. Failed diagnostic runs remain in the evidence archive and are not counted as final passes.

GitHub CI is checked against the release commit before publication; its result and link appear in the release notes. See [NEXT-UPDATE.md](NEXT-UPDATE.md) for open work.

Phone: Samsung Galaxy A57 SM-A576B, Android 17/API 37, One UI 9/90000, 120 Hz, 1080x2340, patch 2026-09-05. USB connected; synthetic blue/red protected apps and public green app; immediate per-app relocking. Only the owner profile was used.

## Measurement limits

A public accessibility service cannot prevent frames displayed before Android reports the protected window. Frame-commit callbacks measure buffer submission, not physical display presentation. Screen recordings sample the compositor and do not prove every 120 Hz display frame. Zero-frame exposure and native/OEM enforcement are not promised. Notifications, widgets and other apps' Recents previews remain separate exposure surfaces. Force-stop, safe mode, service revocation and owner uninstall interrupt this ordinary app's protection. Multiwindow, foldables, DeX, external displays, long idle battery behavior and other Samsung models require separate verification.

## API references

- [Frame commit callbacks](https://developer.android.com/reference/android/view/ViewTreeObserver#registerFrameCommitCallback(java.lang.Runnable)): submission is not guaranteed display visibility.
- [Frame-driven scheduling](https://developer.android.com/reference/android/view/Choreographer#postFrameCallback(android.view.Choreographer.FrameCallback)).
- [CLEAR_TASK with NEW_TASK](https://developer.android.com/reference/android/content/Intent#FLAG_ACTIVITY_CLEAR_TASK): applied only to the app's separate authentication task.

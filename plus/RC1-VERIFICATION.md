# Candidate verification — Samsung A57

## Current candidate

Release `app.sam.locker`, debug `app.sam.locker.debug`, version 1.1.0-rc1 (3). Requested package change makes this a fresh installation. Signing uses the established release certificate. Source namespace remains `app.locker.plus`.

New changes: softer card styling and line navigation icons; auto-confirm PIN by default with authenticated manual-confirm setting; persisted PIN-length metadata learned at enrollment or successful verification; biometric overlay handoff waits for activity entry completion and a subsequent frame commit. No arbitrary handoff sleep.

The owner configured the new package and disabled the old service. After the final update, Samsung required an owner-controlled service reconnect. Installed APK SHA-256 matches the signed artifact: `64c21782d2445555a2acb3b7c57a0ecbd4215d84219bd58f2f94b195e694bb8a`.

Build: debug/release and isolated runner succeeded; 26 JVM tests passed; lint has 0 errors and 22 warnings per variant. Release is non-debuggable, signed with the established certificate, and passes 16 KB APK/native alignment checks.

New-package preliminary checks:
- First probe had no protected fixture selection; owner then added both fixtures.
- Next probe observed one stuck shield on a protected-to-public transition. Android permitted the fixture launch. The 3-cycle repeat passed. This intermittent failure remains documented; a repeat pass does not erase it.
- 12 recorded launch/cancel/public-exit cycles passed. In 2,916 decoded frames over 60.08 seconds, zero protected-color reappearances were detected between secure blackouts, compared with four in the earlier recording.
- Initial identifiable fixture-color exposure before blackout: 12 samples, median 82.96 ms, p95/max 122.52 ms. This is NOT event-to-shield latency and is not a zero-exposure result. Variable-rate screenrecord blacks out FLAG_SECURE content and is not physical presentation measurement.
- New release: 100/100 protected launch/cancel/public-exit cycles passed in 343.277 seconds (34 launcher, 33 VIEW, 33 SEND; 50 blue/50 red). Exactly 100 automatic requests and 100 host handoffs; no duplicate prompt or stuck exit in this run.
- Direct event-to-frame-commit: n=91, median 32 ms, p95 50 ms, max 80 ms. Nine entries began from a scan instead; those do not have a direct event-start measurement. Scan-origin callbacks (including cancellation overlays): n=109, median 46 ms, p95 55 ms, max 106 ms. Do not combine these into a full event-latency distribution.
- Cancellation-to-first-replacement-frame-commit: n=100, median 56 ms, p95 64 ms, max 69 ms. Secure host remains until replacement submission.
- 100/100 unprotected-app cycles passed in 151.910 seconds, including 10 keyboard entries. Operational log contained zero shield draw/frame or biometric launch events.
- Auto-confirm PIN UI test passed in 18.756 seconds: management unlock without Continue; wrong PIN did not request uninstall; correct PIN requested exactly the selected synthetic package. ACTION_DELETE was intercepted and no package was removed.
- Manual-confirm mode plus 20 picker switches passed in 52.729 seconds. An initial test expected an obscured heading behind Samsung's modal biometric prompt; the runner was corrected to select its visible PIN action. This runner-only correction did not change the release APK.
- Four credential tests passed in 13.323 seconds: 6/8/12-digit metadata, authenticated migration, corrupted verifier rejection, persistent rate limiting, PIN change and 100 recreated-vault checks. Those 100 native Argon2id checks: median 51 ms, p95 54 ms, max 58 ms. Legacy PBKDF2 migration took 2,311 ms once; new enrollment does not use that legacy path.
- Dark-theme Locked/Unlocked screenshots were reviewed from the synthetic testbed. Its “Setup incomplete” banner is intentional because its accessibility service was never enabled; it is not the owner's release dashboard. Other themes, large fonts, landscape and foldable layouts need further visual testing.
- Temporary testbeds/runner and phone-side recordings were removed. Original USB screen-awake setting restored to 0; owner's new release and fixtures retained.

The owner confirmed on the new installed release that PIN unlock occurs after the last digit without Continue, and successful fingerprint unlock works. The private PIN was not read or supplied by the test runner.

Do not attribute earlier-package results below to this build.

## Earlier candidate evidence (historical)

Samsung A57 SM-A576B, Android 17 API 37, One UI 9 (90000), 120 Hz, 1080×2340, security patch 2026-09-05. Earlier candidate used `app.locker.plus.release`.

- 100 protected launch/cancel/public-exit cycles passed: 34 launcher, 33 VIEW, 33 SEND; 50 blue / 50 red synthetic fixtures. Automatic biometric prompt and cancellation retained the PIN fallback.
- 100 unrelated public-fixture cycles passed, including 10 keyboard entries: no shield draw or biometric launch events in the app's operational log.
- 20 picker switch changes passed in an isolated testbed.
- Fresh uninstall authentication test passed using an intercepted ACTION_DELETE request; no actual app was removed.
- Owner confirmed automatic fingerprint and PIN unlock; requested additional visual refinement.
- Direct event-to-frame-commit samples: n=95, median 30 ms, p95 59 ms, maximum 90 ms. Commit means buffer submission, not physical presentation.
- A separate 12-cycle recording exposed four protected-color reappearances during biometric handoff, approximately 11–102 ms. Passing UI assertions did not establish visual privacy. This evidence prompted the entry-completion handoff change; its efficacy requires a new recording.

No zero-frame guarantee, multiwindow/DeX certification, battery result, or global uninstall interception is claimed. Notification content, widgets and other apps' Recents previews remain separate exposure surfaces.

Reference for the revised handoff: https://developer.android.com/reference/android/app/Activity#onEnterAnimationComplete()

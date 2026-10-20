# Sam App Locker 1.3 verification

Date: 2026-10-20. Version 1.3.0/code 9, Android 15/API 35 minimum, compile/target 37. Release `app.sam.locker`; debug `app.sam.locker.debug`.

## Build and delivery identity

- Debug/release assembly, 57 JVM tests, debug lint and release lint passed. Zero test failures and zero lint errors; 27 warnings remain in each variant and are not represented as resolved.
- Implementation commit `4ac26ad26c7e4d6060de5b44e1a728db46c00276` passed [GitHub CI](https://github.com/Addymore/Sam-App-Locker/actions/runs/39498615888). Subsequent release commits change documentation only.
- Release SHA-256: `b683798533ef63bf05f6a5317713a1d7c387202a2aee5b8fe904cb071e75a603`.
- Debug SHA-256: `986181579cb703b9e984720fca0131426f1742f2b4cabfc8b5cfb8191ced9f44`.
- Existing dedicated release certificate SHA-256: `98c298eccbd29e15e83668331e74220df2471d37bb410855c71b077e989067ed`. Verified non-debuggable release and 16 KB APK alignment; installed as an upgrade retaining owner data.

## Physical Samsung checks

Galaxy A57 SM-A576B, Android 17, One UI 9 (`90000`), USB-connected with temporary screen-awake. Regular Accessibility service enabled; isolated runner targets only the disposable testbed. Blue/red synthetic apps protected, public fixture unprotected. Private owner credentials are never read or supplied by automation.

- Protected entry: 100/100 cycles passed in 613.026 seconds. Includes synthetic launcher/deep-link/share intents, cold fixture starts, automatic fingerprint, cancellation retaining the shield, no credential input while fingerprint is active, explicit menu PIN selection, host completion and public-app exits. Ten explicit fingerprint retries are included. Diagnostics counted 100 automatic requests, 110 total biometric launches and 110 opaque-host handoffs.
- Direct window-event-to-shield frame commit: n=84, p95 **49 ms**, max **67 ms**. Scan-triggered frames are reported separately: n=126, p95 52 ms, max 78 ms. These measure callback-to-frame-commit, not physical screen presentation or exposure from app launch. They do not prove zero-frame exposure.
- Rapid protected-to-public switching: 100/100 passed in 141.345 seconds, including leaving while the shield or biometric host was entering.
- Public app/keyboard: 100/100 passed in 151.514 seconds, with no new shield draws or automatic fingerprint requests in PID-scoped diagnostics.
- Home/public notification shade and Quick Settings: 100/100 passed in 241.817 seconds, with zero shield draws and zero automatic fingerprint requests in locker diagnostics. Uses status-bar expand/collapse commands and window ownership, not notification contents. This does not repeat owner-unlock-to-shade gestures or the already-authenticated protected-app shade flow.
- Credential-change lifecycle test passed in 17.157 seconds: backgrounding and immediately returning while the vault worker is blocked does not leave enrollment interactive or alter the stored credential. Only the disposable testbed was reset before this test.
- Credential storage/theme tests: 8 passed in 14.563 seconds. Includes credential-format/storage/rate-limit behavior and theme opacity/contrast/preferences.
- Compact picker and removal tests: 2 passed in 78.32 seconds. Repeated search changes retained position/focus; 20 lock toggles matched observable/persisted state. Wrong PIN and existing dashboard authorization emitted no removal request; fresh correct PIN emitted exactly the selected app request. Actual Android uninstall was intercepted, not executed.
- Credential UI: PIN, pattern and password controls stayed hidden until explicitly selected and accepted synthetic input. Passed in 18.981 seconds. This is not certification of every real-app keyboard or TalkBack scenario.

The owner confirmed successful fingerprint and automatic PIN unlock on this exact final APK, with no screen recording. The testbed and runner are removed from the primary user after testing; synthetic fixtures are retained for owner testing. The original USB screen-awake setting is restored.

## Failed/interrupted evidence retained

An emulator appeared after the physical shade run completed; the subsequent generic cleanup command reported multiple devices. The passing run was already attached to the Samsung. Subsequent commands explicitly selected the Samsung; no emulator test result is reported as physical evidence.

The initial release smoke attempt failed its post-cancellation shield assertion before owner-confirmed fixture setup. Its diagnostics did not show a protected-entry shield; the cause is not asserted conclusively. After the owner confirmed the fixtures were locked and the phone ready, a separately labelled three-cycle smoke passed (21.193 seconds), followed by the full protected-entry run above.

The first compact removal test expected `Incorrect PIN`, while the UI emits `Incorrect pin`; that assertion was corrected and both compact tests rerun successfully. The failed result remains in the evidence archive. Earlier build-environment/in-flight-edit failures are not counted as successful builds.

## Remaining limits

No new physical-frame/high-speed-camera exposure measurement, successful-authentication-to-physical-shield-removal measurement, idle battery/CPU/memory assessment, reboot/Doze certification or full multiwindow/foldable/DeX coverage. Android 15/16 physical testing, rotation/configuration changes during the native biometric prompt, password-IME rapid switching and TalkBack pattern use remain open. See NEXT-UPDATE.md.

This ordinary APK cannot block external uninstall, owner-disabled accessibility, force-stop or safe mode. Notifications/widgets/other apps' Recents previews are separate exposure surfaces. No ADB/OEM/system privileges were added. Visual presets do not reproduce Samsung/OPPO system enforcement.

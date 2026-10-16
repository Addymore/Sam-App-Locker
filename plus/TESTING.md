# Verification protocol

Never infer display presentation from a state-machine assertion or draw callback. Use a synthetic target without private data and separately record the first target frame, event receipt, opaque overlay presentation, successful authentication and shield removal.

## Automated tests

`./gradlew :app:testDebugUnitTest` exercises duplicate events, stale tokens, failed authentication, per-app multiwindow challenges, immediate relocking, screen lock, timeout boundaries, expired background timers, disconnect/reconnect, configuration changes, unknown windows, shared scope, process recreation and 100 deterministic cycles.

For physical tests, build with `-PdeviceTests :app:assembleBenchmark :app:assembleBenchmarkAndroidTest :fixture:assembleDebug`. Install only the testbed and fixture APKs for automated setup. The benchmark application ID ends in `.testbed`; tests refuse to seed credentials/configuration in the final app. Its fixture PIN is **246813**, for synthetic testing only. The user must enable **Sam App Locker Test** in Accessibility Settings. Tests do not write secure settings or grant accessibility themselves.

Run instrumentation with `adb shell am instrument -w app.locker.plus.testbed.test/androidx.test.runner.AndroidJUnitRunner`. DeviceCyclesTest alternates 100 synthetic target launches via MAIN, explicit deep link and explicit share intents, validates the challenge and attached shield, executes real PIN verification, checks removal and delayed duplicate prompts, and writes files/cycles.csv and files/diagnostics.csv in testbed private storage. This validates service/engine/PIN integration, not 100 manual keypad or biometric interactions, notification taps, or compositor frame secrecy. CredentialTest validates Keystore-backed verifier round-trip and persistent rate limiting. Test APKs are never release deliverables.

## Physical matrix

Record device model, build fingerprint, API, One UI version, active refresh rate, thermal/power settings and whether USB charging was connected. For each available device use at least 100 launch/switch cycles. Count missed shields, repeated prompts, cancellation failures and visible flashes. Report warm/cold conditions and sample size, p50/p95/max. Treat the initial p95 event-to-opaque-frame <100 ms target as unproven until measured on actual output frames.

Additional manual paths: launcher taps, Recents, notification taps, browser links, shares through the chooser; protected A→B→unprotected switching; screen off/device credential unlock; keyboard and rotation; biometric success/cancel/lockout/PIN fallback; process death, reboot, update, service interruption and permission revocation. Test split screen and Samsung pop-up with both panes protected, then convert to full screen. Foldables and DeX require appropriate hardware; default-display support must not be extrapolated to external displays.

Record the synthetic apps with screenrecord where possible, and use an external high-frame-rate camera for secure-window/biometric transitions. FLAG_SECURE may black out the overlay in screenrecord, so a black recording is not evidence of what the user physically saw. Inspect frame by frame. The app's `shield_draw_submitted` measures event receipt to backing-view draw submission, not SurfaceFlinger presentation. Pair `pin_accepted`/`biometric_accepted` timestamps with `shield_remove_submitted` for submission-level handoff latency. Do not equate PIN KDF execution time with removal latency.

Measure memory with dumpsys meminfo for the specific package, CPU with a time-window process sample, and idle power over a controlled unplugged interval with screen/state/network conditions held constant. USB-powered short runs are not a valid battery-drain benchmark. Do not reset the device's global battery statistics without owner consent.

The local operational ring stores only event categories, monotonic timestamps and durations. No screen contents, credentials, or package names are logged. Keep synthetic test artifacts separate from personal device data.

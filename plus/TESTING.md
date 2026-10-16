# Reproduce the verification

Use synthetic fixtures, not private app content. Keep the phone unlocked during UI tests. Read VERIFICATION.md for the tested build and remaining limitations.

## Build and JVM checks

From `plus/`, with JDK 17 and Android SDK 37 configured:

```sh
./gradlew -PdeviceTests :app:assembleDebug :app:assembleBenchmark :app:assembleBenchmarkAndroidTest :fixture:assembleRedDebug :fixture:assembleBlueDebug :app:testBenchmarkUnitTest :app:lintDebug
```

The normal APK is `app/build/outputs/apk/debug/app-debug.apk`. The isolated test application uses `app.locker.plus.testbed`; it cannot access the normal app's PIN or settings.

## UI regression run on an owner-configured phone

1. Install the normal debug APK with `adb install -r`. Preserve the same signing identity to retain the owner's data. Let the owner create their PIN and enable **Sam App Locker** in Accessibility.
2. Install both fixture APKs from `fixture/build/outputs/apk/{red,blue}/debug/`. The owner selects both **Locker test fixture** entries in the normal app's protected list. Enroll a strong fingerprint in Samsung Settings.
3. Install `app-benchmark.apk` and `app-benchmark-androidTest.apk` from their respective build output directories. Leave **Sam App Locker Test** accessibility disabled for this run. Instrumentation targets that isolated package so it does not restart the normal service.
4. Unlock the phone normally. Run:

```sh
adb shell am instrument -w -r -e class app.locker.plus.PickerUiTest,app.locker.plus.BiometricUiProbeTest -e cycles 100 app.locker.plus.testbed.test/androidx.test.runner.AndroidJUnitRunner
```

PickerUiTest enrolls a known synthetic PIN only in the testbed, authenticates through the keypad, filters the fixture list and toggles a fixture 20 times. Every tap must update the accessible switch state, observable UI state and preferences without leaving the screen. It ends with that fixture disabled in the testbed; the normal app's selections are untouched.

BiometricUiProbeTest alternates the protected fixture apps using the normal installation. Each cycle must find the fingerprint action, observe Samsung's active/focused biometric window, cancel it, find the lock shield again and confirm the authentication Activity has gone. It uses `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` on every UI automation access. A successful run does not prove fingerprint matching, physical frame ordering or security in untested modes. Fingerprint success still needs the owner's finger.

Do not run all instrumentation tests indiscriminately on an owner-configured phone. The older DeviceCyclesTest intentionally targets the testbed service and requires its separate owner-controlled Accessibility setup. Restarting that instrumentation process can leave Samsung's testbed service enabled but disconnected. Its historical results are retained separately.

## Timings and recordings

Capture the normal app's `SamLocker` log continuously during the run to avoid buffer rollover. Logs contain event categories and monotonic durations, not package names, screen content or credentials. The supplied `summarize_ui_run.py` accepts the log path and normal process ID. `shield_event_frame_committed` starts at a direct protected window-event receipt; `shield_scan_frame_committed` starts at a later window-scan decision. Do not combine them into a claimed event-to-display metric.

For frame analysis, record only the synthetic test workload. The supplied classifier exports colour fractions and timestamps from the central fixture area; raw launcher-containing recordings are not part of public evidence. Secure windows are blacked out by Android's recorder. Use an external high-speed camera for actual display-presentation and zero-frame claims.

Restore temporary screen-awake settings after testing. Controlled idle battery testing requires a separate unplugged run. Do not infer battery drain from USB-powered automation.

## Version 1 credential regression

The isolated credential suite does not need the testbed Accessibility service or the owner's PIN:

```sh
adb shell am instrument -w -r -e class app.locker.plus.CredentialTest app.locker.plus.testbed.test/androidx.test.runner.AndroidJUnitRunner
```

It creates only synthetic testbed credentials, tests old-format migration and tamper rejection, checks persistent rate limiting, and reports all 100 recreated-vault verification durations. The 500 ms p95 assertion is a generous device-regression ceiling, not a promise of instantaneous rendering. Retain the instrumentation output with the specific device/build conditions.

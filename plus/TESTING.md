# Reproduce the verification

Use synthetic fixtures, not private app content. Keep the phone unlocked during UI tests. Read VERIFICATION.md for the tested build and remaining limitations.

## Build and JVM checks

From `plus/`, with JDK 17 and Android SDK 37 configured:

```sh
./gradlew -PdeviceTests :app:assembleDebug :app:assembleBenchmark :app:assembleBenchmarkAndroidTest :fixture:assembleRedDebug :fixture:assembleBlueDebug :app:testBenchmarkUnitTest :app:lintDebug
```

The normal APK is `app/build/outputs/apk/debug/app-debug.apk`. The isolated test application uses `app.sam.locker.testbed`; it cannot access the normal app's PIN or settings.

## UI regression run on an owner-configured phone

1. Install the normal debug APK with `adb install -r`. Preserve the same signing identity to retain the owner's data. Let the owner create their PIN and enable **Sam App Locker** in Accessibility.
2. Install both fixture APKs from `fixture/build/outputs/apk/{red,blue}/debug/`. The owner selects both **Locker test fixture** entries in the normal app's protected list. Enroll a strong fingerprint in Samsung Settings.
3. Install `app-benchmark.apk` and `app-benchmark-androidTest.apk` from their respective build output directories. Leave **Sam App Locker Test** accessibility disabled for this run. Instrumentation targets that isolated package so it does not restart the normal service.
4. Unlock the phone normally. Run:

```sh
adb shell am instrument -w -r -e class app.locker.plus.PickerUiTest,app.locker.plus.BiometricUiProbeTest -e cycles 100 app.sam.locker.testbed.test/androidx.test.runner.AndroidJUnitRunner
```

PickerUiTest enrolls a known synthetic PIN only in the testbed, chooses PIN fallback, filters the fixture list and toggles a fixture 20 times. Each tap must move the row out of its previous tab; the test selects the other tab and checks the accessible switch state, observable state and preferences. It ends with that fixture disabled in the testbed; the normal app's selections are untouched. The revised 1.1 device test has been prepared but not yet run on hardware.

BiometricUiProbeTest alternates the protected fixture apps using the normal installation. In 1.1 each cycle must observe Samsung's active/focused biometric window automatically, without a fingerprint-button tap, then cancel it, find the lock shield and PIN Continue action, and confirm the authentication Activity has gone. It uses `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` on every UI automation access. A successful run does not prove fingerprint matching, physical frame ordering or security in untested modes. Fingerprint success still needs the owner's finger. The revised automatic flow requires a new physical run; the 1.0 results are historical.

### Signed release package

Install the published signed release without replacing its APK with a debug build. Have the owner create their private PIN, enable only its service and select both fixture packages. If fixtures were installed while the app picker was already open, reopen the activity to refresh the installed-app list before selecting them. Keep the isolated testbed Accessibility service disabled.

The runner accepts only the known debug/release package IDs and checks the specified package's shield and authentication host. Test the release using:

```sh
adb shell am instrument --user 0 -w -r -e class app.locker.plus.BiometricUiProbeTest -e lockerPackage app.sam.locker -e mixedEntries true -e cycles 100 app.sam.locker.testbed.test/androidx.test.runner.AndroidJUnitRunner
```

`mixedEntries=true` alternates explicit MAIN, VIEW deep-link and SEND intents across the two fixtures. These are direct intent launches, not taps on launcher icons, notifications or share-chooser UI. Every cycle includes Home, one protected launch, an automatic biometric prompt and cancellation. The runner never reads or supplies the owner's PIN. Actual PIN and fingerprint success require a separate owner check.

Do not run all instrumentation tests indiscriminately on an owner-configured phone. The older DeviceCyclesTest intentionally targets the testbed service and requires its separate owner-controlled Accessibility setup. Restarting that instrumentation process can leave Samsung's testbed service enabled but disconnected. Its historical results are retained separately.

## Timings and recordings

Capture the normal app's `SamLocker` log continuously during the run to avoid buffer rollover. Logs contain event categories and monotonic durations, not package names, screen content or credentials. The supplied `summarize_ui_run.py` accepts the log path and normal process ID. `shield_event_frame_committed` starts at a direct protected window-event receipt; `shield_scan_frame_committed` starts at a later window-scan decision. Do not combine them into a claimed event-to-display metric.

For frame analysis, record only the synthetic test workload. The supplied classifier exports colour fractions and timestamps from the central fixture area; raw launcher-containing recordings are not part of public evidence. Secure windows are blacked out by Android's recorder. Use an external high-speed camera for actual display-presentation and zero-frame claims.

Restore temporary screen-awake settings after testing. Controlled idle battery testing requires a separate unplugged run. Do not infer battery drain from USB-powered automation.

## Version 1 credential regression

The isolated credential suite does not need the testbed Accessibility service or the owner's PIN:

```sh
adb shell am instrument -w -r -e class app.locker.plus.CredentialTest app.sam.locker.testbed.test/androidx.test.runner.AndroidJUnitRunner
```

It creates only synthetic testbed credentials, tests old-format migration and tamper rejection, checks persistent rate limiting, and reports all 100 recreated-vault verification durations. The 500 ms p95 assertion is a generous device-regression ceiling, not a promise of instantaneous rendering. Retain the instrumentation output with the specific device/build conditions.

# Sam App Locker verification — 2026-10-16

## Release status

Installable **debug preview**, not a production-certified security boundary. API 37 / Android 17 build. Original MIT attribution retained. No legitimate production signing credentials were available, so no release APK or signing key is supplied.

## Physical hardware and conditions

- Samsung Galaxy A57, model SM-A576B; Android 17; One UI property 90000 (One UI 9).
- Build: A576BXXU6BZIE_ODM6BZIE; Android build CP2A.260605.016.
- Default display 1080 × 2340, density 450; active display mode 120 Hz, also supports 60 Hz.
- USB debugging and USB charging connected. Ordinary unrooted installation; no OEM/Knox privileges. No battery exemption was granted by automation.
- Two synthetic fixture apps; alternating warm MAIN, explicit VIEW deep-link and explicit SEND launches. These do not stand in for tapping real notifications, selecting a share chooser, or tapping launcher/Recents UI.
- No emulator results are included. No second Samsung, foldable or DeX device was available.

## Build and automated verification

- JDK 17; Gradle 9.5.1; Android Gradle Plugin 9.2.1; compile/target SDK 37.
- Debug, isolated benchmark and instrumentation APKs built successfully.
- 17 JVM tests passed: 14 engine tests and 3 window-evidence regression tests.
- Android lint completed without errors. Dependency-update, Kotlin-style and compatibility warnings remain; this is not a warning-free build.
- Real-device CredentialTest passed (1 test, 22.164 seconds): encrypted credential round trip, incorrect PIN, persistent failed-attempt limiter and fresh-vault penalty restoration.
- The final physical DeviceCyclesTest passed **100/100 cycles**, 367.178 seconds including owner reconnect wait. No missed challenge, missed attached shield, stale PIN result, delayed duplicate lock or additional removal was detected inside that test's assertion windows. These are service-integration assertions, not a claim of zero visible flashes. Code commit: `9f9754c`.
- Normal debug APK installed successfully as `app.locker.plus`, label **Sam App Locker**. The isolated testbed and instrumentation packages were removed after evidence collection.

## Completed-run timings

| Measurement | Samples | Median | p95 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| Launch request to challenge observed by test | 100 | 195 ms | 287 ms | 581 ms |
| PIN round trip, including 150 ms duplicate-check wait | 100 | 2459.5 ms | 2539 ms | 2622 ms |
| Shield decision to draw submission, mixed launch and Home-transition samples | 175 | 46 ms | 75 ms | 98 ms |
| Accepted PIN to removal submission completion | 87 | 62 ms | 78 ms | 83 ms |

Timing percentiles use nearest rank. The full cycle CSV contains all 100 iterations. The retained logcat sample contains only 87 accepted PIN events and 175 shield draws because earlier log entries rolled out; these diagnostic percentiles must not be described as 100-launch presentation measurements. Shield origins include direct window-event handling and queued window scans. The recorded duration starts at the decision's supplied timestamp, so the mixed metric does not include all queueing time for scan-origin shields. PIN KDF work accounts for most of the roughly 2.3-second authentication wait; it runs off the main thread.

During the cycle workload, one `dumpsys meminfo` sample reported 122,140 KiB PSS (119.3 MiB), 239,520 KiB RSS (233.9 MiB), 64 KiB swap PSS. The normal app after setup reported 182,162 KiB PSS (177.9 MiB) and 313,144 KiB RSS (305.8 MiB). These are snapshots, not peak or steady idle memory. Four short CPU samples during owner interaction ranged from 0 to 88.8% of one core; they are not an idle benchmark. Controlled idle CPU and unplugged battery drain have not been measured.

The owner completed normal-app PIN setup and accessibility enablement. A live `service_connected` event independently confirmed connection in the normal installation. The owner's private PIN was not requested or captured.

## Failures found during development

The original implementation had delayed detection, main-thread work, overlay removal before biometric handoff and race-prone session decisions; see ARCHITECTURE.md. Initial physical testing of the new implementation also exposed delayed Samsung window snapshots during launches. A protected foreground event now installs the opaque backing shield before inspecting window roots; a package-only foreground hint survives temporarily stale snapshots. The Compose instance survives overlay detachment.

A stricter physical run stopped at zero-based cycle 60 because its shield-removal counter increased twice. The test previously accepted the already-unlocked app as evidence that Home had arrived. The next run explicitly waits for a Home event, and diagnostic categories identify foreground changes and releases. The failed run remains a failure in the supplied evidence; it is not counted as a successful 100-cycle run. Conservative unknown-window shielding can still briefly flash during a Home transition.

Instrumentation restarts killed the testbed service process. Samsung left the service enabled but disconnected; the owner had to toggle Accessibility off/on to reconnect. Enabled status alone is therefore not reliable proof of protection. The app dashboard separately observes the actual service connection.

## Frame-by-frame recording: exposure confirmed

A 119.6-second variable-frame-rate recording from the preceding physical run was decoded into 2,856 frames. Classification examined only the central synthetic red/blue content region. In 34 identifiable launch sequences (transition → target colour → secure blackout lasting over one second), target content was recorded before blackout:

| Recording measurement | Result |
| --- | ---: |
| Median first detected target content to blackout | 86.1 ms |
| p95, nearest-rank | 157.2 ms |
| Maximum | 158.3 ms |

These results **disprove zero-frame protection for that tested build**. They are not event-to-shield presentation latency. The secure overlay is blacked out by screenrecord, and variable capture cadence prevents treating encoded frames as a complete record of the 120 Hz physical display. External high-speed camera validation is still required. Raw recordings include launcher transitions and are retained only in the working directory; exported evidence contains timing classifications, not personal launcher images.

The event-to-first-opaque-frame p95 target below 100 ms remains unverified at the compositor/physical-display level. A native `dispatchDraw` timestamp reports draw submission only. It cannot prove when the pixels reached the screen or erase content rendered before the accessibility event.

## Required follow-up before production use

- Manually verify fingerprint success, cancel, lockout and PIN fallback; verify whether any enrolled face modality meets BIOMETRIC_STRONG. Automated PIN service calls do not test keypad usability or fingerprint window ordering.
- Launcher taps, Recents, notification taps, browser deep links and share chooser; rapid direct protected-to-protected switching.
- Screen off/device unlock, rotation, keyboard, permission revocation, reboot, app update, power saving and Doze. Engine unit coverage does not prove these physical lifecycle paths.
- Split screen and Samsung pop-up with every protected pane visible; foldable and DeX transitions on appropriate hardware. Multiwindow remains experimental; external-display/DeX protection is unsupported.
- Controlled unplugged battery run, sustained idle CPU sampling and memory after long usage. USB-powered short runs cannot establish idle battery drain.
- External high-frame-rate video synchronized with event receipt, auth completion and presented overlay frames. Confirm missed locks, duplicate prompts and flashes over 100 cycles for each additional supported device/OS combination.

Notifications, widgets and other apps' Recents previews remain outside the launch-lock boundary. Force-stop, safe mode, owner uninstall and service interruption stop ordinary third-party enforcement. Recovery Settings and emergency calls remain accessible.

# Sam App Locker verification — 2026-10-16

> Follow-up: the exact signed v1.0.0 release has now been installed and tested on the A57. See [RELEASE-DEVICE-VERIFICATION.md](RELEASE-DEVICE-VERIFICATION.md) for 100/100 prompt/cancellation cycles and owner-confirmed fingerprint and PIN unlock. The pending release-installation statements below describe the earlier, disconnected session; the follow-up supersedes them. Original measurements retain their original test scope.

## Release status

Version **1.0.0** (versionCode 2), API 37 / Android 17. Debug and a dedicated-key signed release APK are supplied. Release signing does not certify native/OEM enforcement or untested device modes. Original MIT attribution is retained. The release identity is separate from the existing debug installation so its private data is not erased. No private signing key is included in the public artifacts.

## Version 1 PIN fix: physical results

Final version-1 builds passed: debug, release, benchmark and instrumentation APKs; 17/17 JVM tests; debug and release lint each had zero errors and 20 warnings. The signed release passed APK v2/v3 signature verification and zipalign's 16 KB page check. All bundled native ELF LOAD segments were also inspected and had 16,384-byte alignment. Its manifest was checked for version 1.0.0/code 2, the separate release package ID and absence of the debuggable flag.

The final debug APK was installed successfully in place on the A57. The USB device disconnected before a separate signed-release installation/startup smoke test and before post-update live service status could be checked. Therefore those release-device checks remain unverified. The temporary USB screen-awake setting had already been restored to 0. The disabled isolated testbed packages and generated phone-side test video could not be cleaned up after disconnection; the testbed uses synthetic credentials, and the phone-side recording includes launcher transitions and was excluded from public artifacts. These temporary test files can be removed later. No normal-app data was cleared.

The preview used Android's PBKDF2-HMAC-SHA256/600,000 implementation on every PIN entry. The work was off the UI thread but still delayed the authentication result by roughly 2.3 seconds. Version 1 uses native Argon2id v1.3 (19 MiB, two iterations, one lane) with random salt and the existing Keystore AES-GCM wrapper. This meets the cited OWASP baseline; it does not replace a password KDF with a fast hash or keep the PIN cached.

On the same A57, the isolated CredentialTest completed **3/3 tests in 13.213 seconds**. It verified legacy migration, no migration for a wrong PIN, encrypted-record tamper rejection, PIN replacement, input-array clearing, wrong-PIN rejection and persistence of the failed-attempt penalty. Across 100 valid checks, each with a newly constructed PinVault:

| Measurement | Result |
| --- | ---: |
| One-time successful legacy verification plus migration | 2,362 ms |
| New PIN verification median | 55 ms |
| New PIN verification p95 | 59 ms |
| New PIN verification maximum | 61 ms |

Durations include Keystore decryption, native KDF, constant-time comparison and the persistent attempt-counter commits. They exclude UI entry, executor queueing and rendered screen transitions. Vault-object recreation is tested; it is not a claim of 100 full process restarts. The native library was already loaded in this process. The first legacy PIN entry remains slow once; subsequent entries use the new record. Evidence: device-pin-v1.txt, with all 100 samples. The owner's actual PIN was neither read nor used by automation.

## Picker and biometric UI regression results

Runtime commit `7c4630c`, before the isolated version-1 credential change: **2/2 physical UI tests passed in 335.038 seconds**. The picker test made 20 actual row taps and checked accessible switch state, observable UI state and stored selections after every tap. The biometric test performed 100 alternating synthetic red/blue app launches, tapped the fingerprint action, observed Samsung's active/focused prompt, cancelled it and verified that the shield returned and the authentication Activity stopped. There were 100 button callbacks, 100 prompt launches, 100 cancellations and 100 completed replacement-frame callbacks. No extra prompt was observed by these assertions. This is not a test of actual fingerprint matching on every cycle.

The owner separately confirmed that their enrolled fingerprint unlocked the blue fixture and that app switches updated promptly. A content-free `biometric_accepted` event corroborated the successful fingerprint result. No fingerprint template or private PIN was captured. Version 1 does not change the engine, picker or biometric handoff code from this tested implementation; their 100-cycle results are historical regression evidence, not a newly repeated 100-cycle UI run of the release package.

| UI-run timing | Samples | Median | p95 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| Direct protected window event to shield hardware frame commit | 95 | 33 ms | 49 ms | 53 ms |
| Scan decision to hardware frame commit, mixed origins | 107 | 41 ms | 51 ms | 98 ms |
| Cancellation result to replacement-overlay frame commit | 100 | 63 ms | 84 ms | 98 ms |

Frame commit means submission to the swap chain, not physical presentation. Five launch shields originated from window scans, so the 95 event-origin samples are not all 100 launches. The scan set also contains cancellation returns and setup samples; it cannot be presented as event latency. The original p95 below 100 ms physical-frame target remains unverified.

A 179.98-second recording of part of this UI run was decoded into **13,037 frames**. In 47 identifiable transition-to-target-colour-to-blackout launch sequences, content-to-blackout durations were median 39.74 ms, p95 95.40 ms and maximum 111.30 ms. No target-colour-between-blackout pattern was detected in this sample. These findings still confirm launch content exposure. Variable capture cadence, thresholded colour classification and screenrecord's secure-window blackout prevent a zero-exposure or no-flash claim. Only colour/timing classifications are published, not raw launcher-containing video.

After that run, six 10-second CPU interval samples at Home ranged from 0.0% to 0.8% of one core (the initial cumulative sample is excluded). Memory was 121,629 KiB PSS and 243,052 KiB RSS. These are a brief USB-powered idle observation, not an unplugged battery or sustained-memory benchmark. Temporary USB screen-awake was restored to its original value 0.

## Physical hardware and conditions

- Samsung Galaxy A57, model SM-A576B; Android 17; One UI property 90000 (One UI 9).
- Build: A576BXXU6BZIE_ODM6BZIE; Android build CP2A.260605.016.
- Default display 1080 × 2340, density 450; active display mode 120 Hz, also supports 60 Hz.
- USB debugging and USB charging connected. Ordinary unrooted installation; no OEM/Knox privileges. No battery exemption was granted by automation.
- Two synthetic fixture apps; alternating warm MAIN, explicit VIEW deep-link and explicit SEND launches. These do not stand in for tapping real notifications, selecting a share chooser, or tapping launcher/Recents UI.
- No emulator results are included. No second Samsung, foldable or DeX device was available.

## Earlier build and service verification (preview baseline)

- JDK 17; Gradle 9.5.1; Android Gradle Plugin 9.2.1; compile/target SDK 37.
- Debug, isolated benchmark and instrumentation APKs built successfully.
- 17 JVM tests passed: 14 engine tests and 3 window-evidence regression tests.
- Android lint completed without errors. Dependency-update, Kotlin-style and compatibility warnings remain; this is not a warning-free build.
- Real-device CredentialTest passed (1 test, 22.164 seconds): encrypted credential round trip, incorrect PIN, persistent failed-attempt limiter and fresh-vault penalty restoration.
- The final physical DeviceCyclesTest passed **100/100 cycles**, 367.178 seconds including owner reconnect wait. No missed challenge, missed attached shield, stale PIN result, delayed duplicate lock or additional removal was detected inside that test's assertion windows. These are service-integration assertions, not a claim of zero visible flashes. Code commit: `9f9754c`.
- Normal debug APK installed successfully as `app.locker.plus`, label **Sam App Locker**. Subsequent fixes were installed in place, retaining its signing identity and owner data. The isolated testbed was reinstalled for UI regression tests.

## Earlier service/PIN-run timings (commit 9f9754c, before the PIN fix)

| Measurement | Samples | Median | p95 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| Launch request to challenge observed by test | 100 | 195 ms | 287 ms | 581 ms |
| PIN round trip, including 150 ms duplicate-check wait | 100 | 2459.5 ms | 2539 ms | 2622 ms |
| Shield decision to draw submission, mixed launch and Home-transition samples | 175 | 46 ms | 75 ms | 98 ms |
| Accepted PIN to removal submission completion | 87 | 62 ms | 78 ms | 83 ms |

Timing percentiles use nearest rank. The full cycle CSV contains all 100 iterations. The retained logcat sample contains only 87 accepted PIN events and 175 shield draws because earlier log entries rolled out; these diagnostic percentiles must not be described as 100-launch presentation measurements. Shield origins include direct window-event handling and queued window scans. The recorded duration starts at the decision's supplied timestamp, so the mixed metric does not include all queueing time for scan-origin shields. PIN KDF work accounts for most of the roughly 2.3-second authentication wait; it runs off the main thread.

During the cycle workload, one `dumpsys meminfo` sample reported 122,140 KiB PSS (119.3 MiB), 239,520 KiB RSS (233.9 MiB), 64 KiB swap PSS. The normal app after setup reported 182,162 KiB PSS (177.9 MiB) and 313,144 KiB RSS (305.8 MiB). These are snapshots, not peak or steady idle memory. Four short CPU samples during owner interaction ranged from 0 to 88.8% of one core; they are not an idle benchmark. At this earlier stage no controlled idle sample existed; the later brief Home sample is reported above. Unplugged battery drain remains unmeasured.

The owner completed normal-app PIN setup and accessibility enablement. A live `service_connected` event independently confirmed connection in the normal installation. The owner's private PIN was not requested or captured.

## Failures found during development

The original implementation had delayed detection, main-thread work, overlay removal before biometric handoff and race-prone session decisions; see ARCHITECTURE.md. Initial physical testing of the new implementation also exposed delayed Samsung window snapshots during launches. A protected foreground event now installs the opaque backing shield before inspecting window roots; a package-only foreground hint survives temporarily stale snapshots.

The owner subsequently reported an unresponsive fingerprint button. An independent UI test reproduced it: the button was present and enabled, but no tap callback or system prompt appeared after window reuse. The preceding service-level 100-cycle test did not catch this because it invoked PIN verification directly. The retained Compose composition was replaced with detach-time disposal: the native opaque backing is still reused, but interactive content is rebuilt after its first frame on each window attachment. This prevents preserving a composition/input scope whose window has gone away. Button-level regression results are recorded separately; earlier 100-cycle measurements are not silently relabelled as results from this revised APK.

The owner also reported delayed app-selection switches. The UI previously read a plain preference snapshot and used a revision counter that was not reliably consumed by the skipped dashboard composition. It now observes immutable Compose state and immediately updates the selected set. Window inspection is queued outside the selection callback. Apps and Settings have separate dashboard views, reducing the scrolling needed to reach the picker.

Further biometric testing found that Samsung's type-2017 prompt could not take focus above the accessibility overlay. Coverage now transfers to an opaque Activity after its first hardware frame commit; cancellation restores the overlay and waits for its frame before finishing the Activity. The first handoff revision passed two prompt/cancel interactions but stalled on the third: querying hardware acceleration immediately after addView returned false and skipped the completion callback, leaving a stale Activity. The callback is now registered on the pre-attachment observer, which Android merges into the attached view tree. This failure is retained in device-ui-handoff-smoke.txt.

Test-harness failures were also distinguished from application failures: default UiAutomation access suppressed the owner's service, an early Back event closed the picker before the keyboard appeared, and an unconditional scroll attempted to scroll a fully visible two-row filtered list. The runner now consistently preserves accessibility services, validates PIN digit-count feedback, observes the IME before dismissing it and scrolls only when needed.

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

- Verify biometric lockout, enrolled face configurations and protected-overlay PIN fallback across device modes. Fingerprint success and cancellation have now been tested as described above; this does not validate every authenticator or lockout path.
- Launcher taps, Recents, notification taps, browser deep links and share chooser; rapid direct protected-to-protected switching.
- Screen off/device unlock, rotation, keyboard, permission revocation, reboot, app update, power saving and Doze. Engine unit coverage does not prove these physical lifecycle paths.
- Split screen and Samsung pop-up with every protected pane visible; foldable and DeX transitions on appropriate hardware. Multiwindow remains experimental; external-display/DeX protection is unsupported.
- Controlled unplugged battery run, sustained idle CPU sampling and memory after long usage. USB-powered short runs cannot establish idle battery drain.
- External high-frame-rate video synchronized with event receipt, auth completion and presented overlay frames. Confirm missed locks, duplicate prompts and flashes over 100 cycles for each additional supported device/OS combination.

Notifications, widgets and other apps' Recents previews remain outside the launch-lock boundary. Force-stop, safe mode, owner uninstall and service interruption stop ordinary third-party enforcement. Recovery Settings and emergency calls remain accessible.

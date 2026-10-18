# Sam App Locker 1.2 verification

Date: 2026-10-18. Version 1.2.0, code 8. Android 15/API 35 minimum; compile/target 37. Release `app.sam.locker`, debug `app.sam.locker.debug`. Same dedicated release certificate and in-place upgrade from 1.1.

## Local and artifact checks

- Debug/release assembly, 52 JVM tests and debug/release lint passed. Zero test failures/errors; lint has zero errors and 27 warnings per variant (dependency, SDK-guard, package-visibility, resource and Kotlin suggestions).
- Final implementation commit `6d81688` passed [GitHub CI](https://github.com/Addymore/Sam-App-Locker/actions/runs/39169163254).
- Signed release is not debuggable and passes APK 16 KB alignment verification. Certificate SHA-256: `98c298eccbd29e15e83668331e74220df2471d37bb410855c71b077e989067ed`.
- Delivery APK SHA-256: `3b7551195ea294d3999cdf8e254fd369fc7cb0a79ee89a05b1cb80a599f0f315`. The full-loop 1.2 APK was `0d37042ec88aea3032eaf577cda6335a8405350f7b896b420e5fea41a303f09d`; delivery adds only the credential-settings background safeguard, retaining the tested locking engine. A three-cycle fingerprint/cancellation/public-exit smoke check passed on the delivery APK in 12.675 s.
- A Windows paging-file exhaustion and a later disappearing build daemon interrupted intermediate host runs. The successful final build is recorded separately; those failures were not counted as test passes.

## Physical device and method

Samsung Galaxy A57 SM-A576B, Android 17, One UI 9 (`90000`), 1080×2340, active 120 Hz mode. USB connected with temporary screen-awake, owner-enabled regular accessibility service, blue/red synthetic fixtures locked and public fixture unlocked. Immediate/per-app relocking. The isolated runner never reads or supplies the owner's credential, and the regular app is not its instrumentation target.

On the installed delivery APK, the owner confirmed both fingerprint unlock and automatic PIN unlock worked. Screen recording was off for this private-credential check. The disposable testbed and runner were removed from the primary user after testing, the original USB screen-awake setting was restored, and the regular protection service remained connected.

Six credential-storage instrumentation tests passed in 15.011 s: old PIN migration, recreated vaults, corruption, changed credentials, pattern/Unicode-password persistence, kind tampering, failure penalties and input wiping. Across 100 recreated-vault PIN checks: median 56 ms, p95 60 ms, maximum 62 ms. One-time legacy PBKDF migration took 2332 ms. These are verifier timings, not complete unlock latency.

The synthetic credential UI test passed for direct PIN auto-submit, password keyboard submission and drawn-pattern submission (13.449 s in the captured run). Initial harness failures were corrected: the success label was behind the status bar, and Back was closing a password test Activity that had not opened its keyboard. Synthetic UI checks are not proof of every owner enrollment/rotation/IME scenario.

A dedicated settings lifecycle test passed in 14.295 s using a synthetic vault: authenticate, enter credential change, block the serialized credential worker, go Home and immediately resume. Enrollment stays unavailable until stored authentication state is rechecked, and the saved credential remains unchanged. Its first run exposed a quick-return gap before onStop; the fix invalidates enrollment at onPause and rejects credential input while state is unloaded or the Activity is not resumed. A completed credential change revokes unlock sessions even after backgrounding. The failed and passing runs are both retained.

The final locking engine passed 100 rapid protected-to-public switches in 146.462 s, using the pre-version-bump 1.2 development APK. The full-loop 1.2.0 APK changes only version metadata from that development build; delivery also adds the settings safeguard described above. Earlier development runs failed a biometric handoff and a focus-stealing shield; logs are retained. An interrupted run was not counted as a pass.

The first full-loop-APK fingerprint/cancellation run completed 81 cycles, then Android rejected the next synthetic fixture start with error 102. This was a failed 100-cycle run, not an authentication success claim. The rerun adds an explicit launcher-readiness assertion before each entry and retains the failed-run evidence.

That rerun passed 100/100 fingerprint/cancellation cycles in 373.215 s, including launcher/deep-link/share fixture intents, cold fixture starts, cancellation, direct PIN fallback visibility, host completion and public-app exit. Diagnostics counted exactly 100 automatic requests and 100 opaque-Activity handoffs. Direct event-to-shield frame-commit latency: n=88, p95 56 ms, maximum 73 ms. Scan-triggered frames are not pooled into that metric. This meets the initial sub-100-ms p95 event-to-commit target on this device, not a zero-exposure or physical-presentation guarantee.

The full-loop APK passed 100 Home/public notification-shade and Quick Settings cycles in 235.044 s. PID-scoped locker diagnostics recorded zero shield draws and zero automatic biometric requests. The test uses Android status-bar expand/collapse commands, verifies an active System UI panel, and reads window ownership only. It is not certification of every touch gesture or a repeat of locked-phone unlock/authenticated-protected-app shade flows.

The same full-loop APK passed 100 unlocked/public app and keyboard cycles in 157.32 s, with zero shield draws and zero automatic biometric requests. This checks unsolicited locker windows; it does not measure physical-frame flashes on protected entry.

## Limits and follow-up

No new frame-by-frame exposure recording, high-speed-camera measurement, successful-authentication-to-removal timing, idle battery/CPU/memory measurement, reboot/Doze certification, or full multiwindow/foldable/DeX coverage was completed. Event-to-frame-commit diagnostics are not compositor presentation timings and cannot prove zero-frame exposure.

Android 15/16 physical coverage, password-IME rapid switching, TalkBack pattern use, credential changes through rotation/backgrounding and broad notification/deep-link/Recents scenarios remain in NEXT-UPDATE.md. Test loops include synthetic launcher/deep-link/share entry intents, not every real app's integrations.

Uninstall through Sam App Locker asks for fresh authentication; external Settings/launcher uninstall and removal of Sam App Locker itself remain possible. ADB grants are not OEM privileges or managed-device provisioning. Notifications, widgets and other apps' Recents previews remain separate exposure surfaces. Force-stop, safe mode, revoked service access and owner uninstall can interrupt protection.

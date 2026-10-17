# Sam App Locker 1.1 verification

Date: 2026-10-17. Version 1.1.0 (code 6), release `app.sam.locker`, debug `app.sam.locker.debug`. Minimum Android 15 / API 35; compile/target API 37. This is a normal release at the owner's request, not an assertion of flawless/native enforcement.

## Build and artifact checks

- Debug and release assembly, JVM tests, debug/release lint: passed locally.
- 49 JVM tests, zero failures/errors/skips. Includes shade/session preservation, unknown windows, timeout/screen-lock invalidation, stale callbacks, window identity removal, and reconnect foreground evidence.
- Lint: zero errors/fatal findings; 26 warnings per variant remain.
- Release signature matches the existing certificate (`98c298eccbd29e15e83668331e74220df2471d37bb410855c71b077e989067ed`); APK not debuggable. APK alignment and bundled 64-bit ELF load alignment meet 16 KB checks. No 16 KB physical device was tested.
- Installed final APK SHA-256: `907a57a42401c11fa7f34c3d6ee7875bfc4d411427e3e0dce6e6ce20e76f4740`, identical to the release asset.

## Physical device and method

Samsung Galaxy A57 SM-A576B, Android 17/API 37, One UI 9 (`90000`), 120 Hz, 1080×2340, arm64, 4 KB pages. Connected over USB, owner-enabled accessibility, blue/red synthetic fixtures protected, public fixture unlocked, immediate/per-app relock. USB screen-awake was enabled temporarily for reproducibility. Tests run in an isolated instrumentation package; they do not instrument or clear the release app's data. Owner PIN/fingerprint credentials were never read or supplied by automation.

Shade tests use repeated Android status-bar expand/collapse commands, require an actual System UI panel window, sample window ownership at bounded intervals, and collect PID-scoped locker diagnostics. They do not read notification text or take screenshots. These are not frame-by-frame presentation measurements or a certification of all touch gestures. The manual locked-phone → owner unlock → Quick Settings attempt passed earlier on rc2; it was not reclassified as a final-APK result.

The authenticated-shade run used APK `912b806765be0c00d715d83ab972fb46af70b3ddbb0439f6dc243bdde04f6dfe`: same final locking engine, before the final Settings-only uninstall status wording. It passed 100/100 cycles in 161.993 s, with zero shield attachments or biometric launches in its diagnostic log. Android identified 66 obsolete application-window snapshots, which were discarded through its refresh API. The owner confirmed successful fingerprint unlocking before that run. Final-APK regression results are listed separately below.

## Scope and remaining limitations

Android 15/16 and other Samsung builds were not physically tested. Android 17's obsolete-window refresh is guarded; Android 15/16 retain conservative handling. Previous candidates failed; see NOTIFICATION-SHADE-FIX.md and the included evidence. A normal release label does not erase those results.

No new frame-by-frame exposure, successful-authentication-to-removal latency, long idle battery/CPU/memory, reboot/Doze, DeX/foldable or complete multiwindow certification was performed for this release. Earlier rc2 measurements are historical, not new 1.1 measurements. Brief post-authentication shields and initial protected-content exposure remain priorities for broader verification. No zero-frame guarantee.

External uninstall of protected apps and of Sam App Locker remains possible. Only the in-app Uninstall action requires fresh authentication. Settings now explicitly reports that system-wide and self-uninstall blocking are unavailable. [Android uninstall policy](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setUninstallBlocked(android.content.ComponentName,%20java.lang.String,%20boolean)) requires separately provisioned device/profile ownership or an authorized delegate; this APK does not gain that privilege from accessibility or ADB grants. Notifications, widgets and other apps' Recents previews remain separate exposure surfaces. Force-stop, safe mode, revoked access and owner uninstall can interrupt protection.

## Final APK regression results

| Test | Result |
| --- | --- |
| Protected entry, automatic fingerprint, cancellation/PIN fallback, public exit | **100/100 passed**, 392.431 s; 100 automatic requests, 100 host handoffs and 100 stopped hosts |
| Unprotected fixture + keyboard | **100/100 passed**, 163.894 s |
| Home/public notification + Quick Settings | **100/100 passed**, 251.27 s; no shield attachments in the operational log |
| Rapid protected → public launch race | **Failed on cycle 50**, 71.961 s; 49 cycles completed, 100 planned. Public fixture absent with Samsung biometric window focused. This remains open. |

The protected-entry run recorded 76 direct-event shield frame commits: p50 32 ms, p95 56 ms, max 59 ms. Its 124 scan-origin commits (including cancellation return shields) had p50 48 ms, p95 62 ms, max 84 ms. Draw/frame-commit telemetry is a submission proxy, not proof of actual first on-screen presentation or zero initial content exposure. Do not mix cancellation handoff timings with successful-authentication removal latency. No duplicate automatic requests were observed in the completed protected run; the rapid-switch failure prevents a universal no-late-prompts claim.

Implementation CI: [passed](https://github.com/Addymore/Sam-App-Locker/actions/runs/39027713978) for commit `94854938a6b56b230405b67ff42d89a948483d89`. Attached evidence preserves both successes and failures.

Cleanup: isolated testbed/runner removed from user 0; original USB stay-awake value 0 restored. Normal release service remained bound and enabled after testing. Fixtures were retained for future owner checks.

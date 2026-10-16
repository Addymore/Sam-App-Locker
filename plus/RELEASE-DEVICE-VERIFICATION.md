# Signed release device verification — 2026-10-16

The published Sam App Locker **1.0.0** signed release installed and ran on a physical Samsung Galaxy A57. After the owner confirmed both synthetic apps were selected for protection, **100/100 automated launch, fingerprint-prompt and cancellation cycles passed**. The owner separately confirmed a real fingerprint unlock and a prompt PIN unlock. The release accessibility service was still enabled and bound after testing.

This follow-up supersedes the earlier report's pending release-installation check. It does not change the APK, signing identity or `v1.0.0` tag. Earlier build, credential and video results remain historical evidence in [VERIFICATION.md](VERIFICATION.md).

## Exact artifact and device

| Item | Value |
| --- | --- |
| APK | `Sam-App-Locker-1.0.0-release.apk` |
| Package | `app.locker.plus.release` |
| Version | 1.0.0 / code 2; minimum API 26, target API 37 |
| Runtime source | `80737652aa5ed4bc66eee44a98d3c9805e82fecf` (`v1.0.0`) |
| Isolated test-runner source | `c50996563c8307943b461136ed9ec4414ccf9739` |
| APK SHA-256 | `db6a0f928824823b5eff9adc57379868359d488a98fda74d02ab7974a8b22d4f` |
| Hardware | Samsung Galaxy A57, SM-A576B, arm64-v8a |
| OS | Android 17 / API 37; One UI property `90000` (One UI 9) |
| Build | `CP2A.260605.016`, incremental `A576BXXU6BZIE` |
| Security patch | 2026-09-05 |
| Display | 1080 × 2340, density 450; active 120 Hz |
| Kernel page size | 4,096 bytes |
| Conditions | USB debugging and charging, ordinary installation in primary user 0, no OEM/Knox privileges |

The installed `base.apk` hash matched the published APK byte for byte. Its installed flags did not include `DEBUGGABLE`. Installation returned `Success`; a cold dashboard start returned `Status: ok`, TotalTime **377 ms**, WaitTime **390 ms**. This start timing is not protected-app lock latency. Previously verified 16 KB APK/native-library alignment is a static compatibility check; this phone uses 4 KB pages.

The owner created their own PIN, enabled Accessibility access and selected the fixture apps. Automation did not read or supply that PIN. A separate testbed package hosted instrumentation, leaving the production service process running throughout the tests. The runner preserves accessibility services with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`.

## Setup probe and 100-cycle result

The first three-cycle probe failed on its first launch: the blue fixture opened without a shield, and the test could not find the fingerprint action. That failure is retained as `smoke.txt`. The fixture apps had been installed after the app list was opened. The dashboard was reopened to refresh its inventory, and the owner explicitly confirmed both fixture switches were on. A subsequent three-cycle probe passed in **10.409 seconds**. The initial selection state was not read from private storage, so its precise cause is not independently established.

The complete release run then passed **100 cycles in 295.113 seconds** (`100-cycles.txt`, one parameterized test method):

- 50 launches each of synthetic blue and red fixture apps, with Home between cycles.
- 34 MAIN/LAUNCHER, 33 VIEW with a synthetic URI, and 33 SEND with synthetic text; all explicitly targeted the fixture Activity.
- Each cycle checked the locker action, tapped it, observed Samsung's active or focused biometric window, cancelled with Back, verified the lock screen returned, and verified the biometric host Activity finished.
- The test also checked that the previous lock screen left Home before the next launch.
- Operational logs recorded exactly 100 fingerprint-button callbacks, prompt requests, cancellation results, replacement-frame callbacks and biometric Activity stops. No missed lock/prompt or extra prompt request was observed within these assertions.

These are prompt/cancellation cycles, not 100 successful fingerprint matches. Explicit intents test entry handling; they do not replace tapping an actual launcher icon, notification, Recents card or share chooser. These assertions detect UI state, not every physically displayed frame.

## Timing and memory

Statistics use nearest-rank p95 from content-free operational events in the complete 100-cycle run:

| Measurement | Samples | Median | p95 | Maximum |
| --- | ---: | ---: | ---: | ---: |
| Direct protected window event receipt to shield hardware frame commit | 100 | 30 ms | 43 ms | 50 ms |
| Window-scan decision to shield hardware frame commit, mixed origins | 101 | 40 ms | 48 ms | 53 ms |
| Cancellation result to replacement-overlay frame commit | 100 | 49 ms | 57 ms | 61 ms |

**Hardware frame commit means submission to the swap chain, not physical presentation.** Scan timing excludes time before the scan decision and includes setup/cancellation returns. It is not a second measure of launch-event latency. The original p95 below 100 ms target for the first physically presented opaque frame remains unverified. A separate manual launch recorded **124 ms** to event-origin frame commit; it is outside the repeated-run sample and demonstrates that the repeated-run maximum is not a general bound.

No new frame-by-frame video was recorded in this follow-up. The earlier recording documented brief target-content exposure. These results do not establish zero exposure, no flashes, or native/OEM enforcement. Successful authentication-to-physical-shield-removal latency was not measured here.

One post-run process snapshot measured **84,289 KiB PSS**, **201,300 KiB RSS** and **10,801 KiB swap PSS**. This is neither a peak-memory measurement nor a long-term leak test. This follow-up did not measure idle battery drain or CPU consumption; the earlier brief USB-powered CPU observation is reported separately.

## Real authentication checks

The owner reported **“Fingerprint unlock works”** on the protected blue fixture. The release emitted `biometric_accepted`, corroborating a real accepted result. The owner then reported **“PIN unlock works promptly”** after being asked to return Home, reopen a fixture and unlock with the private app PIN. This PIN result is owner-observed: the manual log contains no PIN acceptance/timing event, so no independent elapsed PIN-unlock time is claimed for this check. The earlier 100 credential-verification measurements were isolated testbed measurements, not repeated unlocks of this release package.

## Cleanup and final observed state

- Removed the isolated testbed and its instrumentation package from user 0.
- Restored the original USB screen-awake setting to `0` and verified it.
- Left the signed release installed with the owner's settings intact; its accessibility service was still enabled and bound.
- Left both synthetic fixture apps available for further owner checks. They may be deselected and uninstalled when no longer needed.
- Published only operational events, synthetic test output and device/build metadata. No private PIN, screen text, biometric template, raw video or signing secret is included.

## Remaining verification

Physical presentation/exposure and flash measurements; real launcher/Recents/notification taps and share chooser; direct rapid protected-to-protected switching; screen off/on, rotation, process death, reboot/update, permission revocation and Doze/power saving under workload; biometric lockout and supported face configurations; split screen, pop-up windows, foldable transitions and DeX; sustained memory and unplugged battery measurements remain incomplete. This is one physical Samsung, with no emulator or second-device results in this follow-up.

Notifications, widgets and other apps' Recents previews remain separate exposure surfaces. Force-stop, safe mode, owner uninstall and disabled Accessibility can interrupt ordinary third-party protection. `FLAG_SECURE` in this app does not protect another app's screenshots or thumbnails.

## Evidence and reproduction

The [v1.0.0 release](https://github.com/Addymore/Sam-App-Locker/releases/tag/v1.0.0) includes `Release-device-evidence-2026-10-16.zip`, this report and separate follow-up checksums. Original APK/source artifacts and their checksums remain unchanged. See [TESTING.md](TESTING.md) for the isolated release-run command. The evidence archive includes the failed setup probe, corrected probe, full 100-cycle output, content-free logs, metric calculation script, installed-APK verification, metadata, memory snapshot and cleanup checks. The script can reproduce `metrics.json` using `python summarize_ui_run.py cycles-events.txt --pid 15051`.

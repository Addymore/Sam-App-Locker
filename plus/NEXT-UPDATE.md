# Next update: remaining work

Status at 1.1.0, 2026-10-17. These items remain open; this release does not claim flawless or native enforcement.

- [ ] **P1: Initial protected-content exposure.** Investigate the 194 ms direct-event outlier and initial recorded exposure up to 177.656 ms. Profile cold starts and actual presentation with an external high-speed camera. Android can display content before accessibility reports it; public permissions or ADB grants cannot guarantee zero exposure.
- [ ] **P1: Reproduced rapid-switch late prompt.** The final 1.1 APK failed on cycle 50 of the rapid protected-to-public switch test: Samsung's biometric window remained instead of the public fixture appearing. The preceding 49 cycles completed; this is not a 100-cycle pass. Prioritize closing/cancelling a stale authentication host during this launch race. Home remains the recovery route.
- [ ] **P1: Wider stuck-screen/late-prompt regression coverage.** See RELEASE-1.1-VERIFICATION.md for final 1.1 regression outcomes. Repeat across actual launcher, Recents, notification taps, share sheets, permission dialogs, screen off/on, rotation, process death, reboot, app update and service interruption. Retain stale-token and unknown-window safeguards.
- [ ] **P1: Multiwindow and additional displays.** Verify every visible protected window in split screen, pop-up, foldable transitions and DeX. These modes and external keyboards are not certified by this release.
- [ ] **P2: Long-running reliability and performance.** Measure idle battery, CPU/memory, power saving/Doze, repeated fingerprint lockout/cancellation, and successful-authentication-to-shield-removal timing. Current cancellation and event-to-frame timings are not substitutes.
- [ ] **P2: Lint maintenance.** Review the 26 warnings per variant without weakening protection; zero errors/fatal findings currently.

- [ ] **P1: Broader shade/Quick Settings validation.** The revised engine passed 100 authenticated-fixture shade cycles on the A57. Repeat real touch gestures after device unlock, with PIN keyboard shown, notifications launching apps, screen rotation, timeouts and additional One UI builds. Preserve fail-closed handling for still-live unknown windows.
- [ ] **P1: Authentication handoff flashes.** One successful-authentication log in this investigation showed a brief replacement shield during the return to the app. Measure the final presentation frame by frame and fix without uncovering protected content.
- [ ] **P2: Android 15/16 physical coverage.** Minimum API 35 is enforced, but the Android 17 refresh path is unavailable on these versions; verify the conservative fallback on real devices.

## Platform boundaries, not promised fixes

- Protected notifications, widgets and other apps' Recents previews need separate settings/design; this app's FLAG_SECURE does not protect another app's surfaces.
- External Settings/launcher uninstall cannot be made a universal fingerprint/PIN gate by this ordinary app. The in-app uninstall path requires fresh authentication; device-owner control remains intact. Sam App Locker can also be uninstalled externally. Its Settings card states these limits. Enforced uninstall blocking is a separate managed-device project, not an ordinary permission toggle.
- Force-stop, safe mode, revoked accessibility access and owner uninstall can interrupt protection. ADB grants do not confer Samsung/OEM signing privileges. Enterprise Knox would be a separate provisioned/licensed deployment.

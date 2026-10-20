# Next update: remaining work

Status for 1.3.0, 2026-10-20. See RELEASE-1.3-VERIFICATION.md for the exact tested APKs. This release does not claim flawless or native enforcement.

- [ ] **P1: Initial protected-content exposure.** Investigate the 194 ms direct-event outlier and initial recorded exposure up to 177.656 ms. Profile cold starts and actual presentation with an external high-speed camera. Android can display content before accessibility reports it; public permissions or ADB grants cannot guarantee zero exposure.
- [x] **Reproduced rapid-switch regression, tested scope.** The 1.2 locking engine passed 100 protected-to-public rapid switches on the A57 after correcting host invalidation and touch-keypad focus. Earlier 1.1 and 1.2-development failures are preserved as evidence. This closes the tested PIN/full-screen reproduction, not every possible stuck-screen path.
- [ ] **P1: Password IME and pattern coverage.** Storage and synthetic input checks pass, but repeat rapid switching while the password keyboard is open, credential changes in background/rotation, TalkBack pattern entry and enrollment confirmation on additional devices. Hardware-keyboard forwarding is not certified; PIN/pattern overlays remain touch-only to preserve Samsung window evidence.
- [ ] **P1: Wider stuck-screen/late-prompt regression coverage.** See RELEASE-1.1-VERIFICATION.md for final 1.1 regression outcomes. Repeat across actual launcher, Recents, notification taps, share sheets, permission dialogs, screen off/on, rotation, process death, reboot, app update and service interruption. Retain stale-token and unknown-window safeguards.
- [ ] **P1: Multiwindow and additional displays.** Verify every visible protected window in split screen, pop-up, foldable transitions and DeX. These modes and external keyboards are not certified by this release.
- [ ] **P2: Long-running reliability and performance.** Measure idle battery, CPU/memory, power saving/Doze, repeated fingerprint lockout/cancellation, and successful-authentication-to-shield-removal timing. Current cancellation and event-to-frame timings are not substitutes.
- [ ] **P2: Lint maintenance.** Review the 27 warnings per variant without weakening protection; zero errors/fatal findings in the 1.2 release build.

- [ ] **P1: Broader shade/Quick Settings validation.** The 1.1 investigation passed 100 authenticated-fixture shade cycles on the A57; 1.2 passed 100 Home/public shade cycles. Repeat real touch gestures after device unlock, over an authenticated protected app, with the credential keyboard shown, notifications launching apps, screen rotation, timeouts and additional One UI builds. Preserve fail-closed handling for still-live unknown windows.
- [ ] **P1: Authentication handoff flashes.** One successful-authentication log in this investigation showed a brief replacement shield during the return to the app. Measure the final presentation frame by frame and fix without uncovering protected content.
- [ ] **P2: Android 15/16 physical coverage.** Minimum API 35 is enforced, but the Android 17 refresh path is unavailable on these versions; verify the conservative fallback on real devices.

## 1.3 regression scope

Fingerprint-first presentation, explicit overflow fallback, per-attempt stale callback rejection, compact app selection/search and dark-theme contrast are implemented. Synthetic credential inputs, search/toggles and in-app removal confirmation have passing Samsung checks; see the release verification for repeated-loop results. These do not close the broader rotation, password-IME, multiwindow or long-idle items above.

## Platform boundaries, not promised fixes

- Protected notifications, widgets and other apps' Recents previews need separate settings/design; this app's FLAG_SECURE does not protect another app's surfaces.
- External Settings/launcher uninstall cannot be made a universal fingerprint/PIN gate by this ordinary app. The in-app uninstall path requires fresh authentication; device-owner control remains intact. Sam App Locker can also be uninstalled externally. Its Settings card states these limits. Enforced uninstall blocking is a separate managed-device project, not an ordinary permission toggle.
- Force-stop, safe mode, revoked accessibility access and owner uninstall can interrupt protection. ADB grants do not confer Samsung/OEM signing privileges. Enterprise Knox would be a separate provisioned/licensed deployment.

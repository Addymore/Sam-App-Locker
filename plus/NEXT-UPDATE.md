# Next update: remaining work

Status at 1.1.0-rc2, 2026-10-16. These items remain open; this release does not claim flawless or native enforcement.

- [ ] **P1: Initial protected-content exposure.** Investigate the 194 ms direct-event outlier and initial recorded exposure up to 177.656 ms. Profile cold starts and actual presentation with an external high-speed camera. Android can display content before accessibility reports it; public permissions or ADB grants cannot guarantee zero exposure.
- [ ] **P1: Wider stuck-screen/late-prompt regression coverage.** The final 100 protected and 100 rapid cycles pass on the A57. Repeat across actual launcher, Recents, notification taps, share sheets, permission dialogs, screen off/on, rotation, process death, reboot, app update and service interruption. Retain stale-token and unknown-window safeguards.
- [ ] **P1: Multiwindow and additional displays.** Verify every visible protected window in split screen, pop-up, foldable transitions and DeX. These modes and external keyboards are not certified by this release.
- [ ] **P2: Long-running reliability and performance.** Measure idle battery, CPU/memory, power saving/Doze, repeated fingerprint lockout/cancellation, and successful-authentication-to-shield-removal timing. Current cancellation and event-to-frame timings are not substitutes.
- [ ] **P2: Lint maintenance.** Review the 22 warnings per variant without weakening protection; zero errors/fatal findings currently.

## Platform boundaries, not promised fixes

- Protected notifications, widgets and other apps' Recents previews need separate settings/design; this app's FLAG_SECURE does not protect another app's surfaces.
- External Settings/launcher uninstall cannot be made a universal fingerprint/PIN gate by this ordinary app. The in-app uninstall path can require fresh authentication; device-owner control remains intact.
- Force-stop, safe mode, revoked accessibility access and owner uninstall can interrupt protection. ADB grants do not confer Samsung/OEM signing privileges. Enterprise Knox would be a separate provisioned/licensed deployment.

# Next update: remaining work

Status for 1.5.0, 2026-11-03. See [RELEASE-1.5-VERIFICATION.md](RELEASE-1.5-VERIFICATION.md) and [UPDATE-1.5.md](UPDATE-1.5.md) for all 1.5.0 features, flash fixes, and verified APKs. This release does not claim native system-level enforcement.

- [ ] **P1: Initial protected-content exposure.** Investigate the 194 ms direct-event outlier and initial recorded exposure up to 177.656 ms. Profile cold starts and actual presentation with an external high-speed camera. Android can display content before accessibility reports it; public permissions or ADB grants cannot guarantee zero exposure.
- [x] **Reproduced rapid-switch regression & screen flashing while switching locked apps.** Closed in 1.5: `PrivacyShield` now keeps its `ComposeView` permanently attached inside `ShieldFrame`, starts directly in `BiometricBackdrop` mode when biometrics are active, filters occluded exiting full-screen windows during 250ms transitions, prioritizes the newly entered foreground app in `LockEngine.windows(..., preferred)`, and suppresses Android 15–17 Shell open/close animations in `BiometricActivity`.
- [x] **P1: Notification privacy for locked apps.** Completed in 1.5 via `LockerNotificationService` (`NotificationListenerService`), canceling and suppressing active and incoming status-bar notifications from locked apps when not unlocked in the foreground while preserving incoming call and alarm alerts.
- [x] **P1: Uninstall protection & package installer guard.** Completed in 1.5 via optional `LockerDeviceAdminReceiver` activation plus `LockerService` interception of system package installers and `DeviceAdminAdd` / `DeviceAdminSettings` when Uninstall Guard is enabled.
- [x] **P1: Stealth pattern trail & PIN keypad scrambling.** Completed in 1.5: added `"Hide pattern trail"` and `"Scramble PIN keypad"` options in Settings -> Authentication, plus Zenzy VIP layout and styles (`Zenzy VIP`, `Pure AMOLED`, `Midnight`).
- [x] **P1: Password IME and pattern coverage.** Fixed in 1.4 and maintained in 1.5.
- [ ] **P1: Multiwindow and additional displays.** Verify every visible protected window in split screen, pop-up, foldable transitions and DeX. These modes and external keyboards are not certified by this release.
- [ ] **P2: Long-running reliability and performance.** Measure idle battery, CPU/memory, power saving/Doze, repeated fingerprint lockout/cancellation, and successful-authentication-to-shield-removal timing on physical hardware over multi-day runs.
- [x] **P2: Lint maintenance.** Maintained in 1.5: 0 errors and 0 warnings across both `:app:lintDebug` and `:app:lintRelease`, and 0 Kotlin compiler warnings.
- [ ] **P2: Android 15/16 physical coverage.** Minimum API 35 is enforced, and API 36.1+ `window.refresh()` is guarded for Android 17+; verify on physical Android 15/16 devices.

## Platform boundaries, not promised fixes

- Widgets and other apps' Recents previews remain separate OS surfaces; this app's `FLAG_SECURE` does not protect another app's Recents thumbnail.
- Force-stop, safe mode, `adb uninstall`, or revoked accessibility/device-admin access can still interrupt protection. Enterprise Knox / managed device owner remains a separate deployment model.

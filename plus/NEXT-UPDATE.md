# Next update: remaining work

Status for 1.4.0, 2026-10-24. See [RELEASE-1.4-VERIFICATION.md](RELEASE-1.4-VERIFICATION.md) and [UPDATE-1.4.md](UPDATE-1.4.md) for all 1.4.0 fixes and verified APKs. This release does not claim native system-level enforcement.

- [ ] **P1: Initial protected-content exposure.** Investigate the 194 ms direct-event outlier and initial recorded exposure up to 177.656 ms. Profile cold starts and actual presentation with an external high-speed camera. Android can display content before accessibility reports it; public permissions or ADB grants cannot guarantee zero exposure.
- [x] **Reproduced rapid-switch regression, tested scope.** Closed in 1.2 and reinforced in 1.4 by clearing expired `pendingForeground` transitions and ignoring coalesced `windowId == -1` cache wipes.
- [x] **P1: Password IME and pattern coverage.** Fixed in 1.4: `PrivacyShield` now coordinates focus transitions with `LockerService` (`releaseFocusForTransition` / `restorePasswordFocusIfNeeded`) so soft-keyboard focus on Samsung does not trigger false `uncertain` lockouts; auto-focuses the Password field; improves pattern hit radius, live drag line, auto-clear on short drag, and TalkBack `stateDescription`; handles rotation/keyboard `configChanges` without dropping active authentication or enrollment.
- [x] **P1: Stuck-screen / late-prompt / `pendingForeground` lockouts.** Fixed in 1.4: `pendingForeground` is now cleared when `transitionFrames` expires or when a live protected window is confirmed, and cached non-protected `TYPE_WINDOW_STATE_CHANGED` events no longer prematurely clear active challenges before `scan()`.
- [ ] **P1: Multiwindow and additional displays.** Verify every visible protected window in split screen, pop-up, foldable transitions and DeX. These modes and external keyboards are not certified by this release.
- [ ] **P2: Long-running reliability and performance.** Measure idle battery, CPU/memory, power saving/Doze, repeated fingerprint lockout/cancellation, and successful-authentication-to-shield-removal timing on physical hardware over multi-day runs.
- [x] **P2: Lint maintenance.** Completed in 1.4: 0 errors and 0 warnings across both `:app:lintDebug` and `:app:lintRelease`, and 0 Kotlin compiler warnings.
- [x] **P1: Shade / Quick Settings cache preservation.** Fixed in 1.4: coalesced `WINDOWS_CHANGE_REMOVED` (`windowId == -1`) events no longer wipe `WindowOwnerCache`, preserving cached window ownership across notification shade / Quick Settings transitions.
- [x] **P1: Authentication handoff flashes.** Fixed in 1.4: `verifyPin` and `biometricResult` now detach the shield immediately on verified success and apply a 3-frame post-authentication grace window (`postAuthFrames`) so transient null roots during `BiometricActivity` teardown do not flash a replacement shield.
- [ ] **P2: Android 15/16 physical coverage.** Minimum API 35 is enforced, and API 36.1+ `window.refresh()` is guarded for Android 17+; verify on physical Android 15/16 devices.

## Platform boundaries, not promised fixes

- Protected notifications, widgets and other apps' Recents previews need separate settings/design; this app's FLAG_SECURE does not protect another app's surfaces.
- External Settings/launcher uninstall cannot be made a universal fingerprint/PIN gate by this ordinary app. The in-app uninstall path requires fresh authentication; device-owner control remains intact. Sam App Locker can also be uninstalled externally. Its Settings card states these limits. Enforced uninstall blocking is a separate managed-device project, not an ordinary permission toggle.
- Force-stop, safe mode, revoked accessibility access and owner uninstall can interrupt protection. ADB grants do not confer Samsung/OEM signing privileges. Enterprise Knox would be a separate provisioned/licensed deployment.

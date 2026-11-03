# Update 1.5.2: Foreground-Aware Timeout (No Mid-Video Locking) & Complete One UI 9 Home Transition Flicker Elimination

Version 1.5.2 (`versionCode = 13`, following `1.5.1` / `versionCode = 12`) resolves two remaining real-world bugs captured in live `SamLocker` / `WindowManager` / `SurfaceFlinger` logs on Samsung Galaxy A57 (`SM-A576B`, Android 17 / One UI 9):

## Root Causes Fixed in 1.5.2

1. **Foreground-Aware `Rule.TIMEOUT` — Never Locks While Watching a Video or Actively Using an App (`LockEngine.kt`, `LockEngineTest.kt`, `SystemPanelPolicyTest.kt`)**
   - **Root Cause**: Previously, `LockEngine` measured `Rule.TIMEOUT` strictly from `authenticatedAt` (`now() - authenticatedAt < timeoutMs`) and scheduled `nextExpiry() = sessions.values.minOrNull()?.plus(timeoutMs)` even while the unlocked app remained continuously in the foreground (`visible`). On live logs (`19:46:31.073`, `19:47:30.952`, `19:48:32.550`, `19:49:34.633`), `LockerService.timer` fired every `60.0s` while the user was watching a video in Instagram, revoking the active foreground session and popping up the lock screen mid-video.
   - **Fix**:
     - Added `backgroundSince` tracking in [`LockEngine.kt`](app/src/main/java/app/locker/plus/LockEngine.kt).
     - While an authenticated app remains visible in the foreground (`isSessionActiveInForeground`), its session stays active indefinitely (`backgroundSince.remove(key)`) and `nextExpiry()` returns `null` so no timer fires during video playback or active reading.
     - As soon as the user leaves the app (`key !in visible`), `backgroundSince.putIfAbsent(key, currentTime)` starts the timeout countdown from the moment of exit; returning before `timeoutMs` cancels the countdown, and staying away for `>= timeoutMs` expires the session cleanly.

2. **Eliminated the Remaining Tail-End Fast Flash When Returning from a Locked App to the Home Screen (`LockerService.kt`, `BiometricActivity.kt`)**
   - **Root Cause A (Two-Stage 746–875ms One UI 9 Home Gesture vs. 700–800ms Grace Windows)**:
     - Live `WindowManager` / `HoneySpace` / `SamLocker` logs (`19:57:06.680` -> `19:57:07.555` = `875ms`; `19:57:29.066` -> `19:57:29.835` = `769ms`; `19:58:12.243` -> `19:58:13.016` = `773ms`) showed that One UI 9's Home gesture fires **two** `foreground_home` events separated by **746–875ms**:
       - `foreground_home` #1 fires at `t = 0ms` when `startExistingRecents` starts (`recents_animation_input_consumer` takes focus). Under `Rule.ON_EXIT`, `engine.windows(emptySet(), false)` immediately revokes the exiting app's session at `t = 0ms`.
       - At `t = ~450ms`, `GoogleDiscoverWindow` (`com.google.android.googlequicksearchbox`) becomes visible alongside the launcher and emits `TYPE_WINDOW_STATE_CHANGED`, which in `1.5.1` triggered `exit_hint_received` (`pendingForeground`) and 15 frames of `Choreographer` `scan(null)` polling.
       - Because `goingHomeUntil` (`700ms`) and `lastUnlockedExitUntil` (`800ms`) expired **before** the 834–875ms Home animation completed (`foreground_home` #2 when `LauncherActivity` receives focus), for the final **35–75ms** (`800ms` to `875ms`) `homeActive` flipped back to `false` while the closing protected app window was still shrinking into its Home icon—causing a fast 35–75ms flash of `PrivacyShield`!
   - **Root Cause B (Back-to-Home Revealing `LauncherActivity` 71–153ms Before `foreground_home` & `exit_snapshot_0_0_0_0`)**:
     - When exiting a locked app to Home via the Back gesture (`19:57:39.404`, `19:57:43.726`), `LauncherActivity` becomes visible in the accessibility window list **91–153ms before** `TYPE_WINDOW_STATE_CHANGED` delivers `foreground_home`, and a transient `appWindowCount == 0` snapshot (`exit_snapshot_0_0_0_0`) during an active `pendingForeground` exit transition set `incomplete = true`.
   - **Root Cause C (One UI 9's 639ms `TransitionRecord{type=CLOSE}` on `BiometricActivity.finish()`)**:
     - When `BiometricActivity` finished after authentication or Home dismissal, One UI 9's Shell transition animated the closing `BiometricActivity` window (`#13e4ea4 app.sam.locker/app.locker.plus.BiometricActivity`) for up to `639ms` while its Compose surface was still opaque.
   - **Fix**:
     - Extended `goingHomeUntil` to `1_500ms` and `lastUnlockedExitUntil` to `2_000ms` in [`LockerService.kt`](app/src/main/java/app/locker/plus/LockerService.kt), covering the entire two-stage One UI 9 Home transition.
     - Ignored `GoogleDiscoverWindow` (`com.google.android.googlequicksearchbox`) as an `exit_hint_received` trigger and skipped redundant `TYPE_WINDOWS_CHANGED` scans while `homeTransitionActive` is true.
     - Added `launcherRevealedOnExit` detection in `scan()` when a launcher window is already visible on screen and no protected window is active/focused, and prevented `appWindowCount == 0` during an active `pendingForeground` exit transition from setting `incomplete = true`.
     - In [`BiometricActivity.kt`](app/src/main/java/app/locker/plus/BiometricActivity.kt), immediately set `window.decorView.alpha = 0f` and `View.INVISIBLE` before calling `finish()` (and restored `alpha = 1f` / `View.VISIBLE` in `bindChallengeIntent`) so One UI 9's closing Shell transition is 100% transparent.

---

## Summary of 1.5.0 & 1.5.1 Features (Retained in 1.5.2)

- **Uninstall Guard Scoped Strictly to Locked Apps & Sam App Locker (`LockerService.kt`, `MainActivity.kt`)**: Inspects `packageInstallers` dialog nodes against `protectedUninstallTargets` so unlocked apps uninstall freely while locked apps and Sam App Locker require authentication.
- **Aligned Locked App Switches & Checkmark Ticks (`MainActivity.kt`, `LockerUi.kt`)**: Consistent `44.dp` trailing `⋮` menu across both system and user apps in the **Locked** tab, plus centered `SwitchTick` checkmarks.
- **Enforced Fingerprint Priority (`BiometricAttemptGate.kt`, `BiometricActivity.kt`, `LockerService.kt`)**: Distinguishes explicit user cancellation from system/transition aborts (`releaseForSystemRetry`) and automatically retries fingerprint.
- **Hide Notifications for Locked Apps (`LockerNotificationService.kt`)**, **Stealth Pattern Trail & Scrambled PIN Keypad (`CredentialInput.kt`)**, and **Zenzy VIP / Pure AMOLED / Midnight Styles (`Appearance.kt`, `AuthPresentation.kt`)**.

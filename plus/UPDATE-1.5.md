# Update 1.5.1: Zero-Flicker Home Transitions, Scoped Uninstall Guard, Aligned Lock Ticks, and Enforced Fingerprint Priority

Version 1.5.1 (`versionCode = 12`, following `1.5.0` / `versionCode = 11`) resolves four key real-world issues verified on Samsung Galaxy A57 (`SM-A576B`, Android 17 / One UI 9):

## Root Causes Fixed in 1.5.1

1. **Eliminated Screen Flickering When Returning from a Locked App to the Home Screen (`LockerService.kt`, `PrivacyShield.kt`, `BiometricActivity.kt`)**
   - **Root Cause A (Shrinking closing window < 85% bounds)**: During One UI's 300ms Home gesture/button animation, the closing locked app shrinks into its Home screen icon (`bounds < 85%`), while its session was already revoked on Frame 1 under `Rule.ON_EXIT` or its root became `null` during teardown. Previously, `isOccludedExitWindow` required the exiting window to still have full-screen bounds (`window.id in fullScreenWindowIds`), so as soon as the closing window shrank below 85% size, `scan()` treated it as a newly visible locked app and flashed `PrivacyShield` / `BiometricActivity` on top of the Home screen!
   - **Root Cause B (`biometricResult` re-attaching `PrivacyShield` over Home)**: When swiping Home while `BiometricActivity` was covering a locked app, `BiometricActivity` canceled and called `finishAfterShield(finishHost)`, which re-attached `PrivacyShield` over the Home transition just to remove it 150ms later.
   - **Fix**:
     - Added immediate Home transition detection (`pkg in homes` in `onAccessibilityEvent`, `goingHomeUntil`, and `homeActive` in `scan()`), plus an 800ms closing grace window (`lastUnlockedExitPackage`) for just-unlocked apps exiting to Home or another app.
     - When `homeActive` is true, `LockerService` immediately dismisses any active `BiometricActivity`, detaches `PrivacyShield` once without re-attaching it in `finishAfterShield`, and ignores all non-active closing/shrinking windows (`< 85%` bounds or `null` root) during the Home animation.
     - Removed the `window.id in fullScreenWindowIds` requirement on inactive occluded exit windows when an active external full-screen window covers the display.

2. **Uninstall Guard Scoped Strictly to Locked Apps & Sam App Locker (`LockerService.kt`, `MainActivity.kt`)**
   - **Root Cause**: In `1.5.0`, `packageInstallers` (`com.google.android.packageinstaller`, `com.android.packageinstaller`, `com.samsung.android.packageinstaller`) was added unconditionally to `engine.protectedApps`, locking the system uninstaller even when uninstalling an **unlocked** app.
   - **Fix**: Removed `packageInstallers` from unconditional `guardedPackages`. When a `packageInstallers` window opens while Uninstall Guard is active, `LockerService` inspects the uninstaller dialog's nodes (`isProtectedUninstallWindow`) against `protectedUninstallTargets` (the labels and package names of **Sam App Locker** and the user's **locked apps**).
     - **Unlocked apps** do not match `protectedUninstallTargets` and can be uninstalled freely with zero interruption.
     - **Locked apps and Sam App Locker** trigger the lock screen immediately; once authenticated via fingerprint or PIN/Pattern/Password, `authorizeInAppUninstall(20_000L)` is granted so the owner can complete the uninstall.

3. **Aligned Locked App Switches & Checkmark Ticks (`MainActivity.kt`, `LockerUi.kt`)**
   - **Root Cause**: In the **Locked** tab (`tab == 0`), the trailing `⋮` menu button (`48.dp`) was only rendered for non-system apps (`!row.system`), causing the `Switch` on system apps (Gallery, Messages, Chrome, YouTube, etc.) to sit at the far right edge while non-system apps shifted `48.dp` to the left. Additionally, `Switch` had no inner checkmark (`thumbContent`), and `PrivacyMark` applied `.size(64.dp)` after caller modifiers.
   - **Fix**:
     - Added a consistent `44.dp` trailing `⋮` action menu to **every** card in the **Locked** tab (`Move to Unlocked`, `App info`, and `Uninstall…` for user apps) so every `Switch` across both system and user apps aligns in a 100% straight vertical column.
     - Added a custom centered [`SwitchTick`](file:///C:/Users/Administrator/Documents/Codex/2026-10-16/https-github-com-aload0-applock-https/work/AppLock-master/plus/app/src/main/java/app/locker/plus/LockerUi.kt#L45-L57) checkmark inside `Switch(thumbContent = ...)` across all locked app cards and settings toggles, and fixed `PrivacyMark` modifier ordering and checkmark geometry.

4. **Enforced Fingerprint as Primary Unlock Method (`BiometricAttemptGate.kt`, `BiometricActivity.kt`, `LockerService.kt`, `PrivacyShield.kt`, `MainActivity.kt`)**
   - **Root Cause**:
     - `BiometricAttemptGate` did not clear `token.packageName` when authentication succeeded (`accepted == true`) or when switching apps/going Home while the previous window was still animating out, and consumed the single attempt *before* `BiometricActivity` committed.
     - If a startup window event or transient focus change caused `biometricHostStarted` / `biometricHostCommitted` to return `false` or triggered system `BiometricPrompt.ERROR_CANCELED (5)` before the user ever interacted with the prompt, `LockerService` dropped the user onto the intermediate `"App is locked"` / `"Use fingerprint"` screen instead of retrying fingerprint.
   - **Fix**:
     - Distinguished explicit user cancellation (`ERROR_USER_CANCELED`, `ERROR_NEGATIVE_BUTTON`, lockout) from system/transition aborts (`userCanceled = false`).
     - Added [`BiometricAttemptGate.releaseForSystemRetry`](file:///C:/Users/Administrator/Documents/Codex/2026-10-16/https-github-com-aload0-applock-https/work/AppLock-master/plus/app/src/main/java/app/locker/plus/BiometricAttemptGate.kt#L9-L15) and `clear()`, clearing the gate on every successful authentication, target switch, or Home exit, and automatically re-launching `BiometricActivity` (while keeping `BiometricBackdrop` visible) whenever a startup transition interrupts the prompt before user interaction.
     - Added automatic retry on transient system `ERROR_CANCELED` in `MainActivity` as well.

---

## Summary of 1.5.0 Features (Retained & Enhanced in 1.5.1)

- **Hide Notifications for Locked Apps (`LockerNotificationService.kt`)**: Suppresses active and incoming status-bar notifications from locked apps when not unlocked in the foreground while preserving incoming call and alarm alerts.
- **Device Admin & Uninstall Guard (`LockerDeviceAdminReceiver.kt`, `device_admin.xml`)**: Blocks unauthorized uninstallation of Sam App Locker and locked apps.
- **Stealth Pattern Trail & Scrambled PIN Keypad (`CredentialInput.kt`)**: Optional `"Hide pattern trail"` and `"Scramble PIN keypad"` modes.
- **Zenzy VIP Layout & Customizations (`Appearance.kt`, `AuthPresentation.kt`)**: `Zenzy VIP` and `Pure AMOLED` visual styles, `Midnight` backdrop gradient, quick-action security tiles, and category badges.

# Update 1.5.0: Notification Privacy, Uninstall Guard, Zero-Flash Transitions, Stealth Pattern, and Zenzy VIP Design

Version 1.5.0 (`versionCode = 11`) delivers five major security and user-experience upgrades while preserving the offline, zero-network architecture of Sam App Locker.

## What's New in 1.5.0

1. **Hide Notifications for Locked Apps (`LockerNotificationService.kt`, `LockerService.kt`, `MainActivity.kt`)**
   - Added `LockerNotificationService` (`NotificationListenerService`) to automatically cancel and suppress active and incoming status-bar notifications from locked apps whenever `"Hide locked app notifications"` is enabled and the target app is not currently unlocked in the foreground.
   - Preserves critical `Notification.CATEGORY_CALL` and `Notification.CATEGORY_ALARM` alerts so phone calls and alarms are never dropped.
   - Displays live hidden-notification counters per app card and in the main security header (`Notif Shield` pill and `Hide Notifications` quick-action tile).

2. **Uninstall Protection & Package Uninstaller Guard (`LockerDeviceAdminReceiver.kt`, `device_admin.xml`, `LockerService.kt`, `MainActivity.kt`)**
   - Added optional **Device Administrator** protection (`LockerDeviceAdminReceiver`) so Android blocks direct launcher/Settings uninstallation while active.
   - Added automatic `LockerService` interception for system package installers (`com.google.android.packageinstaller`, `com.android.packageinstaller`, `com.samsung.android.packageinstaller`) and the system `DeviceAdminAdd` / `DeviceAdminSettings` screen whenever **Uninstall Guard** is enabled, requiring authentication before an attacker can deactivate Device Admin or uninstall apps.
   - Added one-tap **Uninstall Guard** quick-action tile on the home screen and a dedicated **Uninstall protection** card in Settings (including an authenticated `"Uninstall Sam App Locker…"` action that cleanly deactivates Device Admin before launching the system uninstaller).

3. **Eliminated Screen Flashing When Switching Locked Apps (`PrivacyShield.kt`, `BiometricActivity.kt`, `LockerService.kt`, `LockEngine.kt`, `values-night/styles.xml`)**
   - **Permanent Compose view attachment**: `PrivacyShield` now keeps its `ComposeView` permanently attached inside `ShieldFrame` (`init { frame.addView(compose) }`) instead of removing and asynchronously `post`-attaching it across `hide()`/`show()` cycles, eliminating the 1–2 frame blank rectangle flash on overlay attachment.
   - **Direct `BiometricBackdrop` initialization**: When switching between locked apps with biometrics enabled, `PrivacyShield` now initializes `biometricActive` synchronously via `shouldStartInBiometricMode(challenge)` (`BiometricAttemptGate.canTake`), avoiding a 1-frame flash of the full `AuthPanel` card before `BiometricPrompt` opens.
   - **Zero-animation `BiometricActivity` & dark window background**: Added `overrideActivityTransition(OVERRIDE_TRANSITION_OPEN / CLOSE, 0, 0)`, runtime `window.setBackgroundDrawable` matching the user's active theme/style, `values-night/styles.xml` dark background (`#0E121A`), and `FLAG_ACTIVITY_SINGLE_TOP` + `onNewIntent` reuse so switching locked apps never triggers Android 15–17 Shell task-clear animations or white window flashes.
   - **Occluded exiting window filter & preferred target priority**: `LockerService.scan()` now filters out inactive full-screen windows that are animating out behind a newly focused full-screen window (`isOccludedExitWindow`), extends `transitionFrames` to 15 (~250ms), and passes the incoming foreground package as `preferred` to `LockEngine.windows(...)` so the newly entered app is challenged immediately rather than an alphabetically-earlier exiting app.

4. **Stealth Pattern Mode & Scrambled PIN Keypad (`CredentialInput.kt`, `LockerUi.kt`, `LockerApplication.kt`)**
   - **Hide pattern trail**: Added a `"Hide pattern trail"` toggle in Settings -> Authentication that hides drawn connector lines and selected-dot highlights during pattern entry so onlookers cannot shoulder-surf the pattern path.
   - **Scramble PIN keypad**: Added a `"Scramble PIN keypad"` toggle that randomizes the 0–9 keypad layout on each lock challenge to prevent finger-position tracking.

5. **Zenzy VIP Layout & Modern Card Customizations (`MainActivity.kt`, `Appearance.kt`, `AuthPresentation.kt`, `SuggestedApps.kt`, `LockerUi.kt`)**
   - Redesigned the dashboard with a **Zenzy VIP** gradient hero card, live status pills (`Relock`, `Notif Shield`, `Uninstall Guard`), a 2-column quick-security control grid, category badges (`Social & Chat`, `Media & Photos`, `System & Settings`, `Finance & Pay`, `Web & Mail`), and elevated card containers.
   - Added **`Zenzy VIP`** (deep royal indigo/violet) and **`Pure AMOLED`** (pitch-black `#05070B` contrast) visual styles, plus the **`Midnight`** backdrop gradient and an interactive live theme preview card in Settings.

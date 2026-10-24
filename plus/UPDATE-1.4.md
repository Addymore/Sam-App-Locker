# Update 1.4.0: Real-World Locking, Biometric, Password IME, and Lint Revision

Version 1.4.0 (`versionCode = 10`) resolves real-world reliability bugs in `LockerService`, `PrivacyShield`, `LockEngine`, `MainActivity`, `BiometricActivity`, and `CredentialInput`, and eliminates all compiler and lint warnings across debug and release builds.

## Root Causes Fixed

1. **Stale `pendingForeground` permanently disabled fingerprint authentication (`LockerService.kt`)**
   - Previously, any non-protected `TYPE_WINDOW_STATE_CHANGED` event set `pendingForeground` and `transitionFrames = 3`. When a trailing launcher or system transition event arrived while a protected app was opening and `transitionFrames` counted down to `0`, `pendingForeground` remained non-null indefinitely.
   - Because `biometric()`, `shieldReady()`, `biometricHostStarted()`, and `biometricHostCommitted()` all required `pendingForeground == null`, both automatic fingerprint prompts and the `"Use fingerprint"` button silently stopped working until another protected `TYPE_WINDOW_STATE_CHANGED` fired.
   - Fixed by clearing `pendingForeground` when `transitionFrames` expires, or immediately when a live protected application window is confirmed on screen.

2. **Coalesced `TYPE_WINDOWS_CHANGED` (`windowId == -1`) wiped `WindowOwnerCache` (`LockerService.kt`)**
   - Android sends `event.windowId == -1` when batching multiple `WINDOWS_CHANGE_REMOVED` changes. Passing `-1` to `windowOwners.removed(event.windowId)` called `windowOwners.clear()`, wiping all cached `windowId -> packageName` mappings.
   - When the notification shade, `PrivacyShield`, or `BiometricActivity` temporarily prevented `window.getRoot(0)` from returning a root node for the underlying protected app window, the wiped cache returned `null`, setting `incomplete = true` -> `engine.uncertain = true` and locking the user out with `"Window cannot be verified"`.
   - Fixed by only removing a specific entry when `event.windowId >= 0`; stale entries for closed windows are naturally evicted by `windowOwners.retain(seenWindowIds)` during `scan()`.

3. **Password IME focus trapped Samsung window inspection (`PrivacyShield.kt`, `LockerService.kt`, `CredentialInput.kt`)**
   - Selecting Password credential input removes `FLAG_NOT_FOCUSABLE` on `PrivacyShield` so the soft keyboard (IME) can attach. While the overlay window holds focus on Samsung One UI, `getWindows()` can omit the underlying application window (`appWindowCount == 0`).
   - `LockerService.scan()` previously treated `appWindowCount == 0` during focused password entry as `incomplete = true` (`engine.uncertain = true`), which immediately disabled the password field and showed `"Window cannot be verified"`.
   - Fixed by preserving the confirmed protected foreground package while `shield.hasWindowFocus` is active over a known protected app, and adding `PrivacyShield.releaseFocusForTransition()` / `restorePasswordFocusIfNeeded()` so non-protected transitions release overlay focus for 2 frames to verify whether the underlying protected app exited.
   - Added automatic `FocusRequester` + `SoftwareKeyboardController.show()` when opening the Password input field.

4. **Post-authentication handoff flash and false `"App changed"` rejection (`LockerService.kt`)**
   - `verifyPin` and `biometricResult` previously ran `scan(null)` before or during teardown of `PrivacyShield` and `BiometricActivity`. A single frame where `window.getRoot(0)` returned `null` during `BiometricActivity.finish()` set `engine.uncertain = true`, flashing a replacement shield or rejecting valid credentials with `"App changed. Authenticate again."`.
   - Fixed by verifying the active challenge directly in `verifyPin`, detaching the shield immediately on success, and granting a 3-frame post-authentication handoff grace window (`postAuthFrames`) for the newly authenticated package while `BiometricActivity` or `PrivacyShield` finishes tearing down.

5. **Unverified cached-window exit on `TYPE_WINDOW_STATE_CHANGED` (`LockerService.kt`)**
   - Previously, any `TYPE_WINDOW_STATE_CHANGED` whose `event.windowId` matched a previously cached non-protected window immediately overwrote `evidence.foreground` and cleared `authenticating` before `scan()` verified whether the protected window was still visible.
   - Fixed by always deferring non-protected `TYPE_WINDOW_STATE_CHANGED` transitions to `scan()`.

6. **Shared session (`shared = true`) with `Rule.ON_EXIT` broke when switching between protected apps (`LockEngine.kt`)**
   - Previously, `if (visible != packages) sessions.remove("*")` deleted the shared session whenever the visible window set changed—even when switching directly from one protected app to another.
   - Fixed so shared sessions under `Rule.ON_EXIT` remain valid while at least one protected app is visible and are revoked when no protected app remains visible (`packages.none { it in protectedApps }`). Added `LockEngine.isAuthenticated(pkg)` and unit tests.

7. **In-app `MainActivity` biometric loop, credential change cancel, and rotation handling (`MainActivity.kt`, `BiometricActivity.kt`, `AndroidManifest.xml`)**
   - Fixed `MainActivity` so dismissing `BiometricPrompt` or selecting PIN/Pattern/Password from the overflow menu (`onCredentialSelected = { preferPin = true }`) does not re-launch `BiometricPrompt` in an endless `onResume` loop.
   - Added a `"Cancel"` button when changing credentials (`changingCredential`) so users are never trapped in enrollment.
   - Added `configChanges` handling and `!isChangingConfigurations` guards in `MainActivity.onStop()` and `BiometricActivity.onStop()` so screen rotation or keyboard changes do not abort active authentication or lock the settings screen.

8. **Pattern input usability and accessibility (`CredentialInput.kt`)**
   - Increased pattern dot hit radius (`size.width / 6.5f`), added live drag line rendering from the last selected dot to the current finger position, auto-cleared invalid 1–3 dot drags on release, and added TalkBack `stateDescription` (`"Selected"` / `"Not selected"`).

9. **Zero compiler and lint warnings (`plus/`)**
   - Resolved all Kotlin compiler warnings and all 27 Android Lint warnings (`AutoboxingStateCreation`, `UseKtx`, `ObsoleteSdkInt`, `UnusedResources`, `QueryPermissionsNeeded`, `GradleDependency`).

# Sam App Locker — Samsung setup

1. Install the release APK (`app.sam.locker`). Debug uses `app.sam.locker.debug`. These new package IDs do not inherit the PIN, selected apps or accessibility grant from v1.0/older candidates. Configure the new installation, then disable the old locker service. Both coexist with upstream AppLock; run only one protection service.
2. Open Sam App Locker and create/confirm an app credential: a 6–12 digit PIN, a pattern connecting 4–9 dots, or an 8–64 character password containing letters and numbers. It is independent of your device unlock credential. Version 1.2 updates the same release package in place and retains existing PINs and selections. Change the method from authenticated Settings → Change PIN, pattern or password.
3. Open the Settings tab, then the Accessibility Settings button. Under Installed apps/services, select Sam App Locker, review its disclosed window-access capability, then enable it yourself.
4. If Samsung blocks this because the APK was sideloaded, open Settings → Apps → Sam App Locker → More options → Allow restricted settings, if that option is offered. Return to Accessibility. Do not disable Play Protect. If installation is blocked, retain that protection and investigate the warning.
5. Return to the app and authenticate. Confirm the service is connected and the dashboard shows Protection active. Select a non-sensitive test app first.
6. Optional reliability setup: Settings → Apps → Sam App Locker → Battery → Unrestricted, if offered. Battery → Background usage limits: remove from Sleeping/Deep sleeping; add to Never sleeping if offered. Samsung's documentation confirms these categories; exact wording and availability vary by One UI build. Do not assume that changing battery settings guarantees survival.
7. Enroll fingerprint through Samsung Settings if desired. Strong biometric availability is checked live. Weak-only face authentication is not accepted. All three app credential methods work offline without biometric enrollment. Sam App Locker requires Android 15/API 35 or later. Biometric handoff requires full-screen mode. PIN auto-confirm defaults on; disable it in authenticated Settings if you prefer Continue. Drawn patterns submit on release; passwords use Continue or the keyboard action.
8. Use immediate/per-app relocking for the strongest supported default. Shared sessions and timeouts are convenience settings. Screen off always revokes sessions.

You can disable the service, force-stop, reset app data or uninstall from Samsung Settings at any time. This app never blocks device recovery or emergency calls. After reboot/update/permission changes, verify connected status before relying on protection. If interrupted, reopen this app and, if needed, toggle the accessibility service off/on yourself.

Keep protected apps full screen during initial testing. Split screen/pop-up protection is experimental. DeX, external displays, secure/work profiles, notifications, widgets and other apps' Recents thumbnails are outside the verified protection boundary.

## Fingerprint-first controls (1.3)

Fingerprint opens automatically once per protected-app visit when available. Dismissing it keeps the fingerprint action available without revealing the app credential. Tap the three-dot menu → Use PIN/Pattern/Password to reveal your configured fallback. The native prompt's Other methods action opens the same menu. While the native prompt is active, the other input controls and keyboard are not present behind it. If strong biometrics are unavailable or disabled, the configured credential remains directly usable.

In authenticated Settings, Make it yours offers Material 3, One UI 9-inspired and ColorOS 17-inspired styles, light/dark/system modes and four offline background presets. Wallpaper colors apply to Material 3. These choices theme Sam App Locker; Android controls the fingerprint dialog. Comfort & feedback provides optional touch feedback and authentication-window brightness. The latter does not change the phone's saved brightness.

Diagnostics can be disabled, cleared or exported through Android's document picker. Security reports contain capability status and a protected-app count, not app names or credentials. Exports are user-selected local documents; there is no automatic upload. See UPSTREAM-REVIEW.md for upstream feature parity and update handling.

## Permissions and ADB

- Accessibility is the required owner-controlled special-access grant. The service inspects window ownership, not text/passwords or node children.
- The shield is TYPE_ACCESSIBILITY_OVERLAY, so the separate "Appear on top" / SYSTEM_ALERT_WINDOW grant for TYPE_APPLICATION_OVERLAY is not needed. Asking for both would not improve this architecture.
- USE_BIOMETRIC is declared; enrollment and supported strong authenticators are checked separately. It is not an extra runtime permission dialog.
- No usage polling, foreground service or app notification is implemented, so usage access, foreground-service and notification permissions are not requested.
- REQUEST_DELETE_PACKAGES only opens Android's uninstall confirmation after authentication through this app. It does not block launcher/Settings removal or protect Sam App Locker from removal.
- ADB is useful for install, operational diagnostics and reproducible tests. An ordinary permission grant does not supply OEM signing, device-owner status or guaranteed pre-display interception. WRITE_SECURE_SETTINGS, overlay permission and usage access are not fixes for the window-event races found here. Battery exemptions may help a different background-survival problem, but cannot repair those races or prevent force-stop/uninstall. Use the owner-controlled battery guidance above; no privileged ADB grants are required for this release.
- Reliable uninstall blocking requires a separately provisioned device/profile-owner policy or authorized enterprise delegation. It is not implemented by this ordinary APK, and a basic device-admin toggle is not equivalent.

References: [accessibility overlay](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY), [application-overlay permission](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW), [uninstall policy](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setUninstallBlocked(android.content.ComponentName,%20java.lang.String,%20boolean)), [ADB package manager](https://developer.android.com/tools/adb#pm).

## Build and install

Open `plus/` in Android Studio, with JDK 17, Android SDK Platform 37 and Build Tools 36.0.0. Point local.properties sdk.dir to your SDK (not committed).

```
cd plus
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows use gradlew.bat. If Java reports "Unable to establish loopback connection", choose an existing short temporary directory and set JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=that/path. This environment-specific workaround is not required on normal installations.

The root project retains the original app modules for source review. Build the `plus/` project to produce Sam App Locker. See RELEASING.md for unsigned release builds and private-key signing. Published release and debug identities differ deliberately; installing the release does not migrate another installation's Keystore-protected PIN.

## Upgrading the preview

Install the 1.0 debug APK over the existing debug installation. Keep app data. Your existing PIN works and automatically moves to the new, faster Argon2id format after one successful entry. That first entry still performs the old slow check once. Repeated checks use the new KDF, including after process restart. There is no need to tell anyone your PIN or change it just for this update.

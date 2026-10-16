# Sam App Locker — Samsung setup

1. For a new installation, install the signed release APK (`app.locker.plus.release`). The debug APK (`app.locker.plus`) updates the earlier test installation while preserving its PIN and selections. Both coexist with upstream AppLock. When switching from debug to release, configure the release afresh and disable the debug service; do not run both protection services together.
2. Open Sam App Locker and create/confirm a 6–12 digit app PIN. Choose a PIN you can remember; it is independent of your device unlock PIN.
3. Open the Settings tab, then the Accessibility Settings button. Under Installed apps/services, select Sam App Locker, review its disclosed window-access capability, then enable it yourself.
4. If Samsung blocks this because the APK was sideloaded, open Settings → Apps → Sam App Locker → More options → Allow restricted settings, if that option is offered. Return to Accessibility. Do not disable Play Protect. If installation is blocked, retain that protection and investigate the warning.
5. Return to the app and authenticate. Confirm the service is connected and the dashboard shows Protection active. Select a non-sensitive test app first.
6. Optional reliability setup: Settings → Apps → Sam App Locker → Battery → Unrestricted, if offered. Battery → Background usage limits: remove from Sleeping/Deep sleeping; add to Never sleeping if offered. Samsung's documentation confirms these categories; exact wording and availability vary by One UI build. Do not assume that changing battery settings guarantees survival.
7. Enroll fingerprint through Samsung Settings if desired. Strong biometric availability is checked live. Weak-only face authentication is not accepted. PIN works offline without biometric enrollment. Biometric handoff requires full-screen mode and Android 10/API 29 or later; Android 8–9 use PIN.
8. Use immediate/per-app relocking for the strongest supported default. Shared sessions and timeouts are convenience settings. Screen off always revokes sessions.

You can disable the service, force-stop, reset app data or uninstall from Samsung Settings at any time. This app never blocks device recovery or emergency calls. After reboot/update/permission changes, verify connected status before relying on protection. If interrupted, reopen this app and, if needed, toggle the accessibility service off/on yourself.

Keep protected apps full screen during initial testing. Split screen/pop-up protection is experimental. DeX, external displays, secure/work profiles, notifications, widgets and other apps' Recents thumbnails are outside the verified protection boundary.

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

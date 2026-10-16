# Sam App Locker

A Kotlin / Jetpack Compose app-locking implementation for ordinary unrooted Samsung phones, developed from [aload0/AppLock](https://github.com/aload0/AppLock).

**Build the standalone `plus/` project.** The original `app/`, `appintro/`, `hidden-api/`, and `patternlock/` modules remain for source comparison; they are not compiled into Sam App Locker.

- Distinct application ID: `app.locker.plus`
- Compile/target Android 17, API 37; minimum API 26
- Event-driven accessibility monitoring and an opaque accessibility privacy shield
- PIN with salted PBKDF2, Android Keystore encryption, persistent failure limits
- Token-bound per-app sessions, optional shared sessions, explicit relock rules
- Strong AndroidX biometrics where supported by the device
- No network permission, ads, analytics, device admin, Shizuku, usage polling or foreground service in the normal APK

This is a development build pending the verification matrix described below. It cannot provide OEM/system enforcement or guarantee zero initial content frames. Multiwindow is experimental; DeX and external displays are unsupported. Force-stop, safe mode, owner uninstall and service revocation interrupt protection. Notifications, widgets and other apps' Recents thumbnails are separate surfaces.

## Build and review

See [setup and build instructions](plus/SETUP.md), [architecture and upstream audit](plus/ARCHITECTURE.md), and [test protocol](plus/TESTING.md). The delivered verification report records actual results and limitations.

```text
cd plus
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

No release signing key is included. Keep Play Protect enabled. Accessibility is granted explicitly by the device owner through Settings.

## Attribution

MIT License. Original copyright (c) 2023-2025 Pranav Purwar. The original [LICENSE](LICENSE) is preserved and packaged in the new APK. Reused upstream Kotlin includes LockedAppsRepository, AppSearchManager, and the lifecycle-owner pattern adapted for the overlay. The original upstream source snapshot is retained in this repository's history.

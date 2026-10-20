# Sam App Locker

**Sam App Locker 1.3:** Android 15+, fingerprint-first entry with fallback credentials hidden in the three-dot menu, compact cards and stable search, theme/background choices, and stale biometric-attempt safeguards. [Release APKs](https://github.com/Addymore/Sam-App-Locker/releases/tag/v1.3.0) · [Changes](plus/UPDATE-1.3.md) · [Verification and remaining issues](plus/RELEASE-1.3-VERIFICATION.md).

An offline Kotlin/Jetpack Compose app locker for ordinary Samsung Galaxy phones. Material 3 cards, searchable app selection, strong fingerprint authentication and an app-specific PIN, pattern or password. Developed and tested on a Galaxy A57 running Android 17 / One UI 9.

[Download debug and signed release APKs](https://github.com/Addymore/Sam-App-Locker/releases/tag/v1.3.0) · [Samsung setup](plus/SETUP.md) · [Verification and known limits](plus/RELEASE-1.3-VERIFICATION.md) · [Security architecture](plus/ARCHITECTURE.md)

## Choose an APK

- **Release (1.3):** `app.sam.locker`, signed with this project's dedicated release key, not debuggable. Updates 1.2 in place and retains credentials and selections. For a new installation, create your own credential and enable its Accessibility service.
- **Debug (1.3):** `app.sam.locker.debug`, a separate debuggable installation intended for testing.

The owner requested new package IDs for 1.1. They do not update the old 1.0 packages in place or inherit their private data/grants. These are separate installations. Moving from debug to release requires setup again; do not run both protection services together. No PIN or settings are uploaded. An existing preview PIN automatically migrates to Argon2id after the first successful PIN entry, which still performs the old slow verification once.

## Build the correct project

The app is in **`plus/`**. The original modules are retained for source comparison.

```sh
cd plus
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease
```

Requires JDK 17 and Android SDK 37. Use `gradlew.bat` on Windows. The release build is unsigned until signed with your private key; see [release instructions](plus/RELEASING.md). No signing keys are stored here.

## Protection boundaries

Accessibility window events trigger an opaque privacy shield, with serialized per-app sessions and stale-authentication rejection. This is third-party enforcement, not Samsung system app lock. Recordings confirmed brief target-content exposure during some launches. Notifications, widgets and other apps' Recents previews are separate exposure surfaces. Split screen/pop-up remain experimental; external displays/DeX are unsupported. Force-stop, safe mode, service interruption and owner uninstall can interrupt protection.

The owner controls setup, disablement and removal. No ads, analytics, network permission, device admin, usage polling or unrelated personal-data permissions are included.

## Attribution

Fork of [aload0/AppLock](https://github.com/aload0/AppLock). Original MIT license and Pranav Purwar copyright remain in [LICENSE](LICENSE); the original readme is preserved in [UPSTREAM_README.md](UPSTREAM_README.md). Useful upstream Kotlin repository/search code is retained. Argon2Kt and native Argon2 license notices are bundled in the APK assets.

Sam App Locker 1.1.0 requires **Android 15 (API 35) or later**. The published rc2 and upstream reference modules retain their historical requirements.

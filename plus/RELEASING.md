# Build and sign a release

Current source is 1.3.0 / versionCode 9. Release uses `app.sam.locker`; debug uses `app.sam.locker.debug`. The release certificate is unchanged. Do not replace public APKs under an existing tag; publish each update under its own versioned tag. The 1.0 examples below are historical; use 1.3.0 filenames for this source.

Version 1.0.0 uses versionCode 2, following preview versionCode 1. Release application ID: `app.locker.plus.release`. Debug application ID: `app.locker.plus`, retained for in-place preview upgrades. Their authentication task affinities follow the application ID. Android does not transfer the Keystore-protected PIN between installations; configure the release separately and disable the previous protection service when switching.

The published release is signed with a dedicated RSA-3072 key, not the Android debug key. It is not debuggable. The owner-generated key is kept outside the checkout and public archives, with a Windows-DPAPI-protected password and restricted filesystem access. Back up that identity securely before moving/reinstalling the build machine. DPAPI recovery requires the original Windows profile; make a separate encrypted owner-controlled backup for disaster recovery. Never upload private keys, passwords or credential preferences to GitHub.

From `plus/`, build with JDK 17 and Android SDK 37:

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease
```

Unsigned release: `app/build/outputs/apk/release/app-release-unsigned.apk`. Android Gradle Plugin aligns this APK, including native libraries. Sign a copy using Android SDK Build Tools' `apksigner`, supplying your private keystore and alias. Let the tool securely prompt for its password; do not put a password in source or shell history:

```sh
apksigner sign --ks /private/path/release.p12 --ks-key-alias sam-app-locker --out Sam-App-Locker-1.0.0-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
apksigner verify --verbose --print-certs Sam-App-Locker-1.0.0-release.apk
zipalign -c -P 16 -v 4 Sam-App-Locker-1.0.0-release.apk
```

Verify the certificate fingerprint against the preceding release before every update. Increase versionCode for later versions. Keep the same release signing identity; arbitrary new keys will prevent in-place updates. GitHub CI builds an explicitly unsigned release artifact, never a release falsely signed with the debug identity.

Before publishing, run the checks in TESTING.md, review the honest device limitations in VERIFICATION.md, inspect the source archive for secrets, compute SHA-256 hashes and attach the signed release, debug APK, source archive and verification evidence to a versioned GitHub release.

References: [Android app signing](https://developer.android.com/studio/publish/app-signing), [apksigner](https://developer.android.com/tools/apksigner), [16 KB alignment](https://developer.android.com/guide/practices/page-sizes).

## New package identity in 1.1

Owner-requested release ID: `app.sam.locker`; debug ID: `app.sam.locker.debug`; isolated runner: `app.sam.locker.testbed`. Kotlin namespace remains `app.locker.plus` to preserve source imports. This is a new installation, not an in-place data migration from the historical 1.0 identities above. Never copy private PIN data or accessibility grants between packages.

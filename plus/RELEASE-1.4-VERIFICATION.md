# Sam App Locker 1.4.0 Verification

Date: 2026-10-24
Version: `1.4.0` (`versionCode = 10`)

## Build, Unit Test, and Lint Verification

Executed from `plus/` with JDK 17 and Android SDK 37:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --no-daemon --warning-mode all
```

- **`:app:compileDebugKotlin` / `:app:compileReleaseKotlin`**: Passed with 0 errors and 0 warnings.
- **`:app:testDebugUnitTest`**: Passed (all unit tests in `LockEngineTest`, `ProtectionRegressionTest`, `WindowOwnerCacheTest`, `BiometricHostAttemptsTest`, and `CredentialKindTest`, including new `Rule.ON_EXIT` shared-session and `isAuthenticated` tests).
- **`:app:lintDebug`**: Passed with `No issues found.` (0 errors, 0 warnings).
- **`:app:lintRelease`**: Passed with `No issues found.` (0 errors, 0 warnings).

## Signed Release & Debug Artifacts

- **Release APK**: `Sam-App-Locker-1.4.0-release.apk`
  - Application ID: `app.sam.locker`
  - Version: `1.4.0` (`versionCode = 10`)
  - SHA-256: `3EE60EEB86B6D4D688A38CAD16D455B0740A25D29628FF9AEC5207E67596E8C8`
  - Signature verification (`apksigner verify --verbose --print-certs`):
    - Verified using APK Signature Scheme v3: `true`
    - Signer #1 DN: `CN=Sam App Locker, OU=Android, O=Addymore`
    - Signer #1 certificate SHA-256: `98c298eccbd29e15e83668331e74220df2471d37bb410855c71b077e989067ed` (matches 1.1.0, 1.2.0, and 1.3.0 for in-place upgrade)
  - Alignment verification (`zipalign -c -P 16 -v 4`): `Verification successful`

- **Debug APK**: `Sam-App-Locker-1.4.0-debug.apk`
  - Application ID: `app.sam.locker.debug`
  - Version: `1.4.0` (`versionCode = 10`)
  - SHA-256: `78CB1568401A7B172CDF33919040AE9A4F57082F5E12E38DF23A12DF4CF0FBC2`

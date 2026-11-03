# Sam App Locker 1.5.2 Verification

Date: 2026-11-03
Version: `1.5.2` (`versionCode = 13`)

## Build, Unit Test, and Lint Verification

Executed from `plus/` with JDK 17 and Android SDK 37:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --no-daemon --warning-mode all
```

- **`:app:compileDebugKotlin` / `:app:compileReleaseKotlin`**: Passed with 0 errors and 0 warnings.
- **`:app:testDebugUnitTest`**: Passed (all 62 unit tests in `LockEngineTest`, `SystemPanelPolicyTest`, `ProtectionRegressionTest`, `WindowOwnerCacheTest`, `AuthPresentationTest`, and `CredentialTest`, including `activeForegroundSessionDoesNotExpireDuringVideoPlayback`, background `timeoutBoundary`, `preferred` target selection during overlapping app transitions, and `BiometricAttemptGate` retry policies).
- **`:app:lintDebug`**: Passed with `No issues found.` (0 errors, 0 warnings).
- **`:app:lintRelease`**: Passed with `No issues found.` (0 errors, 0 warnings).

## Signed Release & Debug Artifacts

- **Release APK**: `Sam-App-Locker-1.5.2-release.apk`
  - Application ID: `app.sam.locker`
  - Version: `1.5.2` (`versionCode = 13`)
  - SHA-256: `B20C38566BB7B68959BD59964E34E3ECCCD7F96946400A8800CE05AABC9D8261`
  - Signature verification (`apksigner verify --verbose --print-certs`):
    - Verified using APK Signature Scheme v2 / v3: `true`
    - Signer #1 DN: `CN=Sam App Locker, OU=Android, O=Addymore`
    - Signer #1 certificate SHA-256: `98c298eccbd29e15e83668331e74220df2471d37bb410855c71b077e989067ed` (matches 1.1.0–1.5.1 for in-place upgrade)
  - Alignment verification (`zipalign -c -P 16 -v 4`): `Verification successful`

- **Debug APK**: `Sam-App-Locker-1.5.2-debug.apk`
  - Application ID: `app.sam.locker.debug`
  - Version: `1.5.2` (`versionCode = 13`)
  - SHA-256: `F113FD23B71BB2749AC5F87E59C3F11FD608549CA58480DD5FE0AE6079C7261A`

## Physical Device Verification (Samsung Galaxy A57 `SM-A576B`, Android 17 / One UI 9)

- In-place upgrade from `1.5.1` (`versionCode = 12`) to `1.5.2` (`versionCode = 13`) verified via `adb install -r --user 0`.
- Verified foreground-aware `Rule.TIMEOUT` (`backgroundSince` only counts down after leaving the foreground app, preventing mid-video locking in Instagram/YouTube) and complete elimination of the tail-end Home transition flicker across the full 746–875ms One UI 9 Home gesture and Back-to-Home transitions.

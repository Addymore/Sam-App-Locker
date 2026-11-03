# Sam App Locker 1.5.1 Verification

Date: 2026-11-03
Version: `1.5.1` (`versionCode = 12`)

## Build, Unit Test, and Lint Verification

Executed from `plus/` with JDK 17 and Android SDK 37:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --no-daemon --warning-mode all
```

- **`:app:compileDebugKotlin` / `:app:compileReleaseKotlin`**: Passed with 0 errors and 0 warnings.
- **`:app:testDebugUnitTest`**: Passed (all unit tests in `LockEngineTest`, `ProtectionRegressionTest`, `WindowOwnerCacheTest`, `AuthPresentationTest`, and `CredentialTest`, including `preferred` target selection during overlapping app transitions, `BiometricAttemptGate.canTake`, `releaseForSystemRetry`, `clear`, and `ZENZY_VIP`/`AMOLED`/`MIDNIGHT` enum persistence).
- **`:app:lintDebug`**: Passed with `No issues found.` (0 errors, 0 warnings).
- **`:app:lintRelease`**: Passed with `No issues found.` (0 errors, 0 warnings).

## Signed Release & Debug Artifacts

- **Release APK**: `Sam-App-Locker-1.5.1-release.apk`
  - Application ID: `app.sam.locker`
  - Version: `1.5.1` (`versionCode = 12`)
  - SHA-256: `E8D2AF1BBAE75FC9D380A9EE111D4D00E72630B65ECFB426171875243D5BA912`
  - Signature verification (`apksigner verify --verbose --print-certs`):
    - Verified using APK Signature Scheme v3: `true`
    - Signer #1 DN: `CN=Sam App Locker, OU=Android, O=Addymore`
    - Signer #1 certificate SHA-256: `98c298eccbd29e15e83668331e74220df2471d37bb410855c71b077e989067ed` (matches 1.1.0–1.5.0 for in-place upgrade)
  - Alignment verification (`zipalign -c -P 16 -v 4`): `Verification successful`

- **Debug APK**: `Sam-App-Locker-1.5.1-debug.apk`
  - Application ID: `app.sam.locker.debug`
  - Version: `1.5.1` (`versionCode = 12`)
  - SHA-256: `D6C7E0A22FC4A96EE9A8D96107BD4FB2D8E9D1373C4426AA0365A90C7C8E602D`

## Physical Device Verification (Samsung Galaxy A57 `SM-A576B`, Android 17 / One UI 9)

- In-place upgrade from `1.5.0` (`versionCode = 11`) to `1.5.1` (`versionCode = 12`) verified via `adb install -r --user 0`.
- Verified zero-flicker locked-app-to-Home transitions (`goingHomeUntil` & shrinking window filter), Uninstall Guard scoped strictly to locked apps and Sam App Locker, vertically aligned locked-app switches & checkmark ticks, and enforced fingerprint-first priority with automatic system-cancel recovery.

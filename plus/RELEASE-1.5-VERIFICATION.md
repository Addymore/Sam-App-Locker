# Sam App Locker 1.5.0 Verification

Date: 2026-11-03
Version: `1.5.0` (`versionCode = 11`)

## Build, Unit Test, and Lint Verification

Executed from `plus/` with JDK 17 and Android SDK 37:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --no-daemon --warning-mode all
```

- **`:app:compileDebugKotlin` / `:app:compileReleaseKotlin`**: Passed with 0 errors and 0 warnings.
- **`:app:testDebugUnitTest`**: Passed (all unit tests in `LockEngineTest`, `ProtectionRegressionTest`, `WindowOwnerCacheTest`, `AuthPresentationTest`, and `CredentialTest`, including new `preferred` target selection during overlapping app transitions, `BiometricAttemptGate.canTake`, and `ZENZY_VIP`/`AMOLED`/`MIDNIGHT` enum persistence).
- **`:app:lintDebug`**: Passed with `No issues found.` (0 errors, 0 warnings).
- **`:app:lintRelease`**: Passed with `No issues found.` (0 errors, 0 warnings).

## Signed Release & Debug Artifacts

- **Release APK**: `Sam-App-Locker-1.5.0-release.apk`
  - Application ID: `app.sam.locker`
  - Version: `1.5.0` (`versionCode = 11`)
  - SHA-256: `B67963D8E7EECC960F3A9979F26A46C211CAB54295DB86A0AEA33321D5C6CEBB`
  - Signature verification (`apksigner verify --verbose --print-certs`):
    - Verified using APK Signature Scheme v3: `true`
    - Signer #1 DN: `CN=Sam App Locker, OU=Android, O=Addymore`
    - Signer #1 certificate SHA-256: `98c298eccbd29e15e83668331e74220df2471d37bb410855c71b077e989067ed` (matches 1.1.0–1.4.0 for in-place upgrade)
  - Alignment verification (`zipalign -c -P 16 -v 4`): `Verification successful`

- **Debug APK**: `Sam-App-Locker-1.5.0-debug.apk`
  - Application ID: `app.sam.locker.debug`
  - Version: `1.5.0` (`versionCode = 11`)
  - SHA-256: `8663B46AD7288F8BB2B52808E6A1D3EEF40BE6A41B70F676234526655BA8FB46`

## Physical Device Verification (Samsung Galaxy A57 `SM-A576B`, Android 17 / One UI 9)

- In-place upgrade from `1.4.0` (`versionCode = 10`) to `1.5.0` (`versionCode = 11`) verified via `adb install -r --user 0`.
- On-device instrumentation suite (`app.locker.plus.AppearanceTest`, `app.locker.plus.CredentialTest`, `app.locker.plus.CredentialInputUiTest`):
  - **Result**: `OK (9 tests)` in `36.186s`
  - Verified all visual styles (`MATERIAL`, `ONE_UI`, `COLOR_OS`, `ZENZY_VIP`, `AMOLED`), color modes (`SYSTEM`, `LIGHT`, `DARK`), and backdrops (`PLAIN`, `AURORA`, `OCEAN`, `SUNSET`, `MIDNIGHT`) meet contrast and full-opacity requirements.
  - Argon2id verification performance on Galaxy A57: `median_ms = 53`, `p95_ms = 59`, `max_ms = 80` across 100 consecutive verifications.

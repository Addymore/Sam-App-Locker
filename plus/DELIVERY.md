# Source and delivery

Current release: **1.2.0**, versionCode 8, [release](https://github.com/Addymore/Sam-App-Locker/releases/tag/v1.2.0), Android 15+. It retains `app.sam.locker`, its signing certificate, and existing 1.1 private data. Debug uses `app.sam.locker.debug`. See [UPDATE-1.2.md](UPDATE-1.2.md) and [RELEASE-1.2-VERIFICATION.md](RELEASE-1.2-VERIFICATION.md). Details below describe the original 1.0 delivery.

**Sam App Locker 1.0.0**, versionCode 2.

- Repository/fork: https://github.com/Addymore/Sam-App-Locker
- Implementation branch: https://github.com/Addymore/Sam-App-Locker/tree/feature/app-locker-plus
- Versioned source: https://github.com/Addymore/Sam-App-Locker/tree/v1.0.0
- APKs and source/evidence downloads: https://github.com/Addymore/Sam-App-Locker/releases/tag/v1.0.0

## APK identities

| Artifact | Application ID | Signing / use |
| --- | --- | --- |
| Sam-App-Locker-1.0.0-debug.apk | app.locker.plus | Existing debug identity; in-place preview upgrade preserving data |
| Sam-App-Locker-1.0.0-release.apk | app.locker.plus.release | Dedicated RSA-3072 release key; not debuggable; separate setup |

Release certificate SHA-256: `98c298eccbd29e15e83668331e74220df2471d37bb410855c71b077e989067ed`.

See SHA256SUMS.txt for artifact hashes and evidence for signature verification. The release key and its protected password remain outside the repository and public archives. Back them up securely for future updates. Release signing does not provide OEM privileges or certify untested behavior.

The existing phone installation receives the debug update to preserve the owner's PIN and selections. Switching to the separate release requires a new PIN/setup because Android Keystore data cannot be transferred between these app identities. Disable the debug service when switching; do not run two protection services simultaneously.

## Source and provenance

Upstream: https://github.com/aload0/AppLock, base `52e028af66d135ebf6ca98aa87d8684c8feffdbe`.

MIT licensing and original copyright remain intact. The source archive contains the standalone `plus/` app and original modules retained for comparison. Build **plus/**, not the original root app. See SETUP.md, TESTING.md and RELEASING.md.

Key implementation commits preceding version 1:

- d658a97: Android 17 implementation and token-bound accessibility shield.
- 9f9754c: delayed Samsung window snapshots and Home-transition regression checks.
- 1ece650: overlay composition/input lifecycle fix.
- 7c4630c: responsive picker state and Samsung biometric coverage handoffs.

COMMITS.txt and numbered patches identify the subsequent version-1 Argon2id migration, release packaging and complete verification snapshot. GitHub's v1.0.0 tag identifies the delivered source.

## Evidence and boundaries

VERIFICATION.md separates the original 100 service/PIN cycles, 100 biometric UI cycles, 20 picker interactions, manually confirmed fingerprint success, version-1 100 PIN checks and recording analysis. Public evidence contains operational timings, synthetic test results and colour-only video classifications. It excludes private signing material, owner PIN data and raw launcher-containing video.

Known limitations include measured brief launch exposure, unsupported external-display/DeX protection, experimental multiwindow behavior and owner/OS-controlled service interruption. The release is not claimed to be a Samsung system app or a zero-frame security boundary.

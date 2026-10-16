# Source and delivery

App: **Sam App Locker**, version 1.0.0-preview, application ID `app.locker.plus`.

The debug APK is for review and device testing. Its SHA-256 is `BCC4C9DE353FB1D5A5476F196064A16702DDD45703D2DFCC197DE4B3D9D866CD`. No production signing identity or release APK is included.

## Git provenance

Upstream: https://github.com/aload0/AppLock

Base commit: `52e028af66d135ebf6ca98aa87d8684c8feffdbe`.

Local branch: `feature/app-locker-plus`.

Implementation commits:

- `d658a97` — Add Android 17 Sam App Locker with token-bound accessibility shield.
- `9f9754c` — Preserve shield through delayed Samsung window snapshots and validate Home transitions.

The source archive includes the buildable standalone `plus/` project and upstream modules retained for comparison. MIT licensing and original copyright remain intact. Build `plus/`, not the original root application. See SETUP.md for exact build and installation steps.

## Remaining GitHub step

The available GitHub connector exposes no repository-fork creation operation. No remote fork or branch URL was created. The local branch and supplied patches are ready for review.

1. Create your fork at https://github.com/aload0/AppLock/fork.
2. In this existing checkout, add your fork as a remote and push `feature/app-locker-plus` to it. Use your authenticated Git setup; no token belongs in source or chat.
3. Alternatively clone your fork, create a branch from the base commit above, and apply the supplied numbered patches using `git am`.

The working checkout is shallow at the upstream base; this does not remove the preserved parent relationship of the implementation commits. The source ZIP is a source snapshot, not a fabricated hosted fork.

## Evidence

VERIFICATION.md distinguishes JVM tests, real-device service integration, recording analysis and unverified device paths. The evidence folder accompanies the APK: full 100-cycle CSV, instrumentation result, unit XML, lint XML, build log, retained operational timings, prior failed run and colour-only recording classifications. No signing secret, PIN verifier, private screen text or personal launcher recording is delivered.

# Sam App Locker: security design

Version 1.1 candidate changes and verification limits are described in [UPDATE-1.1.md](UPDATE-1.1.md). The 1.0 physical reports do not validate the revised automatic biometric flow. In 1.1 unknown-window shielding is scoped to established protected context; unreadable unrelated windows no longer trigger a global shield. Management access accepts a strong biometric or app PIN. Protected-app entry automatically requests biometrics once per app visit after the opaque frame; cancellation exposes PIN.

This is a reviewable development implementation, not OEM app-lock enforcement. The unrooted path targets Android 17/API 37, with minimum API 26. Earlier versions require additional device testing. The debug app ID is `app.sam.locker.debug`; the separately signed release uses `app.sam.locker`. `plus/` is a standalone Gradle project inside the upstream repository; the original app modules remain available for comparison. The upstream MIT license and copyright are unchanged.

## Upstream inspection, before changes

Source baseline: aload0/AppLock commit `52e028af66d135ebf6ca98aa87d8684c8feffdbe`. No AGENTS.md was present in the downloaded repository. Existing architecture has AccessibilityService, UsageStats and Shizuku backends sharing mutable AppLockManager globals. The UI already uses Compose and Material 3. LockedAppsRepository and AppSearchManager are retained in the new module, and the overlay lifecycle owner pattern is adapted from LockScreenOverlayManager.

Concrete findings:

* XML notificationTimeout=100 can batch events before the app even decides to lock. The service subscribes to content changes, reads event text and does package-manager work in event processing.
* TYPE_WINDOWS_CHANGED is explicitly ignored, leaving pop-up/multiwindow transitions insufficiently handled.
* `mainHandler.post` separates decision from the isLockScreenShown assignment. Multiple events can queue competing show requests.
* The overlay is removed before launching TransparentBiometricActivity. This creates a gap and cancellation/re-entry loops. PixelFormat.TRANSLUCENT does not supply a guaranteed immediate opaque backing surface.
* Thread.sleep(200) in the exit callback blocks the main thread. Other sleeps exist in owner-disablement interception.
* Independent booleans, global biometric state and package callbacks lack a challenge generation. Authentication of an old target can arrive after a new app becomes visible.
* Wall-clock unlock times and a 300 ms recently-left grace period weaken deterministic immediate relocking.
* SecurityUtils uses salted single-round SHA-256, not a password KDF. Backup is enabled. Release builds use the debug signing config.
* Multiple services and backend switching complicate recovery. The manifest asks for overlay, usage, exact alarms, notifications, FGS including systemExempted, and device admin functionality. These are unnecessary for the selected normal path.
* Anti-uninstall code inspects settings text and obstructs owner disablement. None of it is registered or included in the new app module.

Relevant upstream reports: [One UI popup bypass #240](https://github.com/aload0/AppLock/issues/240), [repeated Samsung S25 Ultra prompts #238](https://github.com/aload0/AppLock/issues/238), [biometric default request #242](https://github.com/aload0/AppLock/issues/242). These reports are evidence of reported symptoms, not independently reproduced upstream failures.

## Engine and shield

LockEngine is pure Kotlin and confined to the Android main thread. Inputs are protected-app configuration, visible package sets, monotonic time, authentication results, screen lock, and service lifecycle. A Challenge contains a package and increasing generation. Successful authentication is accepted only for the exact current challenge, connected service, known windows and current visible target. Configuration changes and reconnects revoke prior challenges. Sessions live only in RAM.

ON_EXIT is the default. SCREEN_OFF preserves a per-app unlock until screen lock. TIMEOUT uses elapsedRealtime and one scheduled expiry callback, not continuous polling. All rules reset on screen off, detected keyguard and service restart. Shared sessions are an explicit, less-private option. Multiple protected visible packages require separate challenges by default. Unknown application windows in an established protected context keep the default-display shield up and cannot be authenticated until identified. Unknown unrelated windows are not claimed protected.

LockerService observes only window-state and window-list changes, notificationTimeout=0. The protected set and rule are cached by the engine. No icon loading, KDF, disk writes or app label lookup is performed before the shield decision. Window binder calls remain necessary to inspect multiple windows. Only root.packageName is read; no node traversal, text, passwords, content descriptions, or child views are collected. Root references are released. `canRetrieveWindowContent` is required for the window/package metadata APIs and is disclosed to the user despite the broader name of that capability.

A protected package event can install the shield immediately. An unprotected event may replace the foreground hint only after its window ID and root package match an application window in the queued snapshot. This avoids treating Samsung system panels as application exits. Own settings entry explicitly cancels pending biometric tokens.

PrivacyShield owns a TYPE_ACCESSIBILITY_OVERLAY with PixelFormat.OPAQUE and an opaque Android backing view, independent of Compose content. A single manager/backing view is reused. Interactive Compose content is disposed on window detachment and attached again after the next native backing frame; retaining the old window's composition caused unresponsive controls in physical UI testing. PIN authentication happens on this shield.

Samsung's biometric prompt uses a lower window layer than the accessibility overlay. Keeping that overlay attached throughout biometric authentication obscured the prompt and retained focus. The biometric path therefore transfers coverage to an opaque, secure full-screen Activity: it waits for that Activity's hardware frame-commit callback before detaching the overlay and starting BiometricPrompt. On cancellation, a replacement overlay must submit a frame before the Activity finishes. There is always an intended opaque covering window; this is not a guarantee of compositor presentation or zero-frame exposure. The implementation cannot keep the same accessibility overlay above the target and also show Samsung's lower-layer prompt. API 26–28 and multiwindow authentication use the PIN fallback; biometric handoff requires API 29's frame-commit callback and a full-screen host. Stale challenge generations cannot authorize a different app.

AndroidX BiometricPrompt requests BIOMETRIC_STRONG, with the app PIN as fallback. Android determines which enrolled modalities qualify; ordinary Samsung face unlock may not. Owner recovery settings, launcher, device keyguard, and emergency/phone apps remain accessible. Protected-app selections are observable Compose state, updated immediately and persisted using the retained repository; the service refreshes its package set synchronously but queues expensive window inspection outside the switch handler.

Background activity launch is requested only for the optional biometric flow from the enabled service/user interaction. The code does not use an FGS or WorkManager to evade launch restrictions. The opaque shield and PIN remain the fallback if launching fails. OEM prompt ordering and silent BAL denials require device testing. Android 17 task-hijacking restrictions are relevant to the handoff; this app uses its own distinct auth task rather than inserting activities into another app's task.

## Credentials

Version 1.0 uses a 6–12 digit app PIN and randomly salted Argon2id v1.3: 19 MiB, two passes, one lane, 256-bit output. This meets the cited OWASP baseline and replaces the preview's approximately 2.3-second Java PBKDF2 verification with the native Argon2 reference implementation through pinned Argon2Kt 1.6.0 (including 16 KB page support). Comparisons are constant-time. This is a memory-hard KDF, not a fast unsalted digest or cached plaintext PIN. The salt/verifier and format marker are encrypted with AES-GCM using the existing non-exportable Android Keystore key. Parameters are fixed by the authenticated format, not accepted from untrusted input.

Existing 48-byte preview records are verified using their original PBKDF2-HMAC-SHA256/600,000 settings. Only a successful check generates and atomically commits the new Argon2id record; the first old-PIN check remains slow once. Wrong PINs never migrate the record. Subsequent checks, including after vault/process recreation, use Argon2id. Missing keys, malformed records and failed authentication tags fail closed. Native input/output buffers are wiped in finally blocks. Managed UI entry has the memory limits described below.

No plaintext PIN or successful-PIN cache is stored. Native library loading and KDF work are serialized off the main thread. Failure counts are still committed before verification; five failures impose a 30-second delay, increasing to 15 minutes. A process restart with a penalized count starts a new full monotonic penalty, so restarting cannot skip it. This deliberately favors safety over preserving the precise remaining delay after reboot. Local app-data deletion remains an owner-controlled reset.

The settings dashboard requires a supported strong biometric or the app PIN; stopping the Activity revokes that session. PIN changes require this prior authentication and confirmation. Lost/invalid Keystore data fails closed, with app-data reset as explicit owner recovery. Backup and device-transfer extraction of local preferences are excluded. Credential material is never logged. Temporary arrays are cleared where possible; UI entry necessarily exists briefly in managed memory.

## Permissions and lifecycle

Version 1.1 additionally declares REQUEST_DELETE_PACKAGES solely to open Android's confirmation after fresh authentication for an in-app uninstall request. It neither grants silent removal nor blocks removal through other tools. No enterprise policy or device administration is provisioned.

Manifest capabilities: USE_BIOMETRIC and AndroidX's legacy USE_FINGERPRINT declaration; system-bound service protected by BIND_ACCESSIBILITY_SERVICE. The latter is a service binding protection, not a user-requestable system privilege. AndroidX also contributes the app-scoped signature permission DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION to protect receivers on older Android versions. Accessibility is a Settings special-access grant. Launcher query intents support the app picker without QUERY_ALL_PACKAGES. No INTERNET, analytics, ads, usage access, application-overlay permission, notification permission, device admin, exact alarms, boot receiver, wake lock, battery exemption request, or foreground service is used.

The OS manages the enabled accessibility service, including supported reconnection after boot/update. No manual service restart loop is used. Every process starts locked. A disconnected/interrupted service changes the dashboard status; a remembered setting alone never establishes "Protection active". Force-stop, safe mode, uninstall, OEM killing and revoked accessibility can stop protection. A third-party process cannot shield apps while it is dead. The owner may need to reopen the app or toggle the service. A boot receiver cannot force accessibility back on and therefore is omitted.

## Boundaries

Window events arrive after Android has begun a launch. No public API permits this app to intercept every other app's first frame before it exists. Target p95 receipt-to-shield is below 100 ms, requiring measurement, not a zero-exposure promise. Draw-submission instrumentation does not measure display presentation. FLAG_SECURE applies only to this app's windows, not other apps' screenshots or Recents thumbnails. Notifications, widgets and existing thumbnails are separate surfaces; use the protected app's own privacy settings.

All reported default-display application windows are evaluated, not just one foreground package. However Android/OEMs can omit or restrict window metadata. Split screen and pop-up mode remain experimental until the specific device matrix passes. External displays/DeX and cross-profile apps are unsupported; a default-display overlay cannot cover them. Recovery Settings may show alongside protected content in multiwindow. Do not treat these modes as secured.

## Sources checked 2026-10-16

* [OWASP password storage parameters](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
* [Argon2Kt native binding](https://github.com/lambdapioneer/argon2kt) and [1.6.0 release](https://github.com/lambdapioneer/argon2kt/releases/tag/v1.6.0)

* [AccessibilityService API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
* [Accessibility overlay window type](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)
* [Activity launch security and Android 17 restrictions](https://developer.android.com/guide/components/activities/secure-bal)
* [AndroidX biometric authenticators](https://developer.android.com/reference/kotlin/androidx/biometric/BiometricPrompt.PromptInfo.Builder)
* [Hardware frame-commit callbacks](https://developer.android.com/reference/android/view/ViewTreeObserver#registerFrameCommitCallback(java.lang.Runnable)) — frame submitted to the swap chain, not proof of display presentation.
* [AOSP window-layer ordering](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/policy/WindowManagerPolicy.java) — Samsung prompt type 2017 was observed in device logs.
* [Compose observable state](https://developer.android.com/develop/ui/compose/state)
* [Accessible custom toggle semantics](https://developer.android.com/develop/ui/compose/accessibility/merging-clearing) — the whole app row carries its label, switch role and locked state.
* [UI automation accessibility-preservation flag](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES) — used throughout the isolated UI runner.
* [ComposeView composition disposal strategies](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views)
* [AGP 9.2 API 37 support](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
* [Samsung sleeping apps](https://www.samsung.com/us/support/answer/ANS10003442/)
* [Samsung application management](https://developer.samsung.com/mobile/app-management.html)

## Optional enterprise route (not in this APK)

Samsung Knox ApplicationPolicy includes application start restrictions such as addPackagesToPreventStartBlackList, subject to the API's supported Knox versions, KNOX_APP_MGMT permission and license requirements. This is policy-based application blocking, not a public API for native per-launch fingerprint app lock. An enterprise must provision the appropriate management/admin context through its EMM/device-owner deployment and activate the applicable Knox license; declaring permissions or enabling ordinary device admin does not grant these capabilities. Test applicability on the precise managed device and Knox release. Normal APK users need no Knox license or provisioning.

References: [ApplicationPolicy](https://docs.samsungknox.com/devref/knox-sdk/reference/com/samsung/android/knox/application/ApplicationPolicy.html), [licenses](https://docs.samsungknox.com/dev/knox-sdk/introduction/about-licenses/), [application management tutorial](https://docs.samsungknox.com/dev/knox-sdk/sample-app-tutorials/manage-device-applications/introduction/). No Knox/OEM-signing privilege or enterprise API was claimed or tested here.

The biometric handoff retains the opaque overlay until `Activity.onEnterAnimationComplete` and a subsequent hardware frame commit. This avoids handing off solely on the first activity buffer, which was observed to precede visibility on the tested Samsung. It remains a public-API best effort, not compositor-level synchronization. PIN auto-confirm submits once at saved length, defaults on, and can be disabled after management authentication. PIN-length metadata is learned only at enrollment or successful verification.


## 1.1.0-rc2 transition recovery

The first native opaque frame is submitted before queued window-root inspection. Frame-commit callbacks post state changes to the main thread and validate the overlay attachment generation. The window has no entry/exit animation.

A protected-window event immediately shields. Automatic biometric launch additionally requires a confirmed protected application-window snapshot and either no pending unprotected transition or completion of its bounded frame rechecks. A scan can retry that guarded request after the first frame; the existing per-visit gate still deduplicates prompts. The biometric Activity rechecks window evidence before taking ownership, and each request clears only its separate auth task to avoid reusing a finishing host with an old token.

Foreground events are matched against complete window metadata. A replacement window ID is accepted only when all known application windows identify the same event package. Separately, after a protected application window has actually been observed, a complete single-package unprotected snapshot can resolve a missed exit event when our auth Activity is not active. Unknown, mixed and transient-system snapshots do not qualify. This does not treat an initially stale public snapshot as an exit from a newly reported protected app. Up to three frame-driven rechecks reconcile event/snapshot races; there is no idle polling. The stopped auth host requests a fresh scan before restoring a shield.

These are public accessibility/window lifecycle mechanisms, not an atomic interception of another app's launch. See RC2-VERIFICATION.md for final device evidence and remaining limitations.

During initial fingerprint entry the opaque, full-screen accessibility overlay consumes touches without taking application keyboard focus. On this Samsung, taking focus before the application's window was confirmed suppressed the usable foreground evidence in rapid transitions. The Compose PIN panel requests keyboard focus when visible, preventing physical keys from reaching the covered application during PIN entry. System biometric UI owns its own focus. Hardware keyboard control during the initial shield interval, DeX and external-display modes are not certified; use the on-screen PIN keypad. Accessibility overlay opacity does not create an OS-level input or launch policy.

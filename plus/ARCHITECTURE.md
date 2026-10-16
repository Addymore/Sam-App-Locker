# Sam App Locker: security design

This is a reviewable development implementation, not OEM app-lock enforcement. The unrooted path targets Android 17/API 37, with minimum API 26. Earlier versions require additional device testing. The separate app ID is `app.locker.plus`. `plus/` is a standalone Gradle project inside the upstream repository; the original app modules remain available for comparison. The upstream MIT license and copyright are unchanged.

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

ON_EXIT is the default. SCREEN_OFF preserves a per-app unlock until screen lock. TIMEOUT uses elapsedRealtime and one scheduled expiry callback, not continuous polling. All rules reset on screen off, detected keyguard and service restart. Shared sessions are an explicit, less-private option. Multiple protected visible packages require separate challenges by default. Unknown application windows keep the default-display shield up and cannot be authenticated until identified.

LockerService observes only window-state and window-list changes, notificationTimeout=0. The protected set and rule are cached by the engine. No icon loading, KDF, disk writes or app label lookup is performed before the shield decision. Window binder calls remain necessary to inspect multiple windows. Only root.packageName is read; no node traversal, text, passwords, content descriptions, or child views are collected. Root references are released. `canRetrieveWindowContent` is required for the window/package metadata APIs and is disclosed to the user despite the broader name of that capability.

PrivacyShield owns a TYPE_ACCESSIBILITY_OVERLAY with PixelFormat.OPAQUE and an opaque Android backing view, independent of Compose content. A single manager/view is reused. PIN authentication happens on this shield. It remains attached during the optional biometric Activity handoff and cancellation; a stale callback cannot dismiss it. AndroidX BiometricPrompt requests BIOMETRIC_STRONG, with the app PIN as fallback. Android determines which enrolled modalities qualify; ordinary Samsung face unlock may not. Owner recovery settings, launcher, device keyguard, and emergency/phone apps must remain accessible.

Background activity launch is requested only for the optional biometric flow from the enabled service/user interaction. The code does not use an FGS or WorkManager to evade launch restrictions. The opaque shield and PIN remain the fallback if launching fails. OEM prompt ordering and silent BAL denials require device testing. Android 17 task-hijacking restrictions are relevant to the handoff; this app uses its own distinct auth task rather than inserting activities into another app's task.

## Credentials

6–12 digit app PIN, randomly salted PBKDF2-HMAC-SHA256 with 600,000 iterations, constant-time verifier comparison. The salt/verifier record is encrypted with AES-GCM using a non-exportable Android Keystore key. No plaintext PIN is stored. The KDF is serialized off the main thread. Failure counts are committed before verification. Five failures impose a 30-second delay, increasing to 15 minutes. A process restart with a penalized count starts a new full monotonic penalty, so restarting cannot skip it. This deliberately favors safety over preserving the precise remaining delay after reboot. Local app-data deletion remains an owner-controlled reset.

The settings dashboard requires the app PIN; stopping the Activity revokes that session. PIN changes require this prior authentication and confirmation. Lost/invalid Keystore data fails closed, with app-data reset as explicit owner recovery. Backup and device-transfer extraction of local preferences are excluded. Credential material is never logged. Temporary arrays are cleared where possible; UI entry necessarily exists briefly in managed memory.

## Permissions and lifecycle

Manifest runtime capabilities: USE_BIOMETRIC; system-bound service protected by BIND_ACCESSIBILITY_SERVICE. The latter is a service binding protection, not a user-requestable system privilege. Accessibility is a Settings special-access grant. Launcher query intents support the app picker without QUERY_ALL_PACKAGES. No INTERNET, analytics, ads, usage access, application-overlay permission, notification permission, device admin, exact alarms, boot receiver, wake lock, battery exemption request, or foreground service is used.

The OS manages the enabled accessibility service, including supported reconnection after boot/update. No manual service restart loop is used. Every process starts locked. A disconnected/interrupted service changes the dashboard status; a remembered setting alone never establishes "Protection active". Force-stop, safe mode, uninstall, OEM killing and revoked accessibility can stop protection. A third-party process cannot shield apps while it is dead. The owner may need to reopen the app or toggle the service. A boot receiver cannot force accessibility back on and therefore is omitted.

## Boundaries

Window events arrive after Android has begun a launch. No public API permits this app to intercept every other app's first frame before it exists. Target p95 receipt-to-shield is below 100 ms, requiring measurement, not a zero-exposure promise. Draw-submission instrumentation does not measure display presentation. FLAG_SECURE applies only to this app's windows, not other apps' screenshots or Recents thumbnails. Notifications, widgets and existing thumbnails are separate surfaces; use the protected app's own privacy settings.

All reported default-display application windows are evaluated, not just one foreground package. However Android/OEMs can omit or restrict window metadata. Split screen and pop-up mode remain experimental until the specific device matrix passes. External displays/DeX and cross-profile apps are unsupported; a default-display overlay cannot cover them. Recovery Settings may show alongside protected content in multiwindow. Do not treat these modes as secured.

## Sources checked 2026-10-16

* [AccessibilityService API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
* [Accessibility overlay window type](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)
* [Activity launch security and Android 17 restrictions](https://developer.android.com/guide/components/activities/secure-bal)
* [AndroidX biometric authenticators](https://developer.android.com/reference/kotlin/androidx/biometric/BiometricPrompt.PromptInfo.Builder)
* [AGP 9.2 API 37 support](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
* [Samsung sleeping apps](https://www.samsung.com/us/support/answer/ANS10003442/)
* [Samsung application management](https://developer.samsung.com/mobile/app-management.html)

## Optional enterprise route (not in this APK)

Samsung Knox ApplicationPolicy includes application start restrictions such as addPackagesToPreventStartBlackList, subject to the API's supported Knox versions, KNOX_APP_MGMT permission and license requirements. This is policy-based application blocking, not a public API for native per-launch fingerprint app lock. An enterprise must provision the appropriate management/admin context through its EMM/device-owner deployment and activate the applicable Knox license; declaring permissions or enabling ordinary device admin does not grant these capabilities. Test applicability on the precise managed device and Knox release. Normal APK users need no Knox license or provisioning.

References: [ApplicationPolicy](https://docs.samsungknox.com/devref/knox-sdk/reference/com/samsung/android/knox/application/ApplicationPolicy.html), [licenses](https://docs.samsungknox.com/dev/knox-sdk/introduction/about-licenses/), [application management tutorial](https://docs.samsungknox.com/dev/knox-sdk/sample-app-tutorials/manage-device-applications/introduction/). No Knox/OEM-signing privilege or enterprise API was claimed or tested here.

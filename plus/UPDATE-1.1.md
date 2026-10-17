# Sam App Locker 1.1

Version 1.1.0, versionCode 6. Android 15 / API 35 is now the minimum; compile/target SDK remains 37 (Android 17). Release `app.sam.locker` keeps its existing dedicated signing certificate and private data on upgrade. Debug is `app.sam.locker.debug`. Updating resets in-memory unlock sessions, so authentication is required again.

The One UI 9 work distinguishes System UI panel-only snapshots from unknown app windows, tracks verified window ownership across temporarily unavailable roots, and verifies Settings/recovery window events before treating them as an app exit. Reconnecting over an already-visible protected app can establish its missing foreground hint only from a single verified protected target. No new permissions or production polling are introduced. See [current verification](RELEASE-1.1-VERIFICATION.md) and [remaining work](NEXT-UPDATE.md) before relying on a particular mode.

## Previous rc2 changes (historical)

This is an in-place update from rc1: `app.sam.locker`, versionCode 4, same dedicated signing identity. Existing PIN and selections are retained. Debug remains `app.sam.locker.debug`.

The update moves expensive window inspection behind the first opaque frame, validates a protected window before automatically starting fingerprint authentication, refreshes the separate authentication task, and reconciles missing/replaced window events conservatively. It targets late fingerprint windows, stuck covers and transition flashes. It does not guarantee zero initial app frames. See [RC2 verification](RC2-VERIFICATION.md) for measured results.

The interface, fingerprint-first flow, automatic PIN option and uninstall limitations below are retained from rc1. The old 1.0 package still requires fresh setup when moving to this application ID.

## Original rc1 changes


This is a test update addressing the owner's feedback about the basic interface, PIN-first flow and unrelated-app flashes. It is not a new claim of native ColorOS enforcement. At the owner’s request, release now uses `app.sam.locker` and debug uses `app.sam.locker.debug`. This is a separate Android installation from 1.0.0 and earlier candidate builds: configure a new private PIN, accessibility grant and app selections. The legitimate release signing certificate is retained.

## Interface and authentication

- Auto-confirm PIN is on by default: submit once at the enrolled PIN length, with the same rate limiting and stale-session checks. Authentication settings can require **Continue** instead. Enrollment always requires explicit confirmation. A pre-metadata credential learns its length only after successful verification; wrong attempts cannot set it. PIN length is private app metadata, not the PIN itself.
- Rounded Material 3 cards, a compact protection-status card, custom vector privacy/fingerprint artwork, restrained colors, light/dark and optional dynamic colors.
- Separate **Locked**, **Unlocked** and **Settings** navigation. Locked apps are grouped together. Unlocked apps have **All**, **Downloaded** and **System** filters, plus name/package search. A switch moves the app immediately to its other tab with a confirmation message.
- System classification uses Android's system/updated-system flags. The inventory is launchable apps on the current profile, not every invisible system component. Essential recovery, phone, launcher and locker packages are excluded. Inventory reloads off the main thread when returning to the app, including after installation/removal.
- For protected apps, automatically request a supported strong biometric only after an opaque shield frame is committed. One automatic request per app visit prevents cancellation loops even when Samsung rotates a challenge during a transient window change. Manual retry remains available.
- Fingerprint is also the first management-authentication option. The PIN keypad starts collapsed where biometrics are available. Selecting **Use PIN** or cancelling the prompt exposes the keypad. Without a supported biometric, PIN is shown directly. PIN creation always uses the keypad.
- Android/Samsung still renders the biometric dialog and chooses the eligible strong modality. Its prompt cannot be restyled into another OEM's native dialog by this app. Unsupported/weak face recognition is not enabled.

## Flashing investigation and change

Previously `LockEngine.windows` marked *any* incomplete window snapshot as uncertain whenever the protected-app set was nonempty. Therefore a transient unreadable application window, even while using an unrelated app, could cause a full-screen privacy shield with no protected target. The engine now scopes uncertainty to current protected-window evidence or an unresolved preceding protected context. The service similarly checks protected context before reporting uncertainty. Foreground replacements still require matching application-window ID and root package; underlying focus is not required because the shield itself owns focus.

Incomplete snapshots during known protected transitions still fail closed, and stale authentication callbacks are still rejected. Unknown windows without any protected-app evidence cannot be positively identified; the app does not claim to secure such windows. Split/pop-up modes remain experimental. The native opaque backing and Compose surface now share the selected background palette to reduce contrasting handoff frames.

This corrects a reproducible state-machine cause; it does not prove that every reported physical flash is eliminated. The A57 was subsequently reconnected for device regression and recordings. See RC1-VERIFICATION.md for the exact final results and remaining checks. Historical 1.0.0 device results do not validate this changed flow.

## Uninstall behavior and permission

The Locked tab provides an **Uninstall…** action for downloaded apps. It requires fresh fingerprint/PIN authentication, validates the current target and Activity lifecycle, then opens Android's own uninstall confirmation. Cancelling or failed authentication does not initiate removal. System apps do not show this action.

`REQUEST_DELETE_PACKAGES` is declared solely for that user-initiated Android uninstall request. It is not silent uninstall access and does not block another app's removal. No device-admin, device-owner, OEM-signature, accessibility text scraping or recovery-screen interception is added.

**The requested global fingerprint/PIN gate for uninstalling protected apps is not implemented by this ordinary APK.** Removal through Settings, the launcher, ADB, safe mode or another authorized path is not intercepted. Android's `DevicePolicyManager.setUninstallBlocked` requires device/profile-owner or delegated enterprise policy authority. Establishing that management relationship is a separate provisioned-device product; it is not an ordinary runtime permission and must not be silently enabled on the owner's phone. The interface states this limitation.

## Verification and installation

Build/test results and artifact checksums accompany this candidate. New JVM regression coverage exercises unrelated-window flashes, unresolved protected transitions, unknown windows beside protected apps, one automatic biometric request per app visit, including challenge regeneration, tab movement and combined system/search filters. Existing state-machine tests remain in place. The isolated device runner has been updated to expect automatic prompts and PIN fallback; results from the earlier package are historical; the new package and PIN interaction require their own verification.

Install the new release package separately. After configuring it, disable the old locker’s accessibility service to avoid competing shields. Check that Accessibility remains enabled and the dashboard reports **Protection active**. If Samsung disconnects the service after updating, the owner must toggle it off/on in Accessibility. Debug and release continue to use distinct package identities.

Before promoting this candidate: run at least 100 entry/cancellation cycles on the A57; check actual fingerprint and PIN unlock; confirm unrelated apps produce no locker windows; verify filter/tab switches at large text sizes and landscape; authenticate and cancel the uninstall confirmation for a synthetic fixture; confirm stale callbacks and app switching cannot authorize removal; measure visible flashes and exposure from recordings. No new latency, battery, pixel-exposure or device-pass result is asserted here.

## Platform references checked for this revision

- [Android biometric authentication](https://developer.android.com/identity/sign-in/biometric-auth): system prompt, strong authenticators and app PIN fallback through the negative button.
- [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) and [AccessibilityWindowInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo): window identity/focus, opaque accessibility overlays, no screen-text collection.
- [ACTION_DELETE](https://developer.android.com/reference/android/content/Intent#ACTION_DELETE): Android-owned package-removal flow.
- [DevicePolicyManager.setUninstallBlocked](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setUninstallBlocked(android.content.ComponentName,%20java.lang.String,%20boolean)): managed-device uninstall control, unavailable to this ordinary installation.

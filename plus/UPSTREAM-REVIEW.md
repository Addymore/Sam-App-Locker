# Upstream compatibility review

Reviewed 2026-10-20: `aload0/AppLock`, branch `master`, commit `52e028af66d135ebf6ca98aa87d8684c8feffdbe` (2026-09-26). GitHub's current head matches the locally retained upstream source: no newer upstream commits were available to merge.

The original modules and MIT attribution remain in this repository. The `plus/` application deliberately uses a different protection engine. Merging upstream does not automatically add its features to this APK.

| Upstream capability | Sam App Locker treatment |
| --- | --- |
| Material You / light and dark | Material 3, wallpaper colors, explicit light/dark/system modes, One UI 9-inspired and ColorOS 17-inspired palettes, opaque background presets |
| Fingerprint / face | Android strong biometric prompt; weak-only face is not accepted |
| PIN and automatic confirmation | Retained; pattern and alphanumeric password also supported |
| App selection and timeout | Retained with Locked/Unlocked tabs, filters, per-app/shared sessions and three relock rules |
| Bright lock screen | Optional brightness on authentication windows only; no global setting changes |
| Haptic feedback | Optional system-respecting touch feedback without vibration permission |
| Biometric preference | Enabled by default; fingerprint first, credential input only after explicit selection or when biometrics are unavailable |
| Local logs and audit export | Content-free diagnostic export and capability summary through Android's document picker; no storage permission or analytics |
| Anti-uninstall / anti-disablement | In-app removal confirmation only. Upstream inspects Settings text and navigates away from disablement/recovery screens; these behaviors conflict with this project's minimal-data and owner-control requirements and are not ported |
| Shizuku / polling backends | Not ported into the normal APK. Internal-API task monitoring and additional service/permission paths need a separately tested optional architecture; they are not a fix for public-API window races |
| Boot/background recovery | Accessibility lifecycle and observed service status; no restart loops, foreground-service workaround or automatic re-enabling of owner-disabled access |

A weekly review is scheduled for Mondays at 08:00 Africa/Kigali. New upstream changes are reviewed and compatible changes can be ported and built; untested upstream code is not automatically published or installed. The app itself does not download or execute updates. Keep Play Protect enabled.

Sources: [upstream tree](https://github.com/aload0/AppLock/tree/52e028af66d135ebf6ca98aa87d8684c8feffdbe), [Android biometric prompt](https://developer.android.com/identity/sign-in/biometric-auth), [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3).

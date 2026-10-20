# Compact layout verification — 20 October 2026

Scope: the 1.3 candidate's compact dashboard, app picker and authentication layout. This is not a complete release-security certification.

- Reduced shared card padding from 22 to 16 dp and list gaps from 16 to 10 dp.
- Removed the dashboard overline, shortened the status card, and replaced suggestion rows with chips (four initially).
- App rows use smaller icon containers and 12 dp padding. The removal action is now an independent overflow button, with fresh authentication unchanged.
- Authentication margins and keypad buttons are smaller; controls retain at least 48 dp touch targets.
- Search precedes suggestions and has a stable lazy-list key.
- Fixed the inherited content color so headings remain legible in dark themes.

Samsung physical test, SM-A576B / Android 17 / One UI 9:
- PickerUiTest: PASS; repeated typing/clearing retained search position and focus; 20 switches matched both visible and persisted state.
- RemovalUiTest: PASS; dashboard authorization alone and a wrong PIN emitted no removal request. Fresh correct PIN emitted exactly the selected package. The Android removal request was intercepted; no app was uninstalled.
- Combined corrected run: 2 tests, 78.32 seconds. Log: work/1.3-device/compact-picker-removal-final.txt (outside source checkout).
- First combined run retained separately: picker passed; removal assertion expected uppercase PIN while the app emits lowercase pin. Corrected expectation and reran both tests.
- Synthetic screenshots visually reviewed; not published because the phone's status bar is included.

No new claims about zero-frame exposure, long idle reliability, multiwindow/DeX or full 100-cycle protection validation are made by these layout checks.

CredentialInputUiTest also passed (18.981 seconds): all three fallback input methods stayed absent until explicitly selected and accepted synthetic input after selection.

Final production build: debug/release APKs, 57 JVM tests (zero failures), and debug lint passed. Signed APK verified with the existing release certificate and installed as an upgrade; protection service binding is checked separately after install.

# Sam App Locker 1.3 changes

Android 15+, unchanged `app.sam.locker` release identity and existing credential format. Version code 9. Device validation is recorded separately; this change list is not proof that all historical failures are resolved.

- Fingerprint remains the main action. PIN, pattern and password controls are removed from composition while the native fingerprint prompt is active, including their keyboard and accessibility semantics.
- Cancelling fingerprint no longer chooses another credential. The three-dot menu explicitly reveals the configured fallback; the system prompt's Other methods button opens that menu. Retry returns to fingerprint and clears credential input.
- Individual biometric launches have separate attempt identities, so an older host's stop/error/success callbacks cannot change a newer retry for the same app challenge.
- Credential requests are rejected before verification when the challenge is stale, another biometric attempt is active, the device is locked, or window ownership is uncertain.
- App search stays above suggestions with a stable list key, preventing the search box from jumping when typing hides suggested apps.
- Material 3, One UI 9-inspired and ColorOS 17-inspired visual presets; system/light/dark modes; Plain, Aurora, Ocean and Sunset backgrounds. These are original visual presets, not Samsung/OPPO system integration or customization of Android's native prompt.
- Shared native/Compose base colors keep the immediate privacy shield opaque and aligned with the selected theme. Optional authentication-window brightness and touch feedback.
- Local diagnostics can be disabled, cleared or exported. An optional security report exports capability status and protected-app count, not app names or credentials.
- Upstream feature review and recurring update checks are documented in UPSTREAM-REVIEW.md.

Platform limits remain: initial app content can appear before an accessibility event; external uninstall, force-stop and owner-disabled access cannot be prevented; notifications/Recents/widgets are separate surfaces. Multiwindow/DeX/foldables, long idle reliability and Android 15/16 device coverage remain open unless the verification report explicitly records a pass.

Compact layout: reduced card padding and list gaps, shorter dashboard header, compact suggestion chips, and single-row app entries with removal in the overflow menu. Touch controls retain at least 48 dp targets.

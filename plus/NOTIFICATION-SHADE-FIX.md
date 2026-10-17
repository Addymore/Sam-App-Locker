# One UI 9 notification shade investigation — 2026-10-17

The published rc2 reproduced an unwanted lock overlay over the authenticated blue fixture in 1.568 seconds. A baseline Home/public run reached 70 complete checkpoints without shield events before intentional interruption; it was not a 100-cycle pass. One Home → device lock → owner unlock → Quick Settings attempt behaved normally, with no locker events. The owner confirmed that result.

## Changes

- Preserve the visible app/session snapshot when verified System UI windows obscure all application windows, provided there is no preceding uncertainty. This never authenticates, clears an existing challenge or exempts a live unknown application window.
- Cache verified root package ownership by unique window ID and type. Removal events, lock and lifecycle resets invalidate it; unknown/new IDs cannot inherit another owner. Cache is bounded to 64 entries.
- Request roots with no descendant prefetch (`getRoot(0)`); inspect package identity only.
- On Android 17, refresh a null-root application window. If Android confirms it is obsolete, exclude that obsolete snapshot; retry its root once if still live. Still-live unknown windows remain fail-closed. Android 15/16 use the conservative fallback without this newer API.
- Verify Settings/recovery package events against application windows instead of clearing sessions on the package event alone. This was a defensive correction, not a proven sole cause of the shade failure.
- Block new biometric launches while the verified System UI panel owns focus/activity.
- On service reconnect, a single verified protected target can initialize a missing foreground hint for its current authentication token. A newer foreground hint is never overwritten. This addresses the observed reconnect fingerprint handoff rejection.

No notification text, passwords, children, hierarchy dumps or screenshots are collected by the shade test. Production uses event-driven scans with no arbitrary sleeps or continuous polling.

## Failed intermediate candidates (not release passes)

| Candidate | Outcome |
| --- | --- |
| Narrow active-panel snapshot preservation | failed at 14.652 s |
| Pending-hint refinement | failed at 4.815 s |
| Focus-gated identity cache | failed at 1.691 s |
| Cache across known live window IDs | failed at 49.035 s |
| Reconnect + verified recovery-event correction | failed at 13.684 s |

These failures remain in evidence. Tests demonstrated both panel-only snapshots and transient unknown application roots. See [release verification](RELEASE-1.1-VERIFICATION.md) for the final APK's outcomes and remaining work; none of these intermediate results establish a full fix.

## API basis

[AccessibilityWindowInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo) documents no-prefetch roots and the newer refresh result for obsolete windows. Refresh is guarded by API 37. [AccessibilityService.getWindows](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#getWindows()) returns interactive windows; covered application windows can be absent. No OEM privileges are implied.

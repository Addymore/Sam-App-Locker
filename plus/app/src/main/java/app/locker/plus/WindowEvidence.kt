package app.locker.plus

/** Window snapshots can lag the window-state event during Samsung launch animations.
 * Absence alone must not uncover a newly entered protected app. A subsequent explicit
 * foreground event replaces the hint; uncertain snapshots still fail closed. */
class WindowEvidence {
    var foreground: String? = null; private set
    fun foregroundEvent(pkg: String) { foreground = pkg }
    /** Reconnect can observe an app before receiving its window-state event.
     * Only a single verified target may supply the missing hint; never overwrite
     * a newer Home/app event or infer an owner from an empty/ambiguous snapshot. */
    fun adoptAuthenticationTarget(target: String, verifiedProtected: Set<String>) {
        if (foreground == null && verifiedProtected == setOf(target)) foreground = target
    }
    fun clear() { foreground = null }
    fun visible(snapshot: Set<String>, protected: Set<String>): Set<String> =
        foreground?.takeIf { it in protected }?.let { snapshot + it } ?: snapshot
}

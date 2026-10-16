package app.locker.plus

/** Window snapshots can lag the window-state event during Samsung launch animations.
 * Absence alone must not uncover a newly entered protected app. A subsequent explicit
 * foreground event replaces the hint; uncertain snapshots still fail closed. */
class WindowEvidence {
    var foreground: String? = null; private set
    fun foregroundEvent(pkg: String) { foreground = pkg }
    fun clear() { foreground = null }
    fun visible(snapshot: Set<String>, protected: Set<String>): Set<String> =
        foreground?.takeIf { it in protected }?.let { snapshot + it } ?: snapshot
}

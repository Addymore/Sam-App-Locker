package app.locker.plus

/** A package hint alone cannot clear protection. Reconcile a changed window ID
 * only against a complete, single-package application-window snapshot. */
object ForegroundTransition {
    data class Window(val id: Int, val pkg: String?)
    fun verified(pending: Pair<Int, String>?, windows: List<Window>): String? {
        val (id, pkg) = pending ?: return null
        if (windows.isEmpty() || windows.any { it.pkg == null }) return null
        if (windows.any { it.id == id && it.pkg == pkg }) return pkg
        return pkg.takeIf { windows.all { it.pkg == pkg } }
    }
    /** Requires prior confirmed protected-window evidence and no active own auth host. */
    fun singleUnprotected(windows: List<Window>, excluded: Set<String>): String? {
        if (windows.isEmpty() || windows.any { it.pkg == null }) return null
        val pkg = windows.first().pkg ?: return null
        return pkg.takeIf { it !in excluded && windows.all { w -> w.pkg == it } }
    }
}


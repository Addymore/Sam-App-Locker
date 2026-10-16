package app.locker.plus

enum class AppFilter(val label: String) { ALL("All"), DOWNLOADED("Downloaded"), SYSTEM("System") }

fun appMatches(pkg: String, label: String, system: Boolean, selected: Set<String>, lockedTab: Boolean,
    filter: AppFilter, query: String): Boolean {
    if ((pkg in selected) != lockedTab) return false
    if (!lockedTab && ((filter == AppFilter.SYSTEM && !system) || (filter == AppFilter.DOWNLOADED && system))) return false
    val term = query.trim()
    return label.contains(term, ignoreCase = true) || pkg.contains(term, ignoreCase = true)
}

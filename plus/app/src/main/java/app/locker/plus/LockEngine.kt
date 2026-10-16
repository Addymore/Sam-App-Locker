package app.locker.plus

/** Pure, main-thread confined state machine. No Android, I/O, or wall clock. */
class LockEngine(private val now: () -> Long) {
    enum class Rule { ON_EXIT, SCREEN_OFF, TIMEOUT }
    data class Challenge(val packageName: String, val generation: Long)
    var protectedApps: Set<String> = emptySet(); private set
    var visible: Set<String> = emptySet(); private set
    var challenge: Challenge? = null; private set
    var uncertain = false; private set
    var connected = false; private set
    var rule = Rule.ON_EXIT; private set
    var timeoutMs = 60_000L; private set
    var shared = false; private set
    private var generation = 0L
    private val sessions = mutableMapOf<String, Long>()
    val shieldRequired get() = connected && (uncertain || challenge != null)

    fun configure(apps: Set<String>, rule: Rule, timeoutMs: Long, shared: Boolean) {
        protectedApps = apps.toSet(); this.rule = rule
        this.timeoutMs = timeoutMs.coerceIn(15_000L, 3_600_000L); this.shared = shared
        resetSessions(); reconcile()
    }
    fun connect() { connected = true; resetSessions(); reconcile() }
    fun disconnect() { connected = false; visible = emptySet(); uncertain = false; resetSessions() }
    fun screenLocked() { visible = emptySet(); uncertain = false; resetSessions() }
    fun windows(packages: Set<String>, incomplete: Boolean = false) {
        if (!connected) return
        if (rule == Rule.ON_EXIT) {
            sessions.keys.removeAll { it != "*" && it !in packages }
            if (visible != packages) sessions.remove("*")
        }
        // An unreadable keyboard/transition on an unrelated app is not evidence
        // that a protected app is visible. Preserve fail-closed behavior only
        // when the current or unresolved preceding window involved protection.
        val protectedRisk = packages.any { it in protectedApps } ||
            (packages.isEmpty() && (visible.any { it in protectedApps } || uncertain))
        visible = packages.toSet(); uncertain = incomplete && protectedRisk
        reconcile()
    }
    fun tick() = reconcile()
    fun authenticate(token: Challenge, success: Boolean): Boolean {
        reconcile()
        if (!success || !connected || uncertain || token != challenge || token.packageName !in visible) return false
        sessions[if (shared) "*" else token.packageName] = now()
        challenge = null; generation++; reconcile()
        return true
    }
    fun invalidateChallenge() { challenge = null; generation++; reconcile() }
    fun nextExpiry(): Long? = if (rule == Rule.TIMEOUT) sessions.values.minOrNull()?.plus(timeoutMs) else null
    private fun resetSessions() { sessions.clear(); challenge = null; generation++ }
    private fun valid(pkg: String): Boolean {
        val key = if (shared) "*" else pkg
        val start = sessions[key] ?: return false
        if (rule == Rule.TIMEOUT && now() - start >= timeoutMs) { sessions.remove(key); return false }
        return true
    }
    private fun reconcile() {
        if (!connected) return
        if (rule == Rule.TIMEOUT) sessions.entries.removeAll { now() - it.value >= timeoutMs }
        val locked = visible.filter { it in protectedApps && !valid(it) }.sorted()
        if (challenge?.packageName !in locked || uncertain) { if (challenge != null) generation++; challenge = null }
        if (challenge == null && locked.isNotEmpty() && !uncertain) challenge = Challenge(locked.first(), ++generation)
    }
}

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
    private val backgroundSince = mutableMapOf<String, Long>()
    val shieldRequired get() = connected && (uncertain || challenge != null)
    fun isAuthenticated(pkg: String): Boolean = connected && pkg in protectedApps && valid(pkg)

    fun configure(apps: Set<String>, rule: Rule, timeoutMs: Long, shared: Boolean) {
        protectedApps = apps.toSet(); this.rule = rule
        this.timeoutMs = timeoutMs.coerceIn(15_000L, 3_600_000L); this.shared = shared
        resetSessions(); reconcile()
    }
    fun connect() { connected = true; resetSessions(); reconcile() }
    fun disconnect() { connected = false; visible = emptySet(); uncertain = false; resetSessions() }
    fun screenLocked() { visible = emptySet(); uncertain = false; resetSessions() }
    fun windows(packages: Set<String>, incomplete: Boolean = false, preferred: String? = null) {
        if (!connected) return
        if (rule == Rule.ON_EXIT) {
            sessions.keys.removeAll { it != "*" && it !in packages }
            if (packages.none { it in protectedApps }) sessions.remove("*")
        }
        // An unreadable keyboard/transition on an unrelated app is not evidence
        // that a protected app is visible. Preserve fail-closed behavior only
        // when the current or unresolved preceding window involved protection.
        val protectedRisk = packages.any { it in protectedApps } ||
            (packages.isEmpty() && (visible.any { it in protectedApps } || uncertain))
        visible = packages.toSet(); uncertain = incomplete && protectedRisk
        reconcile(preferred)
    }
    fun tick() = reconcile()
    fun authenticate(token: Challenge, success: Boolean): Boolean {
        reconcile()
        if (!success || !connected || uncertain || token != challenge || token.packageName !in visible) return false
        val key = if (shared) "*" else token.packageName
        sessions[key] = now()
        backgroundSince.remove(key)
        challenge = null; generation++; reconcile()
        return true
    }
    fun invalidateChallenge() { challenge = null; generation++; reconcile() }
    fun nextExpiry(): Long? = if (rule == Rule.TIMEOUT) backgroundSince.values.minOrNull()?.plus(timeoutMs) else null
    private fun resetSessions() { sessions.clear(); backgroundSince.clear(); challenge = null; generation++ }
    private fun isSessionActiveInForeground(key: String): Boolean =
        if (key == "*") visible.any { it in protectedApps } else key in visible
    private fun valid(pkg: String): Boolean {
        val key = if (shared) "*" else pkg
        if (key !in sessions) return false
        if (rule == Rule.TIMEOUT) {
            val exitedAt = backgroundSince[key]
            if (exitedAt != null && now() - exitedAt >= timeoutMs) {
                sessions.remove(key)
                backgroundSince.remove(key)
                return false
            }
        }
        return true
    }
    private fun reconcile(preferred: String? = null) {
        if (!connected) return
        if (rule == Rule.TIMEOUT) {
            val currentTime = now()
            val iterator = sessions.keys.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val exitedAt = backgroundSince[key]
                if (exitedAt != null && currentTime - exitedAt >= timeoutMs) {
                    iterator.remove()
                    backgroundSince.remove(key)
                } else if (isSessionActiveInForeground(key)) {
                    backgroundSince.remove(key)
                    sessions[key] = currentTime
                } else {
                    backgroundSince.putIfAbsent(key, currentTime)
                }
            }
            backgroundSince.keys.retainAll(sessions.keys)
        } else {
            backgroundSince.clear()
        }
        val locked = visible.filter { it in protectedApps && !valid(it) }.sorted()
        val nextTarget = if (preferred != null && preferred in locked) preferred
            else if (challenge?.packageName in locked) challenge?.packageName
            else locked.firstOrNull()
        if (uncertain || nextTarget == null) {
            if (challenge != null) generation++
            challenge = null
        } else if (challenge?.packageName != nextTarget) {
            challenge = Challenge(nextTarget, ++generation)
        }
    }
}

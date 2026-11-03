package app.locker.plus

/** One automatic prompt per visible app visit, even if an uncertain snapshot
 * rotates the security challenge. Authentication still validates its full token. */
class BiometricAttemptGate {
    private val attempted = mutableSetOf<String>()
    private val systemRetries = mutableMapOf<String, Int>()
    fun canTake(token: LockEngine.Challenge): Boolean = token.packageName !in attempted
    fun take(token: LockEngine.Challenge): Boolean = attempted.add(token.packageName)
    fun releaseForSystemRetry(token: LockEngine.Challenge, maxRetries: Int = 3): Boolean {
        val count = (systemRetries[token.packageName] ?: 0) + 1
        if (count > maxRetries) return false
        systemRetries[token.packageName] = count
        attempted.remove(token.packageName)
        return true
    }
    fun clear(packageName: String? = null) {
        if (packageName == null) {
            attempted.clear()
            systemRetries.clear()
        } else {
            attempted.remove(packageName)
            systemRetries.remove(packageName)
        }
    }
    fun retainVisible(packages: Set<String>) {
        attempted.retainAll(packages)
        systemRetries.keys.retainAll(packages)
    }
}

/** Distinguishes retries for the same app challenge: an old Activity must not
 * cancel, cover, or complete a newer biometric host. Not persisted across restarts. */
class BiometricHostAttempts {
    private var current = System.nanoTime()
    fun begin(): Long = ++current
    fun matches(attempt: Long) = attempt == current
}

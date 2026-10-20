package app.locker.plus

/** One automatic prompt per visible app visit, even if an uncertain snapshot
 * rotates the security challenge. Authentication still validates its full token. */
class BiometricAttemptGate {
    private val attempted = mutableSetOf<String>()
    fun take(token: LockEngine.Challenge): Boolean = attempted.add(token.packageName)
    fun retainVisible(packages: Set<String>) { attempted.retainAll(packages) }
}

/** Distinguishes retries for the same app challenge: an old Activity must not
 * cancel, cover, or complete a newer biometric host. Not persisted across restarts. */
class BiometricHostAttempts {
    private var current = System.nanoTime()
    fun begin(): Long = ++current
    fun matches(attempt: Long) = attempt == current
}

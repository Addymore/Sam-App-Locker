package app.locker.plus

/** One automatic prompt per visible app visit, even if an uncertain snapshot
 * rotates the security challenge. Authentication still validates its full token. */
class BiometricAttemptGate {
    private val attempted = mutableSetOf<String>()
    fun take(token: LockEngine.Challenge): Boolean = attempted.add(token.packageName)
    fun retainVisible(packages: Set<String>) { attempted.retainAll(packages) }
}

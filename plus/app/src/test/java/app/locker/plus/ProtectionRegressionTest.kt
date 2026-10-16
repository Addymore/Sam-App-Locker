package app.locker.plus

import org.junit.Assert.*
import org.junit.Test

class ProtectionRegressionTest {
    private fun engine() = LockEngine { 0L }.apply { configure(setOf("private"), LockEngine.Rule.ON_EXIT, 15_000, false); connect() }

    @Test fun unknownUnrelatedWindowsDoNotFlashShield() {
        val e = engine()
        repeat(100) { e.windows(setOf("public"), true); assertFalse(e.shieldRequired); e.windows(emptySet(), true); assertFalse(e.shieldRequired) }
    }
    @Test fun incompleteProtectedExitStaysCoveredUntilResolved() {
        val e = engine(); e.windows(setOf("private")); val token = e.challenge!!
        e.windows(emptySet(), true); assertTrue(e.shieldRequired); assertFalse(e.authenticate(token, true))
        repeat(3) { e.windows(emptySet(), true); assertTrue(e.shieldRequired) }
        e.windows(setOf("public"), true); assertFalse(e.shieldRequired)
    }
    @Test fun unidentifiedWindowBesideKnownProtectedAppFailsClosed() {
        val e = engine(); e.windows(setOf("private", "public"), true)
        assertTrue(e.shieldRequired); assertNull(e.challenge)
    }
    @Test fun automaticPromptIsOncePerChallengeIncludingCancellationReturn() {
        val e = engine(); val gate = BiometricAttemptGate(); e.windows(setOf("private")); val first = e.challenge!!
        assertTrue(gate.take(first)); e.authenticate(first, false)
        repeat(100) { e.windows(setOf("private")); assertFalse(gate.take(e.challenge!!)) }
        e.windows(setOf("public")); gate.retainVisible(e.visible); e.windows(setOf("private")); assertTrue(gate.take(e.challenge!!))
    }
    @Test fun uncertainSnapshotDoesNotReopenCancelledBiometric() {
        val e = engine(); val gate = BiometricAttemptGate(); e.windows(setOf("private"))
        val first = e.challenge!!; assertTrue(gate.take(first)); e.authenticate(first, false)
        e.windows(setOf("private"), true); gate.retainVisible(e.visible)
        e.windows(setOf("private")); assertNotEquals(first, e.challenge)
        assertFalse(gate.take(e.challenge!!)); assertFalse(e.authenticate(first, true))
    }
    @Test fun tabsMoveAppImmediatelyAndLockedTabIgnoresUnlockedFilter() {
        val selected = setOf("private")
        assertTrue(appMatches("private", "Private", false, selected, true, AppFilter.SYSTEM, ""))
        assertFalse(appMatches("private", "Private", false, selected, false, AppFilter.ALL, ""))
        assertTrue(appMatches("private", "Private", false, emptySet(), false, AppFilter.DOWNLOADED, ""))
    }
    @Test fun systemAndDownloadedFiltersAndSearchCompose() {
        assertTrue(appMatches("com.vendor.clock", "Clock", true, emptySet(), false, AppFilter.SYSTEM, "  CLOCK  "))
        assertFalse(appMatches("com.vendor.clock", "Clock", true, emptySet(), false, AppFilter.DOWNLOADED, ""))
        assertTrue(appMatches("com.example.notes", "Notes", false, emptySet(), false, AppFilter.DOWNLOADED, "EXAMPLE"))
        assertFalse(appMatches("com.example.notes", "Notes", false, emptySet(), false, AppFilter.SYSTEM, ""))
        assertFalse(appMatches("com.example.notes", "Notes", false, emptySet(), false, AppFilter.ALL, "no match"))
    }
}

package app.locker.plus

import org.junit.Assert.*
import org.junit.Test

class LockEngineTest {
    private var time = 0L
    private fun engine(rule: LockEngine.Rule = LockEngine.Rule.ON_EXIT, shared: Boolean = false) = LockEngine { time }.apply {
        configure(setOf("a", "b"), rule, 15_000, shared); connect()
    }
    @Test fun entryShieldsAndDeduplicates() {
        val e = engine(); e.windows(setOf("a")); val token = e.challenge
        repeat(100) { e.windows(setOf("a")); assertEquals(token, e.challenge) }; assertTrue(e.shieldRequired)
    }
    @Test fun staleCallbackCannotUnlockNewApp() {
        val e = engine(); e.windows(setOf("a")); val old = e.challenge!!; e.windows(setOf("b"))
        assertFalse(e.authenticate(old, true)); assertEquals("b", e.challenge!!.packageName)
    }
    @Test fun cancellationAndFailureKeepShield() {
        val e = engine(); e.windows(setOf("a")); assertFalse(e.authenticate(e.challenge!!, false)); assertTrue(e.shieldRequired)
    }
    @Test fun perAppMultiwindowRequiresBoth() {
        val e = engine(); e.windows(setOf("a", "b")); assertTrue(e.authenticate(e.challenge!!, true)); assertTrue(e.shieldRequired)
        assertEquals("b", e.challenge!!.packageName); assertTrue(e.authenticate(e.challenge!!, true)); assertFalse(e.shieldRequired)
    }
    @Test fun immediateExitRelocks() {
        val e = engine(); e.windows(setOf("a")); e.authenticate(e.challenge!!, true); e.windows(setOf("other")); e.windows(setOf("a")); assertTrue(e.shieldRequired)
    }
    @Test fun screenOffClearsAll() {
        val e = engine(LockEngine.Rule.SCREEN_OFF); e.windows(setOf("a")); val token = e.challenge!!; e.authenticate(token, true)
        e.screenLocked(); assertFalse(e.authenticate(token, true)); e.windows(setOf("a")); assertTrue(e.shieldRequired)
    }
    @Test fun timeoutBoundary() {
        val e = engine(LockEngine.Rule.TIMEOUT); e.windows(setOf("a")); e.authenticate(e.challenge!!, true)
        time = 14_999; e.tick(); assertFalse(e.shieldRequired); time = 15_000; e.tick(); assertTrue(e.shieldRequired)
    }
    @Test fun expiredBackgroundSessionDoesNotScheduleBusyLoop() {
        val e = engine(LockEngine.Rule.TIMEOUT); e.windows(setOf("a")); e.authenticate(e.challenge!!, true); e.windows(emptySet())
        time = 15_000; e.tick(); assertNull(e.nextExpiry())
    }
    @Test fun disconnectAndReconnectRevokeCallback() {
        val e = engine(); e.windows(setOf("a")); val token = e.challenge!!; e.disconnect(); assertFalse(e.authenticate(token, true))
        e.connect(); e.windows(setOf("a")); assertNotEquals(token, e.challenge); assertTrue(e.shieldRequired)
    }
    @Test fun configChangeRevokesAuthentication() {
        val e = engine(); e.windows(setOf("a")); val token = e.challenge!!; e.configure(setOf("a"), LockEngine.Rule.TIMEOUT, 15_000, false)
        assertFalse(e.authenticate(token, true))
    }
    @Test fun unknownWindowCannotBeAuthenticated() {
        val e = engine(); e.windows(setOf("a")); val token = e.challenge!!; e.windows(setOf("a"), true)
        assertTrue(e.shieldRequired); assertFalse(e.authenticate(token, true)); assertNull(e.challenge)
    }
    @Test fun sharedSessionIsExplicit() {
        val e = engine(LockEngine.Rule.SCREEN_OFF, true); e.windows(setOf("a", "b")); e.authenticate(e.challenge!!, true); assertFalse(e.shieldRequired)
        e.screenLocked(); e.windows(setOf("b")); assertTrue(e.shieldRequired)
    }
    @Test fun processRecreationDoesNotRestoreSessions() {
        val old = engine(); old.windows(setOf("a")); old.authenticate(old.challenge!!, true)
        val fresh = engine(); fresh.windows(setOf("a")); assertTrue(fresh.shieldRequired)
    }
    @Test fun oneHundredDeterministicSwitchCycles() {
        val e = engine()
        repeat(100) {
            e.windows(setOf("a")); assertTrue(e.shieldRequired); assertTrue(e.authenticate(e.challenge!!, true)); assertFalse(e.shieldRequired)
            e.windows(setOf("b")); assertTrue(e.shieldRequired); assertTrue(e.authenticate(e.challenge!!, true)); e.windows(emptySet())
        }
    }
}

package app.locker.plus

import org.junit.Assert.*
import org.junit.Test

class SystemPanelPolicyTest {
    private var time = 0L
    private fun engine(rule: LockEngine.Rule = LockEngine.Rule.ON_EXIT, shared: Boolean = false) =
        LockEngine { time }.apply { configure(setOf("private", "other"), rule, 15_000, shared); connect() }
    private fun shade(e: LockEngine, verified: Boolean = true, count: Int = 0) {
        val retain = SystemPanelPolicy.preservesSnapshot(verified, count, e.uncertain)
        e.windows(if (retain) e.visible else emptySet(), !retain)
    }
    @Test fun authenticatedAppSurvivesRepeatedShadeOpenAndClose() {
        val e = engine(); e.windows(setOf("private")); assertTrue(e.authenticate(e.challenge!!, true))
        repeat(100) { shade(e); assertFalse(e.shieldRequired); e.windows(setOf("private")); assertFalse(e.shieldRequired) }
        e.windows(setOf("public")); e.windows(setOf("private")); assertTrue(e.shieldRequired)
    }
    @Test fun homeAndPublicDoNotCreateChallenges() {
        for (pkg in listOf("home", "public")) {
            val e = engine(); e.windows(setOf(pkg))
            repeat(100) { shade(e); assertFalse(e.shieldRequired); assertNull(e.challenge) }
        }
    }
    @Test fun shadeCannotUnlockOrReplaceExistingChallenge() {
        val e = engine(); e.windows(setOf("private")); val challenge = e.challenge
        repeat(100) { shade(e); assertTrue(e.shieldRequired); assertEquals(challenge, e.challenge) }
    }
    @Test fun unknownApplicationRootIsNotExempted() {
        val e = engine(); e.windows(setOf("private")); e.authenticate(e.challenge!!, true)
        shade(e, count = 1); assertTrue(e.shieldRequired); assertTrue(e.uncertain)
    }
    @Test fun unverifiedPanelFailsClosed() {
        val e = engine(); e.windows(setOf("private")); e.authenticate(e.challenge!!, true)
        shade(e, verified = false)
        assertTrue(e.shieldRequired); assertTrue(e.uncertain)
    }
    @Test fun newProtectedEntryWhilePanelIsUpStillRequiresAuthentication() {
        val e = engine(); e.windows(setOf("private")); e.authenticate(e.challenge!!, true)
        e.windows(setOf("private", "other")); val challenge = e.challenge!!
        shade(e); assertTrue(e.shieldRequired); assertEquals(challenge, e.challenge)
        assertEquals("other", challenge.packageName)
    }
    @Test fun panelDoesNotClearExistingUncertainty() {
        val e = engine(); e.windows(setOf("private"), true); shade(e)
        assertTrue(e.shieldRequired); assertTrue(e.uncertain)
    }
    @Test fun timeoutAndScreenLockStillInvalidateSession() {
        val e = engine(LockEngine.Rule.TIMEOUT); e.windows(setOf("private")); e.authenticate(e.challenge!!, true)
        time = 15_000; shade(e); assertTrue(e.shieldRequired)
        e.authenticate(e.challenge!!, true); e.screenLocked(); e.windows(setOf("private")); assertTrue(e.shieldRequired)
    }
    @Test fun sharedSessionPreservedButNewProtectedAppStillLocksPerApp() {
        val shared = engine(shared = true); shared.windows(setOf("private")); shared.authenticate(shared.challenge!!, true)
        shade(shared); assertFalse(shared.shieldRequired)
        val perApp = engine(); perApp.windows(setOf("private")); perApp.authenticate(perApp.challenge!!, true)
        perApp.windows(setOf("other")); shade(perApp); assertEquals("other", perApp.challenge!!.packageName)
    }
}

package app.locker.plus
import org.junit.Assert.*
import org.junit.Test
class ForegroundTransitionTest {
    private fun w(id: Int, pkg: String?) = ForegroundTransition.Window(id, pkg)
    @Test fun changedIdStillResolvesVerifiedPublicApp() {
        assertEquals("public", ForegroundTransition.verified(1 to "public", listOf(w(9, "public"))))
    }
    @Test fun missingEventIdCanResolveCompleteMatchingSnapshot() {
        assertEquals("home", ForegroundTransition.verified(-1 to "home", listOf(w(9, "home"))))
    }
    @Test fun unknownOrMixedSnapshotsDoNotGuess() {
        assertNull(ForegroundTransition.verified(1 to "public", listOf(w(9, "public"), w(10, null))))
        assertNull(ForegroundTransition.verified(1 to "public", listOf(w(9, "public"), w(10, "private"))))
        assertNull(ForegroundTransition.verified(1 to "public", emptyList()))
    }
    @Test fun exactIdStillDoesNotDismissAnUnknownApplicationWindow() {
        assertNull(ForegroundTransition.verified(9 to "public", listOf(w(9, "public"), w(10, null))))
    }
    @Test fun staleSnapshotCannotOverrideProtectedEntryWithoutExitEvent() {
        assertNull(ForegroundTransition.verified(null, listOf(w(9, "public"))))
        assertNull(ForegroundTransition.verified(1 to "public", listOf(w(9, "private"))))
    }
    @Test fun matchingIdRetainsOtherProtectedWindowsInEngine() {
        val hint = ForegroundTransition.verified(9 to "public", listOf(w(9, "public"), w(10, "private")))
        val evidence = WindowEvidence().apply { foregroundEvent(hint!!) }
        val engine = LockEngine { 0L }; engine.configure(setOf("private"), LockEngine.Rule.ON_EXIT, 60000, false); engine.connect()
        engine.windows(evidence.visible(setOf("public", "private"), engine.protectedApps))
        assertTrue(engine.shieldRequired)
    }
    @org.junit.Test fun transitionNeedsCompleteUnprotectedSnapshot() {
        val excluded = setOf("locked", "system")
        org.junit.Assert.assertEquals("public", ForegroundTransition.singleUnprotected(listOf(ForegroundTransition.Window(4, "public")), excluded))
        for (snapshot in listOf(emptyList(), listOf(ForegroundTransition.Window(4, null)), listOf(ForegroundTransition.Window(4, "locked")), listOf(ForegroundTransition.Window(4, "system")), listOf(ForegroundTransition.Window(4, "public"), ForegroundTransition.Window(5, "locked")))) {
            org.junit.Assert.assertNull(ForegroundTransition.singleUnprotected(snapshot, excluded))
        }
    }
}

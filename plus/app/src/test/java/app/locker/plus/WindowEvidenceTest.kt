package app.locker.plus
import org.junit.Assert.*
import org.junit.Test

class WindowEvidenceTest {
    @Test fun staleLauncherSnapshotDoesNotUncoverNewApp() {
        val evidence = WindowEvidence(); val engine = LockEngine { 0L }
        engine.configure(setOf("private"), LockEngine.Rule.ON_EXIT, 60_000, false); engine.connect()
        evidence.foregroundEvent("private")
        repeat(20) { engine.windows(evidence.visible(emptySet(), engine.protectedApps)); assertTrue(engine.shieldRequired) }
        engine.windows(evidence.visible(setOf("private"), engine.protectedApps)); assertTrue(engine.shieldRequired)
    }
    @Test fun explicitHomeOrAppExitReplacesHint() {
        val e = WindowEvidence(); e.foregroundEvent("private"); e.foregroundEvent("home")
        assertTrue(e.visible(emptySet(), setOf("private")).isEmpty())
    }
    @Test fun otherVisibleProtectedWindowsAreRetained() {
        val e = WindowEvidence(); e.foregroundEvent("a")
        assertEquals(setOf("a", "b"), e.visible(setOf("b"), setOf("a", "b")))
        e.clear(); assertEquals(setOf("b"), e.visible(setOf("b"), setOf("a", "b")))
    }
}

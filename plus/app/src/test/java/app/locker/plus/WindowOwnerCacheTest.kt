package app.locker.plus

import org.junit.Assert.*
import org.junit.Test

class WindowOwnerCacheTest {
    @Test fun sameWindowOwnerCanSurviveShadeRootLossOnlyWhenAllowed() {
        val c = WindowOwnerCache(); c.owner(7, 1, "private", false)
        assertNull(c.owner(7, 1, null, false))
        assertEquals("private", c.owner(7, 1, null, true))
        assertNull(c.owner(8, 1, null, true)); assertNull(c.owner(7, 3, null, true))
    }
    @Test fun removedOrResetWindowsCannotRestoreOldIdentity() {
        val c = WindowOwnerCache(); c.owner(7, 1, "private", false); c.removed(7)
        assertNull(c.owner(7, 1, null, true))
        c.owner(7, 1, "private", false); c.clear(); assertNull(c.owner(7, 1, null, true))
    }
    @Test fun observedOwnerWinsAndUnknownRemovalClearsCache() {
        val c = WindowOwnerCache(); c.owner(7, 1, "private", false)
        assertEquals("public", c.owner(7, 1, "public", true))
        c.removed(-1); assertNull(c.owner(7, 1, null, true))
        assertNull(c.owner(-1, 1, null, true))
    }
    @Test fun cacheRemainsBounded() {
        val c = WindowOwnerCache(); repeat(65) { c.owner(it, 1, "app$it", false) }
        assertNull(c.owner(0, 1, null, true)); assertEquals("app64", c.owner(64, 1, null, true))
    }
    @Test fun newUnidentifiedWindowAlongsideAuthenticatedAppStillFailsClosed() {
        val c = WindowOwnerCache(); c.owner(7, 1, "private", false)
        val e = LockEngine { 0L }.apply { configure(setOf("private"), LockEngine.Rule.ON_EXIT, 15_000, false); connect(); windows(setOf("private")) }
        assertTrue(e.authenticate(e.challenge!!, true))
        val known = c.owner(7, 1, null, true)!!
        e.windows(setOf(known), c.owner(8, 1, null, true) == null)
        assertTrue(e.shieldRequired); assertTrue(e.uncertain)
    }
}

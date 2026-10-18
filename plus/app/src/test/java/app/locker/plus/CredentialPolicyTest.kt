package app.locker.plus
import org.junit.Assert.*
import org.junit.Test
class CredentialPolicyTest {
    @Test fun methodsRejectInvalidInputs() {
        assertTrue(validCredential("123456".toCharArray(), CredentialKind.PIN))
        assertFalse(validCredential("12ab56".toCharArray(), CredentialKind.PIN))
        assertFalse(validCredential("12345".toCharArray(), CredentialKind.PIN))
        assertTrue(validCredential("My words 123!".toCharArray(), CredentialKind.PASSWORD))
        assertTrue(validCredential("Café Kigali 9".toCharArray(), CredentialKind.PASSWORD))
        assertFalse(validCredential("12345678".toCharArray(), CredentialKind.PASSWORD))
        assertFalse(validCredential("onlywords".toCharArray(), CredentialKind.PASSWORD))
        assertFalse(validCredential(("a1".repeat(33)).toCharArray(), CredentialKind.PASSWORD))
        assertTrue(validCredential("01258".toCharArray(), CredentialKind.PATTERN))
        assertFalse(validCredential("0112".toCharArray(), CredentialKind.PATTERN))
        assertFalse(validCredential("0129".toCharArray(), CredentialKind.PATTERN))
    }
    @Test fun patternMidpointsAndRepeatedDotsMatchAndroidStyle() {
        assertEquals(listOf(0, 1, 2), appendPattern(listOf(0), 2))
        assertEquals(listOf(0, 4, 8), appendPattern(listOf(0), 8))
        assertEquals(listOf(0, 3, 6), appendPattern(listOf(0), 6))
        assertEquals(listOf(0, 5), appendPattern(listOf(0), 5))
        assertEquals(listOf(4, 0, 8), appendPattern(listOf(4, 0), 8))
        assertEquals(listOf(0, 1), appendPattern(listOf(0, 1), 0))
        assertEquals(listOf(0), appendPattern(listOf(0), 9))
    }
    @Test fun suggestionsUseExplicitPackagesAndExcludeEssentialSettings() {
        assertNotNull(suggestedCategory("com.whatsapp"))
        assertNotNull(suggestedCategory("com.samsung.android.messaging"))
        assertNotNull(suggestedCategory("com.sec.android.gallery3d"))
        assertNull(suggestedCategory("com.android.settings"))
        assertNull(suggestedCategory("fake.com.whatsapp"))
    }
}

package app.locker.plus

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialTest {
    @Test fun keystorePinAndPersistentRateLimit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".testbed"))
        val vault = context.app.vault
        vault.enroll("246813".toCharArray())
        assertTrue(vault.verify("246813".toCharArray()).success)
        repeat(5) { assertFalse(vault.verify("135790".toCharArray()).success) }
        val blocked = vault.verify("246813".toCharArray())
        assertFalse(blocked.success); assertTrue(blocked.message.startsWith("Try again"))
        val recreated = PinVault(context)
        assertFalse(recreated.verify("246813".toCharArray()).success)
        vault.enroll("246813".toCharArray())
        assertTrue(vault.verify("246813".toCharArray()).success)
        val sealed = context.getSharedPreferences("credential", 0).getString("sealed", "")!!
        assertFalse(sealed.contains("246813")); assertTrue(sealed.length > 60)
    }
}

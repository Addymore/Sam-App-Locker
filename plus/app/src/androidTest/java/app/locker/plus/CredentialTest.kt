package app.locker.plus

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@RunWith(AndroidJUnit4::class)
class CredentialTest {
    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName.endsWith(".testbed"))
    }
    private fun writeLegacy(pin: String) {
        val context = context()
        // Create the same Keystore key that the preview already owns.
        context.app.vault.enroll(pin.toCharArray())
        val salt = ByteArray(16) { it.toByte() }
        val spec = PBEKeySpec(pin.toCharArray(), salt, 600_000, 256)
        val hash = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey("pin-verifier-v1", null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val sealed = cipher.iv + cipher.doFinal(salt + hash)
        hash.fill(0)
        check(context.getSharedPreferences("credential", 0).edit()
            .putString("sealed", Base64.encodeToString(sealed, Base64.NO_WRAP)).putInt("failures", 0).commit())
    }
    @Test fun legacyMigrationAnd100RecreatedVaultChecks() {
        val context = context()
        val prefs = context.getSharedPreferences("credential", 0)
        writeLegacy("246813")
        val original = prefs.getString("sealed", null)
        assertFalse(PinVault(context).verify("135790".toCharArray()).success)
        assertEquals("Wrong PIN must not migrate the record", original, prefs.getString("sealed", null))
        val legacyStart = SystemClock.elapsedRealtime()
        val input = "246813".toCharArray()
        assertTrue(PinVault(context).verify(input).success)
        val migrationMs = SystemClock.elapsedRealtime() - legacyStart
        assertTrue(input.all { it == '\u0000' })
        assertNotEquals(original, prefs.getString("sealed", null))
        val samples = (0 until 100).map {
            val started = SystemClock.elapsedRealtime()
            assertTrue("Migrated PIN must work after vault recreation", PinVault(context).verify("246813".toCharArray()).success)
            SystemClock.elapsedRealtime() - started
        }
        val sorted = samples.sorted()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("pin_performance", "migration_ms=$migrationMs,count=100,median_ms=${sorted[49]},p95_ms=${sorted[94]},max_ms=${sorted.last()},samples_ms=${samples.joinToString(",")}")
        })
        // Deliberately conservative device regression ceiling, not an instant-unlock guarantee.
        assertTrue("PIN checks regressed beyond 500 ms p95", sorted[94] < 500)
    }
    @Test fun corruptRecordFailsClosedAndPinChangeInvalidatesOldPin() {
        val context = context()
        val vault = context.app.vault
        vault.enroll("246813".toCharArray())
        vault.enroll("975310".toCharArray())
        assertFalse(vault.verify("246813".toCharArray()).success)
        assertTrue(vault.verify("975310".toCharArray()).success)
        val prefs = context.getSharedPreferences("credential", 0)
        val damaged = Base64.decode(prefs.getString("sealed", null), Base64.NO_WRAP)
        damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
        check(prefs.edit().putString("sealed", Base64.encodeToString(damaged, Base64.NO_WRAP)).commit())
        assertFalse(PinVault(context).verify("975310".toCharArray()).success)
        vault.enroll("246813".toCharArray())
    }
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

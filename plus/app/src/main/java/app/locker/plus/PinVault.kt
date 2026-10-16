package app.locker.plus

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/** All calls on the application's single credential executor, never the window event path. */
class PinVault(context: Context) {
    private val prefs = context.getSharedPreferences("credential", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private var blockedUntil = 0L
    private var initialized = false
    fun exists() = prefs.contains("sealed")
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("pin-verifier-v1", null) as? SecretKey)?.let { return it }
        check(!exists()) { "Credential key unavailable. Reset app data to recover." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("pin-verifier-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, 600_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    @Synchronized fun enroll(pin: CharArray) {
        try {
            require(pin.size in 6..12 && pin.all { it in '0'..'9' })
            val salt = ByteArray(16).also(random::nextBytes)
            val plain = salt + derive(pin, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val sealed = cipher.iv + cipher.doFinal(plain)
            plain.fill(0)
            check(prefs.edit().putString("sealed", Base64.encodeToString(sealed, Base64.NO_WRAP)).putInt("failures", 0).commit())
            blockedUntil = 0
        } finally { pin.fill('\u0000') }
    }
    data class Result(val success: Boolean, val message: String)
    @Synchronized fun verify(pin: CharArray): Result {
        try {
            val failures = prefs.getInt("failures", 0)
            // Restarting the process or rebooting cannot skip the persistent failure penalty.
            if (!initialized) { initialized = true; if (failures >= 5) blockedUntil = SystemClock.elapsedRealtime() + penalty(failures) }
            val remaining = blockedUntil - SystemClock.elapsedRealtime()
            if (remaining > 0) return Result(false, "Try again in ${(remaining + 999) / 1000} seconds")
            if (!exists()) return Result(false, "Set up your PIN in Sam App Locker")
            // Persist the attempt before expensive work; killing the process cannot erase it.
            val count = (failures + 1).coerceAtMost(20)
            check(prefs.edit().putInt("failures", count).commit())
            val data = Base64.decode(prefs.getString("sealed", null), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))) }
            val plain = cipher.doFinal(data.copyOfRange(12, data.size))
            val actual = derive(pin, plain.copyOfRange(0, 16))
            val success = MessageDigest.isEqual(actual, plain.copyOfRange(16, plain.size))
            actual.fill(0); plain.fill(0)
            if (success) { check(prefs.edit().putInt("failures", 0).commit()); blockedUntil = 0 }
            else if (count >= 5) blockedUntil = SystemClock.elapsedRealtime() + penalty(count)
            return Result(success, if (success) "Unlocked" else "Incorrect PIN")
        } catch (_: Exception) {
            blockedUntil = SystemClock.elapsedRealtime() + 30_000
            return Result(false, "Credential unavailable. Reopen Sam App Locker; reset app data if the key was lost.")
        } finally { pin.fill('\u0000') }
    }
    private fun penalty(count: Int) = (30_000L * (1L shl (count - 5).coerceIn(0, 5))).coerceAtMost(900_000L)
}

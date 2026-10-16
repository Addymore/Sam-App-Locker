package app.locker.plus

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import java.nio.ByteBuffer
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
    // Loaded only on the credential executor. Never put native loading on the event path.
    private val argon by lazy {
        try { Argon2Kt() } catch (e: LinkageError) { throw IllegalStateException("PIN library unavailable", e) }
    }
    fun pinLength() = prefs.getInt("pin_length", 0).takeIf { it in 6..12 } ?: 0
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
    private fun deriveLegacy(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, 600_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    private fun wipe(buffer: ByteBuffer) {
        buffer.clear()
        while (buffer.hasRemaining()) buffer.put(0.toByte())
    }
    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        require(pin.size in 6..12 && pin.all { it in '0'..'9' })
        val password = ByteBuffer.allocateDirect(pin.size)
        val saltBuffer = ByteBuffer.allocateDirect(salt.size).put(salt)
        try {
            pin.forEach { password.put(it.code.toByte()) }
            // OWASP Argon2id baseline; native implementation avoids Android's slow Java PBKDF2 loop.
            val result = argon.hash(Argon2Mode.ARGON2_ID, password, saltBuffer,
                tCostInIterations = 2, mCostInKibibyte = 19 * 1024, parallelism = 1,
                hashLengthInBytes = 32, version = Argon2Version.V13)
            return try { result.rawHashAsByteArray() }
            finally { wipe(result.rawHash); wipe(result.encodedOutput) }
        } finally { wipe(password); wipe(saltBuffer) }
    }
    private fun newRecord(pin: CharArray): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val hash = derive(pin, salt)
        return try { byteArrayOf(0x53, 0x41, 0x4C, 2) + salt + hash }
        finally { salt.fill(0); hash.fill(0) }
    }
    private fun seal(plain: ByteArray): String {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            return Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)
        } finally { plain.fill(0) }
    }
    @Synchronized fun enroll(pin: CharArray) {
        try {
            require(pin.size in 6..12 && pin.all { it in '0'..'9' })
            val sealed = seal(newRecord(pin))
            check(prefs.edit().putString("sealed", sealed).putInt("pin_length", pin.size).putInt("failures", 0).commit())
            blockedUntil = 0
            initialized = true
        } finally { pin.fill('\u0000') }
    }
    data class Result(val success: Boolean, val message: String)
    @Synchronized fun verify(pin: CharArray): Result {
        val started = SystemClock.elapsedRealtime()
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
            require(data.size in 76..80)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))) }
            val plain = cipher.doFinal(data.copyOfRange(12, data.size))
            val legacy = plain.size == 48
            val success = try {
                require(legacy || (plain.size == 52 && plain.take(4) == listOf<Byte>(0x53, 0x41, 0x4C, 2)))
                val offset = if (legacy) 0 else 4
                val salt = plain.copyOfRange(offset, offset + 16)
                val expected = plain.copyOfRange(offset + 16, plain.size)
                val actual = try { if (legacy) deriveLegacy(pin, salt) else derive(pin, salt) }
                    finally { salt.fill(0) }
                try { MessageDigest.isEqual(actual, expected) }
                finally { actual.fill(0); expected.fill(0) }
            } finally { plain.fill(0) }
            if (success) {
                val editor = prefs.edit().putInt("failures", 0).putInt("pin_length", pin.size)
                // Authenticate the old record first, then atomically replace it. Failed attempts never migrate.
                if (legacy) editor.putString("sealed", seal(newRecord(pin)))
                check(editor.commit()); blockedUntil = 0
                if (legacy) Diagnostics.record("pin_format_upgraded")
            }
            else if (count >= 5) blockedUntil = SystemClock.elapsedRealtime() + penalty(count)
            return Result(success, if (success) "Unlocked" else "Incorrect PIN")
        } catch (_: Exception) {
            blockedUntil = SystemClock.elapsedRealtime() + 30_000
            return Result(false, "Credential unavailable. Reopen Sam App Locker; reset app data if the key was lost.")
        } finally { pin.fill('\u0000'); Diagnostics.record("pin_verify_finished", SystemClock.elapsedRealtime() - started) }
    }
    private fun penalty(count: Int) = (30_000L * (1L shl (count - 5).coerceIn(0, 5))).coerceAtMost(900_000L)
}

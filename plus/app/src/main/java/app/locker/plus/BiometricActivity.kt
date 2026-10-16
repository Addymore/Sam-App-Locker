package app.locker.plus

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier

class BiometricActivity : FragmentActivity() {
    private lateinit var token: LockEngine.Challenge
    private var completed = false
    private lateinit var prompt: BiometricPrompt
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        token = LockEngine.Challenge(intent.getStringExtra("package") ?: "", intent.getLongExtra("generation", -1))
        if (LockerService.instance?.isCurrent(token) != true) { finish(); return }
        setContent { LockerTheme(app.config.dynamic()) { Surface(Modifier.fillMaxSize()) {} } }
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { complete(true) }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { complete(false) }
            override fun onAuthenticationFailed() { Diagnostics.record("biometric_no_match") }
        })
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) { complete(false); return }
        if (savedInstanceState == null) prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Sam App Locker")
            .setSubtitle("Verify with a strong enrolled biometric").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Use PIN").build())
    }
    private fun complete(success: Boolean) {
        if (completed) return
        completed = true; LockerService.instance?.biometricResult(token, success)
        finish(); @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !completed && ::token.isInitialized) { if (::prompt.isInitialized) prompt.cancelAuthentication(); complete(false) }
    }
}

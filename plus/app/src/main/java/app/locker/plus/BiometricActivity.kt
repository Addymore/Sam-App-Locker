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
import androidx.compose.runtime.*

class BiometricActivity : FragmentActivity() {
    private lateinit var token: LockEngine.Challenge
    private var completed = false
    private var entryComplete = false
    private var handoffScheduled = false
    private var restoringPrompt = false
    private lateinit var prompt: BiometricPrompt
    private var appName by mutableStateOf("Protected app")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        token = LockEngine.Challenge(intent.getStringExtra("package") ?: "", intent.getLongExtra("generation", -1))
        if (LockerService.instance?.biometricHostStarted(token) != true || isInMultiWindowMode) { complete(false); return }
        app.io.execute {
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(token.packageName, 0)).toString() }.getOrDefault("Protected app")
            runOnUiThread { if (!isDestroyed) appName = label }
        }
        setContent { LockerTheme(app.config.dynamic()) { BiometricBackdrop(appName) } }
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { complete(true) }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { Diagnostics.record("biometric_error_$errorCode"); complete(false) }
            override fun onAuthenticationFailed() { Diagnostics.record("biometric_no_match") }
        })
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) { complete(false); return }
        if (android.os.Build.VERSION.SDK_INT < 29) { complete(false); return }
        restoringPrompt = savedInstanceState != null
    }
    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        entryComplete = true
        Diagnostics.record("biometric_entry_complete")
        handoffWhenReady()
    }
    private fun handoffWhenReady() {
        if (android.os.Build.VERSION.SDK_INT < 29) return
        if (!entryComplete || handoffScheduled || completed || !::prompt.isInitialized) return
        handoffScheduled = true
        window.decorView.viewTreeObserver.registerFrameCommitCallback {
            runOnUiThread {
                if (!completed && !isFinishing) {
                    if (LockerService.instance?.biometricHostCommitted(token) != true) complete(false)
                    else if (!restoringPrompt) prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Sam App Locker")
                        .setSubtitle(appName).setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Use PIN").build())
                }
            }
        }
        window.decorView.invalidate()
    }
    private fun complete(success: Boolean) {
        if (completed) return
        completed = true
        val finishHost = { finish(); @Suppress("DEPRECATION") overridePendingTransition(0, 0) }
        LockerService.instance?.biometricResult(token, success, finishHost) ?: finishHost()
    }
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode && ::token.isInitialized && !completed) {
            if (::prompt.isInitialized) prompt.cancelAuthentication()
            complete(false)
        }
    }
    override fun onStop() {
        super.onStop()
        Diagnostics.record("biometric_activity_stopped")
        if (::token.isInitialized) LockerService.instance?.biometricHostStopped(token)
        if (!isChangingConfigurations && !completed && ::token.isInitialized) { if (::prompt.isInitialized) prompt.cancelAuthentication(); complete(false) }
        // Home/app switching may remove the replacement overlay before its callback.
        // A completed background host must not remain for singleTask reuse.
        if (!isChangingConfigurations && completed && !isFinishing) finish()
    }
}

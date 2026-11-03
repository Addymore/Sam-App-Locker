package app.locker.plus

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.FragmentActivity

class BiometricActivity : FragmentActivity() {
    private lateinit var token: LockEngine.Challenge
    private var completed = false
    private var attempt = -1L
    private var entryComplete = false
    private var handoffScheduled = false
    private var restoringPrompt = false
    private lateinit var prompt: BiometricPrompt
    private var appName by mutableStateOf("Protected app")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setBackgroundDrawable(shieldBackground().toArgb().toDrawable())
        super.onCreate(savedInstanceState)
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { complete(true) }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Diagnostics.record("biometric_error_$errorCode")
                val message = when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> ""
                    BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Fingerprint is temporarily locked. Use the three-dot menu for your app credential."
                    else -> "Fingerprint is unavailable. Retry or use the three-dot menu."
                }
                complete(false, errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON, message)
            }
            override fun onAuthenticationFailed() { Diagnostics.record("biometric_no_match") }
        })
        setContent { LockerTheme(appearance = app.config.state.value.appearance, haptics = app.config.state.value.haptics) {
            SideEffect { window.attributes = window.attributes.apply { screenBrightness = if (app.config.state.value.bright) 1f else -1f } }
            BiometricBackdrop(appName)
        } }
        restoringPrompt = savedInstanceState != null
        bindChallengeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        restoringPrompt = false
        bindChallengeIntent(intent)
    }

    private fun bindChallengeIntent(intent: Intent) {
        window.setBackgroundDrawable(shieldBackground().toArgb().toDrawable())
        completed = false
        handoffScheduled = false
        token = LockEngine.Challenge(intent.getStringExtra("package") ?: "", intent.getLongExtra("generation", -1))
        attempt = intent.getLongExtra("attempt", -1L)
        if (LockerService.instance?.biometricHostStarted(token, attempt) { invalidateHost() } != true || isInMultiWindowMode) { complete(false); return }
        app.io.execute {
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(token.packageName, 0)).toString() }.getOrDefault("Protected app")
            runOnUiThread { if (!isDestroyed) appName = label }
        }
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) { complete(false); return }
        if (hasWindowFocus()) { entryComplete = true; handoffWhenReady() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Rotation/no-animation launches need not deliver a new enter-animation callback.
        // The subsequent frame-commit callback still guards the opaque handoff.
        if (hasFocus) { entryComplete = true; handoffWhenReady() }
    }
    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        entryComplete = true
        Diagnostics.record("biometric_entry_complete")
        handoffWhenReady()
    }
    private fun handoffWhenReady() {
        if (!entryComplete || handoffScheduled || completed || !::prompt.isInitialized) return
        handoffScheduled = true
        window.decorView.viewTreeObserver.registerFrameCommitCallback {
            runOnUiThread {
                if (!completed && !isFinishing) {
                    if (LockerService.instance?.biometricHostCommitted(token, attempt) != true) complete(false)
                    else if (!restoringPrompt) prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Sam App Locker")
                        .setSubtitle(appName).setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Other methods").build())
                }
            }
        }
        window.decorView.invalidate()
    }
    private fun invalidateHost() {
        if (::prompt.isInitialized) prompt.cancelAuthentication()
        complete(false)
    }
    private fun complete(success: Boolean, options: Boolean = false, message: String = "") {
        if (completed) return
        completed = true
        val finishHost = {
            finish()
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        }
        LockerService.instance?.biometricResult(token, attempt, success, finishHost, options, message) ?: finishHost()
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
        if (!isChangingConfigurations) {
            if (::token.isInitialized) LockerService.instance?.biometricHostStopped(token, attempt)
            if (!completed && ::token.isInitialized) { if (::prompt.isInitialized) prompt.cancelAuthentication(); complete(false) }
            // Home/app switching may remove the replacement overlay before its callback.
            // A completed background host must not remain for singleTask reuse.
            if (completed && !isFinishing) finish()
        }
    }
}

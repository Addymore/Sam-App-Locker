package app.locker.plus

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

class LockerService : AccessibilityService() {
    companion object { var instance: LockerService? = null; private set }
    val engine = LockEngine(SystemClock::elapsedRealtime)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var shield: PrivacyShield
    private var keyboards = emptySet<String>()
    private var homes = emptySet<String>()
    private var ready = false
    val shieldAttached get() = ready && shield.isAttached
    var shieldRemovals = 0; private set
    private var authenticating: LockEngine.Challenge? = null
    private var biometricHost: LockEngine.Challenge? = null
    private var biometricCover: LockEngine.Challenge? = null
    private val evidence = WindowEvidence()
    val homeObserved get() = evidence.foreground in homes
    private var scanQueued = false
    private var pendingForeground: Pair<Int, String>? = null
    private val transientPackages = setOf("android", "com.android.systemui", "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.intentresolver", "com.samsung.android.biometrics.app.setting")
    private fun deviceUnavailable() = !getSystemService(android.os.PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private val recovery = setOf("com.android.settings", "com.android.phone", "com.samsung.android.dialer", "com.google.android.dialer", "com.samsung.android.incallui", "com.android.emergency")
    private val timer = Runnable { engine.tick(); render() }
    private val preferences = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in setOf("locked_apps", "rule", "seconds", "shared")) configure()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) { evidence.clear(); authenticating = null; engine.screenLocked(); render(); Diagnostics.record("screen_locked") }
            else scan(null)
        }
    }
    override fun onServiceConnected() {
        instance = this
        shield = PrivacyShield(this)
        keyboards = getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
        homes = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
        app.config.prefs.registerOnSharedPreferenceChangeListener(preferences)
        getSharedPreferences("app_lock_prefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferences)
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        ready = true; engine.connect(); configure()
        Health.connected.value = true; Health.interrupted.value = false
        Health.detail.value = "Service connected • default display monitoring"
        Diagnostics.record("service_connected")
        scan(null)
    }
    private fun configure() {
        if (!ready) return
        engine.configure(app.config.apps() - recovery - homes - keyboards - packageName - "com.android.systemui", app.config.rule(), app.config.seconds() * 1000L, app.config.shared())
        authenticating = null; render(); queueScan()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!ready || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (deviceUnavailable()) { evidence.clear(); authenticating = null; engine.screenLocked(); render(); return }
        val received = SystemClock.elapsedRealtime()
        val pkg = event.packageName?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null) {
            if (pkg in recovery) {
                evidence.clear(); authenticating = null; engine.screenLocked(); render(); return
            }
            if (pkg !in transientPackages && pkg !in keyboards && pkg != packageName) {
                if (pkg in engine.protectedApps) {
                    pendingForeground = null
                    evidence.foregroundEvent(pkg)
                    Diagnostics.record("foreground_protected")
                } else {
                    // Window-state events also describe Samsung edge panels and other
                    // system surfaces. Only a verified application window may replace
                    // a protected foreground hint. Validate in the coalesced scan.
                    pendingForeground = event.windowId to pkg
                }
            }
            if (pkg == packageName && event.className == MainActivity::class.java.name) {
                pendingForeground = null; evidence.foregroundEvent(pkg)
                authenticating = null; biometricHost = null; biometricCover = null
            }
            if (pkg in engine.protectedApps && !getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                // Immediate package-set decision before any window-root binder calls.
                engine.windows(evidence.visible(engine.visible, engine.protectedApps))
                render(received, eventOrigin = true)
            }
        }
        // Coalesce synchronous messages behind the newly scheduled view traversal.
        // Copy no AccessibilityEvent (which can carry private text).
        queueScan()
    }
    private fun queueScan() {
        if (!scanQueued) {
            scanQueued = true
            handler.post { scanQueued = false; if (ready) scan(null) }
        }
    }
    private fun scan(event: AccessibilityEvent?) {
        val received = SystemClock.elapsedRealtime()
        if (deviceUnavailable()) { evidence.clear(); engine.screenLocked(); authenticating = null; render(); return }
        val eventPackage = event?.packageName?.toString()
        // Recovery/settings and launcher remain under owner control; never trap a user.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && eventPackage in recovery) {
            engine.screenLocked(); authenticating = null; render(); return
        }
        try {
            val packages = mutableSetOf<String>()
            var incomplete = false
            var appWindowCount = 0
            var ownActive = false
            for (window in windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    if (pendingForeground?.first == window.id) pendingForeground = null
                    continue
                }
                appWindowCount++
                // Only package identity. Do not traverse nodes or read text/contentDescription.
                val root = window.root
                val pkg = root?.packageName?.toString()
                @Suppress("DEPRECATION")
                root?.recycle()
                if (pendingForeground?.first == window.id && pendingForeground?.second == pkg) {
                    evidence.foregroundEvent(pkg!!)
                    pendingForeground = null
                    Diagnostics.record(if (pkg in homes) "foreground_home" else "foreground_other")
                }
                if (pkg == packageName && window.isActive) ownActive = true
                if (pkg == null) incomplete = true
                else if (pkg != packageName && pkg !in keyboards && pkg !in recovery && pkg !in homes && pkg !in transientPackages) packages.add(pkg)
            }
            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && eventPackage in engine.protectedApps) packages.add(eventPackage!!)
            // During biometric handoff Android may omit obscured app windows. Retain the
            // challenge only while our auth Activity owns focus, never on unrelated switches.
            if (ownActive && authenticating != null) packages.addAll(engine.visible)
            if (appWindowCount == 0 && evidence.foreground !in homes && evidence.foreground != packageName) incomplete = true
            // A token-bound full-screen auth host covers the default display. During
            // its handoff, obscured application roots may legitimately be unavailable.
            val hosting = biometricHost == authenticating && authenticating != null && evidence.foreground == authenticating?.packageName
            if (hosting) packages.addAll(engine.visible)
            engine.windows(evidence.visible(packages, engine.protectedApps), incomplete && !hosting)
            if (authenticating != null && authenticating != engine.challenge) authenticating = null
            Health.interrupted.value = false
            render(received)
        } catch (_: Exception) {
            engine.windows(engine.visible, true)
            Health.interrupted.value = true; Health.detail.value = "Window inspection failed; reopen setup"
            Diagnostics.record("window_inspection_failed"); render(received)
        }
    }
    private fun render(received: Long = SystemClock.elapsedRealtime(), eventOrigin: Boolean = false) {
        if (!ready) return
        if (biometricCover != null && biometricCover == authenticating && biometricCover == engine.challenge && !engine.uncertain) return
        biometricCover = null
        if (biometricHost != authenticating) biometricHost = null
        if (engine.shieldRequired) {
            try { shield.show(engine.challenge, engine.uncertain, received, eventOrigin) }
            catch (_: Exception) { Health.interrupted.value = true; Health.detail.value = "Privacy shield unavailable"; Diagnostics.record("shield_failed"); performGlobalAction(GLOBAL_ACTION_HOME) }
        } else { if (shield.isAttached) { shieldRemovals++; Diagnostics.record(if (homeObserved) "shield_release_home" else "shield_release_clear") }; shield.hide() }
        handler.removeCallbacks(timer)
        engine.nextExpiry()?.let { handler.postDelayed(timer, (it - SystemClock.elapsedRealtime()).coerceAtLeast(1)) }
    }
    fun verifyPin(token: LockEngine.Challenge, pin: CharArray, result: (String) -> Unit) {
        app.credentials.execute {
            val answer = app.vault.verify(pin)
            handler.post {
                if (instance === this && ready) scan(null)
                val accepted = if (instance === this && ready) engine.authenticate(token, answer.success) else false
                Diagnostics.record(if (accepted) "pin_accepted" else "pin_rejected_or_stale")
                result(if (answer.success && !accepted) "App changed. Authenticate again." else answer.message)
                render()
            }
        }
    }
    fun biometric(token: LockEngine.Challenge) {
        if (android.os.Build.VERSION.SDK_INT < 29) { shield.message.value = "Use PIN on this Android version."; return }
        if (token != engine.challenge) { Diagnostics.record("biometric_stale_action"); shield.message.value = "The app changed. Try fingerprint again."; return }
        if (authenticating != null) { Diagnostics.record("biometric_already_requested"); shield.message.value = "Biometric request pending. You can still use PIN."; return }
        Diagnostics.record("biometric_action_received")
        authenticating = token
        try {
            startActivity(Intent(this, BiometricActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION).putExtra("generation", token.generation).putExtra("package", token.packageName))
            Diagnostics.record("biometric_launch_requested")
        } catch (_: Exception) { authenticating = null; Diagnostics.record("biometric_launch_denied"); shield.message.value = "Biometric screen unavailable. Use PIN." }
    }
    fun biometricHostStarted(token: LockEngine.Challenge): Boolean {
        if (!isCurrent(token)) return false
        biometricHost = token
        return true
    }
    fun biometricHostCommitted(token: LockEngine.Challenge): Boolean {
        if (!isCurrent(token) || biometricHost != token || engine.uncertain || evidence.foreground != token.packageName || deviceUnavailable()) return false
        biometricCover = token
        // The opaque Activity has submitted a frame. It is now the privacy shield;
        // leaving an accessibility overlay above Samsung's prompt prevents its use.
        shield.hide()
        Diagnostics.record("shield_handoff_activity")
        return true
    }
    fun biometricHostStopped(token: LockEngine.Challenge) {
        if (biometricHost == token) { biometricHost = null; biometricCover = null; render() }
    }
    fun biometricResult(token: LockEngine.Challenge, success: Boolean, finishHost: () -> Unit) {
        if (authenticating != token) { finishAfterShield(finishHost); return }
        scan(null)
        if (authenticating != token) { finishAfterShield(finishHost); return }
        authenticating = null; biometricHost = null; biometricCover = null
        val accepted = engine.authenticate(token, success)
        Diagnostics.record(if (accepted) "biometric_accepted" else "biometric_rejected_or_stale")
        shield.message.value = if (accepted) "Unlocked" else "Use PIN or try fingerprint again"
        render()
        finishAfterShield(finishHost)
    }
    private fun finishAfterShield(finishHost: () -> Unit) {
        biometricCover = null
        render()
        if (engine.shieldRequired && shield.isAttached) shield.afterOpaqueFrame(finishHost) else finishHost()
    }
    fun isCurrent(token: LockEngine.Challenge) = ready && authenticating == token && engine.challenge == token
    fun home() { authenticating = null; engine.invalidateChallenge(); performGlobalAction(GLOBAL_ACTION_HOME) /* shield stays until a subsequent window event */ }
    override fun onInterrupt() {
        if (!ready) return
        authenticating = null; engine.configure(engine.protectedApps, engine.rule, engine.timeoutMs, engine.shared)
        Health.interrupted.value = true; Health.detail.value = "Service interrupted; reopen setup"
        Diagnostics.record("service_interrupted"); render()
    }
    override fun onUnbind(intent: Intent?): Boolean { shutdown(); return super.onUnbind(intent) }
    override fun onDestroy() { shutdown(); super.onDestroy() }
    private fun shutdown() {
        if (!ready) return
        ready = false; authenticating = null; engine.disconnect(); handler.removeCallbacksAndMessages(null)
        shield.destroy(); runCatching { unregisterReceiver(receiver) }
        app.config.prefs.unregisterOnSharedPreferenceChangeListener(preferences)
        getSharedPreferences("app_lock_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferences)
        instance = null; Health.connected.value = false; Health.interrupted.value = true
        Diagnostics.record("service_disconnected")
    }
}

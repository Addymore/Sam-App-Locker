package app.locker.plus

import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.*
import androidx.savedstate.*

/** Lifecycle pattern retained from upstream LockScreenOverlayManager, with a persistent
 * opaque Android backing view and token-bound callbacks instead of removal on handoff. */
class PrivacyShield(private val service: LockerService) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry get() = saved.savedStateRegistry
    private val wm = service.getSystemService(WindowManager::class.java)
    private val token = mutableStateOf<LockEngine.Challenge?>(null)
    private val uncertain = mutableStateOf(false)
    val message = mutableStateOf("")
    val pinFallback = mutableStateOf(false)
    private val removalActions = mutableListOf<() -> Unit>()
    private var attached = false
    val isAttached get() = attached
    var awaitingFirstFrame = false; private set
    private val appLabel = mutableStateOf("")
    private var presentedPackage: String? = null
    private var receipt = 0L
    private var measured = false
    private var attachment = 0L
    private lateinit var compose: ComposeView
    private val frame = object : FrameLayout(service) {
        override fun dispatchDraw(canvas: android.graphics.Canvas) {
            super.dispatchDraw(canvas)
            if (!measured) {
                measured = true; Diagnostics.record("shield_draw_submitted", SystemClock.elapsedRealtime() - receipt)
                // Submit the opaque native backing frame before first Compose composition.
                if (childCount == 0) post { if (childCount == 0 && attached) addView(compose, FrameLayout.LayoutParams(-1, -1)) }
            }
        }
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) { if (event.action == KeyEvent.ACTION_UP) service.home(); return true }
            return super.dispatchKeyEvent(event)
        }
    }
    init {
        saved.performRestore(null); registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        frame.setViewTreeLifecycleOwner(this); frame.setViewTreeSavedStateRegistryOwner(this); frame.setViewTreeViewModelStoreOwner(this)
        val dark = service.resources.configuration.uiMode and 0x30 == 0x20
        val background = if (service.app.config.dynamic() && android.os.Build.VERSION.SDK_INT >= 31) {
            (if (dark) androidx.compose.material3.dynamicDarkColorScheme(service) else androidx.compose.material3.dynamicLightColorScheme(service)).background.toArgb()
        } else if (dark) 0xff11141c.toInt() else 0xfff4f6fc.toInt()
        frame.setBackgroundColor(background)
        compose = ComposeView(service).apply {
            // A WindowManager removal destroys the window's recomposer/input scope.
            // Never retain that composition across distinct overlay windows.
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent { LockerTheme(service.app.config.dynamic()) {
                val current = token.value
                AuthPanel(title = if (uncertain.value) "Window cannot be verified" else "App is locked", subtitle = if (current == null) "Return home and open the app in full screen." else appLabel.value.ifEmpty { current.packageName },
                    error = message.value, enabled = current != null && !uncertain.value, identity = current,
                    onPin = { pin, done -> current?.let { service.verifyPin(it, pin) { message.value = it; done() } } ?: done() },
                    preferPin = pinFallback.value, credentialKind = service.app.vault.kind(),
                    autoPinLength = if (service.app.config.state.value.autoPin) service.app.vault.pinLength() else 0,
                    onBiometric = if (service.biometricAvailable) ({ current?.let(service::biometric) }) else null, onHome = service::home, onPinVisibility = ::setPinFocus)
            } }
        }
    }
    fun show(challenge: LockEngine.Challenge?, unknown: Boolean, received: Long, eventOrigin: Boolean = false) {
        val changed = token.value != challenge
        if (challenge != null && presentedPackage != challenge.packageName) {
            presentedPackage = challenge.packageName
            message.value = ""; appLabel.value = ""; pinFallback.value = false
            if (challenge != null) service.app.io.execute {
                val label = runCatching { service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(challenge.packageName, 0)).toString() }.getOrDefault(challenge.packageName)
                android.os.Handler(service.mainLooper).post { if (token.value == challenge) appLabel.value = label }
            }
        }
        token.value = challenge; uncertain.value = unknown
        if (attached) {
            if (changed && challenge != null && !unknown) afterOpaqueFrame { service.shieldReady(challenge) }
            return
        }
        receipt = received; measured = false; attachment++
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.OPAQUE)
        params.setTitle("Sam App Locker privacy shield")
        params.windowAnimations = 0
        awaitingFirstFrame = android.os.Build.VERSION.SDK_INT >= 29
        try { wm.addView(frame, params); attached = true }
        catch (error: Exception) { awaitingFirstFrame = false; throw error }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val currentAttachment = attachment
            frame.viewTreeObserver.registerFrameCommitCallback {
                val duration = SystemClock.elapsedRealtime() - received
                frame.post {
                    if (attached && attachment == currentAttachment) {
                        awaitingFirstFrame = false
                        Diagnostics.record(if (eventOrigin) "shield_event_frame_committed" else "shield_scan_frame_committed", duration)
                        service.onShieldFrameCommitted()
                        // Reconcile queued window evidence before requesting authentication.
                        frame.post { if (attached && challenge != null && token.value == challenge && !uncertain.value) service.shieldReady(challenge) }
                    }
                }
            }
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    private fun setPinFocus(visible: Boolean) {
        if (!attached) return
        val params = frame.layoutParams as? WindowManager.LayoutParams ?: return
        val flags = if (visible) params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            else params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (flags != params.flags) { params.flags = flags; wm.updateViewLayout(frame, params) }
    }
    fun clearSessionUi() { presentedPackage = null; pinFallback.value = false; message.value = "" }
    /** Never finish an opaque auth host until its replacement frame is submitted. */
    fun afterOpaqueFrame(finishOnRemoval: Boolean = false, action: () -> Unit) {
        if (!attached) return
        // isHardwareAccelerated can still be false immediately after addView.
        // The window explicitly requests hardware rendering; its floating observer
        // carries this callback into the attached tree for the very first frame.
        if (android.os.Build.VERSION.SDK_INT < 29) return
        val currentAttachment = attachment
        if (finishOnRemoval) removalActions.add(action)
        frame.viewTreeObserver.registerFrameCommitCallback {
            frame.post { if (attached && attachment == currentAttachment) {
                removalActions.remove(action)
                Diagnostics.record("shield_return_frame_committed"); action()
            } }
        }
        frame.invalidate()
    }
    fun hide() {
        if (!attached) return
        val start = SystemClock.elapsedRealtime()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        wm.removeViewImmediate(frame); attached = false; awaitingFirstFrame = false
        // Next attachment must submit the native opaque backing before rebuilding UI.
        frame.removeView(compose)
        compose.disposeComposition()
        val finishers = removalActions.toList(); removalActions.clear()
        finishers.forEach { it() }
        Diagnostics.record("shield_remove_submitted", SystemClock.elapsedRealtime() - start)
    }
    fun destroy() { hide(); registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); viewModelStore.clear() }
}

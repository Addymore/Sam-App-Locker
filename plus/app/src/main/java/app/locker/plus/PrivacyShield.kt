package app.locker.plus

import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
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
    private var attached = false
    val isAttached get() = attached
    private val appLabel = mutableStateOf("")
    private var receipt = 0L
    private var measured = false
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
        frame.setBackgroundColor(if (service.resources.configuration.uiMode and 0x30 == 0x20) 0xff17151d.toInt() else 0xfff6f3fa.toInt())
        compose = ComposeView(service).apply {
            // A WindowManager removal destroys the window's recomposer/input scope.
            // Never retain that composition across distinct overlay windows.
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent { LockerTheme(service.app.config.dynamic()) {
                val current = token.value
                AuthPanel(title = if (uncertain.value) "Window cannot be verified" else "App is locked", subtitle = if (current == null) "Return home and open the app in full screen." else appLabel.value.ifEmpty { current.packageName },
                    error = message.value, enabled = current != null && !uncertain.value, identity = current,
                    onPin = { pin, done -> current?.let { service.verifyPin(it, pin) { message.value = it; done() } } ?: done() },
                    onBiometric = { current?.let(service::biometric) }, onHome = service::home)
            } }
        }
    }
    fun show(challenge: LockEngine.Challenge?, unknown: Boolean, received: Long) {
        if (token.value != challenge) {
            message.value = ""; appLabel.value = ""
            if (challenge != null) service.app.io.execute {
                val label = runCatching { service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(challenge.packageName, 0)).toString() }.getOrDefault(challenge.packageName)
                android.os.Handler(service.mainLooper).post { if (token.value == challenge) appLabel.value = label }
            }
        }
        token.value = challenge; uncertain.value = unknown
        if (attached) return
        receipt = received; measured = false
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.OPAQUE)
        params.setTitle("Sam App Locker privacy shield")
        wm.addView(frame, params); attached = true
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun hide() {
        if (!attached) return
        val start = SystemClock.elapsedRealtime()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        wm.removeViewImmediate(frame); attached = false
        // Next attachment must submit the native opaque backing before rebuilding UI.
        frame.removeView(compose)
        compose.disposeComposition()
        Diagnostics.record("shield_remove_submitted", SystemClock.elapsedRealtime() - start)
    }
    fun destroy() { hide(); registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); viewModelStore.clear() }
}

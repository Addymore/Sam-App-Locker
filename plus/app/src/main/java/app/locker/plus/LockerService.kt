package app.locker.plus

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

class LockerService : AccessibilityService() {
    companion object {
        var instance: LockerService? = null; private set
        @Volatile var authorizedUninstallUntil: Long = 0L
        fun authorizeInAppUninstall(durationMs: Long = 15_000L) {
            authorizedUninstallUntil = SystemClock.elapsedRealtime() + durationMs
            instance?.let {
                it.activeProtectedInstaller = null
                it.configure()
            }
        }
    }
    val engine = LockEngine(SystemClock::elapsedRealtime)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var shield: PrivacyShield
    private var keyboards = emptySet<String>()
    private var homes = emptySet<String>()
    private var ready = false
    val shieldAttached get() = ready && shield.isAttached
    var shieldRemovals = 0; private set
    private var authenticating: LockEngine.Challenge? = null
    private var biometricDismiss: (() -> Unit)? = null
    private var biometricHost: LockEngine.Challenge? = null
    private var biometricCover: LockEngine.Challenge? = null
    private val hostAttempts = BiometricHostAttempts()
    private var confirmedProtectedWindow: String? = null
    private var snapshotProtected = emptySet<String>()
    private var systemPanelActive = false
    private val windowOwners = WindowOwnerCache()
    private var autoRequestQueued = false
    private val automaticBiometric = BiometricAttemptGate()
    private var postAuthPackage: String? = null
    private var postAuthUntil = 0L
    private var goingHomeUntil = 0L
    private var enteringProtectedUntil = 0L
    private var lastUnlockedExitPackage: String? = null
    private var lastUnlockedExitUntil = 0L
    @Volatile private var protectedUninstallTargets = setOf("sam app locker", "app.sam.locker", "app.sam.locker.debug")
    private var activeProtectedInstaller: String? = null
    val biometricAvailable get() = app.config.state.value.biometrics &&
        androidx.biometric.BiometricManager.from(this).canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    private val evidence = WindowEvidence()
    val homeObserved get() = evidence.foreground in homes || SystemClock.elapsedRealtime() < goingHomeUntil
    private var scanQueued = false
    private var pendingForeground: Pair<Int, String>? = null
    private var transitionFrames = 0
    private var transitionFrameQueued = false
    private val transitionFrame = android.view.Choreographer.FrameCallback {
        transitionFrameQueued = false
        if (ready && pendingForeground != null) {
            if (transitionFrames > 0) {
                transitionFrames--
                queueScan()
            } else {
                pendingForeground = null
                shield.restorePasswordFocusIfNeeded()
                queueScan()
            }
        }
    }
    private fun retryPendingTransition() {
        if (pendingForeground == null || transitionFrameQueued) return
        if (transitionFrames <= 0) {
            pendingForeground = null
            shield.restorePasswordFocusIfNeeded()
            return
        }
        transitionFrameQueued = true
        android.view.Choreographer.getInstance().postFrameCallback(transitionFrame)
    }
    fun onShieldFrameCommitted() { queueScan() }
    fun shouldStartInBiometricMode(challenge: LockEngine.Challenge?): Boolean =
        challenge != null && !engine.uncertain && biometricAvailable && automaticBiometric.canTake(challenge)
    fun isUnlockedInForeground(pkg: String): Boolean =
        ready && evidence.foreground == pkg && engine.isAuthenticated(pkg)

    private val packageInstallers = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )
    private val transientPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.samsung.android.permissioncontroller",
        "com.android.intentresolver",
        "com.samsung.android.app.sharelive",
        "com.samsung.android.biometrics.app.setting",
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.android.app.smartcapture",
        "com.samsung.android.onehand",
        "com.samsung.android.mdx.quickboard",
        "com.samsung.android.mcfds"
    )
    private fun deviceUnavailable() = !getSystemService(android.os.PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private val recovery = setOf("com.android.settings", "com.android.phone", "com.samsung.android.dialer", "com.google.android.dialer", "com.samsung.android.incallui", "com.android.emergency")
    private val timer = Runnable { engine.tick(); render() }
    private val preferences = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (ready) shield.refreshAppearance()
        if (key == null || key in setOf("locked_apps", "rule", "seconds", "shared", "credential_epoch", "uninstall_protection")) configure()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                evidence.clear(); windowOwners.clear(); confirmedProtectedWindow = null; pendingForeground = null
                postAuthPackage = null; postAuthUntil = 0L; goingHomeUntil = 0L; enteringProtectedUntil = 0L
                lastUnlockedExitPackage = null; lastUnlockedExitUntil = 0L
                activeProtectedInstaller = null; authenticating = null
                automaticBiometric.clear()
                engine.screenLocked(); render(); Diagnostics.record("screen_locked")
                LockerNotificationService.instance?.sweepActiveNotifications()
            } else scan(null)
        }
    }
    private fun refreshEnvironmentPackages() {
        keyboards = runCatching { getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet() }.getOrDefault(emptySet())
        homes = runCatching { packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet() }.getOrDefault(emptySet())
    }
    private fun refreshProtectedUninstallTargets(lockedApps: Set<String>) {
        app.io.execute {
            val targets = mutableSetOf(
                "sam app locker",
                "sam app locker test",
                packageName.lowercase()
            )
            runCatching {
                val ownLabel = applicationInfo.loadLabel(packageManager).toString().trim().lowercase()
                if (ownLabel.isNotEmpty()) targets.add(ownLabel)
            }
            for (pkg in lockedApps) {
                targets.add(pkg.lowercase())
                runCatching {
                    val label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString().trim().lowercase()
                    if (label.isNotEmpty()) targets.add(label)
                }
            }
            protectedUninstallTargets = targets
        }
    }
    private fun isProtectedUninstallWindow(root: AccessibilityNodeInfo?, targets: Set<String>): Boolean {
        if (root == null || targets.isEmpty()) return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var matched = false
        while (queue.isNotEmpty() && visited < 48) {
            val node = queue.removeFirst()
            visited++
            val texts = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            for (raw in texts) {
                val text = raw.trim().lowercase()
                if (text.isEmpty()) continue
                if (targets.any { target ->
                    text == target ||
                        text.contains("uninstall $target") ||
                        text.contains("\"$target\"") ||
                        text.contains("“$target”") ||
                        (target.length >= 3 && text.startsWith("$target ")) ||
                        (target.length >= 4 && text.contains(target))
                }) {
                    matched = true
                    break
                }
            }
            if (matched) break
            val count = node.childCount
            for (i in 0 until count) {
                node.getChild(i)?.let { queue.add(it) }
            }
            if (node !== root) {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }
        while (queue.isNotEmpty()) {
            val leftover = queue.removeFirst()
            if (leftover !== root) {
                @Suppress("DEPRECATION")
                leftover.recycle()
            }
        }
        return matched
    }
    override fun onServiceConnected() {
        windowOwners.clear()
        instance = this
        shield = PrivacyShield(this)
        refreshEnvironmentPackages()
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
        refreshEnvironmentPackages()
        postAuthPackage = null; postAuthUntil = 0L
        val lockedApps = app.config.apps()
        refreshProtectedUninstallTargets(lockedApps)
        val uninstallGuardActive = app.config.uninstallProtection() && SystemClock.elapsedRealtime() >= authorizedUninstallUntil
        if (!uninstallGuardActive) activeProtectedInstaller = null
        val installerSet = if (uninstallGuardActive && activeProtectedInstaller != null) setOf(activeProtectedInstaller!!) else emptySet()
        val guardedPackages = lockedApps + installerSet
        engine.configure(guardedPackages - recovery - homes - keyboards - packageName - "com.android.systemui", app.config.rule(), app.config.seconds() * 1000L, app.config.shared())
        authenticating = null; render(); queueScan()
    }
    private fun updateProtectedInstaller(installerPkg: String?) {
        val effective = if (app.config.uninstallProtection() && SystemClock.elapsedRealtime() >= authorizedUninstallUntil) installerPkg else null
        if (activeProtectedInstaller == effective) return
        activeProtectedInstaller = effective
        val lockedApps = app.config.apps()
        val installerSet = if (effective != null) setOf(effective) else emptySet()
        val guardedPackages = lockedApps + installerSet
        engine.configure(guardedPackages - recovery - homes - keyboards - packageName - "com.android.systemui", app.config.rule(), app.config.seconds() * 1000L, app.config.shared())
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (ready) { refreshEnvironmentPackages(); shield.refreshAppearance(); queueScan() }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!ready || event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (deviceUnavailable()) {
            evidence.clear(); windowOwners.clear(); confirmedProtectedWindow = null; pendingForeground = null
            postAuthPackage = null; postAuthUntil = 0L; goingHomeUntil = 0L; enteringProtectedUntil = 0L
            lastUnlockedExitPackage = null; lastUnlockedExitUntil = 0L
            activeProtectedInstaller = null; authenticating = null
            automaticBiometric.clear()
            engine.screenLocked(); render(); return
        }
        // Only invalidate specific removed window IDs; coalesced -1 events must not wipe live window owners.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_REMOVED != 0 &&
            event.windowId >= 0) windowOwners.removed(event.windowId)
        val received = SystemClock.elapsedRealtime()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            received < goingHomeUntil && evidence.foreground in homes &&
            !shield.isAttached && authenticating == null) {
            return
        }
        val pkg = event.packageName?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null) {
            if (pkg == "com.android.settings" && app.config.uninstallProtection() && received >= authorizedUninstallUntil) {
                val cls = event.className?.toString().orEmpty()
                if (cls.contains("DeviceAdminAdd", ignoreCase = true) || cls.contains("DeviceAdminSettings", ignoreCase = true)) {
                    Diagnostics.record("device_admin_deactivation_blocked")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }
            }
            // Fast-path Home launcher entry to eliminate screen flickering when leaving a locked app to Home.
            if (pkg in homes) {
                val prev = evidence.foreground
                if (prev != null && prev !in homes && (prev in engine.protectedApps || engine.isAuthenticated(prev))) {
                    lastUnlockedExitPackage = prev
                    lastUnlockedExitUntil = received + 2_000L
                } else if (lastUnlockedExitPackage != null && received < lastUnlockedExitUntil) {
                    lastUnlockedExitUntil = maxOf(lastUnlockedExitUntil, received + 1_200L)
                }
                goingHomeUntil = received + 1_500L
                enteringProtectedUntil = 0L
                pendingForeground = null
                transitionFrames = 0
                confirmedProtectedWindow = null
                postAuthPackage = null; postAuthUntil = 0L
                activeProtectedInstaller = null
                automaticBiometric.clear()
                shield.releaseFocusForTransition()
                val dismiss = biometricDismiss; biometricDismiss = null
                dismiss?.invoke()
                authenticating = null; biometricHost = null; biometricCover = null
                evidence.foregroundEvent(pkg)
                engine.windows(emptySet(), false)
                Diagnostics.record("foreground_home")
                render(received, eventOrigin = true)
                return
            }
            // Samsung panel events can name Settings without opening its Activity.
            // Verify the application window through the same exit path as Home.
            if (pkg in recovery) Diagnostics.record("recovery_window_hint")
            if (pkg !in transientPackages && pkg !in keyboards && pkg != packageName) {
                if (pkg in engine.protectedApps) {
                    goingHomeUntil = 0L
                    enteringProtectedUntil = received + 500L
                    if (pkg != lastUnlockedExitPackage) {
                        lastUnlockedExitPackage = null; lastUnlockedExitUntil = 0L
                    }
                    pendingForeground = null
                    transitionFrames = 0
                    if (event.windowId >= 0) windowOwners.owner(event.windowId, AccessibilityWindowInfo.TYPE_APPLICATION, pkg, false)
                    if (postAuthPackage != null && postAuthPackage != pkg) { postAuthPackage = null; postAuthUntil = 0L }
                    val switchedTarget = evidence.foreground != pkg
                    if (switchedTarget) {
                        confirmedProtectedWindow = null
                        automaticBiometric.clear(pkg)
                    }
                    evidence.foregroundEvent(pkg)
                    Diagnostics.record("foreground_protected")
                    if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                        // When switching directly to a different protected app, challenge the newly
                        // entered app immediately rather than carrying the exiting app's window set.
                        val immediateSet = if (switchedTarget) setOf(pkg) else evidence.visible(engine.visible, engine.protectedApps)
                        engine.windows(immediateSet, false, preferred = pkg)
                        render(received, eventOrigin = true)
                    }
                } else {
                    if ((received < goingHomeUntil || evidence.foreground in homes) && pkg !in packageInstallers) {
                        return
                    }
                    // Window-state events also describe Samsung edge panels and other
                    // system surfaces. Only a verified application window may replace
                    // a protected foreground hint. Validate in the coalesced scan.
                    val prev = evidence.foreground
                    if (prev != null && (prev in engine.protectedApps || engine.isAuthenticated(prev))) {
                        lastUnlockedExitPackage = prev
                        lastUnlockedExitUntil = received + 1_500L
                    }
                    Diagnostics.record("exit_hint_received")
                    pendingForeground = event.windowId to pkg
                    shield.releaseFocusForTransition()
                    // A previously verified live application window can prove an exit
                    // while our authentication Activity obscures its current root.
                    if (biometricHost != null && windowOwners.owner(event.windowId, AccessibilityWindowInfo.TYPE_APPLICATION, null, true) == pkg) {
                        evidence.foregroundEvent(pkg)
                        authenticating = null
                        postAuthPackage = null; postAuthUntil = 0L
                        Diagnostics.record("verified_cached_exit")
                    }
                    transitionFrames = 15
                }
            }
            if (pkg == packageName && event.className == MainActivity::class.java.name) {
                goingHomeUntil = 0L; enteringProtectedUntil = 0L
                pendingForeground = null; transitionFrames = 0; postAuthPackage = null; postAuthUntil = 0L
                evidence.foregroundEvent(pkg)
                authenticating = null; biometricHost = null; biometricCover = null
                automaticBiometric.clear()
                engine.windows(emptySet(), false)
                render(received)
            }
        }
        // Coalesce synchronous messages behind the newly scheduled view traversal.
        // Copy no AccessibilityEvent (which can carry private text).
        queueScan()
    }
    private fun queueScan() {
        // Do not put root binder calls ahead of the first opaque frame.
        if (shield.awaitingFirstFrame) return
        if (!scanQueued) {
            scanQueued = true
            handler.post { scanQueued = false; if (ready && !shield.awaitingFirstFrame) scan(null) }
        }
    }
    private fun scan(event: AccessibilityEvent?) {
        val received = SystemClock.elapsedRealtime()
        if (deviceUnavailable()) {
            evidence.clear(); windowOwners.clear(); confirmedProtectedWindow = null; pendingForeground = null
            postAuthPackage = null; postAuthUntil = 0L; goingHomeUntil = 0L; enteringProtectedUntil = 0L
            lastUnlockedExitPackage = null; lastUnlockedExitUntil = 0L
            activeProtectedInstaller = null; automaticBiometric.clear()
            engine.screenLocked(); authenticating = null; render(); return
        }
        val eventPackage = event?.packageName?.toString()
        // Recovery/settings and launcher remain under owner control; never trap a user.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && eventPackage in recovery) {
            postAuthPackage = null; postAuthUntil = 0L; engine.screenLocked(); authenticating = null; render(); return
        }
        try {
            val metrics = resources.displayMetrics
            val fullWidthThreshold = (metrics.widthPixels * 85) / 100
            val fullHeightThreshold = (metrics.heightPixels * 85) / 100
            val bounds = Rect()
            val packages = mutableSetOf<String>()
            var incomplete = false
            var appWindowCount = 0
            var ownActive = false
            var verifiedSystemPanel = false
            var systemUiPresent = false
            val rawApplications = mutableListOf<ForegroundTransition.Window>()
            val fullScreenWindowIds = mutableSetOf<Int>()
            val parentWindowIds = mutableMapOf<Int, Int>()
            val activeApplicationIds = mutableSetOf<Int>()
            val uninstallGuardEnabled = app.config.uninstallProtection() && received >= authorizedUninstallUntil
            var detectedProtectedInstaller: String? = null
            var anyInstallerWindowPresent = false
            for (window in windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    // Only root ownership. Remember live system-window identity even
                    // before focus, so a temporarily missing root cannot invent a new owner.
                    if (window.type == AccessibilityWindowInfo.TYPE_SYSTEM) {
                        val systemRoot = window.getRoot(0) ?: window.root
                        val owner = windowOwners.owner(window.id, window.type, systemRoot?.packageName?.toString(), true)
                        if (owner == "com.android.systemui") {
                            systemUiPresent = true
                            if (window.isActive || window.isFocused) verifiedSystemPanel = true
                        }
                        @Suppress("DEPRECATION")
                        systemRoot?.recycle()
                    }
                    if (pendingForeground?.first == window.id) {
                        pendingForeground = null
                        shield.restorePasswordFocusIfNeeded()
                    }
                    continue
                }
                // Request no descendant prefetch first; fall back to standard root and
                // API 36.1+ window.refresh() to drop obsolete windows.
                var root = window.getRoot(0) ?: window.root
                if (root == null && Build.VERSION.SDK_INT >= 37) {
                    if (!window.refresh()) {
                        windowOwners.removed(window.id)
                        Diagnostics.record("obsolete_application_window")
                        continue
                    }
                    root = window.getRoot(0) ?: window.root
                }
                appWindowCount++
                val pkg = root?.packageName?.toString()
                @Suppress("DEPRECATION")
                root?.recycle()
                if (pkg in packageInstallers) {
                    anyInstallerWindowPresent = true
                    if (uninstallGuardEnabled) {
                        if (activeProtectedInstaller == pkg) {
                            detectedProtectedInstaller = pkg
                        } else {
                            val fullRoot = window.root
                            val protectsTarget = isProtectedUninstallWindow(fullRoot, protectedUninstallTargets)
                            @Suppress("DEPRECATION")
                            fullRoot?.recycle()
                            if (protectsTarget) detectedProtectedInstaller = pkg
                        }
                    }
                }
                if (pkg == null) {
                    val parent = window.parent
                    if (parent != null) {
                        parentWindowIds[window.id] = parent.id
                        @Suppress("DEPRECATION")
                        parent.recycle()
                    }
                }
                window.getBoundsInScreen(bounds)
                if (bounds.width() >= fullWidthThreshold && bounds.height() >= fullHeightThreshold) {
                    fullScreenWindowIds.add(window.id)
                }
                rawApplications.add(ForegroundTransition.Window(window.id, pkg))
                if (window.isActive || window.isFocused) activeApplicationIds.add(window.id)
            }
            if (detectedProtectedInstaller != null) {
                updateProtectedInstaller(detectedProtectedInstaller)
                if (evidence.foreground != detectedProtectedInstaller) {
                    pendingForeground = null
                    evidence.foregroundEvent(detectedProtectedInstaller)
                }
            } else if (!anyInstallerWindowPresent && activeProtectedInstaller != null && evidence.foreground != activeProtectedInstaller) {
                updateProtectedInstaller(null)
            }
            val resolvedApplications = mutableListOf<ForegroundTransition.Window>()
            for (window in rawApplications) {
                var pkg = windowOwners.owner(window.id, AccessibilityWindowInfo.TYPE_APPLICATION, window.pkg, true)
                if (pkg == null) {
                    val parentId = parentWindowIds[window.id]
                    if (parentId != null) pkg = windowOwners.owner(parentId, AccessibilityWindowInfo.TYPE_APPLICATION, null, true)
                }
                if (window.pkg == null && pkg != null) Diagnostics.record("panel_cached_window_owner")
                if (pkg == packageName && window.id in activeApplicationIds) ownActive = true
                resolvedApplications.add(ForegroundTransition.Window(window.id, pkg))
            }
            val launcherVisible = resolvedApplications.any { it.pkg in homes }
            val noActiveProtectedWindow = resolvedApplications.none { it.pkg in engine.protectedApps && it.id in activeApplicationIds }
            val launcherRevealedOnExit = launcherVisible && noActiveProtectedWindow &&
                !ownActive && biometricHost == null && received >= enteringProtectedUntil
            val homeActive = received < goingHomeUntil ||
                pendingForeground?.second in homes ||
                resolvedApplications.any { it.pkg in homes && it.id in activeApplicationIds } ||
                launcherRevealedOnExit
            if (homeActive) {
                val prev = evidence.foreground
                if (prev != null && prev !in homes && (prev in engine.protectedApps || engine.isAuthenticated(prev))) {
                    lastUnlockedExitPackage = prev
                    lastUnlockedExitUntil = maxOf(lastUnlockedExitUntil, received + 2_000L)
                }
                if (launcherRevealedOnExit && evidence.foreground !in homes) {
                    goingHomeUntil = maxOf(goingHomeUntil, received + 1_500L)
                    homes.firstOrNull()?.let { evidence.foregroundEvent(it) }
                }
                pendingForeground = null
                transitionFrames = 0
            }
            // When an active full-screen application window (not our own overlay/host) covers the screen,
            // ignore any inactive window from a different package that is shrinking or animating out behind it.
            val activeExternalFullScreen = resolvedApplications.firstOrNull {
                it.pkg != null && it.pkg != packageName && it.id in activeApplicationIds && it.id in fullScreenWindowIds
            }
            val applicationWindows = mutableListOf<ForegroundTransition.Window>()
            for (window in resolvedApplications) {
                val pkg = window.pkg
                if (pkg == packageName) continue
                if (pkg in packageInstallers && pkg != activeProtectedInstaller) continue
                // While transitioning to the Home screen, any non-active window (such as a closing
                // app shrinking into its Home icon or losing its root mid-animation) is exiting.
                if (homeActive && pkg !in homes && window.id !in activeApplicationIds) continue
                val isOccludedExitWindow = activeExternalFullScreen != null &&
                    window.id !in activeApplicationIds &&
                    pkg != activeExternalFullScreen.pkg &&
                    (pkg != evidence.foreground || pendingForeground?.second == activeExternalFullScreen.pkg || activeExternalFullScreen.pkg in homes)
                if (isOccludedExitWindow) continue
                applicationWindows.add(window)
                if (pkg == null) incomplete = true
                else if (pkg !in keyboards && pkg !in recovery && pkg !in homes && pkg !in transientPackages) packages.add(pkg)
            }
            if (homeActive && (resolvedApplications.any { it.pkg in homes } || received < goingHomeUntil)) {
                incomplete = false
                packages.clear()
            }
            if (lastUnlockedExitPackage != null && received < lastUnlockedExitUntil && evidence.foreground != lastUnlockedExitPackage) {
                packages.remove(lastUnlockedExitPackage)
            }
            systemPanelActive = verifiedSystemPanel
            val panelObscuresApps = SystemPanelPolicy.preservesSnapshot(
                systemUiPresent, appWindowCount, engine.uncertain)
            if (verifiedSystemPanel || (appWindowCount == 0 && evidence.foreground in engine.protectedApps))
                Diagnostics.record("panel_snapshot_${if (verifiedSystemPanel) 1 else 0}_${appWindowCount}_${if (pendingForeground != null) 1 else 0}_${if (engine.uncertain) 1 else 0}")
            // System-only snapshots during panel opening/closing need not own focus.
            // Preserve sessions across a shade-only snapshot.
            if (panelObscuresApps && !homeActive) packages.addAll(engine.visible)
            snapshotProtected = applicationWindows.mapNotNull { it.pkg }.filter { it in engine.protectedApps }.toSet()
            if (pendingForeground != null) Diagnostics.record("exit_snapshot_${applicationWindows.size}_${applicationWindows.count { it.pkg == null }}_${applicationWindows.count { it.pkg == pendingForeground?.second }}_${applicationWindows.count { it.pkg in engine.protectedApps }}")
            ForegroundTransition.verified(pendingForeground, applicationWindows)?.let { pkg ->
                if (applicationWindows.none { it.id == pendingForeground?.first && it.pkg == pkg })
                    Diagnostics.record("foreground_window_id_reconciled")
                evidence.foregroundEvent(pkg)
                pendingForeground = null
                postAuthPackage = null; postAuthUntil = 0L
                Diagnostics.record(if (pkg in homes) "foreground_home" else "foreground_other")
            }
            // If an active protected window is verified on screen and the pending exit package
            // is absent from application windows, clear the stale exit hint.
            if (pendingForeground != null && snapshotProtected.isNotEmpty() &&
                applicationWindows.any { it.pkg in engine.protectedApps && it.id in activeApplicationIds } &&
                applicationWindows.none { it.pkg == pendingForeground?.second }) {
                pendingForeground = null
                shield.restorePasswordFocusIfNeeded()
            }
            // Never use an initially stale public snapshot to cancel a new entry.
            // First observe the protected app in an application-window snapshot.
            if (evidence.foreground in engine.protectedApps && applicationWindows.any { it.pkg == evidence.foreground })
                confirmedProtectedWindow = evidence.foreground
            if (!ownActive && confirmedProtectedWindow != null && confirmedProtectedWindow == evidence.foreground) {
                ForegroundTransition.singleUnprotected(applicationWindows,
                    engine.protectedApps + transientPackages + keyboards)?.let { pkg ->
                    evidence.foregroundEvent(pkg)
                    pendingForeground = null; confirmedProtectedWindow = null
                    postAuthPackage = null; postAuthUntil = 0L
                    Diagnostics.record("foreground_snapshot_transition")
                }
            }
            retryPendingTransition()
            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && eventPackage in engine.protectedApps) packages.add(eventPackage!!)
            // During biometric handoff Android may omit obscured app windows. Retain the
            // challenge only while our auth Activity owns focus, never on unrelated switches.
            if (ownActive && authenticating != null && !homeActive) packages.addAll(engine.visible)
            if (appWindowCount == 0 && !panelObscuresApps && !homeActive && pendingForeground == null && evidence.foreground !in homes && evidence.foreground != packageName) incomplete = true
            // A token-bound full-screen auth host covers the default display. During
            // its handoff, obscured application roots may legitimately be unavailable.
            val hosting = !homeActive && biometricHost == authenticating && authenticating != null && evidence.foreground == authenticating?.packageName
            if (hosting) packages.addAll(engine.visible)
            // When our own PrivacyShield covers the confirmed foreground protected app (including
            // when Password IME takes focus), Samsung may omit the obscured target window root.
            val shieldCovering = !homeActive && shield.isAttached && evidence.foreground != null &&
                evidence.foreground in engine.protectedApps && pendingForeground == null &&
                applicationWindows.none { it.pkg != null && it.pkg !in engine.protectedApps }
            if (shieldCovering) {
                packages.add(evidence.foreground!!)
                if (appWindowCount == 0 || (rawApplications.size == 1 && rawApplications.first().pkg == null)) incomplete = false
            }
            // Preserve the just-authenticated session across the brief host/shield teardown frames.
            val postAuthActive = !homeActive && postAuthPackage != null && received < postAuthUntil &&
                evidence.foreground == postAuthPackage && pendingForeground == null
            if (postAuthActive) {
                packages.add(postAuthPackage!!)
                incomplete = false
            }
            // When the foreground protected app is already authenticated by the user, do not let
            // a transient subwindow/shade/rotation/exit null-root lock the user out with "Window cannot be verified".
            val activeAuthenticated = evidence.foreground != null && engine.isAuthenticated(evidence.foreground!!) &&
                applicationWindows.none { it.pkg != null && it.pkg in engine.protectedApps && !engine.isAuthenticated(it.pkg) }
            if (activeAuthenticated) {
                if (!homeActive && pendingForeground == null) packages.add(evidence.foreground!!)
                if (systemUiPresent || appWindowCount <= 1 || pendingForeground != null || homeActive) incomplete = false
            }
            val visible = if (homeActive) emptySet() else evidence.visible(packages, engine.protectedApps)
            val protectedRisk = !homeActive && (visible.any { it in engine.protectedApps } ||
                (evidence.foreground == null && engine.visible.any { it in engine.protectedApps }))
            engine.windows(visible, incomplete && !hosting && protectedRisk, preferred = evidence.foreground)
            if (authenticating != null && authenticating != engine.challenge) authenticating = null
            if (pendingForeground == null) shield.restorePasswordFocusIfNeeded()
            Health.interrupted.value = false
            render(received)
            if (shield.isAttached && !shield.awaitingFirstFrame && !autoRequestQueued) {
                autoRequestQueued = true
                handler.post {
                    autoRequestQueued = false
                    if (ready && shield.isAttached && !shield.awaitingFirstFrame) engine.challenge?.let(::shieldReady)
                }
            }
        } catch (_: Exception) {
            engine.windows(engine.visible, true)
            Health.interrupted.value = true; Health.detail.value = "Window inspection failed; reopen setup"
            Diagnostics.record("window_inspection_failed"); render(received)
        }
    }
    private fun render(received: Long = SystemClock.elapsedRealtime(), eventOrigin: Boolean = false) {
        if (!ready) return
        shield.biometricActive.value = (authenticating != null && authenticating == engine.challenge) ||
            (!shield.pinFallback.value && shouldStartInBiometricMode(engine.challenge))
        automaticBiometric.retainVisible(engine.visible)
        if (biometricDismiss != null && (biometricHost != authenticating || authenticating != engine.challenge ||
            evidence.foreground != authenticating?.packageName)) {
            val dismiss = biometricDismiss; biometricDismiss = null
            handler.post { Diagnostics.record("biometric_host_invalidated"); dismiss?.invoke() }
        }
        if (!engine.uncertain && engine.visible.none { it in engine.protectedApps }) {
            shield.clearSessionUi()
            LockerNotificationService.instance?.sweepActiveNotifications()
        }
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
        if (!ready || token != engine.challenge || authenticating != null || engine.uncertain || deviceUnavailable()) {
            pin.fill('\u0000'); result("Authentication is no longer active."); return
        }
        app.credentials.execute {
            val answer = app.vault.verify(pin)
            handler.post {
                val accepted = if (instance === this && ready && !deviceUnavailable()) engine.authenticate(token, answer.success) else false
                if (accepted) {
                    automaticBiometric.clear(token.packageName)
                    postAuthPackage = token.packageName
                    postAuthUntil = SystemClock.elapsedRealtime() + 1_000L
                    NotificationHealth.clearForPackage(token.packageName)
                    if (token.packageName in packageInstallers) {
                        authorizeInAppUninstall(20_000L)
                    }
                }
                Diagnostics.record(if (accepted) "pin_accepted" else "pin_rejected_or_stale")
                result(if (answer.success && !accepted) "App changed. Authenticate again." else answer.message)
                render()
                if (instance === this && ready) queueScan()
            }
        }
    }
    fun chooseCredential(token: LockEngine.Challenge) {
        if (token != engine.challenge || authenticating != null) return
        automaticBiometric.take(token)
        shield.biometricActive.value = false
        shield.pinFallback.value = true
    }
    fun biometric(token: LockEngine.Challenge) {
        if (token != engine.challenge) { Diagnostics.record("biometric_stale_action"); shield.message.value = "The app changed. Try fingerprint again."; return }
        if (pendingForeground != null && pendingForeground?.second !in homes &&
            (evidence.foreground == token.packageName || token.packageName in snapshotProtected)) {
            pendingForeground = null
        }
        val targetConfirmed = token.packageName in snapshotProtected || evidence.foreground == token.packageName
        if (engine.uncertain || deviceUnavailable() || systemPanelActive || pendingForeground != null || !targetConfirmed) {
            if (!biometricAvailable || !automaticBiometric.canTake(token)) {
                shield.biometricActive.value = false
            }
            return
        }
        if (authenticating != null) { Diagnostics.record("biometric_already_requested"); return }
        shield.pinFallback.value = false; shield.optionsRequest.intValue = 0; shield.biometricActive.value = true; shield.message.value = ""
        Diagnostics.record("biometric_action_received")
        automaticBiometric.take(token)
        evidence.adoptAuthenticationTarget(token.packageName, snapshotProtected.ifEmpty { setOf(token.packageName) })
        authenticating = token
        // Bind the verified launch before startActivity: accessibility can report
        // its obscuring transition even before the host's onCreate is dispatched.
        biometricHost = token
        val attempt = hostAttempts.begin()
        try {
            // BiometricActivity uses launchMode="singleTask" and handles onNewIntent, avoiding
            // task-destruction flashes when switching rapidly between protected apps.
            startActivity(Intent(this, BiometricActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("generation", token.generation)
                .putExtra("package", token.packageName)
                .putExtra("attempt", attempt))
            Diagnostics.record("biometric_launch_requested")
        } catch (_: Exception) { authenticating = null; biometricHost = null; shield.biometricActive.value = false; Diagnostics.record("biometric_launch_denied"); shield.message.value = "Fingerprint could not open. Retry or use the three-dot menu." }
    }
    fun shieldReady(token: LockEngine.Challenge) {
        if (token != engine.challenge || engine.uncertain || deviceUnavailable() || systemPanelActive || authenticating != null) return
        if (pendingForeground != null && pendingForeground?.second !in homes &&
            (evidence.foreground == token.packageName || token.packageName in snapshotProtected)) {
            pendingForeground = null
        }
        val targetConfirmed = token.packageName in snapshotProtected || evidence.foreground == token.packageName
        if (pendingForeground == null && targetConfirmed && biometricAvailable && automaticBiometric.take(token)) {
            Diagnostics.record("biometric_automatic_requested")
            biometric(token)
        } else if (!biometricAvailable || !automaticBiometric.canTake(token)) {
            shield.biometricActive.value = false
        }
    }
    fun biometricHostStarted(token: LockEngine.Challenge, attempt: Long, dismiss: () -> Unit): Boolean {
        if (pendingForeground != null && pendingForeground?.second !in homes && evidence.foreground == token.packageName) {
            pendingForeground = null
        }
        // Starting our opaque Activity can itself remove the obscured target from
        // Samsung's next snapshot. Bind the already verified launch before scanning;
        // a newer foreground event, panel or changed challenge still rejects it.
        if (!hostAttempts.matches(attempt) || !isCurrent(token) || biometricHost != token || pendingForeground != null || systemPanelActive || evidence.foreground != token.packageName) { Diagnostics.record("biometric_host_target_gone"); return false }
        scan(null)
        if (!isCurrent(token) || pendingForeground != null || systemPanelActive || evidence.foreground != token.packageName || deviceUnavailable()) {
            if (biometricHost == token) biometricHost = null
            Diagnostics.record("biometric_host_target_changed")
            return false
        }
        biometricDismiss = dismiss
        return true
    }
    fun biometricHostCommitted(token: LockEngine.Challenge, attempt: Long): Boolean {
        if (!hostAttempts.matches(attempt)) return false
        if (pendingForeground != null && pendingForeground?.second !in homes && evidence.foreground == token.packageName) {
            pendingForeground = null
        }
        scan(null)
        if (pendingForeground != null) return false
        if (!isCurrent(token) || biometricHost != token || engine.uncertain || evidence.foreground != token.packageName || deviceUnavailable()) return false
        biometricCover = token
        // The opaque Activity has submitted a frame. It is now the privacy shield;
        // leaving an accessibility overlay above Samsung's prompt prevents its use.
        shield.hide()
        Diagnostics.record("shield_handoff_activity")
        return true
    }
    fun biometricHostStopped(token: LockEngine.Challenge, attempt: Long) {
        if (hostAttempts.matches(attempt) && biometricHost == token) {
            biometricDismiss = null
            biometricHost = null; biometricCover = null
            if (!homeObserved) scan(null)
        }
    }
    fun biometricResult(
        token: LockEngine.Challenge,
        attempt: Long,
        success: Boolean,
        finishHost: () -> Unit,
        options: Boolean = false,
        message: String = "",
        userCanceled: Boolean = false
    ) {
        if (!hostAttempts.matches(attempt)) { finishHost(); return }
        if (authenticating != token) { finishAfterShield(finishHost); return }
        if (!success) {
            scan(null)
            if (authenticating != token) { finishAfterShield(finishHost); return }
        }
        biometricDismiss = null
        authenticating = null; biometricHost = null; biometricCover = null
        val accepted = if (!deviceUnavailable()) engine.authenticate(token, success) else false
        if (accepted) {
            automaticBiometric.clear(token.packageName)
            postAuthPackage = token.packageName
            postAuthUntil = SystemClock.elapsedRealtime() + 1_000L
            NotificationHealth.clearForPackage(token.packageName)
            if (token.packageName in packageInstallers) {
                authorizeInAppUninstall(20_000L)
            }
        }
        Diagnostics.record(if (accepted) "biometric_accepted" else "biometric_rejected_or_stale")
        val goingHome = SystemClock.elapsedRealtime() < goingHomeUntil ||
            pendingForeground?.second in homes ||
            evidence.foreground in homes
        val shouldAutoRetry = !accepted && !userCanceled && !options && !goingHome &&
            biometricAvailable && engine.challenge == token && !engine.uncertain &&
            evidence.foreground == token.packageName && automaticBiometric.releaseForSystemRetry(token)
        shield.biometricActive.value = shouldAutoRetry
        shield.message.value = if (accepted) "Unlocked" else message
        shield.pinFallback.value = false
        if (!accepted && options) shield.optionsRequest.intValue++
        render()
        if (accepted || goingHome) {
            shield.hide()
            finishHost()
        } else {
            finishAfterShield(finishHost)
        }
        if (shouldAutoRetry) {
            handler.postDelayed({
                if (ready && engine.challenge == token && authenticating == null) shieldReady(token)
            }, 120L)
        }
    }
    private fun finishAfterShield(finishHost: () -> Unit) {
        biometricCover = null
        val goingHome = SystemClock.elapsedRealtime() < goingHomeUntil ||
            pendingForeground?.second in homes ||
            evidence.foreground in homes
        if (goingHome) {
            shield.hide()
            finishHost()
            return
        }
        render()
        if (engine.shieldRequired && shield.isAttached) shield.afterOpaqueFrame(finishOnRemoval = true, action = finishHost) else finishHost()
    }
    fun isCurrent(token: LockEngine.Challenge) = ready && authenticating == token && engine.challenge == token
    fun home() {
        val now = SystemClock.elapsedRealtime()
        val prev = evidence.foreground
        if (prev != null && prev !in homes && (prev in engine.protectedApps || engine.isAuthenticated(prev))) {
            lastUnlockedExitPackage = prev
            lastUnlockedExitUntil = now + 2_000L
        }
        goingHomeUntil = now + 1_500L
        enteringProtectedUntil = 0L
        authenticating = null; biometricHost = null; biometricCover = null
        pendingForeground = null; transitionFrames = 0; confirmedProtectedWindow = null
        postAuthPackage = null; postAuthUntil = 0L
        activeProtectedInstaller = null
        automaticBiometric.clear()
        val dismiss = biometricDismiss; biometricDismiss = null
        dismiss?.invoke()
        shield.releaseFocusForTransition()
        evidence.clear()
        engine.invalidateChallenge()
        engine.windows(emptySet(), false)
        performGlobalAction(GLOBAL_ACTION_HOME)
        render(now)
    }
    override fun onInterrupt() {
        if (!ready) return
        windowOwners.clear()
        postAuthPackage = null; postAuthUntil = 0L; goingHomeUntil = 0L; enteringProtectedUntil = 0L
        authenticating = null; engine.configure(engine.protectedApps, engine.rule, engine.timeoutMs, engine.shared)
        Health.interrupted.value = true; Health.detail.value = "Service interrupted; reopen setup"
        Diagnostics.record("service_interrupted"); render()
    }
    override fun onUnbind(intent: Intent?): Boolean { shutdown(); return super.onUnbind(intent) }
    override fun onDestroy() { shutdown(); super.onDestroy() }
    private fun shutdown() {
        if (!ready) return
        ready = false; authenticating = null; postAuthPackage = null; postAuthUntil = 0L; goingHomeUntil = 0L; enteringProtectedUntil = 0L
        windowOwners.clear(); engine.disconnect(); handler.removeCallbacksAndMessages(null)
        android.view.Choreographer.getInstance().removeFrameCallback(transitionFrame)
        transitionFrameQueued = false
        shield.destroy(); runCatching { unregisterReceiver(receiver) }
        app.config.prefs.unregisterOnSharedPreferenceChangeListener(preferences)
        getSharedPreferences("app_lock_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferences)
        instance = null; Health.connected.value = false; Health.interrupted.value = true
        Diagnostics.record("service_disconnected")
    }
}

package app.locker.plus

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import dev.pranav.applock.features.applist.domain.AppSearchManager
import kotlinx.coroutines.launch

data class AppRow(val pkg: String, val label: String, val icon: Bitmap, val system: Boolean)
class MainActivity : FragmentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var authorized by mutableStateOf(false)
    private var loaded by mutableStateOf(false)
    private var enrolled by mutableStateOf(false)
    private var changingCredential by mutableStateOf(false)
    private var error by mutableStateOf("")
    private var confirm: CharArray? = null
    private var enrollmentKind by mutableStateOf(CredentialKind.PIN)
    private var confirmation by mutableStateOf(false)
    private var accessibilityGranted by mutableStateOf(false)
    private var notificationAccessGranted by mutableStateOf(false)
    private var deviceAdminActive by mutableStateOf(false)
    private var rows by mutableStateOf<List<AppRow>>(emptyList())
    private var showLog by mutableStateOf(false)
    private var loadingApps by mutableStateOf(false)
    private var loadError by mutableStateOf(false)
    private var lifecycleEpoch = 0L
    private var resumeEpoch by mutableIntStateOf(0)
    private var preferPin by mutableStateOf(false)
    private var biometricAutoLaunched by mutableStateOf(false)
    private var biometricSystemRetries = 0
    private var pendingRemoval by mutableStateOf<AppRow?>(null)
    private lateinit var biometricPrompt: BiometricPrompt
    private var biometricPending by mutableStateOf(false)
    private var optionsRequest by mutableIntStateOf(0)
    private var biometricEpoch = -1L
    private var biometricRemoval: AppRow? = null
    private val strongAvailable get() = app.config.state.value.biometrics && BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private var pendingExport: String? = null
    private val exportDocument = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val data = pendingExport; pendingExport = null
        if (uri != null && data != null) app.io.execute {
            val result = runCatching { requireNotNull(contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(data) } }
            runOnUiThread { android.widget.Toast.makeText(this, if (result.isSuccess) "Report saved" else "Could not save report", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
    private fun exportReport(audit: Boolean) {
        if (!authorized) return
        pendingExport = if (audit) "Sam App Locker security report\nAndroid: ${android.os.Build.VERSION.RELEASE}\n" +
            "Accessibility granted: ${accessibilityEnabled()}\nService connected: ${Health.connected.value}\n" +
            "Notification shield access: ${NotificationHealth.isAccessGranted(this)}\n" +
            "Device admin active: ${LockerDeviceAdminReceiver.isActive(this)}\n" +
            "Protected apps: ${app.config.apps().size}\nRelock: ${app.config.rule()}\n" +
            "Credential: ${app.vault.kind().label}\nBiometric available: $strongAvailable\n" +
            "Ordinary app protection; initial-frame exposure remains possible.\n"
        else Diagnostics.text()
        exportDocument.launch(if (audit) "Sam-Locker-security-report.txt" else "Sam-Locker-diagnostics.txt")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (!biometricPending || biometricEpoch != lifecycleEpoch) return
                biometricPending = false
                biometricSystemRetries = 0
                val removal = biometricRemoval
                if (removal != null) {
                    if (pendingRemoval?.pkg == removal.pkg && authorized) launchRemoval(removal)
                } else { authorized = true; error = ""; preferPin = false }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!biometricPending || biometricEpoch != lifecycleEpoch) return
                val currentEpoch = lifecycleEpoch
                if (errorCode == BiometricPrompt.ERROR_CANCELED &&
                    biometricSystemRetries < 3 &&
                    lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                    !preferPin) {
                    biometricSystemRetries++
                    mainHandler.postDelayed({
                        if (lifecycleEpoch == currentEpoch && (!authorized || pendingRemoval != null) && !preferPin && strongAvailable) {
                            biometricPending = false
                            startBiometric()
                        }
                    }, 120L)
                    return
                }
                biometricPending = false; preferPin = false
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) optionsRequest++
                error = if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_CANCELED) "" else "Fingerprint unavailable. Retry or use the three-dot menu."
            }
        })
        app.credentials.execute { val exists = app.vault.exists(); runOnUiThread { enrolled = exists; loaded = true } }
        setContent {
            val uiState = app.config.state.value
            LockerTheme(appearance = uiState.appearance, haptics = uiState.haptics) {
                SideEffect { window.attributes = window.attributes.apply {
                    screenBrightness = if ((!authorized || pendingRemoval != null) && uiState.bright) 1f else -1f
                } }
                val removal = pendingRemoval
                LaunchedEffect(loaded, enrolled, authorized, resumeEpoch, removal?.pkg) {
                    if (loaded && enrolled && (!authorized || removal != null) && !preferPin && !biometricAutoLaunched && strongAvailable) {
                        biometricAutoLaunched = true
                        startBiometric()
                    }
                }
                if (!loaded) Surface(Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else if (removal != null && authorized) AuthPanel(
                    title = "Uninstall ${removal.label}?",
                    subtitle = "Authenticate to open Android’s uninstall confirmation. Removal outside Sam App Locker is controlled by Android.",
                    identity = removal.pkg, biometricActive = biometricPending, optionsRequest = optionsRequest, error = error, preferPin = preferPin, credentialKind = app.vault.kind(),
                    autoPinLength = if (uiState.autoPin) app.vault.pinLength() else 0,
                    hidePatternTrail = uiState.hidePatternTrail,
                    scramblePin = uiState.scramblePin,
                    onPin = { pin, done -> authenticateRemoval(removal, pin, done) },
                    onCredentialSelected = { preferPin = true },
                    onBiometric = if (strongAvailable) ({ startBiometric() }) else null,
                    onHome = { pendingRemoval = null; error = ""; preferPin = false; biometricAutoLaunched = false }, exitLabel = "Keep app")
                else if (!authorized) AuthPanel(
                    title = if (enrolled) "Welcome back" else if (confirmation) "Confirm your ${enrollmentKind.label.lowercase()}" else "Choose your app lock",
                    subtitle = if (enrolled) "Your apps. Your privacy." else enrollmentKind.hint,
                    credentialKind = if (enrolled) app.vault.kind() else enrollmentKind,
                    onKindChange = if (!enrolled && !confirmation) ({ kind -> enrollmentKind = kind; error = "" }) else null,
                    identity = if (enrolled) "login" else if (confirmation) "confirm" else "enroll",
                    error = error, preferPin = preferPin, biometricActive = biometricPending, optionsRequest = optionsRequest,
                    autoPinLength = if (enrolled && uiState.autoPin) app.vault.pinLength() else 0,
                    hidePatternTrail = enrolled && uiState.hidePatternTrail,
                    scramblePin = enrolled && uiState.scramblePin,
                    onPin = { pin, done -> authenticate(pin, done) },
                    onCredentialSelected = { preferPin = true },
                    onBiometric = if (enrolled && strongAvailable) ({ startBiometric() }) else null,
                    onHome = if (changingCredential) ({
                        changingCredential = false; confirm?.fill('\u0000'); confirm = null; confirmation = false; error = ""
                        enrolled = true; authorized = true
                    }) else null,
                    exitLabel = "Cancel")
                else Dashboard()
            }
        }
    }

    private fun refreshApps() {
        if (loadingApps) return
        loadingApps = true; loadError = false
        app.io.execute {
            val result = runCatching {
                val homes = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
                val excluded = homes + setOf("com.android.settings", "com.android.phone", "com.samsung.android.dialer", "com.google.android.dialer",
                    "com.samsung.android.incallui", "com.android.emergency", "com.android.systemui")
                kotlinx.coroutines.runBlocking { AppSearchManager(this@MainActivity).loadApps() }.filter { it.packageName !in excluded && !it.packageName.startsWith("app.locker.plus") && !it.packageName.startsWith("app.sam.locker") }
                    .mapNotNull { info -> runCatching {
                        AppRow(info.packageName, info.loadLabel(packageManager).toString(), info.loadIcon(packageManager).toBitmap(96, 96),
                            info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                    }.getOrNull() }.sortedBy { it.label.lowercase() }
            }
            runOnUiThread { if (!isDestroyed) { result.onSuccess { rows = it }; loadError = result.isFailure; loadingApps = false } }
        }
    }
    private fun startBiometric() {
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        if (!enrolled || biometricPending || isFinishing || !strongAvailable) { if (!strongAvailable) preferPin = true; return }
        preferPin = false; optionsRequest = 0; error = ""; biometricPending = true; biometricEpoch = lifecycleEpoch; biometricRemoval = pendingRemoval
        biometricPrompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (pendingRemoval == null) "Open Sam App Locker" else "Confirm app removal")
            .setSubtitle(pendingRemoval?.label ?: "Authenticate to manage your protected apps")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Other methods").build())
    }
    private fun authenticate(pin: CharArray, done: () -> Unit) {
        if (!loaded || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            pin.fill('\u0000'); done(); return
        }
        val epoch = lifecycleEpoch
        if (!enrolled) {
            if (confirm == null) { confirm = pin; confirmation = true; error = ""; done(); return }
            if (confirm?.contentEquals(pin) != true) { confirm?.fill('\u0000'); confirm = null; pin.fill('\u0000'); confirmation = false; error = "Entries did not match. Try again."; done(); return }
            confirm?.fill('\u0000'); confirm = null
            app.credentials.execute {
                val selectedKind = enrollmentKind
                val success = runCatching { app.vault.enroll(pin, selectedKind) }.isSuccess
                runOnUiThread {
                    // A completed credential change must revoke app sessions even
                    // if the owner backgrounds this Activity while the KDF runs.
                    if (success) app.config.prefs.edit { putLong("credential_epoch", System.nanoTime()) }
                    if (epoch == lifecycleEpoch) { changingCredential = false; enrolled = success; authorized = success; confirmation = false; error = if (success) "" else "Could not save credential securely" }
                    done()
                }
            }
        } else app.credentials.execute {
            val result = app.vault.verify(pin)
            runOnUiThread { if (epoch == lifecycleEpoch) { authorized = result.success; error = if (result.success) "" else result.message }; done() }
        }
    }
    private fun authenticateRemoval(row: AppRow, pin: CharArray, done: () -> Unit) {
        val epoch = lifecycleEpoch
        app.credentials.execute {
            val result = app.vault.verify(pin)
            runOnUiThread {
                if (epoch == lifecycleEpoch && authorized && pendingRemoval?.pkg == row.pkg) {
                    if (result.success) launchRemoval(row) else error = result.message
                }
                done()
            }
        }
    }
    private fun launchRemoval(row: AppRow) {
        pendingRemoval = null; error = ""; preferPin = false; biometricAutoLaunched = false; biometricSystemRetries = 0
        LockerService.authorizeInAppUninstall()
        // Android owns the final confirmation. No device-owner/admin privileges.
        runCatching { startActivity(Intent(Intent.ACTION_DELETE, "package:${row.pkg}".toUri())) }
            .onFailure { error = "Android could not open uninstall. Use the phone’s app settings." }
    }
    private fun launchSelfUninstall() {
        if (!authorized) return
        LockerDeviceAdminReceiver.disableIfActive(this)
        deviceAdminActive = false
        app.config.prefs.edit { putBoolean("uninstall_protection", false) }
        LockerService.authorizeInAppUninstall(30_000L)
        runCatching { startActivity(Intent(Intent.ACTION_DELETE, "package:$packageName".toUri())) }
            .onFailure { android.widget.Toast.makeText(this, "Could not open uninstaller", android.widget.Toast.LENGTH_SHORT).show() }
    }
    private fun requestDeviceAdmin() {
        if (!authorized) return
        LockerService.authorizeInAppUninstall(30_000L)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, LockerDeviceAdminReceiver.component(this@MainActivity))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents unauthorized uninstallation of Sam App Locker until disabled inside Sam App Locker.")
        }
        runCatching { startActivity(intent) }
    }
    override fun onResume() {
        super.onResume()
        accessibilityGranted = accessibilityEnabled()
        notificationAccessGranted = NotificationHealth.isAccessGranted(this)
        deviceAdminActive = LockerDeviceAdminReceiver.isActive(this)
        resumeEpoch++
        refreshApps()
    }
    override fun onPause() {
        super.onPause()
        // A quick Home/return can resume before onStop. Never carry an
        // authenticated credential-change form across that pause boundary.
        if (!enrolled && loaded) invalidateManagement()
    }
    override fun onStop() { super.onStop(); if (!isChangingConfigurations) invalidateManagement() }
    private fun invalidateManagement() {
        lifecycleEpoch++; biometricPending = false; biometricSystemRetries = 0
        mainHandler.removeCallbacksAndMessages(null)
        if (::biometricPrompt.isInitialized) biometricPrompt.cancelAuthentication()
        authorized = false; changingCredential = false; pendingRemoval = null; preferPin = false; biometricAutoLaunched = false; optionsRequest = 0
        confirm?.fill('\u0000'); confirm = null; confirmation = false; error = ""
        // Never expose a stale enrollment form while the serialized credential
        // executor is still finishing work from the previous foreground session.
        loaded = false
        val epoch = lifecycleEpoch
        app.credentials.execute { val exists = app.vault.exists(); runOnUiThread {
            if (epoch == lifecycleEpoch && !isDestroyed) { enrolled = exists; loaded = true }
        } }
    }
    private fun launchSettings(intent: Intent) {
        LockerService.authorizeInAppUninstall(30_000L)
        runCatching { startActivity(intent) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
    private fun accessibilityEnabled(): Boolean {
        val component = ComponentName(this, LockerService::class.java).flattenToString()
        return Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')?.any { it.equals(component, true) } == true
    }

    @Composable private fun QuickFeatureTile(
        modifier: Modifier = Modifier,
        title: String,
        subtitle: String,
        active: Boolean,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
            shape = RoundedCornerShape(22.dp),
            color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Surface(
                        shape = CircleShape,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            if (active) "ON" else "OFF",
                            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable private fun Dashboard() {
        var search by remember { mutableStateOf("") }
        var tab by remember { mutableIntStateOf(0) }
        var filter by remember { mutableStateOf(AppFilter.ALL) }
        val settings by app.config.state
        val selected = settings.apps
        val hiddenMap by NotificationHealth.byApp
        val totalHidden = NotificationHealth.hiddenCount.intValue
        val active = Health.connected.value && !Health.interrupted.value && accessibilityGranted
        val status = if (active) "Protection active" else if (Health.interrupted.value || accessibilityGranted) "Protection interrupted" else "Setup incomplete"
        val snack = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val filtered = rows.filter { appMatches(it.pkg, it.label, it.system, selected, tab == 0, filter, search) }
        LockerBackground { Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = { NavigationBar {
                listOf("Locked", "Unlocked", "Settings").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index; search = "" },
                        icon = { NavigationMark(index) },
                        label = { Text(label) })
                }
            } }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Sam App Locker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("VIP", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text("Samsung One UI Privacy & App Shield", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                PrivacyMark(Modifier.padding(8.dp).size(26.dp))
                            }
                        }
                    }
                    item {
                        Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .65f)))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .75f)) { PrivacyMark(modifier = Modifier.padding(10.dp).size(28.dp)) }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${selected.size} locked apps", style = MaterialTheme.typography.bodySmall)
                                        Text(if (strongAvailable) "Fingerprint primary • ${app.vault.kind().label} fallback" else app.vault.kind().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!active) FilledTonalButton(onClick = { tab = 2 }) { Text("Complete setup") }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .6f)) {
                                        Text(
                                            when (settings.rule) {
                                                LockEngine.Rule.ON_EXIT -> "Relock: Immediate"
                                                LockEngine.Rule.SCREEN_OFF -> "Relock: Screen off"
                                                LockEngine.Rule.TIMEOUT -> "Relock: ${settings.seconds}s"
                                            },
                                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .6f)) {
                                        Text(
                                            if (settings.hideNotifications && notificationAccessGranted) "Notif Shield: Active ($totalHidden hidden)"
                                            else if (settings.hideNotifications) "Notif Shield: Grant access"
                                            else "Notif Shield: Off",
                                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .6f)) {
                                        Text(
                                            if (settings.uninstallProtection || deviceAdminActive) "Uninstall Guard: Locked apps + Sam" else "Uninstall Guard: Off",
                                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (tab != 2) {
                        item(key = "quick-security-tiles") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickFeatureTile(
                                    modifier = Modifier.weight(1f),
                                    title = "Hide Notifications",
                                    subtitle = if (notificationAccessGranted) "$totalHidden hidden" else "Tap to configure",
                                    active = settings.hideNotifications && notificationAccessGranted,
                                    onClick = {
                                        if (!notificationAccessGranted) {
                                            app.config.prefs.edit { putBoolean("hide_notifications", true) }
                                            launchSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        } else {
                                            app.config.prefs.edit { putBoolean("hide_notifications", !settings.hideNotifications) }
                                        }
                                    }
                                )
                                QuickFeatureTile(
                                    modifier = Modifier.weight(1f),
                                    title = "Uninstall Guard",
                                    subtitle = if (deviceAdminActive || settings.uninstallProtection) "Locked apps & Sam only" else "Tap to enable",
                                    active = settings.uninstallProtection || deviceAdminActive,
                                    onClick = {
                                        val next = !settings.uninstallProtection
                                        app.config.prefs.edit { putBoolean("uninstall_protection", next) }
                                        if (next && !deviceAdminActive) requestDeviceAdmin()
                                    }
                                )
                            }
                        }
                        item(key = "app-search") {
                            Text(if (tab == 0) "Your locked apps" else "Choose apps to lock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(search, { search = it }, placeholder = { Text("Search name or package") }, label = { Text("Search apps") },
                                shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(), singleLine = true)
                            if (tab == 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                AppFilter.entries.forEach { kind -> FilterChip(selected = filter == kind, onClick = { filter = kind }, label = { Text(kind.label) }) }
                            }
                        }
                        val suggested = rows.filter { it.pkg !in selected && suggestedCategory(it.pkg) != null }
                        if (search.isBlank() && suggested.isNotEmpty() && (tab == 1 || selected.isEmpty())) item(key = "suggestions") {
                            var expanded by remember { mutableStateOf(false) }
                            LockerCard("Suggested apps", subtitle = "Recommended privacy-sensitive apps on this phone") {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    suggested.take(if (expanded) 8 else 4).forEach { row ->
                                        AssistChip(onClick = { if (authorized) app.config.toggle(row.pkg, true) },
                                            label = { Text("Lock ${row.label}") },
                                            leadingIcon = { Image(row.icon.asImageBitmap(), null, Modifier.size(20.dp)) })
                                    }
                                }
                                if (suggested.size > 4) TextButton(onClick = { expanded = !expanded }) {
                                    Text(if (expanded) "Fewer suggestions" else "More suggestions")
                                }
                            }
                        }
                        if (loadingApps && rows.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        if (loadError) item { LockerCard("Couldn’t load apps") { TextButton(onClick = ::refreshApps) { Text("Try again") } } }
                        if (filtered.isEmpty() && !loadingApps) item {
                            LockerCard(if (search.isNotBlank()) "No matching apps" else if (tab == 0) "Your private space starts here" else "All clear") {
                                Text(if (tab == 0 && search.isBlank()) "Add the apps you want to keep private. They’ll be grouped here." else "Try another search or filter.")
                                if (tab == 0) Button(onClick = { tab = 1; search = "" }) { Text("Choose apps") }
                            }
                        }
                        items(filtered, key = { it.pkg }) { row ->
                            val isLocked = row.pkg in selected
                            val hiddenForApp = hiddenMap[row.pkg] ?: 0
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(end = if (tab == 0) 4.dp else 0.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(Modifier.weight(1f).toggleable(value = isLocked, role = Role.Switch, onValueChange = { value ->
                                        if (authorized) {
                                            app.config.toggle(row.pkg, value)
                                            scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(if (value) "${row.label} moved to Locked" else "${row.label} moved to Unlocked") }
                                        }
                                    }).clearAndSetSemantics {
                                        contentDescription = "Lock ${row.label}, ${row.pkg}"
                                        stateDescription = if (isLocked) "Locked" else "Not locked"
                                        toggleableState = ToggleableState(isLocked); role = Role.Switch
                                    }.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = if (tab == 0) 4.dp else 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) { Image(row.icon.asImageBitmap(), null, Modifier.padding(6.dp).size(34.dp)) }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(row.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(appCategoryBadge(row.pkg, row.system), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (hiddenForApp > 0) {
                                                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                                        Text("$hiddenForApp notif hidden", Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                    }
                                                }
                                            }
                                        }
                                        Switch(
                                            checked = isLocked,
                                            onCheckedChange = null,
                                            thumbContent = if (isLocked) ({ SwitchTick() }) else null
                                        )
                                    }
                                    if (tab == 0) Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                                        var menuOpen by remember(row.pkg) { mutableStateOf(false) }
                                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics {
                                            contentDescription = "More actions for ${row.label}"
                                        }) { Text("\u22ee", style = MaterialTheme.typography.headlineSmall) }
                                        DropdownMenu(menuOpen, { menuOpen = false }) {
                                            DropdownMenuItem(text = { Text("Move to Unlocked") }, onClick = {
                                                menuOpen = false
                                                if (authorized) {
                                                    app.config.toggle(row.pkg, false)
                                                    scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar("${row.label} moved to Unlocked") }
                                                }
                                            })
                                            DropdownMenuItem(text = { Text("App info") }, onClick = {
                                                menuOpen = false
                                                launchSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${row.pkg}".toUri()))
                                            })
                                            if (!row.system) {
                                                DropdownMenuItem(text = { Text("Uninstall\u2026") }, onClick = {
                                                    menuOpen = false; pendingRemoval = row; error = ""; preferPin = false; biometricAutoLaunched = false
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Text("Launcher apps on this profile. Essential recovery apps stay available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                    item { LockerCard("Setup & permissions", badge = if (active) "Active" else "Action Needed") {
                        Text("Accessibility: ${if (accessibilityEnabled()) "enabled" else "not enabled"}; service: ${if (Health.connected.value) "connected" else "disconnected"}")
                        Text("Window access identifies app packages only. No screen text, passwords, or child views are read. An opaque accessibility overlay covers locked apps.")
                        Button(onClick = { launchSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open accessibility settings") }
                        Text("For a sideloaded APK: Settings → Apps → Sam App Locker → ⋮ → Allow restricted settings, if offered. Then enable the installed accessibility service. Keep Play Protect enabled.")
                    } }
                    item { LockerCard("Notification privacy", subtitle = "Hide status-bar & lock-screen notifications for locked apps", badge = if (settings.hideNotifications && notificationAccessGranted) "Protected" else "Off") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Hide notifications for locked apps", style = MaterialTheme.typography.titleMedium)
                                Text("Automatically suppresses incoming and active notifications from locked apps while they are not unlocked in the foreground.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = settings.hideNotifications, thumbContent = if (settings.hideNotifications) ({ SwitchTick() }) else null, onCheckedChange = { enabled ->
                                if (authorized) {
                                    app.config.prefs.edit { putBoolean("hide_notifications", enabled) }
                                    if (enabled && !notificationAccessGranted) {
                                        launchSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    }
                                }
                            })
                        }
                        Text("Notification access: ${if (notificationAccessGranted) "enabled" else "not enabled"}; listener: ${if (NotificationHealth.connected.value) "connected" else "disconnected"} • Hidden this session: $totalHidden",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { launchSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                                Text(if (notificationAccessGranted) "Manage notification access" else "Grant notification access")
                            }
                            if (totalHidden > 0) {
                                TextButton(onClick = { NotificationHealth.reset() }) { Text("Reset counter") }
                            }
                        }
                    } }
                    item { LockerCard("Authentication", badge = app.vault.kind().label) {
                        val strong = BiometricManager.from(this@MainActivity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        Text(if (strong == BiometricManager.BIOMETRIC_SUCCESS) "Strong biometric available. Fingerprint opens first; other methods are hidden in the three-dot menu." else "Strong biometric unavailable or not enrolled. Use your chosen app credential.")
                        Text("Face unlock is offered only if Android exposes it as a strong biometric. Many Samsung face configurations do not qualify.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-confirm PIN", style = MaterialTheme.typography.titleMedium)
                                Text("Unlock after the last digit. Turn off to require Continue.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = settings.autoPin, thumbContent = if (settings.autoPin) ({ SwitchTick() }) else null, onCheckedChange = { if (authorized) app.config.prefs.edit { putBoolean("auto_pin", it) } })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Hide pattern trail", style = MaterialTheme.typography.titleMedium)
                                Text("Stealth pattern mode: hides drawn lines and dot highlights on the lock screen.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = settings.hidePatternTrail, thumbContent = if (settings.hidePatternTrail) ({ SwitchTick() }) else null, onCheckedChange = { if (authorized) app.config.prefs.edit { putBoolean("hide_pattern", it) } })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Scramble PIN keypad", style = MaterialTheme.typography.titleMedium)
                                Text("Randomizes digit positions on the PIN pad each time to prevent shoulder surfing.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = settings.scramblePin, thumbContent = if (settings.scramblePin) ({ SwitchTick() }) else null, onCheckedChange = { if (authorized) app.config.prefs.edit { putBoolean("scramble_pin", it) } })
                        }
                        Text("Current fallback: ${app.vault.kind().label}", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { if (authorized) { enrollmentKind = app.vault.kind(); changingCredential = true; enrolled = false; authorized = false; confirmation = false; error = "" } }) { Text("Change PIN, pattern or password") }
                    } }
                    item { LockerCard("Relock rules") {
                        LockEngine.Rule.entries.forEach { rule ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = settings.rule == rule, onClick = { if (authorized) app.config.prefs.edit { putString("rule", rule.name) } })
                                Text(when (rule) { LockEngine.Rule.ON_EXIT -> "Immediately after leaving"; LockEngine.Rule.SCREEN_OFF -> "On screen off / device lock"; LockEngine.Rule.TIMEOUT -> "After timeout" })
                            }
                        }
                        if (settings.rule == LockEngine.Rule.TIMEOUT) {
                            Text("Timeout: ${settings.seconds} seconds")
                            Slider(value = settings.seconds.toFloat(), valueRange = 15f..600f, onValueChange = { if (authorized) app.config.prefs.edit { putInt("seconds", it.toInt()) } })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = settings.shared, thumbContent = if (settings.shared) ({ SwitchTick() }) else null, onCheckedChange = { if (authorized) app.config.prefs.edit { putBoolean("shared", it) } })
                            Text("Shared session (less private)", Modifier.padding(start = 12.dp))
                        }
                        Text("Default: authenticate each app separately. Screen off and service/process restart always clear every session.")
                    } }
                    item { LockerCard("Uninstall protection", badge = if (settings.uninstallProtection || deviceAdminActive) "Protected" else "Standard") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Guard locked apps & Sam App Locker uninstall", style = MaterialTheme.typography.titleMedium)
                                Text("Requires authentication only when uninstalling a locked app or Sam App Locker. Unlocked apps can be uninstalled normally.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = settings.uninstallProtection, thumbContent = if (settings.uninstallProtection) ({ SwitchTick() }) else null, onCheckedChange = { enabled ->
                                if (authorized) {
                                    app.config.prefs.edit { putBoolean("uninstall_protection", enabled) }
                                }
                            })
                        }
                        Text("Device Admin status: ${if (deviceAdminActive) "active (blocks launcher & system uninstall of Sam App Locker)" else "not active"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!deviceAdminActive) {
                                Button(onClick = { requestDeviceAdmin() }) { Text("Enable Device Admin") }
                            } else {
                                OutlinedButton(onClick = {
                                    if (authorized) {
                                        LockerDeviceAdminReceiver.disableIfActive(this@MainActivity)
                                        deviceAdminActive = false
                                    }
                                }) { Text("Disable Device Admin") }
                            }
                            TextButton(onClick = { launchSelfUninstall() }) { Text("Uninstall Sam App Locker\u2026") }
                        }
                        Text("In-app confirmation · Always on: Uninstall from the Locked tab requires fresh fingerprint or PIN authentication before Android’s confirmation.", style = MaterialTheme.typography.bodySmall)
                    } }
                    item { LockerCard("Samsung battery setup") {
                        Text("Settings → Apps → Sam App Locker → Battery → Unrestricted, if available. Under Battery → Background usage limits, remove this app from Sleeping and Deep sleeping apps; add it to Never sleeping apps if offered. Labels vary by One UI build.")
                        Button(onClick = { launchSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) }) { Text("Open app settings") }
                        Text("Force-stop, safe mode, permission revocation, and uninstall interrupt protection. Only you can enable the accessibility service again.")
                    } }
                    item { AppearancePicker(settings.appearance) { if (authorized) app.config.setAppearance(it) } }
                    item { LockerCard("Comfort & feedback") {
                        listOf(Triple("biometrics", "Use fingerprint first", settings.biometrics),
                            Triple("haptics", "Touch feedback", settings.haptics),
                            Triple("bright", "Brighter authentication screen", settings.bright)).forEach { (key, label, checked) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, Modifier.weight(1f))
                                Switch(checked, { if (authorized) app.config.prefs.edit { putBoolean(key, it) } }, thumbContent = if (checked) ({ SwitchTick() }) else null)
                            }
                        }
                        Text("Brightness changes only the lock window, not your phone's saved brightness.", style = MaterialTheme.typography.bodySmall)
                    } }
                    item { LockerCard("Diagnostics") {
                        Text("Local operational events only; no screen text or credentials. Timings are frame submission, not physical presentation.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Record local diagnostics", Modifier.weight(1f))
                            Switch(settings.logging, { if (authorized) app.config.prefs.edit { putBoolean("logging", it) } }, thumbContent = if (settings.logging) ({ SwitchTick() }) else null)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { exportReport(false) }) { Text("Export diagnostics") }
                            TextButton(onClick = { exportReport(true) }) { Text("Export security report") }
                            TextButton(onClick = { if (authorized) Diagnostics.clear() }) { Text("Clear log") }
                        }
                        TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Hide diagnostics" else "View local diagnostics") }
                        if (showLog) Text(Diagnostics.text(), style = MaterialTheme.typography.bodySmall)
                        Text("Widgets, Recents thumbnails and external displays are separate exposure surfaces.")
                        Text("Based on AppLock • © 2023–2025 Pranav Purwar • MIT License", style = MaterialTheme.typography.labelSmall)
                    } }
                    }
                }
            }
        }
        }
    }
}

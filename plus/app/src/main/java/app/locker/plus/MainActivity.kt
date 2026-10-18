package app.locker.plus

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import dev.pranav.applock.features.applist.domain.AppSearchManager
import kotlinx.coroutines.launch

data class AppRow(val pkg: String, val label: String, val icon: Bitmap, val system: Boolean)
class MainActivity : FragmentActivity() {
    private var authorized by mutableStateOf(false)
    private var loaded by mutableStateOf(false)
    private var enrolled by mutableStateOf(false)
    private var error by mutableStateOf("")
    private var confirm: CharArray? = null
    private var enrollmentKind by mutableStateOf(CredentialKind.PIN)
    private var confirmation by mutableStateOf(false)
    private var accessibilityGranted by mutableStateOf(false)
    private var rows by mutableStateOf<List<AppRow>>(emptyList())
    private var showLog by mutableStateOf(false)
    private var loadingApps by mutableStateOf(false)
    private var loadError by mutableStateOf(false)
    private var lifecycleEpoch = 0L
    private var resumeEpoch by mutableIntStateOf(0)
    private var preferPin by mutableStateOf(false)
    private var pendingRemoval by mutableStateOf<AppRow?>(null)
    private lateinit var biometricPrompt: BiometricPrompt
    private var biometricPending = false
    private var biometricEpoch = -1L
    private var biometricRemoval: AppRow? = null
    private val strongAvailable get() = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (!biometricPending || biometricEpoch != lifecycleEpoch) return
                biometricPending = false
                val removal = biometricRemoval
                if (removal != null) {
                    if (pendingRemoval?.pkg == removal.pkg && authorized) launchRemoval(removal)
                } else { authorized = true; error = ""; preferPin = false }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!biometricPending || biometricEpoch != lifecycleEpoch) return
                biometricPending = false; preferPin = true
                error = if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) "" else "Fingerprint unavailable. Use your app credential."
            }
        })
        app.credentials.execute { val exists = app.vault.exists(); runOnUiThread { enrolled = exists; loaded = true } }
        setContent {
            LockerTheme(app.config.state.value.dynamic) {
                val removal = pendingRemoval
                LaunchedEffect(loaded, enrolled, authorized, resumeEpoch, removal?.pkg) {
                    if (loaded && enrolled && (!authorized || removal != null) && !preferPin && strongAvailable) startBiometric()
                }
                if (!loaded) Surface(Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else if (removal != null && authorized) AuthPanel(
                    title = "Uninstall ${removal.label}?",
                    subtitle = "Authenticate to open Android’s uninstall confirmation. Removal outside Sam App Locker is controlled by Android.",
                    identity = removal.pkg, error = error, preferPin = preferPin, credentialKind = app.vault.kind(),
                    autoPinLength = if (app.config.state.value.autoPin) app.vault.pinLength() else 0,
                    onPin = { pin, done -> authenticateRemoval(removal, pin, done) },
                    onBiometric = if (strongAvailable) ({ startBiometric() }) else null,
                    onHome = { pendingRemoval = null; error = ""; preferPin = false }, exitLabel = "Keep app")
                else if (!authorized) AuthPanel(
                    title = if (enrolled) "Welcome back" else if (confirmation) "Confirm your ${enrollmentKind.label.lowercase()}" else "Choose your app lock",
                    subtitle = if (enrolled) "Your apps. Your privacy." else enrollmentKind.hint,
                    credentialKind = if (enrolled) app.vault.kind() else enrollmentKind,
                    onKindChange = if (!enrolled && !confirmation) ({ kind -> enrollmentKind = kind; error = "" }) else null,
                    identity = if (enrolled) "login" else if (confirmation) "confirm" else "enroll",
                    error = error, preferPin = preferPin,
                    autoPinLength = if (enrolled && app.config.state.value.autoPin) app.vault.pinLength() else 0,
                    onPin = { pin, done -> authenticate(pin, done) },
                    onBiometric = if (enrolled && strongAvailable) ({ startBiometric() }) else null)
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
        biometricPending = true; biometricEpoch = lifecycleEpoch; biometricRemoval = pendingRemoval
        biometricPrompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (pendingRemoval == null) "Open Sam App Locker" else "Confirm app removal")
            .setSubtitle(pendingRemoval?.label ?: "Authenticate to manage your protected apps")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).setNegativeButtonText("Use ${app.vault.kind().label}").build())
    }
    private fun authenticate(pin: CharArray, done: () -> Unit) {
        if (!loaded || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            pin.fill('\u0000'); done(); return
        }
        val epoch = lifecycleEpoch
        if (!enrolled) {
            if (!confirmation) { confirm?.fill('\u0000'); confirm = pin; confirmation = true; error = ""; done(); return }
            if (confirm?.contentEquals(pin) != true) { confirm?.fill('\u0000'); confirm = null; pin.fill('\u0000'); confirmation = false; error = "Entries did not match. Try again."; done(); return }
            confirm?.fill('\u0000'); confirm = null
            app.credentials.execute {
                val selectedKind = enrollmentKind
                val success = runCatching { app.vault.enroll(pin, selectedKind) }.isSuccess
                runOnUiThread {
                    // A completed credential change must revoke app sessions even
                    // if the owner backgrounds this Activity while the KDF runs.
                    if (success) app.config.prefs.edit().putLong("credential_epoch", System.nanoTime()).apply()
                    if (epoch == lifecycleEpoch) { enrolled = success; authorized = success; confirmation = false; error = if (success) "" else "Could not save credential securely" }
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
        pendingRemoval = null; error = ""; preferPin = false
        // Android owns the final confirmation. No device-owner/admin privileges.
        runCatching { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${row.pkg}"))) }
            .onFailure { error = "Android could not open uninstall. Use the phone’s app settings." }
    }
    override fun onResume() { super.onResume(); accessibilityGranted = accessibilityEnabled(); resumeEpoch++; refreshApps() }
    override fun onPause() {
        super.onPause()
        // A quick Home/return can resume before onStop. Never carry an
        // authenticated credential-change form across that pause boundary.
        if (!enrolled && loaded) invalidateManagement()
    }
    override fun onStop() { super.onStop(); invalidateManagement() }
    private fun invalidateManagement() {
        lifecycleEpoch++; biometricPending = false
        if (::biometricPrompt.isInitialized) biometricPrompt.cancelAuthentication()
        authorized = false; pendingRemoval = null; preferPin = false
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
        runCatching { startActivity(intent) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
    private fun accessibilityEnabled(): Boolean {
        val component = ComponentName(this, LockerService::class.java).flattenToString()
        return Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')?.any { it.equals(component, true) } == true
    }
    @Composable private fun Dashboard() {
        var search by remember { mutableStateOf("") }
        var tab by remember { mutableIntStateOf(0) }
        var filter by remember { mutableStateOf(AppFilter.ALL) }
        val settings by app.config.state
        val selected = settings.apps
        val active = Health.connected.value && !Health.interrupted.value && accessibilityGranted
        val status = if (active) "Protection active" else if (Health.interrupted.value || accessibilityGranted) "Protection interrupted" else "Setup incomplete"
        val snack = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val filtered = rows.filter { appMatches(it.pkg, it.label, it.system, selected, tab == 0, filter, search) }
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = { NavigationBar {
                listOf("Locked", "Unlocked", "Settings").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index; search = "" },
                        icon = { NavigationMark(index) },
                        label = { Text(label) })
                }
            } }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("YOUR PRIVATE SPACE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Text("Sam App Locker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                PrivacyMark(Modifier.padding(12.dp).size(24.dp))
                            }
                        }
                    }
                    item {
                        Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .6f)))).padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .65f)) { PrivacyMark(modifier = Modifier.padding(14.dp).size(28.dp)) }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${selected.size} apps in your private space", style = MaterialTheme.typography.bodyMedium)
                                    Text(if (strongAvailable) "${app.vault.kind().label} + fingerprint" else app.vault.kind().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!active) TextButton(onClick = { tab = 2 }) { Text("Complete setup") }
                                }
                            }
                        }
                    }
                    if (tab != 2) {
                        val suggested = rows.filter { it.pkg !in selected && suggestedCategory(it.pkg) != null }
                        if (search.isBlank() && suggested.isNotEmpty() && (tab == 1 || selected.isEmpty())) item {
                            LockerCard("A little more privacy") {
                                Text("Suggested for you", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text("Chats, messages, photos and notes often contain personal information. Choose what belongs in your private space.")
                                suggested.take(8).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Image(row.icon.asImageBitmap(), null, Modifier.size(40.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(row.label, style = MaterialTheme.typography.titleSmall)
                                            Text(suggestedCategory(row.pkg)!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        FilledTonalButton(onClick = { if (authorized) app.config.toggle(row.pkg, true) }) { Text("Lock") }
                                    }
                                }
                                Text("Suggestions only. Nothing is locked until you choose it.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        item {
                            Text(if (tab == 0) "Your locked apps" else "Choose apps to lock", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(search, { search = it }, placeholder = { Text("Search name or package") }, label = { Text("Search apps") },
                                shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(), singleLine = true)
                            if (tab == 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                AppFilter.entries.forEach { kind -> FilterChip(selected = filter == kind, onClick = { filter = kind }, label = { Text(kind.label) }) }
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
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column {
                                    Row(Modifier.fillMaxWidth().toggleable(value = row.pkg in selected, role = Role.Switch, onValueChange = { value ->
                                        if (authorized) {
                                            app.config.toggle(row.pkg, value)
                                            scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(if (value) "${row.label} moved to Locked" else "${row.label} moved to Unlocked") }
                                        }
                                    }).clearAndSetSemantics {
                                        contentDescription = "Lock ${row.label}, ${row.pkg}"
                                        stateDescription = if (row.pkg in selected) "Locked" else "Not locked"
                                        toggleableState = ToggleableState(row.pkg in selected); role = Role.Switch
                                    }.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)) { Image(row.icon.asImageBitmap(), null, Modifier.padding(8.dp).size(40.dp)) }
                                        Column(Modifier.weight(1f)) {
                                            Text(row.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Text(if (row.system) "System app" else "Downloaded app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = row.pkg in selected, onCheckedChange = null)
                                    }
                                    if (tab == 0 && !row.system) TextButton(onClick = { pendingRemoval = row; error = ""; preferPin = false },
                                        modifier = Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 4.dp)) { Text("Uninstall…") }
                                }
                            }
                        }
                        item { Text("Launcher apps on this profile. Essential recovery apps stay available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                    item { LockerCard("Setup & permissions") {
                        Text("Accessibility: ${if (accessibilityEnabled()) "enabled" else "not enabled"}; service: ${if (Health.connected.value) "connected" else "disconnected"}")
                        Text("Window access identifies app packages only. No screen text, passwords, or child views are read. An opaque accessibility overlay covers locked apps.")
                        Button(onClick = { launchSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open accessibility settings") }
                        Text("For a sideloaded APK: Settings → Apps → Sam App Locker → ⋮ → Allow restricted settings, if offered. Then enable the installed accessibility service. Keep Play Protect enabled.")
                        Text("No usage access, draw-over-apps access, notification permission, device admin, or foreground service is required.")
                    } }
                    item { LockerCard("Authentication") {
                        val strong = BiometricManager.from(this@MainActivity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        Text(if (strong == BiometricManager.BIOMETRIC_SUCCESS) "Strong biometric available. Fingerprint opens first; your chosen fallback stays ready underneath." else "Strong biometric unavailable or not enrolled. Use your chosen app credential.")
                        Text("Face unlock is offered only if Android exposes it as a strong biometric. Many Samsung face configurations do not qualify.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-confirm PIN", style = MaterialTheme.typography.titleMedium)
                                Text("Unlock after the last digit. Turn off to require Continue.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = settings.autoPin, onCheckedChange = { if (authorized) app.config.prefs.edit().putBoolean("auto_pin", it).apply() })
                        }
                        Text("Current fallback: ${app.vault.kind().label}", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { if (authorized) { enrollmentKind = app.vault.kind(); enrolled = false; authorized = false; confirmation = false; error = "" } }) { Text("Change PIN, pattern or password") }
                    } }
                    item { LockerCard("Relock rules") {
                        LockEngine.Rule.entries.forEach { rule ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = settings.rule == rule, onClick = { if (authorized) app.config.prefs.edit().putString("rule", rule.name).apply() })
                                Text(when (rule) { LockEngine.Rule.ON_EXIT -> "Immediately after leaving"; LockEngine.Rule.SCREEN_OFF -> "On screen off / device lock"; LockEngine.Rule.TIMEOUT -> "After timeout" })
                            }
                        }
                        if (settings.rule == LockEngine.Rule.TIMEOUT) {
                            Text("Timeout: ${settings.seconds} seconds")
                            Slider(value = settings.seconds.toFloat(), valueRange = 15f..600f, onValueChange = { if (authorized) app.config.prefs.edit().putInt("seconds", it.toInt()).apply() })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = settings.shared, onCheckedChange = { if (authorized) app.config.prefs.edit().putBoolean("shared", it).apply() })
                            Text("Shared session (less private)", Modifier.padding(start = 12.dp))
                        }
                        Text("Default: authenticate each app separately. Screen off and service/process restart always clear every session.")
                    } }
                    item { LockerCard("Samsung battery setup") {
                        Text("Settings → Apps → Sam App Locker → Battery → Unrestricted, if available. Under Battery → Background usage limits, remove this app from Sleeping and Deep sleeping apps; add it to Never sleeping apps if offered. Labels vary by One UI build.")
                        Button(onClick = { launchSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }) { Text("Open app settings") }
                        Text("Force-stop, safe mode, permission revocation, and uninstall interrupt protection. Only you can enable the accessibility service again.")
                    } }
                    item { LockerCard("Appearance & diagnostics") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = settings.dynamic, onCheckedChange = { if (authorized) app.config.prefs.edit().putBoolean("dynamic", it).apply() })
                            Text("Dynamic colors", Modifier.padding(start = 12.dp))
                        }
                        Text("Light and dark themes follow your phone. Diagnostic timings measure draw submission, not actual display presentation.")
                        TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Hide diagnostics" else "View local diagnostics") }
                        if (showLog) Text(Diagnostics.text(), style = MaterialTheme.typography.bodySmall)
                        Text("Notifications, widgets, Recents thumbnails and external displays are separate exposure surfaces. This app does not secure them.")
                        Text("Based on AppLock • © 2023–2025 Pranav Purwar • MIT License", style = MaterialTheme.typography.labelSmall)
                    } }
                        item { LockerCard("Uninstall protection") {
                            Text("In-app confirmation · Always on", style = MaterialTheme.typography.titleSmall)
                            Text("Uninstall from the Locked tab requires fresh fingerprint or PIN authentication before Android’s confirmation.")
                            Text("System-wide blocking · Unavailable", style = MaterialTheme.typography.titleSmall)
                            Text("Samsung Settings, the launcher and other tools can still remove locked apps without this app’s PIN.")
                            Text("Sam App Locker removal · Not blocked", style = MaterialTheme.typography.titleSmall)
                            Text("This installation cannot prevent its own removal, force-stop or accessibility disablement. Uninstall blocking requires separate managed-device provisioning; there is no ordinary permission switch that enables it.")
                        } }
                    }
                }
            }
        }
    }
}


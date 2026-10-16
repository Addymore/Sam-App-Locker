package app.locker.plus

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import dev.pranav.applock.features.applist.domain.AppSearchManager

data class AppRow(val pkg: String, val label: String, val icon: Bitmap)
class MainActivity : FragmentActivity() {
    private var authorized by mutableStateOf(false)
    private var loaded by mutableStateOf(false)
    private var enrolled by mutableStateOf(false)
    private var error by mutableStateOf("")
    private var confirm: CharArray? = null
    private var confirmation by mutableStateOf(false)
    private var revision by mutableStateOf(0)
    private var rows by mutableStateOf<List<AppRow>>(emptyList())
    private var showLog by mutableStateOf(false)
    private var lifecycleEpoch = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        app.credentials.execute { val exists = app.vault.exists(); runOnUiThread { enrolled = exists; loaded = true } }
        app.io.execute {
            val apps = kotlinx.coroutines.runBlocking { AppSearchManager(this@MainActivity).loadApps() }.mapNotNull { info ->
                runCatching { AppRow(info.packageName, info.loadLabel(packageManager).toString(), info.loadIcon(packageManager).toBitmap(96, 96)) }.getOrNull()
            }.filter { it.pkg !in setOf("com.android.settings", "com.samsung.android.dialer", "com.google.android.dialer", "com.android.phone") }
            runOnUiThread { rows = apps }
        }
        setContent {
            val ignored = revision
            LockerTheme(app.config.dynamic()) {
                if (!loaded) Surface(Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else if (!authorized) AuthPanel(
                    title = if (enrolled) "Welcome back" else if (confirmation) "Confirm your PIN" else "Create a private PIN",
                    subtitle = if (enrolled) "Authenticate to change apps and security settings" else "Choose 6–12 digits. This PIN is separate from your phone PIN.", error = error,
                    onPin = { pin, done -> authenticate(pin, done) })
                else Dashboard(revision)
            }
        }
    }
    private fun authenticate(pin: CharArray, done: () -> Unit) {
        val epoch = lifecycleEpoch
        if (!enrolled) {
            if (!confirmation) { confirm?.fill('\u0000'); confirm = pin; confirmation = true; error = ""; done(); return }
            if (confirm?.contentEquals(pin) != true) { confirm?.fill('\u0000'); confirm = null; pin.fill('\u0000'); confirmation = false; error = "PINs did not match. Try again."; done(); return }
            confirm?.fill('\u0000'); confirm = null
            app.credentials.execute {
                val success = runCatching { app.vault.enroll(pin) }.isSuccess
                runOnUiThread { if (epoch == lifecycleEpoch) { enrolled = success; authorized = success; confirmation = false; error = if (success) "" else "Could not save PIN securely" }; done() }
            }
        } else app.credentials.execute {
            val result = app.vault.verify(pin)
            runOnUiThread { if (epoch == lifecycleEpoch) { authorized = result.success; error = if (result.success) "" else result.message }; done() }
        }
    }
    override fun onResume() { super.onResume(); revision++ }
    override fun onStop() { super.onStop(); lifecycleEpoch++; authorized = false; enrolled = app.vault.exists(); confirm?.fill('\u0000'); confirm = null; confirmation = false; error = "" }
    private fun launchSettings(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
    private fun accessibilityEnabled(): Boolean {
        val component = ComponentName(this, LockerService::class.java).flattenToString()
        return Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')?.any { it.equals(component, true) } == true
    }
    @Composable private fun Dashboard(version: Int) {
        var search by remember { mutableStateOf("") }
        val selected = app.config.apps()
        val active = Health.connected.value && !Health.interrupted.value && accessibilityEnabled()
        val status = if (active) "Protection active" else if (Health.interrupted.value || accessibilityEnabled()) "Protection interrupted" else "Setup incomplete"
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("Sam App Locker", style = MaterialTheme.typography.headlineLarge); Text("Privacy, at your pace", color = MaterialTheme.colorScheme.primary) }
                    item { LockerCard(status) {
                        Text("${selected.size} selected apps • offline protection")
                        Text(if (active) "Window monitoring is connected. Protection starts after Android reports a window; native zero-frame protection is not guaranteed." else Health.detail.value)
                        Text("Pop-up / split screen: experimental. DeX and external displays are unsupported.", style = MaterialTheme.typography.bodySmall)
                    } }
                    item { LockerCard("Setup & permissions") {
                        Text("Accessibility: ${if (accessibilityEnabled()) "enabled" else "not enabled"}; service: ${if (Health.connected.value) "connected" else "disconnected"}")
                        Text("Window access identifies app packages only. No screen text, passwords, or child views are read. An opaque accessibility overlay covers locked apps.")
                        Button(onClick = { launchSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open accessibility settings") }
                        Text("For a sideloaded APK: Settings → Apps → Sam App Locker → ⋮ → Allow restricted settings, if offered. Then enable the installed accessibility service. Keep Play Protect enabled.")
                        Text("No usage access, draw-over-apps access, notification permission, device admin, or foreground service is required.")
                    } }
                    item { LockerCard("Authentication") {
                        val strong = BiometricManager.from(this@MainActivity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        Text(if (strong == BiometricManager.BIOMETRIC_SUCCESS) "Strong biometric available. Fingerprint is offered on the lock shield." else "Strong biometric unavailable or not enrolled. PIN remains available.")
                        Text("Face unlock is offered only if Android exposes it as a strong biometric. Many Samsung face configurations do not qualify.")
                        TextButton(onClick = { if (authorized) { enrolled = false; authorized = false; confirmation = false; error = "" } }) { Text("Change app PIN") }
                    } }
                    item { LockerCard("Relock rules") {
                        LockEngine.Rule.entries.forEach { rule ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = app.config.rule() == rule, onClick = { if (authorized) { app.config.prefs.edit().putString("rule", rule.name).apply(); revision++ } })
                                Text(when (rule) { LockEngine.Rule.ON_EXIT -> "Immediately after leaving"; LockEngine.Rule.SCREEN_OFF -> "On screen off / device lock"; LockEngine.Rule.TIMEOUT -> "After timeout" })
                            }
                        }
                        if (app.config.rule() == LockEngine.Rule.TIMEOUT) {
                            Text("Timeout: ${app.config.seconds()} seconds")
                            Slider(value = app.config.seconds().toFloat(), valueRange = 15f..600f, onValueChange = { if (authorized) { app.config.prefs.edit().putInt("seconds", it.toInt()).apply(); revision++ } })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = app.config.shared(), onCheckedChange = { if (authorized) { app.config.prefs.edit().putBoolean("shared", it).apply(); revision++ } })
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
                            Switch(checked = app.config.dynamic(), onCheckedChange = { if (authorized) { app.config.prefs.edit().putBoolean("dynamic", it).apply(); revision++ } })
                            Text("Dynamic colors", Modifier.padding(start = 12.dp))
                        }
                        Text("Light and dark themes follow your phone. Diagnostic timings measure draw submission, not actual display presentation.")
                        TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Hide diagnostics" else "View local diagnostics") }
                        if (showLog) Text(Diagnostics.text(), style = MaterialTheme.typography.bodySmall)
                        Text("Notifications, widgets, Recents thumbnails and external displays are separate exposure surfaces. This app does not secure them.")
                        Text("Based on AppLock • © 2023–2025 Pranav Purwar • MIT License", style = MaterialTheme.typography.labelSmall)
                    } }
                    item { Text("Choose your apps", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(search, { search = it }, label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    items(rows.filter { it.label.contains(search, true) || it.pkg.contains(search, true) }, key = { it.pkg }) { row ->
                        Card(shape = MaterialTheme.shapes.medium) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Image(row.icon.asImageBitmap(), null, Modifier.size(44.dp))
                            Column(Modifier.weight(1f)) { Text(row.label, style = MaterialTheme.typography.titleMedium); Text(row.pkg, style = MaterialTheme.typography.bodySmall) }
                            Switch(checked = row.pkg in selected, onCheckedChange = { if (authorized) { app.config.toggle(row.pkg, it); revision++ } }, modifier = Modifier.semantics { contentDescription = "Lock ${row.label}" })
                        } }
                    }
                }
            }
        }
    }
}


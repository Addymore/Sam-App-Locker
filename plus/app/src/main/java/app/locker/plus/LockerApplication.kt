package app.locker.plus

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.Executors

class LockerApplication : Application() {
    lateinit var config: Config; private set
    lateinit var vault: PinVault; private set
    val credentials = Executors.newSingleThreadExecutor()
    val io = Executors.newSingleThreadExecutor()
    override fun onCreate() { super.onCreate(); config = Config(this); vault = PinVault(this) }
}
val Context.app get() = applicationContext as LockerApplication

class Config(context: Context) {
    val prefs = context.getSharedPreferences("plus-settings", Context.MODE_PRIVATE)
    private val locked = dev.pranav.applock.data.repository.LockedAppsRepository(context)
    private val appPrefs = context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
    data class UiState(val apps: Set<String>, val rule: LockEngine.Rule, val seconds: Int, val shared: Boolean, val dynamic: Boolean)
    private fun snapshot() = UiState(apps(), rule(), seconds(), shared(), dynamic())
    val state = mutableStateOf(snapshot())
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.value = snapshot() }
    init { prefs.registerOnSharedPreferenceChangeListener(listener); appPrefs.registerOnSharedPreferenceChangeListener(listener) }
    fun apps() = locked.getLockedApps()
    fun toggle(pkg: String, enabled: Boolean) {
        state.value = state.value.copy(apps = if (enabled) state.value.apps + pkg else state.value.apps - pkg)
        if (enabled) locked.addLockedApp(pkg) else locked.removeLockedApp(pkg)
    }
    fun rule() = runCatching { LockEngine.Rule.valueOf(prefs.getString("rule", "ON_EXIT")!!) }.getOrDefault(LockEngine.Rule.ON_EXIT)
    fun seconds() = prefs.getInt("seconds", 60)
    fun shared() = prefs.getBoolean("shared", false)
    fun dynamic() = prefs.getBoolean("dynamic", true)
}

object Health {
    val connected = mutableStateOf(false)
    val interrupted = mutableStateOf(false)
    val detail = mutableStateOf("Accessibility service not connected")
}

/** Bounded, local, content-free operational diagnostics. No package names or credentials. */
object Diagnostics {
    private val rows = ArrayDeque<String>()
    @Synchronized fun record(event: String, durationMs: Long? = null) {
        if (rows.size >= 1000) rows.removeFirst()
        rows.addLast("${SystemClock.elapsedRealtime()},$event,${durationMs ?: ""}")
        android.util.Log.i("SamLocker", rows.last())
    }
    @Synchronized fun text() = "elapsed_ms,event,duration_ms\n" + rows.joinToString("\n")
}

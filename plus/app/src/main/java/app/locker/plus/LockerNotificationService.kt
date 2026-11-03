package app.locker.plus

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

object NotificationHealth {
    val connected = mutableStateOf(false)
    val hiddenCount = mutableIntStateOf(0)
    private val perAppCounts = mutableMapOf<String, Int>()
    val byApp = mutableStateOf<Map<String, Int>>(emptyMap())

    @Synchronized fun recordHidden(pkg: String) {
        hiddenCount.intValue++
        perAppCounts[pkg] = (perAppCounts[pkg] ?: 0) + 1
        byApp.value = perAppCounts.toMap()
    }

    @Synchronized fun clearForPackage(pkg: String) {
        if (perAppCounts.remove(pkg) != null) {
            byApp.value = perAppCounts.toMap()
        }
    }

    @Synchronized fun reset() {
        hiddenCount.intValue = 0
        perAppCounts.clear()
        byApp.value = emptyMap()
    }

    fun isAccessGranted(context: Context): Boolean {
        val component = ComponentName(context, LockerNotificationService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabled?.split(':')?.any { it.equals(component, ignoreCase = true) } == true
    }
}

class LockerNotificationService : NotificationListenerService() {
    companion object {
        var instance: LockerNotificationService? = null
            private set
    }

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "hide_notifications" || key == "locked_apps") {
            sweepActiveNotifications()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        NotificationHealth.connected.value = true
        app.config.prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        getSharedPreferences("app_lock_prefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferenceListener)
        Diagnostics.record("notification_listener_connected")
        sweepActiveNotifications()
    }

    override fun onListenerDisconnected() {
        app.config.prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        getSharedPreferences("app_lock_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (instance === this) instance = null
        NotificationHealth.connected.value = false
        Diagnostics.record("notification_listener_disconnected")
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        NotificationHealth.connected.value = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        inspectAndHideIfNeeded(sbn)
    }

    fun sweepActiveNotifications() {
        if (!NotificationHealth.connected.value || !app.config.hideNotifications()) return
        runCatching {
            activeNotifications?.forEach { sbn -> inspectAndHideIfNeeded(sbn) }
        }
    }

    private fun inspectAndHideIfNeeded(sbn: StatusBarNotification) {
        if (!app.config.hideNotifications()) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || pkg == "android") return
        val lockedApps = app.config.apps()
        if (pkg !in lockedApps) return
        val category = sbn.notification?.category
        if (category == Notification.CATEGORY_CALL || category == Notification.CATEGORY_ALARM) return
        if (LockerService.instance?.isUnlockedInForeground(pkg) == true) return
        runCatching {
            cancelNotification(sbn.key)
            NotificationHealth.recordHidden(pkg)
            Diagnostics.record("notification_hidden")
        }
    }
}

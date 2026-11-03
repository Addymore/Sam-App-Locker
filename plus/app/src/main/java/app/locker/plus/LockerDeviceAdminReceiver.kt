package app.locker.plus

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf

object AdminHealth {
    val active = mutableStateOf(false)
}

class LockerDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        AdminHealth.active.value = true
        Diagnostics.record("device_admin_enabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Disabling Device Admin removes system uninstall protection for Sam App Locker."

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        AdminHealth.active.value = false
        Diagnostics.record("device_admin_disabled")
    }

    companion object {
        fun component(context: Context) = ComponentName(context, LockerDeviceAdminReceiver::class.java)
        fun isActive(context: Context): Boolean {
            val active = runCatching {
                context.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(component(context)) == true
            }.getOrDefault(false)
            AdminHealth.active.value = active
            return active
        }
        fun disableIfActive(context: Context) {
            runCatching {
                val dpm = context.getSystemService(DevicePolicyManager::class.java)
                val comp = component(context)
                if (dpm?.isAdminActive(comp) == true) dpm.removeActiveAdmin(comp)
            }
            AdminHealth.active.value = false
        }
    }
}

package app.locker.plus

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Configurator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs only on the isolated benchmark app, never the user's final installation. */
@RunWith(AndroidJUnit4::class)
class DeviceCyclesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = run {
        Configurator.getInstance().uiAutomationFlags = android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        UiDevice.getInstance(instrumentation)
    }
    private val targets = listOf("app.locker.fixture.red", "app.locker.fixture.blue")
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(message: String, timeout: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) { var result = false; main { result = condition() }; if (result) return; SystemClock.sleep(20) }
        fail(message)
    }
    @Test fun hundredLaunchSwitchCycles() {
        check(context.packageName.endsWith(".testbed")) { "Never run destructive fixture setup in the final app" }
        context.app.vault.enroll("246813".toCharArray())
        main {
            targets.forEach { context.app.config.toggle(it, true) }
            context.app.config.prefs.edit().putString("rule", "ON_EXIT").putBoolean("shared", false).commit()
        }
        await("User must re-enable Sam App Locker Test after instrumentation restarts its process", 180_000) { LockerService.instance != null }
        val report = StringBuilder("cycle,target,entry,challenge_observed_ms,pin_roundtrip_ms\n")
        repeat(100) { cycle ->
            device.pressHome()
            await("Home did not clear challenge") { LockerService.instance?.engine?.challenge == null }
            val target = targets[cycle % targets.size]
            val intent = when (cycle % 3) {
                0 -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                1 -> Intent(Intent.ACTION_VIEW, Uri.parse("lockerfixture://test/$cycle"))
                else -> Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Synthetic share")
            }.setClassName(target, "app.locker.fixture.FixtureActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val start = SystemClock.elapsedRealtime(); context.startActivity(intent)
            await("Missed lock cycle $cycle") { LockerService.instance?.engine?.challenge?.packageName == target }
            await("Shield was not attached cycle $cycle") { LockerService.instance?.shieldAttached == true }
            val observed = SystemClock.elapsedRealtime() - start
            var original: LockEngine.Challenge? = null
            main { original = LockerService.instance!!.engine.challenge }
            val latch = CountDownLatch(1); val authStart = SystemClock.elapsedRealtime()
            main { LockerService.instance!!.verifyPin(original!!, "246813".toCharArray()) { latch.countDown() } }
            assertTrue("PIN verification timeout", latch.await(15, TimeUnit.SECONDS))
            await("Shield did not clear after PIN cycle $cycle") { LockerService.instance?.engine?.shieldRequired == false }
            await("Shield remained attached cycle $cycle") { LockerService.instance?.shieldAttached == false }
            // Allow subsequent window callbacks to expose repeated-prompt regressions.
            SystemClock.sleep(150)
            main { assertFalse("Duplicate lock cycle $cycle", LockerService.instance!!.engine.shieldRequired) }
            report.append("$cycle,$target,${cycle % 3},$observed,${SystemClock.elapsedRealtime()-authStart}\n")
        }
        File(context.filesDir, "cycles.csv").writeText(report.toString())
        File(context.filesDir, "diagnostics.csv").writeText(Diagnostics.text())
        device.pressHome()
    }
}

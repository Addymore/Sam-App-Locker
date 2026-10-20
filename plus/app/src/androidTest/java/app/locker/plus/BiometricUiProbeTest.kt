package app.locker.plus

import android.content.Intent
import android.app.UiAutomation
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated runner controls only the synthetic fixture and our locker UI.
 * The normal app is NOT the instrumentation target, so its service is not restarted.
 * Requires owner setup in the normal app. Never reads or supplies its private PIN. */
@RunWith(AndroidJUnit4::class)
class BiometricUiProbeTest {
    @Test fun fingerprintButtonAndCancellation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName.endsWith(".testbed"))
        val lockerPackage = InstrumentationRegistry.getArguments().getString("lockerPackage", "app.sam.locker.debug")
        require(lockerPackage in setOf("app.sam.locker.debug", "app.sam.locker"))
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        Configurator.getInstance().waitForIdleTimeout = 100
        val device = UiDevice.getInstance(instrumentation)
        val launcher = instrumentation.targetContext.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        assertNotNull("A launcher is required for this entry-path test", launcher)
        device.wakeUp()
        val keyguard = instrumentation.targetContext.getSystemService(android.app.KeyguardManager::class.java)
        val deadline = android.os.SystemClock.elapsedRealtime() + 180_000
        while (keyguard.isKeyguardLocked && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(250)
        assertFalse("Owner must unlock the device before UI tests", keyguard.isKeyguardLocked)
        val cycles = InstrumentationRegistry.getArguments().getString("cycles", "1").toInt().coerceIn(1, 100)
        val mixedEntries = InstrumentationRegistry.getArguments().getString("mixedEntries", "false").toBoolean()
        repeat(cycles) { cycle ->
        device.pressHome()
        assertTrue("Launcher not ready before cycle $cycle", device.wait(Until.hasObject(By.pkg(launcher!!)), 10_000))
        assertTrue("Previous shield did not leave Home", device.wait(Until.gone(By.pkg(lockerPackage).text("App is locked")), 10_000))
        val target = if (cycle % 2 == 0) "app.locker.fixture.blue" else "app.locker.fixture.red"
        if (cycle % 10 == 0 && InstrumentationRegistry.getArguments().getString("coldEntries", "false") == "true") {
            device.executeShellCommand("am force-stop --user 0 $target")
        }
        val entryArgs = when (if (mixedEntries) cycle % 3 else 0) {
            0 -> "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
            1 -> "-a android.intent.action.VIEW -d lockerfixture://release-test/$cycle"
            else -> "-a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT synthetic"
        }
        val launch = device.executeShellCommand("am start --user 0 -W -n $target/app.locker.fixture.FixtureActivity $entryArgs")
        assertTrue("Fixture launch failed at $cycle: $launch", launch.contains("Status: ok"))
        // Fingerprint must appear without a tap in 1.1. The opaque shield is
        // committed before the service starts the biometric host.
        val promptDeadline = android.os.SystemClock.elapsedRealtime() + 15_000
        var promptVisible = false
        while (!promptVisible && android.os.SystemClock.elapsedRealtime() < promptDeadline) {
            promptVisible = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).windows.any { window ->
                val root = window.root
                val isPrompt = root?.packageName?.toString() == "com.samsung.android.biometrics.app.setting" && (window.isActive || window.isFocused)
                @Suppress("DEPRECATION")
                root?.recycle()
                isPrompt
            }
            if (!promptVisible) android.os.SystemClock.sleep(50)
        }
        assertTrue("Samsung biometric window did not appear", promptVisible)
        assertFalse("Credential keypad present while fingerprint is active", device.hasObject(By.pkg(lockerPackage).desc("Clear")))
        device.pressBack()
        assertTrue("Shield disappeared after cancellation", device.wait(Until.hasObject(By.pkg(lockerPackage).text("App is locked")), 10_000))
        assertFalse("Cancellation must not reveal the PIN", device.hasObject(By.pkg(lockerPackage).desc("Clear")))
        if (cycle % 10 == 0) {
            device.findObject(By.pkg(lockerPackage).text("Use fingerprint")).click()
            val retryDeadline = android.os.SystemClock.elapsedRealtime() + 10_000
            var retryVisible = false
            while (!retryVisible && android.os.SystemClock.elapsedRealtime() < retryDeadline) {
                retryVisible = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).windows.any { w ->
                    val r = w.root
                    val visible = r?.packageName?.toString() == "com.samsung.android.biometrics.app.setting" && (w.isActive || w.isFocused)
                    @Suppress("DEPRECATION") r?.recycle()
                    visible
                }
                if (!retryVisible) android.os.SystemClock.sleep(50)
            }
            assertTrue("Fingerprint retry failed", retryVisible)
            assertFalse(device.hasObject(By.pkg(lockerPackage).desc("Clear")))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.pkg(lockerPackage).text("App is locked")), 5_000))
        }
        device.wait(Until.findObject(By.pkg(lockerPackage).desc("More unlock options")), 5_000).click()
        device.wait(Until.findObject(By.pkg(lockerPackage).text("Use PIN")), 5_000).click()
        assertTrue("Explicit PIN fallback missing", device.wait(Until.hasObject(By.pkg(lockerPackage).desc("Clear")), 5_000))
        val finishDeadline = android.os.SystemClock.elapsedRealtime() + 5_000
        var hostPresent = true
        while (hostPresent && android.os.SystemClock.elapsedRealtime() < finishDeadline) {
            hostPresent = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).windows.any { window ->
                val root = window.root
                val isHost = window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION && root?.packageName?.toString() == lockerPackage
                @Suppress("DEPRECATION") root?.recycle()
                isHost
            }
            if (hostPresent) android.os.SystemClock.sleep(50)
        }
        assertFalse("Biometric Activity did not finish behind restored shield", hostPresent)
        if (InstrumentationRegistry.getArguments().getString("publicExit", "false") == "true") {
            val exit = device.executeShellCommand("am start --user 0 -W -a android.intent.action.MAIN -n app.locker.fixture.public/app.locker.fixture.FixtureActivity")
            assertTrue("Public fixture launch failed: $exit", exit.contains("Status: ok"))
            assertTrue("Protected-to-public exit left a locker window", device.wait(Until.gone(By.pkg(lockerPackage)), 5_000))
            assertTrue(device.hasObject(By.pkg("app.locker.fixture.public")))
        }
        if ((cycle + 1) % 10 == 0) instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Completed ${cycle + 1}/$cycles fingerprint cancellation cycles\n") })
        }
        device.pressHome()
    }
}

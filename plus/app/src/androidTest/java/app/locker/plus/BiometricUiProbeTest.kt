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
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        Configurator.getInstance().waitForIdleTimeout = 100
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        val keyguard = instrumentation.targetContext.getSystemService(android.app.KeyguardManager::class.java)
        val deadline = android.os.SystemClock.elapsedRealtime() + 180_000
        while (keyguard.isKeyguardLocked && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(250)
        assertFalse("Owner must unlock the device before UI tests", keyguard.isKeyguardLocked)
        val cycles = InstrumentationRegistry.getArguments().getString("cycles", "1").toInt().coerceIn(1, 100)
        repeat(cycles) { cycle ->
        device.pressHome()
        device.wait(Until.gone(By.text("App is locked")), 10_000)
        val target = if (cycle % 2 == 0) "app.locker.fixture.blue" else "app.locker.fixture.red"
        instrumentation.targetContext.startActivity(Intent().setClassName(target, "app.locker.fixture.FixtureActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val button = device.wait(Until.findObject(By.text("Use fingerprint / strong biometric")), 15_000)
        assertNotNull("Biometric button missing on protected fixture", button)
        assertTrue("Biometric button disabled", button!!.isEnabled)
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Biometric bounds: ${button.visibleBounds}; clickable: ${button.isClickable}\n") })
        button.click()
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
        device.pressBack()
        assertTrue("Shield disappeared after cancellation", device.wait(Until.hasObject(By.text("App is locked")), 10_000))
        val finishDeadline = android.os.SystemClock.elapsedRealtime() + 5_000
        var hostPresent = true
        while (hostPresent && android.os.SystemClock.elapsedRealtime() < finishDeadline) {
            hostPresent = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).windows.any { window ->
                val root = window.root
                val isHost = window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION && root?.packageName?.toString() == "app.locker.plus"
                @Suppress("DEPRECATION") root?.recycle()
                isHost
            }
            if (hostPresent) android.os.SystemClock.sleep(50)
        }
        assertFalse("Biometric Activity did not finish behind restored shield", hostPresent)
        if ((cycle + 1) % 10 == 0) instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Completed ${cycle + 1}/$cycles fingerprint cancellation cycles\n") })
        }
        device.pressHome()
    }
}

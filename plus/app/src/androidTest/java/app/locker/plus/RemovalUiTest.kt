package app.locker.plus

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Uses only testbed credentials; intercepts ACTION_DELETE before Android can
 * remove anything. Does not modify the owner's production configuration. */
@RunWith(AndroidJUnit4::class)
class RemovalUiTest {
    @Test fun removalRequiresFreshCorrectPinForItsTarget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "app.sam.locker.testbed")
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        Configurator.getInstance().waitForIdleTimeout = 100
        val device = UiDevice.getInstance(instrumentation)
        val requests = CopyOnWriteArrayList<String>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_DELETE) return null
                requests.add(intent.dataString.orEmpty())
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            device.pressHome()
            context.app.config.prefs.edit().putBoolean("auto_pin", true).commit()
            context.app.vault.enroll("246813".toCharArray())
            instrumentation.runOnMainSync { context.app.config.toggle("app.locker.fixture.public", true) }
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            selectPin(device)
            enterPin(device, "246813")
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).text("Your locked apps")), 10_000))
            device.findObject(By.clazz("android.widget.EditText")).text = "Locker public fixture"
            device.wait(Until.findObject(By.pkg(context.packageName).text("Uninstall…")), 10_000).click()
            selectPin(device)
            assertTrue("An already-authorized dashboard must not initiate uninstall", requests.isEmpty())
            enterPin(device, "000000")
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).text("Incorrect PIN")), 10_000))
            assertTrue("Wrong PIN initiated uninstall", requests.isEmpty())
            enterPin(device, "246813")
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).text("Your locked apps")), 10_000))
            assertEquals(listOf("package:app.locker.fixture.public"), requests.toList())
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Existing dashboard auth and wrong PIN produced zero removal requests; fresh correct PIN produced exactly the selected package request. Android removal was intercepted, not executed.\n") })
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { context.app.config.toggle("app.locker.fixture.public", false) }
            device.pressHome()
        }
    }
    private fun selectPin(device: UiDevice) {
        val systemPin = device.wait(Until.findObject(By.text("Use PIN")), 5_000)
        if (systemPin != null) systemPin.click()
        else device.wait(Until.findObject(By.text("Use PIN instead")), 5_000)?.click()
        assertNotNull(device.wait(Until.findObject(By.desc("Clear")), 10_000))
    }
    private fun enterPin(device: UiDevice, pin: String) {
        device.findObject(By.desc("Clear")).click()
        pin.forEach { device.findObject(By.desc(it.toString())).click() }
        // Automatic confirmation; no Continue tap.
    }
}

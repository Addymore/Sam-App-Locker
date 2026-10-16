package app.locker.plus

import android.app.UiAutomation
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual switch interactions without changing the owner's normal app or private PIN. */
@RunWith(AndroidJUnit4::class)
class PickerUiTest {
    @Test fun switchesUpdateWithoutLeavingScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".testbed"))
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        Configurator.getInstance().waitForIdleTimeout = 100
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.pressHome()
        assertTrue(device.wait(Until.gone(By.text("App is locked")), 10_000))
        context.app.vault.enroll("246813".toCharArray())
        instrumentation.runOnMainSync { context.app.config.toggle("app.locker.fixture.red", false) }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).text("Welcome back")), 15_000))
        device.wait(Until.findObject(By.desc("Clear")), 15_000).click()
        for ((index, digit) in "246813".withIndex()) {
            device.wait(Until.findObject(By.desc(digit.toString())), 15_000).click()
            assertTrue("PIN key did not update", device.wait(Until.hasObject(By.desc("PIN, ${index + 1} digits entered")), 2_000))
        }
        device.findObject(By.text("Continue")).click()
        val search = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 15_000)
        assertNotNull("App picker did not open after test PIN", search)
        search!!.click()
        search.text = "Locker test fixture"
        // Back before the IME attaches can close the picker instead. Observe the
        // actual keyboard window, preserving the owner's accessibility service.
        val keyboardDeadline = android.os.SystemClock.elapsedRealtime() + 3_000
        var keyboardVisible = false
        while (!keyboardVisible && android.os.SystemClock.elapsedRealtime() < keyboardDeadline) {
            keyboardVisible = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            if (!keyboardVisible) android.os.SystemClock.sleep(50)
        }
        if (keyboardVisible) device.pressBack()
        assertTrue("Picker closed while dismissing keyboard", device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 2_000))
        val selector = By.desc("Lock Locker test fixture, app.locker.fixture.red")
        if (!device.hasObject(selector) && device.hasObject(By.scrollable(true))) {
            UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().description("Lock Locker test fixture, app.locker.fixture.red"))
        }
        assertNotNull("Synthetic app switch missing", device.wait(Until.findObject(selector), 15_000))
        repeat(20) { index ->
            val expected = index % 2 == 0
            val started = android.os.SystemClock.elapsedRealtime()
            val control = device.findObject(selector)
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Before toggle $index: bounds=${control.visibleBounds}, clickable=${control.isClickable}, checkable=${control.isCheckable}, checked=${control.isChecked}, enabled=${control.isEnabled}\n") })
            control.click()
            val updated = device.wait(Until.hasObject(By.desc("Lock Locker test fixture, app.locker.fixture.red").checked(expected)), 2_000)
            instrumentation.runOnMainSync {
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "After toggle $index: prefs=${"app.locker.fixture.red" in context.app.config.apps()}, state=${"app.locker.fixture.red" in context.app.config.state.value.apps}\n") })
            }
            assertTrue("Switch did not visually update at toggle $index", updated)
            instrumentation.runOnMainSync {
                assertEquals("Persisted choice disagrees with switch", expected, "app.locker.fixture.red" in context.app.config.apps())
                assertEquals("Observable state disagrees with switch", expected, "app.locker.fixture.red" in context.app.config.state.value.apps)
            }
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Toggle $index visual+stored acknowledgement: ${android.os.SystemClock.elapsedRealtime() - started} ms\n") })
        }
        device.pressHome()
    }
}

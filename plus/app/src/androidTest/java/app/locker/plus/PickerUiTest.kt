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
        context.app.config.prefs.edit().putBoolean("auto_pin", false).commit()
        context.app.vault.enroll("246813".toCharArray())
        instrumentation.runOnMainSync { context.app.config.toggle("app.locker.fixture.red", false) }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // Samsung may hide the activity root while its biometric prompt is modal.
        // Observe the prompt/PIN action rather than the obscured Welcome back heading.
        // Management authentication is now fingerprint-first. The isolated test
        // uses only its own synthetic PIN and explicitly selects the fallback.
        device.wait(Until.findObject(By.text("Other methods")), 5_000)?.click()
        device.wait(Until.findObject(By.pkg(context.packageName).text("Use PIN")), 5_000)?.click()
        device.wait(Until.findObject(By.desc("Clear")), 15_000).click()
        for ((index, digit) in "246813".withIndex()) {
            device.wait(Until.findObject(By.desc(digit.toString())), 15_000).click()
            assertTrue("PIN key did not update", device.wait(Until.hasObject(By.desc("PIN, ${index + 1} digits entered")), 2_000))
        }
        device.findObject(By.text("Continue")).click()
        device.wait(Until.findObject(By.pkg(context.packageName).text("Unlocked")), 15_000).click()
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
        if (keyboardVisible) {
            fun stableSearchTop(): Int {
                val end = android.os.SystemClock.elapsedRealtime() + 3_000
                var previous = Int.MIN_VALUE; var repeats = 0
                while (android.os.SystemClock.elapsedRealtime() < end) {
                    val current = device.findObject(By.clazz("android.widget.EditText")).visibleBounds.top
                    repeats = if (current == previous) repeats + 1 else 0
                    previous = current
                    if (repeats >= 3) return current
                    android.os.SystemClock.sleep(50)
                }
                fail("Search layout never settled")
                return previous
            }
            val top = stableSearchTop()
            for (query in listOf("L", "Lo", "Locker", "", "Locker test fixture")) {
                device.findObject(By.clazz("android.widget.EditText")).text = query
                assertEquals("Search moved when suggestions/results changed", top, stableSearchTop())
                assertTrue("Search lost keyboard focus", device.findObject(By.clazz("android.widget.EditText")).isFocused)
            }
        }
        if (keyboardVisible) device.pressBack()
        assertTrue("Picker closed while dismissing keyboard", device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 2_000))
        val selector = By.desc("Lock Locker test fixture, app.locker.fixture.red")
        if (!device.hasObject(selector) && device.hasObject(By.scrollable(true))) {
            UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().description("Lock Locker test fixture, app.locker.fixture.red"))
        }
        assertNotNull("Synthetic app switch missing", device.wait(Until.findObject(selector), 15_000))
        captureTestbed("unlocked-apps.png", device)
        repeat(20) { index ->
            val expected = index % 2 == 0
            val started = android.os.SystemClock.elapsedRealtime()
            val control = device.findObject(selector)
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Before toggle $index: bounds=${control.visibleBounds}, clickable=${control.isClickable}, checkable=${control.isCheckable}, checked=${control.isChecked}, enabled=${control.isEnabled}\n") })
            control.click()
            assertTrue("App did not leave its previous tab", device.wait(Until.gone(selector), 2_000))
            device.findObject(By.pkg(context.packageName).text(if (expected) "Locked" else "Unlocked")).click()
            val nextSearch = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)
            nextSearch.text = "Locker test fixture"
            val updated = device.wait(Until.hasObject(By.desc("Lock Locker test fixture, app.locker.fixture.red").checked(expected)), 2_000)
            instrumentation.runOnMainSync {
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "After toggle $index: prefs=${"app.locker.fixture.red" in context.app.config.apps()}, state=${"app.locker.fixture.red" in context.app.config.state.value.apps}\n") })
            }
            assertTrue("Switch did not visually update at toggle $index", updated)
            if (index == 0) captureTestbed("locked-apps.png", device)
            instrumentation.runOnMainSync {
                assertEquals("Persisted choice disagrees with switch", expected, "app.locker.fixture.red" in context.app.config.apps())
                assertEquals("Observable state disagrees with switch", expected, "app.locker.fixture.red" in context.app.config.state.value.apps)
            }
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Toggle $index visual+stored acknowledgement: ${android.os.SystemClock.elapsedRealtime() - started} ms\n") })
        }
        device.pressHome()
    }

    /** Screenshots are opt-in and limited to our isolated synthetic testbed.
     * Never clears FLAG_SECURE on the owner's production installation. */
    private fun captureTestbed(name: String, device: UiDevice) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureUi", "false") != "true") return
        val context = instrumentation.targetContext
        check(context.packageName == "app.sam.locker.testbed")
        instrumentation.runOnMainSync {
            val activities = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
            val activity = activities.single { it is MainActivity }
            check(activity.packageName == context.packageName)
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        assertTrue(device.hasObject(By.pkg(context.packageName).text("Sam App Locker")))
        device.waitForIdle()
        assertTrue(device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), name)))
    }
}

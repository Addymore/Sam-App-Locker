package app.locker.plus

import android.app.UiAutomation
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Only synthetic public content. Operational log capture additionally detects
 * short shield attachments that UI hierarchy sampling could miss. */
@RunWith(AndroidJUnit4::class)
class UnprotectedUiTest {
    @Test fun publicAppAndKeyboardDoNotShowLocker() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName.endsWith(".testbed"))
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        Configurator.getInstance().waitForIdleTimeout = 100
        val device = UiDevice.getInstance(instrumentation)
        val locker = InstrumentationRegistry.getArguments().getString("lockerPackage", "app.sam.locker")
        require(locker in setOf("app.sam.locker.debug", "app.sam.locker"))
        repeat(100) { cycle ->
            device.pressHome()
            instrumentation.targetContext.startActivity(Intent(Intent.ACTION_MAIN)
                .setClassName("app.locker.fixture.public", "app.locker.fixture.FixtureActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue(device.wait(Until.hasObject(By.pkg("app.locker.fixture.public")), 5_000))
            if (cycle % 10 == 0) {
                device.findObject(By.pkg("app.locker.fixture.public").clazz("android.widget.EditText")).click()
            }
            assertFalse("Locker appeared over an unprotected fixture at cycle $cycle",
                device.wait(Until.hasObject(By.pkg(locker)), 500))
            if (cycle % 10 == 0) device.pressBack()
            if ((cycle + 1) % 10 == 0) instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Completed ${cycle + 1}/100 unprotected cycles\n") })
        }
        device.pressHome()
    }
}

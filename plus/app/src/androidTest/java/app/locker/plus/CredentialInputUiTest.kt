package app.locker.plus
import android.content.Intent
import android.graphics.Point
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class CredentialInputUiTest {
    @Test fun fallbackIsImmediatelyUsableForEveryMethod() {
        val i = InstrumentationRegistry.getInstrumentation(); val context = i.targetContext
        check(context.packageName.endsWith(".testbed"))
        Configurator.getInstance().uiAutomationFlags = android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        val d = UiDevice.getInstance(i)
        for (kind in listOf("PIN", "PASSWORD", "PATTERN")) {
            val activity = i.startActivitySync(Intent().setClassName(context.packageName, "app.locker.plus.CredentialHarnessActivity")
                .putExtra("kind", kind).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            try {
                assertTrue(d.wait(Until.hasObject(By.text("Synthetic test")), 10_000))
                // This harness contains only fixed synthetic data, never owner input.
                d.takeScreenshot(java.io.File(context.filesDir, "credential-${kind.lowercase()}.png"))
                when (kind) {
                    "PIN" -> {
                        assertNotNull("PIN must be visible without a chooser tap", d.findObject(By.desc("Clear")))
                        "246813".forEach { d.findObject(By.desc(it.toString())).click() }
                    }
                    "PASSWORD" -> {
                        val field = d.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)
                        assertNotNull("Password field must be shown directly", field)
                        field.click()
                        field.text = "Kigali words 123!"
                        d.pressEnter()
                    }
                    else -> {
                        val points = listOf(1, 3, 6, 9).map {
                            val dot = d.wait(Until.findObject(By.desc("Pattern dot $it")), 5_000)
                            assertNotNull(dot)
                            val b = dot.visibleBounds; Point(b.centerX(), b.centerY())
                        }
                        assertTrue(d.swipe(points.toTypedArray(), 20))
                    }
                }
                assertTrue("Credential input failed: $kind", d.wait(Until.hasObject(By.text("Synthetic credential accepted")), 5_000))
            } finally { i.runOnMainSync { activity.finish() } }
        }
        d.pressHome()
    }
}

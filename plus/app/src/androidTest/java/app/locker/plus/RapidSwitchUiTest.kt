package app.locker.plus
import android.app.UiAutomation
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RapidSwitchUiTest {
    @Test fun abandonProtectedEntryWithoutWaitingForAuthentication() {
        val inst = InstrumentationRegistry.getInstrumentation()
        check(inst.targetContext.packageName == "app.sam.locker.testbed")
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        Configurator.getInstance().waitForIdleTimeout = 0
        val device = UiDevice.getInstance(inst)
        device.pressHome()
        fun launch(pkg: String) {
            val result = device.executeShellCommand("am start --user 0 -a android.intent.action.MAIN -n $pkg/app.locker.fixture.FixtureActivity")
            inst.sendStatus(0, android.os.Bundle().apply { putString("stream", result) })
            check(!result.contains("Error") && !result.contains("Exception")) { result }
        }
        try {
            repeat(100) { i ->
                launch(if (i % 2 == 0) "app.locker.fixture.blue" else "app.locker.fixture.red")
                assertTrue("No locker window on protected entry $i", device.wait(Until.hasObject(By.pkg(java.util.regex.Pattern.compile("app\\.sam\\.locker|com\\.samsung\\.android\\.biometrics\\.app\\.setting"))), 5000))
                // Switch while the overlay or automatic biometric host is still entering.
                launch("app.locker.fixture.public")
                assertTrue("Stuck locker after rapid exit $i", device.wait(Until.gone(By.pkg("app.sam.locker")), 5000))
                assertTrue("Public fixture absent $i", device.wait(Until.hasObject(By.pkg("app.locker.fixture.public")), 3000))
                if ((i + 1) % 10 == 0) inst.sendStatus(0, android.os.Bundle().apply { putString("stream", "Rapid exits: ${i + 1}/100\n") })
            }
        } catch (failure: Throwable) {
            val focus = device.executeShellCommand("dumpsys window displays").lineSequence().filter { it.contains("mCurrentFocus=") || it.contains("mFocusedApp=") }.joinToString("\n")
            inst.sendStatus(0, android.os.Bundle().apply { putString("stream", focus) })
            throw failure
        } finally { device.pressHome() }
    }
}

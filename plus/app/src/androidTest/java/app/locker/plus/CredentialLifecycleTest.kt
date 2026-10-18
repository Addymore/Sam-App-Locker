package app.locker.plus

import android.app.UiAutomation
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic vault only. Tests the real settings flow while credential work is blocked. */
@RunWith(AndroidJUnit4::class)
class CredentialLifecycleTest {
    @Test fun backgroundingCredentialChangeCannotReuseEnrollmentWhileVaultIsBusy() {
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        check(context.packageName == "app.sam.locker.testbed")
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        Configurator.getInstance().waitForIdleTimeout = 100
        val d = UiDevice.getInstance(i)
        val app = context.app
        app.credentials.submit { app.vault.enroll("246813".toCharArray()) }.get(10, TimeUnit.SECONDS)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = i.startActivitySync(intent)
        val release = CountDownLatch(1)
        try {
            if (d.wait(Until.hasObject(By.pkg("com.samsung.android.biometrics.app.setting")), 5_000)) d.pressBack()
            assertTrue(d.wait(Until.hasObject(By.text("Welcome back")), 5_000))
            "246813".forEach { d.findObject(By.desc(it.toString())).click() }
            d.wait(Until.findObject(By.text("Settings")), 5_000).click()
            val change = "Change PIN, pattern or password"
            UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(change))
            d.findObject(By.text(change)).click()
            assertTrue(d.wait(Until.hasObject(By.text("Choose your app lock")), 5_000))
            val entered = CountDownLatch(1)
            app.credentials.execute { entered.countDown(); check(release.await(30, TimeUnit.SECONDS)) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            d.pressHome()
            assertTrue(d.wait(Until.gone(By.pkg(context.packageName)), 5_000))
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            assertTrue(d.wait(Until.hasObject(By.pkg(context.packageName)), 5_000))
            assertFalse("Stale enrollment remained interactive", d.hasObject(By.text("Choose your app lock")))
            assertFalse("Credential keypad remained usable before reauthentication", d.hasObject(By.desc("Clear")))
        } finally {
            release.countDown()
            i.runOnMainSync { activity.finish() }
            d.pressHome()
        }
        assertTrue(app.credentials.submit<Boolean> { app.vault.verify("246813".toCharArray()).success }.get(10, TimeUnit.SECONDS))
    }
}

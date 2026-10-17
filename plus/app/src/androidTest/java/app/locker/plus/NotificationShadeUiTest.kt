package app.locker.plus

import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Root package identities only; no notification text, children, screenshots or hierarchy dumps.
 * Capture PID-scoped locker diagnostics alongside this test to detect shorter attachments. */
@RunWith(AndroidJUnit4::class)
class NotificationShadeUiTest {
    @Test fun shadeDoesNotCreateALockerWindow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName.endsWith(".testbed"))
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        val device = UiDevice.getInstance(instrumentation)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val args = InstrumentationRegistry.getArguments()
        val locker = args.getString("lockerPackage", "app.sam.locker")!!
        require(locker in setOf("app.sam.locker", "app.sam.locker.debug"))
        // For this mode, the owner must unlock blue before the test starts. Never enter a PIN here.
        val protectedMode = args.getString("shadeTarget", "public") == "already-unlocked-blue"
        var previousShape = ""
        fun recordShape() {
            val shape = automation.windows.joinToString(";") { window ->
                val root = window.root
                val owner = when (root?.packageName?.toString()) {
                    null -> "unknown"
                    locker -> "locker"
                    "com.android.systemui" -> "systemui"
                    "app.locker.fixture.blue", "app.locker.fixture.public" -> "fixture"
                    else -> "other"
                }
                @Suppress("DEPRECATION")
                root?.recycle()
                "${window.type}:$owner:active=${window.isActive}:focused=${window.isFocused}"
            }
            if (shape != previousShape) {
                previousShape = shape
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Window metadata: $shape\n") })
            }
        }
        fun owners(): Set<String> = automation.windows.mapNotNull { window ->
            val root = window.root
            val pkg = root?.packageName?.toString()
            @Suppress("DEPRECATION")
            root?.recycle()
            pkg
        }.toSet()
        fun panelActive(): Boolean = automation.windows.any { window ->
            if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM || (!window.isActive && !window.isFocused)) false
            else {
                val root = window.root
                val matches = root?.packageName?.toString() == "com.android.systemui"
                @Suppress("DEPRECATION")
                root?.recycle()
                matches
            }
        }
        fun observe(duration: Long, requirePanel: Boolean) {
            val deadline = SystemClock.elapsedRealtime() + duration
            var panelSeen = false
            while (SystemClock.elapsedRealtime() < deadline) {
                recordShape()
                val packages = owners()
                assertFalse("Locker window appeared while using notification shade", locker in packages)
                panelSeen = panelSeen || panelActive()
                SystemClock.sleep(16) // Bounded test sampling, never part of production detection.
            }
            if (requirePanel) assertTrue("System UI panel was not observed", panelSeen)
        }
        if (protectedMode) {
            assertTrue("Owner must first unlock blue", "app.locker.fixture.blue" in owners())
            assertFalse("Authenticate before starting this test", locker in owners())
        }
        try {
            repeat(100) { cycle ->
                if (!protectedMode) {
                    device.pressHome()
                    if (cycle % 2 == 0) {
                        val result = device.executeShellCommand("am start --user 0 -W -n app.locker.fixture.public/app.locker.fixture.FixtureActivity")
                        assertTrue("Synthetic public launch failed", result.contains("Status: ok"))
                    }
                }
                device.executeShellCommand(if (cycle % 4 < 2) "cmd statusbar expand-notifications" else "cmd statusbar expand-settings")
                observe(1_000, true)
                device.executeShellCommand("cmd statusbar collapse")
                observe(500, false)
                if (protectedMode) assertTrue("Protected fixture missing after collapse", "app.locker.fixture.blue" in owners())
                if ((cycle + 1) % 10 == 0) instrumentation.sendStatus(0, android.os.Bundle().apply {
                    putString("stream", "Completed ${cycle + 1}/100 shade cycles\n")
                })
            }
        } finally {
            device.executeShellCommand("cmd statusbar collapse")
            device.pressHome()
        }
    }
}

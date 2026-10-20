package app.locker.plus

import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.toArgb
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AppearanceTest {
    @Test fun presetsKeepOpaqueSurfacesAndReadablePrimaryText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (style in VisualStyle.entries) for (dark in listOf(false, true)) {
            val colors = lockerColors(context, Appearance(style = style, dynamic = false), dark)
            for ((foreground, background) in listOf(colors.onPrimary to colors.primary,
                colors.onSurface to colors.surface, colors.onBackground to colors.background,
                colors.onPrimaryContainer to colors.primaryContainer)) {
                assertEquals(255, android.graphics.Color.alpha(background.toArgb()))
                assertTrue("Low contrast: $style / dark=$dark", ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb()) >= 4.5)
            }
        }
    }
    @Test fun appearanceSurvivesConfigRecreationWithoutChangingProtectedApps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".testbed"))
        val config = context.app.config
        val before = config.state.value.appearance
        val apps = config.apps()
        try {
            val choice = Appearance(VisualStyle.ONE_UI, ColorMode.DARK, Backdrop.OCEAN, false)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { config.setAppearance(choice) }
            assertEquals(choice, Config(context).appearance())
            assertEquals(apps, config.apps())
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { config.setAppearance(before) } }
    }
}

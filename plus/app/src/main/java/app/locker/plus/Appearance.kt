package app.locker.plus

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val LocalAppearance = staticCompositionLocalOf { Appearance() }
val LocalCredentialHaptics = staticCompositionLocalOf { true }
fun Appearance.isDark(system: Boolean) = when (mode) { ColorMode.SYSTEM -> system; ColorMode.DARK -> true; ColorMode.LIGHT -> false }

/** Shared by the immediate native shield and Compose; all base colors are opaque. */
fun lockerColors(context: Context, appearance: Appearance, dark: Boolean): ColorScheme {
    if (appearance.dynamic && appearance.style == VisualStyle.MATERIAL)
        return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val primary = when (appearance.style) {
        VisualStyle.MATERIAL -> if (dark) 0xFFB8C9FF else 0xFF435B97
        VisualStyle.ONE_UI -> if (dark) 0xFFA8C8FF else 0xFF215DB0
        VisualStyle.COLOR_OS -> if (dark) 0xFF86DCC4 else 0xFF006B55
    }
    return if (dark) darkColorScheme(primary = Color(primary), onPrimary = Color(0xFF102C36),
        primaryContainer = Color(0xFF253C51), onPrimaryContainer = Color(0xFFE1EEFF),
        secondary = Color(0xFFB2CCC6), secondaryContainer = Color(0xFF293C40),
        background = Color(0xFF10141B), surface = Color(0xFF191E27), surfaceContainer = Color(0xFF222832))
    else lightColorScheme(primary = Color(primary), onPrimary = Color.White,
        primaryContainer = if (appearance.style == VisualStyle.COLOR_OS) Color(0xFFCBF2E5) else Color(0xFFDDE9FF),
        onPrimaryContainer = Color(0xFF123047), secondary = Color(0xFF426760), secondaryContainer = Color(0xFFDCEDE7),
        background = Color(0xFFF3F5FA), surface = Color.White, surfaceContainer = Color.White)
}
fun Context.shieldBackground() = lockerColors(this, app.config.state.value.appearance,
    app.config.state.value.appearance.isDark(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)).background

@Composable fun LockerTheme(dynamic: Boolean = true, appearance: Appearance = Appearance(dynamic = dynamic),
    haptics: Boolean = true, content: @Composable () -> Unit) {
    val dark = appearance.isDark(isSystemInDarkTheme())
    val colors = lockerColors(LocalContext.current, appearance, dark)
    val radius = if (appearance.style == VisualStyle.ONE_UI) 28.dp else 24.dp
    CompositionLocalProvider(LocalAppearance provides appearance, LocalCredentialHaptics provides haptics,
        LocalContentColor provides colors.onSurface) {
        MaterialTheme(colorScheme = colors, shapes = Shapes(small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(radius), large = RoundedCornerShape(32.dp), extraLarge = RoundedCornerShape(36.dp)), content = content)
    }
}

/** Predefined, offline backgrounds; never make the underlying protected app visible. */
@Composable fun LockerBackground(content: @Composable BoxScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val accent = when (LocalAppearance.current.background) {
        Backdrop.PLAIN -> c.background
        Backdrop.AURORA -> c.primaryContainer
        Backdrop.OCEAN -> c.secondaryContainer
        Backdrop.SUNSET -> c.tertiaryContainer
    }
    Box(Modifier.fillMaxSize().background(c.background).background(Brush.verticalGradient(listOf(accent, c.background, c.background))), content = content)
}

@Composable fun AppearancePicker(value: Appearance, onChange: (Appearance) -> Unit) {
    LockerCard("Make it yours") {
        Text("Style", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { VisualStyle.entries.forEach { style ->
            FilterChip(value.style == style, { onChange(value.copy(style = style)) }, label = { Text(style.label) })
        } }
        Text("Visual presets only. Your phone controls the fingerprint dialog.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ColorMode.entries.forEach { mode ->
            FilterChip(value.mode == mode, { onChange(value.copy(mode = mode)) }, label = { Text(mode.label) })
        } }
        Text("Background", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Backdrop.entries.forEach { background ->
            FilterChip(value.background == background, { onChange(value.copy(background = background)) }, label = { Text(background.label) })
        } }
        if (value.style == VisualStyle.MATERIAL) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Use wallpaper colors", Modifier.weight(1f))
            Switch(value.dynamic, { onChange(value.copy(dynamic = it)) })
        }
        Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Your private space", style = MaterialTheme.typography.titleLarge)
            Text("Changes apply to the dashboard and lock screen.", style = MaterialTheme.typography.bodyMedium)
        } }
    }
}

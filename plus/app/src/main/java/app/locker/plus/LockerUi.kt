package app.locker.plus

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable fun LockerTheme(dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(primary = Color(0xFFADC6EB), secondary = Color(0xFFB6D4CC), background = Color(0xFF11141C),
        surface = Color(0xFF191D28), surfaceContainer = Color(0xFF202532))
    else lightColorScheme(primary = Color(0xFF45658C), secondary = Color(0xFF486B62), background = Color(0xFFF4F6FC),
        surface = Color.White, surfaceContainer = Color.White, primaryContainer = Color(0xFFE0EAF7))
    MaterialTheme(colorScheme = colors, shapes = Shapes(small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(32.dp), extraLarge = RoundedCornerShape(36.dp)), content = content)
}

@Composable fun LockerCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable fun PrivacyMark(modifier: Modifier = Modifier, fingerprint: Boolean = false) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(64.dp)) {
        val w = size.width; val h = size.height; val stroke = Stroke(w * .045f, cap = StrokeCap.Round)
        if (fingerprint) {
            for (i in 0..3) {
                val inset = w * (.12f + i * .095f)
                drawArc(color, 160f, 225f, false, Offset(inset, inset), Size(w - inset * 2, h - inset * 2), style = stroke)
            }
            drawPath(Path().apply {
                moveTo(w * .52f, h * .46f)
                cubicTo(w * .54f, h * .65f, w * .43f, h * .79f, w * .36f, h * .87f)
                moveTo(w * .65f, h * .48f)
                cubicTo(w * .68f, h * .66f, w * .59f, h * .84f, w * .54f, h * .91f)
                moveTo(w * .77f, h * .55f)
                cubicTo(w * .77f, h * .72f, w * .72f, h * .82f, w * .7f, h * .85f)
            }, color, style = stroke)
        } else {
            drawPath(Path().apply {
                moveTo(w * .5f, h * .1f); lineTo(w * .84f, h * .23f); lineTo(w * .81f, h * .55f)
                quadraticTo(w * .76f, h * .79f, w * .5f, h * .92f)
                quadraticTo(w * .24f, h * .79f, w * .19f, h * .55f)
                lineTo(w * .16f, h * .23f); close()
            }, color, style = stroke)
            drawPath(Path().apply { moveTo(w * .34f, h * .5f); lineTo(w * .46f, h * .62f); lineTo(w * .68f, h * .38f) }, color, style = stroke)
        }
    }
}

@Composable fun BiometricBackdrop(appName: String = "Your app") {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PrivacyMark(fingerprint = true, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(28.dp))
            Text("Your privacy, protected", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(appName, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable fun AuthPanel(title: String, subtitle: String, error: String = "", enabled: Boolean = true,
    identity: Any? = title, preferPin: Boolean = false, autoPinLength: Int = 0,
    onPin: (CharArray, () -> Unit) -> Unit, onBiometric: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null, exitLabel: String = "Return home", onPinVisibility: (Boolean) -> Unit = {}) {
    var pin by remember(identity) { mutableStateOf("") }
    var busy by remember(identity) { mutableStateOf(false) }
    var pinVisible by remember(identity) { mutableStateOf(onBiometric == null || preferPin) }
    LaunchedEffect(preferPin, onBiometric == null) { if (preferPin || onBiometric == null) pinVisible = true }
    LaunchedEffect(pinVisible) { onPinVisibility(pinVisible) }
    fun submit() {
        if (!enabled || busy || pin.length !in 6..12) return
        busy = true
        val value = pin.toCharArray(); pin = ""
        onPin(value) { busy = false }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Sam App Locker", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    PrivacyMark(fingerprint = onBiometric != null, modifier = Modifier.padding(18.dp).size(48.dp))
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (pinVisible) {
                    Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (pin.isEmpty()) "Enter PIN" else "●".repeat(pin.length),
                                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp).semantics { contentDescription = "PIN, ${pin.length} digits entered" })
                            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("Clear", "0", "⌫")).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { digit ->
                                        TextButton(onClick = { pin = when (digit) { "Clear" -> ""; "⌫" -> pin.dropLast(1); else -> (pin + digit).take(12) }; if (digit.length == 1 && shouldAutoSubmitPin(autoPinLength, pin.length)) submit() },
                                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { contentDescription = if (digit == "⌫") "Delete last digit" else digit },
                                            enabled = enabled && !busy) { Text(digit, style = if (digit == "Clear") MaterialTheme.typography.labelLarge else MaterialTheme.typography.headlineSmall) }
                                    }
                                }
                            }
                            if (autoPinLength == 0) Button(onClick = { submit() },
                                enabled = enabled && !busy && pin.length >= 6, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Continue") }
                        }
                    }
                    onBiometric?.let { action -> TextButton(onClick = { Diagnostics.record("biometric_button_tapped"); action() }, enabled = enabled && !busy) { Text("Use fingerprint") } }
                } else {
                    onBiometric?.let { action ->
                        Button(onClick = { Diagnostics.record("biometric_button_tapped"); action() }, enabled = enabled && !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Unlock with fingerprint") }
                    }
                    TextButton(onClick = { pinVisible = true }, enabled = enabled && !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use PIN instead") }
                }
                onHome?.let { TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) { Text(exitLabel) } }
            }
        }
    }
}

/** Small original line icons, with labels supplied by the navigation controls. */
@Composable fun NavigationMark(index: Int) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val fill = MaterialTheme.colorScheme.surface
    Canvas(Modifier.size(24.dp)) {
        val w = size.width; val stroke = Stroke(w * .075f, cap = StrokeCap.Round)
        when (index) {
            0, 1 -> {
                drawRoundRect(color, Offset(w * .2f, w * .43f), Size(w * .6f, w * .47f), androidx.compose.ui.geometry.CornerRadius(w * .1f), style = stroke)
                drawArc(color, if (index == 0) 180f else 165f, if (index == 0) 180f else 145f, false,
                    Offset(w * .32f, w * .08f), Size(w * .36f, w * .55f), style = stroke)
                drawLine(color, Offset(w * .5f, w * .6f), Offset(w * .5f, w * .73f), w * .075f, StrokeCap.Round)
            }
            else -> {
                for (i in 0..2) {
                    val y = w * (.24f + i * .26f); val x = w * (if (i == 1) .65f else .35f)
                    drawLine(color, Offset(w * .12f, y), Offset(w * .88f, y), w * .06f, StrokeCap.Round)
                    drawCircle(fill, w * .1f, Offset(x, y))
                    drawCircle(color, w * .1f, Offset(x, y), style = stroke)
                }
            }
        }
    }
}

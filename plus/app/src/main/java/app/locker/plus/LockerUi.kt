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
    credentialKind: CredentialKind = CredentialKind.PIN, onKindChange: ((CredentialKind) -> Unit)? = null,
    onPin: (CharArray, () -> Unit) -> Unit, onBiometric: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null, exitLabel: String = "Return home", onPinVisibility: (Boolean) -> Unit = {}) {
    var busy by remember(identity, credentialKind) { mutableStateOf(false) }
    // Touch-only credentials do not need window focus. Taking it can hide the
    // underlying application's window from Samsung's accessibility snapshot.
    LaunchedEffect(credentialKind) { onPinVisibility(credentialKind == CredentialKind.PASSWORD) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SAM APP LOCKER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    PrivacyMark(fingerprint = onBiometric != null, modifier = Modifier.padding(16.dp).size(40.dp))
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                onKindChange?.let { change ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CredentialKind.entries.forEach { kind -> FilterChip(selected = credentialKind == kind,
                            onClick = { if (!busy) change(kind) }, label = { Text(kind.label) }, enabled = !busy) }
                    }
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                // Never strand the user on a method chooser if the system prompt is delayed.
                CredentialInput(credentialKind, identity, enabled && !busy, autoPinLength) { value ->
                    if (!busy && enabled) { busy = true; onPin(value) { busy = false } } else value.fill('\u0000')
                }
                onBiometric?.let { action -> TextButton(onClick = { Diagnostics.record("biometric_button_tapped"); action() },
                    enabled = enabled && !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use fingerprint") } }
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

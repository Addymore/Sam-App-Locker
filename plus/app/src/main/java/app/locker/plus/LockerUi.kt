package app.locker.plus

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable fun LockerTheme(dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(primary = Color(0xFFCDC0FF), background = Color(0xFF17151D))
    else lightColorScheme(primary = Color(0xFF6554A4), background = Color(0xFFF6F3FA))
    MaterialTheme(colorScheme = colors, shapes = Shapes(medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(32.dp)), content = content)
}

@Composable fun LockerCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable fun AuthPanel(title: String, subtitle: String, error: String = "", enabled: Boolean = true,
    identity: Any? = title, onPin: (CharArray, () -> Unit) -> Unit, onBiometric: (() -> Unit)? = null, onHome: (() -> Unit)? = null) {
    var pin by remember(identity) { mutableStateOf("") }
    var busy by remember(identity) { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Sam App Locker", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                Text(if (pin.isEmpty()) "Enter your PIN" else "●".repeat(pin.length), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { contentDescription = "PIN, ${pin.length} digits entered" })
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("Clear", "0", "⌫")).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { digit ->
                            FilledTonalButton(onClick = {
                                pin = when (digit) { "Clear" -> ""; "⌫" -> pin.dropLast(1); else -> (pin + digit).take(12) }
                            }, modifier = Modifier.weight(1f).heightIn(min = 58.dp).semantics { contentDescription = if (digit == "⌫") "Delete last digit" else digit }, enabled = enabled && !busy) {
                                Text(digit, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
                Button(onClick = { busy = true; val value = pin.toCharArray(); pin = ""; onPin(value) { busy = false } },
                    enabled = enabled && !busy && pin.length >= 6, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Continue") }
                onBiometric?.let { TextButton(onClick = it, enabled = enabled && !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use fingerprint / strong biometric") } }
                onHome?.let { TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) { Text("Return home") } }
            }
        }
    }
}

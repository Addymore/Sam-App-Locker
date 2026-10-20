package app.locker.plus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp

@Composable fun CredentialInput(kind: CredentialKind, identity: Any?, enabled: Boolean,
    autoPinLength: Int, onSubmit: (CharArray) -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val haptics = LocalCredentialHaptics.current
    fun touch() { if (haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
    var value by remember(identity, kind) { mutableStateOf("") }
    fun submit() {
        val secret = value.toCharArray()
        if (enabled && validCredential(secret, kind)) { value = ""; onSubmit(secret) } else secret.fill('\u0000')
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (kind) {
                CredentialKind.PIN -> {
                    Text(if (value.isEmpty()) "Enter PIN" else "●".repeat(value.length), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 12.dp).semantics { contentDescription = "PIN, ${value.length} digits entered" })
                    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("Clear", "0", "⌫")).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { digit -> TextButton(onClick = {
                                touch()
                                value = when (digit) { "Clear" -> ""; "⌫" -> value.dropLast(1); else -> (value + digit).take(12) }
                                if (digit.length == 1 && shouldAutoSubmitPin(autoPinLength, value.length)) submit()
                            }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics {
                                contentDescription = if (digit == "⌫") "Delete last digit" else digit
                            }) { Text(digit, style = if (digit == "Clear") MaterialTheme.typography.labelLarge else MaterialTheme.typography.headlineSmall) } }
                        }
                    }
                }
                CredentialKind.PASSWORD -> OutlinedTextField(value, { if (it.length <= 64) value = it },
                    label = { Text("Password") }, enabled = enabled, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }), modifier = Modifier.fillMaxWidth())
                CredentialKind.PATTERN -> {
                    Text("Draw your pattern", style = MaterialTheme.typography.titleMedium)
                    val color = MaterialTheme.colorScheme.primary
                    val latestSubmit by rememberUpdatedState({ submit() })
                    fun add(dot: Int) {
                        val next = appendPattern(value.map { it - '0' }, dot).joinToString("")
                        if (next != value) touch()
                        value = next
                    }
                    Box(Modifier.sizeIn(maxWidth = 288.dp, maxHeight = 288.dp).fillMaxWidth().aspectRatio(1f)
                        .pointerInput(enabled, identity) {
                            if (enabled) detectDragGestures(onDragStart = { point ->
                                value = ""
                                val col = (point.x / (size.width / 3f)).toInt().coerceIn(0, 2)
                                val row = (point.y / (size.height / 3f)).toInt().coerceIn(0, 2)
                                add(row * 3 + col)
                            }, onDrag = { change, _ ->
                                change.consume()
                                for (dot in 0..8) {
                                    val center = Offset((dot % 3 + .5f) * size.width / 3, (dot / 3 + .5f) * size.height / 3)
                                    if ((change.position - center).getDistance() < size.width / 9f) {
                                        add(dot)
                                    }
                                }
                            }, onDragEnd = { latestSubmit() }, onDragCancel = { value = "" })
                        }) {
                        Canvas(Modifier.matchParentSize()) {
                            val dots = value.map { it - '0' }
                            fun center(dot: Int) = Offset((dot % 3 + .5f) * size.width / 3, (dot / 3 + .5f) * size.height / 3)
                            dots.zipWithNext().forEach { (a, b) -> drawLine(color.copy(alpha = .45f), center(a), center(b), 5.dp.toPx()) }
                            repeat(9) { dot -> drawCircle(color.copy(alpha = if (dot in dots) 1f else .35f), if (dot in dots) 10.dp.toPx() else 6.dp.toPx(), center(dot)) }
                        }
                        Column(Modifier.fillMaxSize()) { repeat(3) { row ->
                            Row(Modifier.weight(1f)) { repeat(3) { col ->
                                TextButton(onClick = { add(row * 3 + col) }, enabled = enabled,
                                    modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "Pattern dot ${row * 3 + col + 1}" }) { }
                            } }
                        } }
                    }
                    TextButton(onClick = { value = "" }, enabled = enabled) { Text("Clear pattern") }
                }
            }
            if (kind != CredentialKind.PIN || autoPinLength == 0) Button(onClick = { submit() },
                enabled = enabled && validCredential(value.toCharArray(), kind), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Continue") }
        }
    }
}

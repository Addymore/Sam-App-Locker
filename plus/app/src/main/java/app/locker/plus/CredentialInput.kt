package app.locker.plus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

@Composable fun CredentialInput(
    kind: CredentialKind,
    identity: Any?,
    enabled: Boolean,
    autoPinLength: Int,
    hidePatternTrail: Boolean = false,
    scramblePin: Boolean = false,
    onSubmit: (CharArray) -> Unit
) {
    var value by remember(identity, kind) { mutableStateOf("") }
    var autoSubmitted by remember(identity, kind) { mutableStateOf(false) }
    var dragPosition by remember(identity, kind) { mutableStateOf<Offset?>(null) }
    val haptic = LocalHapticFeedback.current
    val useHaptics = LocalCredentialHaptics.current
    fun feedback() { if (useHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    fun submit() {
        val chars = value.toCharArray()
        if (enabled && validCredential(chars, kind)) { value = ""; dragPosition = null; onSubmit(chars) } else chars.fill('\u0000')
    }
    val latestSubmit by rememberUpdatedState {
        dragPosition = null
        if (validCredential(value.toCharArray(), kind)) submit()
        else if (kind == CredentialKind.PATTERN) value = ""
    }
    val shuffledDigits = remember(identity, scramblePin) {
        if (scramblePin) ('0'..'9').map { it.toString() }.shuffled()
        else listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    }
    val pinRows = remember(shuffledDigits) {
        listOf(
            shuffledDigits.subList(0, 3),
            shuffledDigits.subList(3, 6),
            shuffledDigits.subList(6, 9),
            listOf("Clear", shuffledDigits[9], "⌫")
        )
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (kind) {
                CredentialKind.PIN -> {
                    Text(if (value.isEmpty()) "6–12 digits" else "●".repeat(value.length),
                        style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { contentDescription = "PIN, ${value.length} digits entered" })
                    pinRows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { key ->
                                FilledTonalButton(onClick = {
                                    feedback()
                                    val next = when (key) { "Clear" -> ""; "⌫" -> value.dropLast(1); else -> (value + key).take(12) }
                                    val shouldSubmit = enabled && !autoSubmitted && next.length > value.length && shouldAutoSubmitPin(autoPinLength, next.length)
                                    value = next
                                    if (next.length < autoPinLength) autoSubmitted = false
                                    if (shouldSubmit) { autoSubmitted = true; submit() }
                                }, enabled = enabled, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 54.dp)
                                    .semantics { contentDescription = if (key == "⌫") "Backspace" else key }) {
                                    Text(key, style = if (key.length == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                CredentialKind.PASSWORD -> {
                    val focusRequester = remember { FocusRequester() }
                    val keyboardController = LocalSoftwareKeyboardController.current
                    LaunchedEffect(identity, enabled) {
                        if (enabled) {
                            runCatching { focusRequester.requestFocus() }
                            keyboardController?.show()
                        }
                    }
                    OutlinedTextField(value = value, onValueChange = { value = it.take(64) }, enabled = enabled, singleLine = true,
                        label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().focusRequester(focusRequester))
                }
                CredentialKind.PATTERN -> {
                    fun add(dot: Int) {
                        val next = appendPattern(value.map { it - '0' }, dot).joinToString("")
                        if (next != value) { feedback(); value = next }
                    }
                    val color = MaterialTheme.colorScheme.primary
                    Text(
                        if (hidePatternTrail) {
                            if (value.isEmpty()) "Stealth pattern active (trail hidden)" else "Drawing stealth pattern…"
                        } else {
                            if (value.isEmpty()) "Connect at least 4 dots" else "${value.length} dots connected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { contentDescription = "Pattern, ${value.length} dots selected" }
                    )
                    Box(Modifier.size(236.dp).semantics { contentDescription = "Pattern grid" }
                        .pointerInput(identity, enabled) {
                            if (!enabled) return@pointerInput
                            detectDragGestures(onDragStart = { offset ->
                                value = ""
                                dragPosition = offset
                                for (dot in 0..8) {
                                    val c = Offset((dot % 3 + .5f) * size.width / 3f, (dot / 3 + .5f) * size.height / 3f)
                                    if (hypot(offset.x - c.x, offset.y - c.y) <= size.width / 6.5f) add(dot)
                                }
                            }, onDrag = { change, _ ->
                                val p = change.position
                                dragPosition = p
                                for (dot in 0..8) {
                                    val c = Offset((dot % 3 + .5f) * size.width / 3f, (dot / 3 + .5f) * size.height / 3f)
                                    if (hypot(p.x - c.x, p.y - c.y) <= size.width / 6.5f) {
                                        add(dot)
                                        change.consume()
                                    }
                                }
                            }, onDragEnd = { latestSubmit() }, onDragCancel = { dragPosition = null; value = "" })
                        }) {
                        Canvas(Modifier.matchParentSize()) {
                            val dots = value.map { it - '0' }
                            fun center(dot: Int) = Offset((dot % 3 + .5f) * size.width / 3, (dot / 3 + .5f) * size.height / 3)
                            if (!hidePatternTrail) {
                                dots.zipWithNext().forEach { (a, b) -> drawLine(color.copy(alpha = .45f), center(a), center(b), 5.dp.toPx()) }
                                val currentDrag = dragPosition
                                if (currentDrag != null && dots.isNotEmpty()) {
                                    drawLine(color.copy(alpha = .3f), center(dots.last()), currentDrag, 4.dp.toPx())
                                }
                            }
                            repeat(9) { dot ->
                                val highlight = !hidePatternTrail && dot in dots
                                drawCircle(
                                    color = color.copy(alpha = if (highlight) 1f else .35f),
                                    radius = if (highlight) 10.dp.toPx() else 6.dp.toPx(),
                                    center = center(dot)
                                )
                            }
                        }
                        Column(Modifier.fillMaxSize()) { repeat(3) { row ->
                            Row(Modifier.weight(1f)) { repeat(3) { col ->
                                val dotIndex = row * 3 + col
                                val selected = ('0' + dotIndex) in value
                                TextButton(onClick = { add(dotIndex) }, enabled = enabled,
                                    modifier = Modifier.weight(1f).fillMaxHeight().semantics {
                                        contentDescription = "Pattern dot ${dotIndex + 1}"
                                        stateDescription = if (selected) "Selected" else "Not selected"
                                    }) { }
                            } }
                        } }
                    }
                    TextButton(onClick = { dragPosition = null; value = "" }, enabled = enabled) { Text("Clear pattern") }
                }
            }
            if (kind != CredentialKind.PIN || autoPinLength == 0) Button(onClick = { submit() },
                enabled = enabled && validCredential(value.toCharArray(), kind), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Continue") }
        }
    }
}

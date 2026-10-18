package app.locker.plus

enum class CredentialKind(val label: String, val hint: String) {
    PIN("PIN", "Choose 6–12 digits."),
    PATTERN("Pattern", "Connect 4–9 dots. Patterns are easier to observe than a long password."),
    PASSWORD("Password", "Use 8–64 characters, including letters and numbers. Spaces and symbols are welcome.")
}
fun validCredential(value: CharArray, kind: CredentialKind): Boolean = when (kind) {
    CredentialKind.PIN -> value.size in 6..12 && value.all { it in '0'..'9' }
    CredentialKind.PATTERN -> value.size in 4..9 && value.all { it in '0'..'8' } && value.toSet().size == value.size
    CredentialKind.PASSWORD -> value.size in 8..64 && value.any { it.isLetter() } && value.any { it.isDigit() }
}
/** Android-style midpoint inclusion, with no repeated dots. */
fun appendPattern(path: List<Int>, next: Int): List<Int> {
    if (next !in 0..8 || next in path) return path
    val last = path.lastOrNull() ?: return listOf(next)
    val dx = next % 3 - last % 3; val dy = next / 3 - last / 3
    val midpoint = if ((kotlin.math.abs(dx) == 2 && (dy == 0 || kotlin.math.abs(dy) == 2)) ||
        (kotlin.math.abs(dy) == 2 && dx == 0)) (last + next) / 2 else null
    return path + listOfNotNull(midpoint?.takeIf { it !in path }, next)
}

package app.locker.plus

/** Submit a complete saved-length entry, never each possible PIN prefix. */
fun shouldAutoSubmitPin(savedLength: Int, enteredLength: Int): Boolean =
    savedLength in 6..12 && enteredLength == savedLength

package app.locker.plus

/** A verified System UI system window may temporarily obscure all app roots.
 * This is not an app exit, nor evidence of a newly unidentified application window.
 * Never use a package event alone, or exempt unreadable application windows. */
object SystemPanelPolicy {
    fun preservesSnapshot(
        verifiedSystemUi: Boolean,
        applicationWindowCount: Int,
        previouslyUncertain: Boolean,
    ): Boolean = verifiedSystemUi && applicationWindowCount == 0 &&
        !previouslyUncertain
}

package app.locker.plus
import org.junit.Assert.*
import org.junit.Test
class PinEntryPolicyTest {
    @Test fun onlyCompleteSavedLengthSubmits() {
        for (saved in 6..12) for (entered in 0..12)
            assertEquals(saved == entered, shouldAutoSubmitPin(saved, entered))
    }
    @Test fun unknownLengthAndManualModeNeverAutoSubmit() {
        for (saved in listOf(0, -1, 5, 13)) for (entered in 0..12)
            assertFalse(shouldAutoSubmitPin(saved, entered))
    }
}

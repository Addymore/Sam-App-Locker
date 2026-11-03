package app.locker.plus

import org.junit.Assert.*
import org.junit.Test

class AuthPresentationTest {
    @Test fun nativePromptAlwaysSuppressesCredentialEvenAfterExplicitChoice() {
        for (available in listOf(true, false)) for (selected in listOf(true, false))
            assertFalse(credentialVisible(available, selected, biometricActive = true))
    }
    @Test fun cancellationDoesNotChooseCredential() {
        assertFalse(credentialVisible(true, explicitlySelected = false, biometricActive = false))
    }
    @Test fun explicitFallbackIsAvailableAndDevicesWithoutBiometricsAreNotStranded() {
        assertTrue(credentialVisible(true, true, false))
        assertTrue(credentialVisible(false, false, false))
    }
    @Test fun oldBiometricHostCannotAffectRetryForSameChallenge() {
        val attempts = BiometricHostAttempts()
        val first = attempts.begin()
        assertTrue(attempts.matches(first))
        val retry = attempts.begin()
        assertFalse(attempts.matches(first))
        assertTrue(attempts.matches(retry))
        assertFalse(attempts.matches(-1))
    }
    @Test fun invalidPersistedAppearanceUsesSafeDefaults() {
        assertEquals(VisualStyle.MATERIAL, savedEnum("removed-style", VisualStyle.MATERIAL))
        assertEquals(VisualStyle.ZENZY_VIP, savedEnum("ZENZY_VIP", VisualStyle.MATERIAL))
        assertEquals(VisualStyle.AMOLED, savedEnum("AMOLED", VisualStyle.MATERIAL))
        assertEquals(ColorMode.SYSTEM, savedEnum(null, ColorMode.SYSTEM))
        assertEquals(Backdrop.OCEAN, savedEnum("OCEAN", Backdrop.PLAIN))
        assertEquals(Backdrop.MIDNIGHT, savedEnum("MIDNIGHT", Backdrop.PLAIN))
    }
    @Test fun biometricAttemptGateCanTakeDoesNotConsumeToken() {
        val gate = BiometricAttemptGate()
        val c1 = LockEngine.Challenge("app.one", 1L)
        assertTrue(gate.canTake(c1))
        assertTrue(gate.canTake(c1))
        assertTrue(gate.take(c1))
        assertFalse(gate.canTake(c1))
        assertFalse(gate.take(c1))
        gate.retainVisible(emptySet())
        assertTrue(gate.canTake(c1))
        assertTrue(gate.take(c1))
        assertTrue(gate.releaseForSystemRetry(c1, maxRetries = 2))
        assertTrue(gate.canTake(c1))
        assertTrue(gate.take(c1))
        assertTrue(gate.releaseForSystemRetry(c1, maxRetries = 2))
        assertTrue(gate.take(c1))
        assertFalse(gate.releaseForSystemRetry(c1, maxRetries = 2))
        gate.clear("app.one")
        assertTrue(gate.canTake(c1))
    }
}

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
        assertEquals(ColorMode.SYSTEM, savedEnum(null, ColorMode.SYSTEM))
        assertEquals(Backdrop.OCEAN, savedEnum("OCEAN", Backdrop.PLAIN))
    }
}
